package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.kafka.json.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.expression.ExpressionEvaluator;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpec;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.core.enums.JsonType;
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
     * Kafka 标准 schema 解析器.
     */
    private final StandardSchemaParser standardSchemaParser = new StandardSchemaParser(expressionEvaluator);

    /**
     * 表配置解析器.
     */
    private final TableConfigResolver tableConfigResolver;

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
        this.tableConfigResolver = new TableConfigResolver(sink, expressionEvaluator);
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
        LOGGER.warn("Skip Kafka JSON message because schema.table.name is missing or no sink table matched, topic={}, partition={}, "
                        + "offset={}, skippedCount={}",
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
        StandardSchema schema = standardSchemaParser.parse(messageObject);
        String tableName = tableConfigResolver.resolveTableName(table);
        if (!matchesTable(schema, tableName)) {
            return Optional.empty();
        }
        ResolvedTableConfig tableConfig = tableConfigResolver.resolve(messageObject, table, schema, tableName);
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

    private boolean matchesTable(StandardSchema schema, String tableName) {
        if (TextUtils.isBlank(tableName)) {
            return false;
        }
        String messageTableName = schema == null || schema.table == null ? null : stringValue(schema.table.name);
        if (TextUtils.isBlank(messageTableName)) {
            return enabledTableCount() == 1 && TableMetadataRules.canRouteWithoutSchemaTableName(singleEnabledTable());
        }
        return tableName.equals(messageTableName);
    }

    private int enabledTableCount() {
        int count = 0;
        for (PaimonTableConfig table : sink.tables) {
            if (table != null && !Boolean.FALSE.equals(table.enabled)) {
                count++;
            }
        }
        return count;
    }

    private PaimonTableConfig singleEnabledTable() {
        PaimonTableConfig matched = null;
        for (PaimonTableConfig table : sink.tables) {
            if (table == null || Boolean.FALSE.equals(table.enabled)) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = table;
        }
        return matched;
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
            if (Boolean.TRUE.equals(tableConfig.includeKafkaMetadataFields)) {
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

    private Object evaluate(Object input, ExpressionSpec spec, String name) {
        return expressionEvaluator.evaluate(input, spec, name);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object toJavaObject(JsonNode message) {
        try {
            return JacksonUtils.treeNode2Bean(message, Object.class);
        } catch (Exception e) {
            throw new KafkaJsonPaimonException("Failed to convert Kafka JSON message", e);
        }
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copied.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copied;
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

    private boolean isSystemColumn(String name) {
        return KAFKA_TOPIC_FIELD.equals(name) || KAFKA_PARTITION_FIELD.equals(name) || KAFKA_OFFSET_FIELD.equals(name);
    }
}
