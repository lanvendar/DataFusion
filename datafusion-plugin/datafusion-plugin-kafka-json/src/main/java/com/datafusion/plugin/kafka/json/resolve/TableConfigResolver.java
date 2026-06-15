package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.kafka.json.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.kafka.json.expression.ExpressionEvaluator;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpec;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.expression.JsonType;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 目标 Paimon 表配置解析器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class TableConfigResolver {

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * 表达式执行器.
     */
    private final ExpressionEvaluator expressionEvaluator;

    TableConfigResolver(PaimonSinkConfig sink, ExpressionEvaluator expressionEvaluator) {
        this.sink = sink;
        this.expressionEvaluator = expressionEvaluator;
    }

    String resolveTableName(Object messageObject, PaimonTableConfig table, StandardSchema schema) {
        boolean useJobTableMetadata = TableMetadataRules.hasJobTableMetadata(table.table);
        return stringValue(evaluate(messageObject, tableSpec(table.table.name, schema.table.name, "name", JsonType.STRING,
                useJobTableMetadata), "sink.tables[].table.name"));
    }

    ResolvedTableConfig resolve(Object messageObject, PaimonTableConfig table, StandardSchema schema, String tableName) {
        boolean useJobTableMetadata = TableMetadataRules.hasJobTableMetadata(table.table);
        ResolvedTableConfig config = new ResolvedTableConfig();
        config.database = requiredString(evaluate(messageObject, ExpressionSpecNormalizer.constant(table.table.database, JsonType.STRING),
                "sink.tables[].table.database"), "sink.tables[].table.database");
        config.tableName = tableName;
        config.loadMode = LoadMode.parse(table.loadMode, LoadMode.parse(sink.loadMode, LoadMode.APPEND));
        config.tableComment = stringValue(evaluate(messageObject, tableSpec(table.table.comment, schema.table.comment, "comment",
                JsonType.STRING, useJobTableMetadata), "sink.tables[].table.comment"));
        Object createIfNotExists = evaluate(messageObject, tableSpec(table.table.createIfNotExists, schema.table.createIfNotExists,
                "createIfNotExists", JsonType.BOOLEAN, useJobTableMetadata), "sink.tables[].table.createIfNotExists");
        config.createIfNotExists = createIfNotExists == null || Boolean.TRUE.equals(createIfNotExists);
        config.partitionKeys = stringList(evaluate(messageObject, tableSpec(table.table.partitionKeys, schema.table.partitionKeys,
                "partitionKeys", JsonType.ARRAY, useJobTableMetadata), "sink.tables[].table.partitionKeys"),
                "sink.tables[].table.partitionKeys");
        config.columns = resolveColumns(schema.columns, table.columns);
        config.primaryKeysConfig = resolvePrimaryKeysConfig(schema.table.primaryKeys, table.table.primaryKeys, useJobTableMetadata);
        config.primaryKeyMode = config.primaryKeysConfig == null ? null : PrimaryKeyMode.parse(config.primaryKeysConfig.mode);
        config.primaryKeys = resolvePrimaryKeys(messageObject, config.primaryKeysConfig, config);
        config.options = new LinkedHashMap<>(sink.globalOptions());
        if (table.options != null) {
            config.options.putAll(table.options);
        }
        appendSystemColumns(config);
        validateLoadMode(config);
        return config;
    }

    private List<String> resolvePrimaryKeys(Object messageObject, PrimaryKeyConfig primaryKey, ResolvedTableConfig config) {
        if (primaryKey == null) {
            return new ArrayList<>();
        }
        PrimaryKeyMode mode = PrimaryKeyMode.parse(primaryKey.mode);
        List<String> fields = stringList(evaluate(messageObject, primaryKeySpec(primaryKey), "sink.tables[].table.primaryKeys"),
                "sink.tables[].table.primaryKeys");
        if (mode == PrimaryKeyMode.PROXY) {
            appendProxyColumn(config, ProxyPrimaryKeyType.parse(primaryKey.algorithm));
            List<String> primaryKeys = new ArrayList<>();
            primaryKeys.add(ProxyPrimaryKeyGenerator.FIELD_NAME);
            primaryKeys.addAll(config.partitionKeys);
            return primaryKeys;
        }
        if (mode == PrimaryKeyMode.FIELDS) {
            return fields;
        }
        throw new KafkaJsonPaimonException("Unsupported primaryKeys.mode: " + primaryKey.mode);
    }

    private ExpressionSpec primaryKeySpec(PrimaryKeyConfig primaryKey) {
        ExpressionSpec spec = new ExpressionSpec();
        spec.path = primaryKey.path;
        spec.defaultValue = primaryKey.defaultValue;
        spec.jsonType = "ARRAY";
        return spec;
    }

    private void appendProxyColumn(ResolvedTableConfig config, ProxyPrimaryKeyType type) {
        String fieldName = ProxyPrimaryKeyGenerator.FIELD_NAME;
        boolean exists = config.columns.stream().anyMatch(column -> fieldName.equals(column.name));
        if (exists) {
            return;
        }
        ColumnConfig column = new ColumnConfig();
        column.name = fieldName;
        column.dataType = "VARCHAR";
        column.length = proxyPrimaryKeyLength(type);
        column.nullable = false;
        column.comment = "代理主键";
        config.columns.add(0, column);
    }

    private void appendSystemColumns(ResolvedTableConfig config) {
        if (!Boolean.TRUE.equals(sink.includeKafkaMetadataFields)) {
            return;
        }
        config.columns.add(kafkaTopicColumn());
        config.columns.add(kafkaPartitionColumn());
        config.columns.add(kafkaOffsetColumn());
    }

    private ColumnConfig kafkaTopicColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = TableResolver.KAFKA_TOPIC_FIELD;
        column.dataType = "VARCHAR";
        column.length = 512;
        column.nullable = false;
        column.comment = "Kafka topic";
        return column;
    }

    private ColumnConfig kafkaPartitionColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = TableResolver.KAFKA_PARTITION_FIELD;
        column.dataType = "INT";
        column.nullable = false;
        column.comment = "Kafka partition";
        return column;
    }

    private ColumnConfig kafkaOffsetColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = TableResolver.KAFKA_OFFSET_FIELD;
        column.dataType = "BIGINT";
        column.nullable = false;
        column.comment = "Kafka offset";
        return column;
    }

    private void validateLoadMode(ResolvedTableConfig config) {
        if (config.partitionKeys == null || config.partitionKeys.isEmpty()) {
            throw new KafkaJsonPaimonException("Paimon partitionKeys is required: " + config.identifier());
        }
        if (config.loadMode == LoadMode.UPSERT && config.primaryKeyMode == null) {
            throw new KafkaJsonPaimonException("Paimon UPSERT requires primaryKeys: " + config.identifier());
        }
    }

    private ExpressionSpec tableSpec(JsonNode jobNode, Object schemaValue, String fieldName, JsonType jsonType, boolean useJobTableMetadata) {
        if (useJobTableMetadata) {
            return ExpressionSpecNormalizer.constant(jobNode, jsonType);
        }
        if (schemaValue instanceof JsonNode node && !node.isNull()) {
            return ExpressionSpecNormalizer.constant(node, jsonType);
        }
        ExpressionSpec spec = new ExpressionSpec();
        spec.jsonType = jsonType.name();
        spec.path = StandardSchemaParser.SCHEMA_TABLE_PATH + "." + fieldName;
        spec.defaultValue = schemaValue;
        return spec;
    }

    private List<ColumnConfig> resolveColumns(List<ColumnConfig> schemaColumns, List<ColumnConfig> jobColumns) {
        // Job columns are an explicit complete table definition. Once columns[] appears in job config,
        // do not merge it with Kafka schema.columns; missing fields would otherwise be silently hidden.
        List<ColumnConfig> source = jobColumns == null || jobColumns.isEmpty() ? schemaColumns : jobColumns;
        List<ColumnConfig> columns = new ArrayList<>();
        if (source == null) {
            return columns;
        }
        for (ColumnConfig column : source) {
            if (!TextUtils.isBlank(column.name)) {
                columns.add(column.copy());
            }
        }
        return columns;
    }

    private PrimaryKeyConfig resolvePrimaryKeysConfig(PrimaryKeyConfig schemaPrimaryKeys, PrimaryKeyConfig jobPrimaryKeys,
            boolean useJobTableMetadata) {
        PrimaryKeyConfig source = useJobTableMetadata ? jobPrimaryKeys : schemaPrimaryKeys;
        return source == null ? null : copyPrimaryKey(source);
    }

    private PrimaryKeyConfig copyPrimaryKey(PrimaryKeyConfig source) {
        PrimaryKeyConfig copy = new PrimaryKeyConfig();
        copy.mode = source.mode;
        copy.algorithm = source.algorithm;
        copy.path = source.path;
        copy.defaultValue = source.defaultValue;
        copy.jsonType = source.jsonType;
        return copy;
    }

    private Object evaluate(Object input, ExpressionSpec spec, String name) {
        return expressionEvaluator.evaluate(input, spec, name);
    }

    private String requiredString(Object value, String name) {
        String text = stringValue(value);
        if (TextUtils.isBlank(text)) {
            throw new KafkaJsonPaimonException(name + " is required");
        }
        return text;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> stringList(Object value, String name) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new KafkaJsonPaimonException(name + " must be string array");
        }
        List<String> values = new ArrayList<>();
        for (Object item : collection) {
            if (item == null || TextUtils.isBlank(String.valueOf(item))) {
                throw new KafkaJsonPaimonException(name + " must not contain blank item");
            }
            values.add(String.valueOf(item));
        }
        return values;
    }

    private int proxyPrimaryKeyLength(ProxyPrimaryKeyType type) {
        if (type == ProxyPrimaryKeyType.SHA_256) {
            return 64;
        }
        if (type == ProxyPrimaryKeyType.SHA_512) {
            return 128;
        }
        return 36;
    }
}
