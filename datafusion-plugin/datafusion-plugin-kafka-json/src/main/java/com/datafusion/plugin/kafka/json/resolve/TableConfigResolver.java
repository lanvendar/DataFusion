package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.config.TableMetadataRules;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.SystemFieldNames;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.kafka.json.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.kafka.json.expression.ExpressionEvaluator;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpec;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.core.enums.JsonType;
import com.datafusion.plugin.kafka.json.util.TextUtils;

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

    String resolveTableName(PaimonTableConfig table) {
        return stringValue(evaluate(null, ExpressionSpecNormalizer.constant(table.table.name, JsonType.STRING),
                "sink.tables[].table.name"));
    }

    ResolvedTableConfig resolve(Object messageObject, PaimonTableConfig table, StandardSchema schema, String tableName) {
        ResolvedTableConfig config = new ResolvedTableConfig();
        config.database = requiredString(evaluate(messageObject, ExpressionSpecNormalizer.constant(table.table.database, JsonType.STRING),
                "sink.tables[].table.database"), "sink.tables[].table.database");
        config.tableName = tableName;
        config.loadMode = LoadMode.parse(table.loadMode, LoadMode.parse(sink.loadMode, LoadMode.APPEND));
        boolean useJobTableMetadata = TableMetadataRules.hasJobTableMetadata(table.table);
        StandardSchema.StandardTableSchema schemaTable = schema == null ? null : schema.table;
        config.tableComment = stringValue(resolveTableMetadataValue(messageObject, useJobTableMetadata, table.table.comment,
                schemaTable == null ? null : schemaTable.comment, JsonType.STRING, "sink.tables[].table.comment"));
        Object createIfNotExists = resolveTableMetadataValue(messageObject, useJobTableMetadata, table.table.createIfNotExists,
                schemaTable == null ? null : schemaTable.createIfNotExists, JsonType.BOOLEAN, "sink.tables[].table.createIfNotExists");
        config.createIfNotExists = createIfNotExists == null || Boolean.TRUE.equals(createIfNotExists);
        config.partitionKeys = stringList(resolveTableMetadataValue(messageObject, useJobTableMetadata, table.table.partitionKeys,
                schemaTable == null ? null : schemaTable.partitionKeys, JsonType.ARRAY, "sink.tables[].table.partitionKeys"),
                "sink.tables[].table.partitionKeys");
        config.columns = resolveColumns(schema.columns, table.columns);
        config.primaryKeysConfig = resolvePrimaryKeysConfig(useJobTableMetadata, table.table.primaryKeys,
                schemaTable == null ? null : schemaTable.primaryKeys);
        config.primaryKeyMode = config.primaryKeysConfig == null ? null : PrimaryKeyMode.parse(config.primaryKeysConfig.mode);
        config.primaryKeys = resolvePrimaryKeys(messageObject, config.primaryKeysConfig, config);
        config.includeKafkaMetadataFields = table.table.includeKafkaMetadataFields;
        config.options = new LinkedHashMap<>(sink.globalOptions());
        if (table.options != null) {
            config.options.putAll(table.options);
        }
        appendSystemColumns(config);
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
        if (!Boolean.TRUE.equals(config.includeKafkaMetadataFields)) {
            return;
        }
        config.columns.add(kafkaTopicColumn());
        config.columns.add(kafkaPartitionColumn());
        config.columns.add(kafkaOffsetColumn());
    }

    private ColumnConfig kafkaTopicColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = SystemFieldNames.KAFKA_TOPIC_FIELD;
        column.dataType = "VARCHAR";
        column.length = 512;
        column.nullable = false;
        column.comment = "Kafka topic";
        return column;
    }

    private ColumnConfig kafkaPartitionColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = SystemFieldNames.KAFKA_PARTITION_FIELD;
        column.dataType = "INT";
        column.nullable = false;
        column.comment = "Kafka partition";
        return column;
    }

    private ColumnConfig kafkaOffsetColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = SystemFieldNames.KAFKA_OFFSET_FIELD;
        column.dataType = "BIGINT";
        column.nullable = false;
        column.comment = "Kafka offset";
        return column;
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

    private Object resolveTableMetadataValue(
            Object messageObject,
            boolean useJobTableMetadata,
            com.fasterxml.jackson.databind.JsonNode jobNode,
            Object schemaValue,
            JsonType jsonType,
            String name) {
        if (useJobTableMetadata) {
            return evaluate(messageObject, ExpressionSpecNormalizer.constant(jobNode, jsonType), name);
        }
        return schemaValue;
    }

    private PrimaryKeyConfig resolvePrimaryKeysConfig(
            boolean useJobTableMetadata,
            PrimaryKeyConfig jobPrimaryKeys,
            PrimaryKeyConfig schemaPrimaryKeys) {
        return copyPrimaryKey(useJobTableMetadata ? jobPrimaryKeys : schemaPrimaryKeys);
    }

    private PrimaryKeyConfig copyPrimaryKey(PrimaryKeyConfig source) {
        if (source == null) {
            return null;
        }
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
