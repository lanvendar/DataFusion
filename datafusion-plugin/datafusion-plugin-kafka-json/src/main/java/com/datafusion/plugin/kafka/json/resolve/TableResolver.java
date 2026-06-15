package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.kafka.json.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.expression.ExpressionEvaluator;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpec;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.expression.JsonType;
import com.datafusion.plugin.kafka.json.source.KafkaRecord;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka JSON 到目标 Paimon 表的解析器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class TableResolver {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(TableResolver.class);

    /**
     * Kafka topic 字段名.
     */
    public static final String KAFKA_TOPIC_FIELD = "_kafka_topic";

    /**
     * Kafka partition 字段名.
     */
    public static final String KAFKA_PARTITION_FIELD = "_kafka_partition";

    /**
     * Kafka offset 字段名.
     */
    public static final String KAFKA_OFFSET_FIELD = "_kafka_offset";

    /**
     * Kafka 标准 schema table 路径.
     */
    private static final String SCHEMA_TABLE_PATH = "schema.table";

    /**
     * Kafka 标准 schema columns 路径.
     */
    private static final String SCHEMA_COLUMNS_PATH = "schema.columns";

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * 单条记录错误策略.
     */
    private final RecordErrorPolicy recordErrorPolicy;

    /**
     * 表达式执行器.
     */
    private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    /**
     * 跳过计数.
     */
    private final AtomicLong skippedCount = new AtomicLong();

    /**
     * 构造解析器.
     *
     * @param sink sink 配置
     */
    public TableResolver(PaimonSinkConfig sink) {
        this.sink = sink;
        this.recordErrorPolicy = RecordErrorPolicy.parse(sink.recordErrorPolicy);
    }

    /**
     * 解析写入计划.
     *
     * @param message Kafka JSON 消息
     * @param record Kafka 记录元数据
     * @return 写入计划
     */
    public Optional<ResolvedTableWritePlan> resolve(JsonNode message, KafkaRecord record) {
        Object messageObject = toJavaObject(message);
        for (PaimonTableConfig table : sink.tables) {
            if (table == null || Boolean.FALSE.equals(table.enabled)) {
                continue;
            }
            Optional<ResolvedTableWritePlan> plan = resolveTable(messageObject, table, record);
            if (plan.isPresent()) {
                return plan;
            }
        }
        long skipped = skippedCount.incrementAndGet();
        LOGGER.warn("Skip Kafka JSON message because target table name is empty, topic={}, partition={}, offset={}, skippedCount={}",
                record.topic, record.partition, record.offset, skipped);
        return Optional.empty();
    }

    /**
     * 获取跳过计数.
     *
     * @return 跳过计数
     */
    public long skippedCount() {
        return skippedCount.get();
    }

    private Optional<ResolvedTableWritePlan> resolveTable(Object messageObject, PaimonTableConfig table, KafkaRecord kafkaRecord) {
        StandardSchema schema = standardSchema(messageObject);
        String tableName = stringValue(evaluate(messageObject, tableSpec(table.table.name, "name", schema.table.name, JsonType.STRING),
                "sink.tables[].table.name"));
        if (TextUtils.isBlank(tableName)) {
            return Optional.empty();
        }
        ResolvedTableConfig tableConfig = buildTableConfig(messageObject, table, schema, tableName);
        List<Map<String, Object>> records = buildRecords(messageObject, table, tableConfig, kafkaRecord);
        if (records.isEmpty()) {
            LOGGER.warn("Skip Kafka JSON message because no valid records resolved, identifier={}, topic={}, partition={}, offset={}",
                    tableConfig.identifier(), kafkaRecord.topic, kafkaRecord.partition, kafkaRecord.offset);
            return Optional.empty();
        }
        ResolvedTableWritePlan plan = new ResolvedTableWritePlan();
        plan.tableConfig = tableConfig;
        plan.records = records;
        plan.topic = kafkaRecord.topic;
        plan.partition = kafkaRecord.partition;
        plan.offset = kafkaRecord.offset;
        return Optional.of(plan);
    }

    private ResolvedTableConfig buildTableConfig(Object messageObject, PaimonTableConfig table, StandardSchema schema, String tableName) {
        ResolvedTableConfig config = new ResolvedTableConfig();
        config.database = requiredString(evaluate(messageObject, tableSpec(table.table.database, "database", schema.table.database,
                JsonType.STRING), "sink.tables[].table.database"), "sink.tables[].table.database");
        config.tableName = tableName;
        config.loadMode = LoadMode.parse(table.loadMode, LoadMode.parse(sink.loadMode, LoadMode.APPEND));
        config.tableComment = stringValue(evaluate(messageObject, tableSpec(table.table.comment, "comment", schema.table.comment,
                JsonType.STRING), "sink.tables[].table.comment"));
        Object createIfNotExists = evaluate(messageObject, tableSpec(table.table.createIfNotExists, "createIfNotExists",
                schema.table.createIfNotExists, JsonType.BOOLEAN), "sink.tables[].table.createIfNotExists");
        config.createIfNotExists = createIfNotExists == null || Boolean.TRUE.equals(createIfNotExists);
        config.partitionKeys = stringList(evaluate(messageObject, tableSpec(table.table.partitionKeys, "partitionKeys",
                schema.table.partitionKeys, JsonType.ARRAY), "sink.tables[].table.partitionKeys"), "sink.tables[].table.partitionKeys");
        config.columns = mergeColumns(schema.columns, table.columns);
        config.primaryKeysConfig = mergePrimaryKeys(schema.table.primaryKeys, table.table.primaryKeys);
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

    private List<Map<String, Object>> buildRecords(Object messageObject, PaimonTableConfig table, ResolvedTableConfig tableConfig,
            KafkaRecord kafkaRecord) {
        Object mapped = evaluate(messageObject, ExpressionSpecNormalizer.path(table.columnsMapping, JsonType.ANY, null),
                "sink.tables[].columnsMapping");
        List<Map<String, Object>> sourceRecords = normalizeRecordSet(mapped, tableConfig, kafkaRecord);
        List<Map<String, Object>> targetRecords = new ArrayList<>();
        for (int i = 0; i < sourceRecords.size(); i++) {
            Map<String, Object> target = mapRecord(sourceRecords.get(i), tableConfig, tableConfig.primaryKeysConfig, kafkaRecord, i);
            if (target != null) {
                targetRecords.add(target);
            }
        }
        return targetRecords;
    }

    private Map<String, Object> mapRecord(Map<String, Object> source, ResolvedTableConfig tableConfig, PrimaryKeyConfig primaryKey,
            KafkaRecord kafkaRecord, int recordIndex) {
        try {
            Map<String, Object> target = new LinkedHashMap<>();
            for (ColumnConfig column : tableConfig.columns) {
                if (isSystemColumn(column.name)) {
                    continue;
                }
                ExpressionSpec valueSpec = ExpressionSpecNormalizer.value(column.value, JsonType.ANY, column.name);
                target.put(column.name, evaluate(source, valueSpec, "columns." + column.name));
            }
            appendProxyPrimaryKey(target, source, primaryKey);
            if (Boolean.TRUE.equals(sink.includeKafkaMetadataFields)) {
                target.put(KAFKA_TOPIC_FIELD, kafkaRecord.topic);
                target.put(KAFKA_PARTITION_FIELD, kafkaRecord.partition);
                target.put(KAFKA_OFFSET_FIELD, kafkaRecord.offset);
            }
            return target;
        } catch (RuntimeException e) {
            if (recordErrorPolicy == RecordErrorPolicy.FAIL) {
                throw e;
            }
            LOGGER.warn("Skip Kafka JSON record because record mapping failed, identifier={}, topic={}, partition={}, offset={}, "
                            + "recordIndex={}, reason={}",
                    tableConfig.identifier(), kafkaRecord.topic, kafkaRecord.partition, kafkaRecord.offset, recordIndex, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> normalizeRecordSet(Object value, ResolvedTableConfig tableConfig, KafkaRecord kafkaRecord) {
        if (value instanceof Map<?, ?> map) {
            return List.of(copyMap(map));
        }
        if (value instanceof Collection<?> collection) {
            List<Map<String, Object>> records = new ArrayList<>();
            int index = 0;
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    records.add(copyMap(map));
                } else {
                    handleRecordSetError(tableConfig, kafkaRecord, index, "columnsMapping item is not object");
                }
                index++;
            }
            return records;
        }
        throw new KafkaJsonPaimonException("columnsMapping result must be object or array object: " + tableConfig.identifier());
    }

    private void handleRecordSetError(ResolvedTableConfig tableConfig, KafkaRecord kafkaRecord, int recordIndex, String reason) {
        if (recordErrorPolicy == RecordErrorPolicy.FAIL) {
            throw new KafkaJsonPaimonException(reason);
        }
        LOGGER.warn("Skip Kafka JSON record because columnsMapping item is invalid, identifier={}, topic={}, partition={}, offset={}, "
                        + "recordIndex={}, reason={}",
                tableConfig.identifier(), kafkaRecord.topic, kafkaRecord.partition, kafkaRecord.offset, recordIndex, reason);
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
        throw new KafkaJsonPaimonException("Unsupported primaryKey.mode: " + primaryKey.mode);
    }

    private ExpressionSpec primaryKeySpec(PrimaryKeyConfig primaryKey) {
        ExpressionSpec spec = new ExpressionSpec();
        spec.path = primaryKey.path;
        spec.defaultValue = primaryKey.defaultValue;
        spec.jsonType = "ARRAY";
        return spec;
    }

    private void appendProxyPrimaryKey(Map<String, Object> target, Map<String, Object> source, PrimaryKeyConfig primaryKey) {
        if (primaryKey == null || PrimaryKeyMode.parse(primaryKey.mode) != PrimaryKeyMode.PROXY) {
            return;
        }
        List<String> fields = stringList(evaluate(source, primaryKeySpec(primaryKey), "sink.tables[].table.primaryKeys"),
                "sink.tables[].table.primaryKeys");
        ProxyPrimaryKeyType type = ProxyPrimaryKeyType.parse(primaryKey.algorithm);
        target.put(ProxyPrimaryKeyGenerator.FIELD_NAME, ProxyPrimaryKeyGenerator.generate(target, fields, type));
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
        column.name = KAFKA_TOPIC_FIELD;
        column.dataType = "VARCHAR";
        column.length = 512;
        column.nullable = false;
        column.comment = "Kafka topic";
        return column;
    }

    private ColumnConfig kafkaPartitionColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = KAFKA_PARTITION_FIELD;
        column.dataType = "INT";
        column.nullable = false;
        column.comment = "Kafka partition";
        return column;
    }

    private ColumnConfig kafkaOffsetColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = KAFKA_OFFSET_FIELD;
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

    private Object evaluate(Object input, ExpressionSpec spec, String name) {
        return expressionEvaluator.evaluate(input, spec, name);
    }

    private Object toJavaObject(JsonNode message) {
        try {
            return JacksonUtils.treeNode2Bean(message, Object.class);
        } catch (Exception e) {
            throw new KafkaJsonPaimonException("Failed to convert Kafka JSON message", e);
        }
    }

    private StandardSchema standardSchema(Object messageObject) {
        StandardSchema schema = new StandardSchema();
        Object tableObject = evaluate(messageObject, pathSpec(SCHEMA_TABLE_PATH, JsonType.OBJECT), SCHEMA_TABLE_PATH);
        if (tableObject instanceof Map<?, ?> tableMap) {
            schema.table = tableSchema(copyMap(tableMap));
        }
        Object columnsObject = evaluate(messageObject, pathSpec(SCHEMA_COLUMNS_PATH, JsonType.ARRAY), SCHEMA_COLUMNS_PATH);
        if (columnsObject instanceof Collection<?> collection) {
            schema.columns = columnSchemas(collection);
        }
        return schema;
    }

    private StandardTableSchema tableSchema(Map<String, Object> table) {
        StandardTableSchema schema = new StandardTableSchema();
        schema.database = table.get("database");
        schema.name = table.get("name");
        schema.comment = table.get("comment");
        schema.createIfNotExists = table.get("createIfNotExists");
        schema.partitionKeys = table.get("partitionKeys");
        schema.primaryKeys = primaryKeysConfig(table.get("primaryKeys"));
        return schema;
    }

    private PrimaryKeyConfig primaryKeysConfig(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            PrimaryKeyConfig primaryKey = new PrimaryKeyConfig();
            primaryKey.mode = PrimaryKeyMode.FIELDS.name();
            primaryKey.defaultValue = new ArrayList<>(collection);
            return primaryKey;
        }
        try {
            JsonNode node = JacksonUtils.obj2JsonNode(value);
            return JacksonUtils.treeNode2Bean(node, PrimaryKeyConfig.class);
        } catch (Exception e) {
            throw new KafkaJsonPaimonException("Failed to parse schema.table.primaryKeys", e);
        }
    }

    private List<ColumnConfig> columnSchemas(Collection<?> collection) {
        List<ColumnConfig> columns = new ArrayList<>();
        for (Object item : collection) {
            if (item == null) {
                continue;
            }
            try {
                JsonNode node = JacksonUtils.obj2JsonNode(item);
                ColumnConfig column = JacksonUtils.treeNode2Bean(node, ColumnConfig.class);
                columns.add(column.copy());
            } catch (Exception e) {
                throw new KafkaJsonPaimonException("Failed to parse schema.columns item", e);
            }
        }
        return columns;
    }

    private ExpressionSpec tableSpec(JsonNode node, String fieldName, Object defaultValue, JsonType jsonType) {
        if (node != null && !node.isNull()) {
            return ExpressionSpecNormalizer.constant(node, jsonType);
        }
        ExpressionSpec spec = new ExpressionSpec();
        spec.jsonType = jsonType.name();
        spec.path = SCHEMA_TABLE_PATH + "." + fieldName;
        spec.defaultValue = defaultValue;
        return spec;
    }

    private ExpressionSpec pathSpec(String path, JsonType jsonType) {
        ExpressionSpec spec = new ExpressionSpec();
        spec.path = path;
        spec.jsonType = jsonType.name();
        return spec;
    }

    private List<ColumnConfig> mergeColumns(List<ColumnConfig> schemaColumns, List<ColumnConfig> jobColumns) {
        Map<String, ColumnConfig> columns = new LinkedHashMap<>();
        for (ColumnConfig column : safeColumns(schemaColumns)) {
            if (TextUtils.isBlank(column.name)) {
                continue;
            }
            columns.put(column.name.toLowerCase(), column.copy());
        }
        for (ColumnConfig column : safeColumns(jobColumns)) {
            if (TextUtils.isBlank(column.name)) {
                continue;
            }
            String key = column.name.toLowerCase();
            ColumnConfig merged = columns.containsKey(key) ? mergeColumn(columns.get(key), column) : column.copy();
            columns.put(key, merged);
        }
        return new ArrayList<>(columns.values());
    }

    private Collection<ColumnConfig> safeColumns(List<ColumnConfig> columns) {
        return columns == null ? List.of() : columns;
    }

    private ColumnConfig mergeColumn(ColumnConfig base, ColumnConfig override) {
        ColumnConfig merged = base.copy();
        merged.name = override.name;
        if (!TextUtils.isBlank(override.dataType) || !TextUtils.isBlank(override.type)) {
            merged.dataType = !TextUtils.isBlank(override.dataType) ? override.dataType : override.type;
        }
        if (override.length != null) {
            merged.length = override.length;
        }
        if (override.precision != null) {
            merged.precision = override.precision;
        }
        if (override.scale != null) {
            merged.scale = override.scale;
        }
        if (override.nullable != null) {
            merged.nullable = override.nullable;
        }
        if (override.comment != null) {
            merged.comment = override.comment;
        }
        if (override.format != null) {
            merged.format = override.format;
        }
        if (override.value != null) {
            merged.value = override.value;
        }
        return merged;
    }

    private PrimaryKeyConfig mergePrimaryKeys(PrimaryKeyConfig schemaPrimaryKeys, PrimaryKeyConfig jobPrimaryKeys) {
        if (jobPrimaryKeys != null) {
            return copyPrimaryKey(jobPrimaryKeys);
        }
        return schemaPrimaryKeys == null ? null : copyPrimaryKey(schemaPrimaryKeys);
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

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
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

    private boolean isSystemColumn(String name) {
        return KAFKA_TOPIC_FIELD.equals(name) || KAFKA_PARTITION_FIELD.equals(name) || KAFKA_OFFSET_FIELD.equals(name);
    }

    /**
     * Kafka 标准 schema.
     */
    private static class StandardSchema {

        /**
         * 表 schema.
         */
        private StandardTableSchema table = new StandardTableSchema();

        /**
         * 字段 schema.
         */
        private List<ColumnConfig> columns = new ArrayList<>();
    }

    /**
     * Kafka 标准 table schema.
     */
    private static class StandardTableSchema {

        /**
         * database.
         */
        private Object database;

        /**
         * 表名.
         */
        private Object name;

        /**
         * 表注释.
         */
        private Object comment;

        /**
         * 是否建表.
         */
        private Object createIfNotExists;

        /**
         * 分区字段.
         */
        private Object partitionKeys;

        /**
         * 主键配置.
         */
        private PrimaryKeyConfig primaryKeys;
    }
}
