package com.datafusion.plugin.flink.schema.paimon.resolve;

import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonSinkGroupConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonTableSinkConfig;
import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.core.enums.LoadMode;
import com.datafusion.plugin.flink.schema.paimon.message.ColumnConfig;
import com.datafusion.plugin.flink.schema.paimon.message.KafkaEnvelope;
import com.datafusion.plugin.flink.schema.paimon.message.TableConfig;
import com.datafusion.plugin.flink.schema.paimon.source.KafkaRecord;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 表白名单解析器.
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
    private final PaimonSinkGroupConfig sink;

    /**
     * 启用表映射.
     */
    private final Map<String, PaimonTableSinkConfig> tables;

    /**
     * 跳过计数.
     */
    private final AtomicLong skippedCount = new AtomicLong();

    /**
     * 构造解析器.
     *
     * @param sink sink 配置
     */
    public TableResolver(PaimonSinkGroupConfig sink) {
        this.sink = sink;
        this.tables = sink.tables.stream()
                .filter(table -> table != null && !Boolean.FALSE.equals(table.enabled))
                .collect(Collectors.toMap(table -> normalize(table.tableName), table -> table, (left, right) -> left, LinkedHashMap::new));
    }

    /**
     * 解析写入计划.
     *
     * @param envelope Kafka 消息
     * @param record Kafka 记录
     * @return 写入计划
     */
    public Optional<ResolvedTableWritePlan> resolve(KafkaEnvelope envelope, KafkaRecord record) {
        validateEnvelope(envelope);
        String messageTableName = envelope.schema.table.name;
        PaimonTableSinkConfig tableSink = tables.get(normalize(messageTableName));
        if (tableSink == null) {
            return handleUnmatched(messageTableName, record);
        }
        ResolvedTableConfig tableConfig = buildTableConfig(envelope, tableSink);
        validateLoadMode(tableConfig);
        ResolvedTableWritePlan plan = new ResolvedTableWritePlan();
        plan.tableConfig = tableConfig;
        plan.records = buildRecords(envelope, record);
        plan.topic = record.topic;
        plan.partition = record.partition;
        plan.offset = record.offset;
        return Optional.of(plan);
    }

    /**
     * 获取跳过计数.
     *
     * @return 跳过计数
     */
    public long skippedCount() {
        return skippedCount.get();
    }

    private void validateEnvelope(KafkaEnvelope envelope) {
        if (envelope == null || envelope.schema == null || envelope.schema.table == null) {
            throw new FlinkSchemaPaimonException("Kafka message schema.table is required");
        }
        if (TextUtils.isBlank(envelope.schema.table.name)) {
            throw new FlinkSchemaPaimonException("Kafka message schema.table.name is required");
        }
        if (envelope.schema.columns == null || envelope.schema.columns.isEmpty()) {
            throw new FlinkSchemaPaimonException("Kafka message schema.columns is required");
        }
        if (envelope.data == null) {
            throw new FlinkSchemaPaimonException("Kafka message data is required");
        }
    }

    private Optional<ResolvedTableWritePlan> handleUnmatched(String tableName, KafkaRecord record) {
        long skipped = skippedCount.incrementAndGet();
        LOGGER.warn("Skip unmatched Kafka table, tableName={}, topic={}, partition={}, offset={}, skippedCount={}",
                tableName, record.topic, record.partition, record.offset, skipped);
        return Optional.empty();
    }

    private ResolvedTableConfig buildTableConfig(KafkaEnvelope envelope, PaimonTableSinkConfig tableSink) {
        ResolvedTableConfig config = new ResolvedTableConfig();
        config.database = tableSink.database;
        config.loadMode = LoadMode.parse(tableSink.loadMode, LoadMode.parse(sink.loadMode, LoadMode.APPEND));
        TableConfig table = envelope.schema.table.copy();
        table.name = tableSink.tableName;
        config.table = table;
        config.columns = copyColumns(envelope.schema.columns);
        if (Boolean.TRUE.equals(sink.includeKafkaMetadataFields)) {
            config.columns.add(kafkaTopicColumn());
            config.columns.add(kafkaPartitionColumn());
            config.columns.add(kafkaOffsetColumn());
        }
        config.options = new LinkedHashMap<>(sink.globalOptions());
        if (tableSink.options != null) {
            config.options.putAll(tableSink.options);
        }
        return config;
    }

    private void validateLoadMode(ResolvedTableConfig config) {
        if (config.loadMode == LoadMode.UPSERT && (config.table.primaryKeys == null || config.table.primaryKeys.isEmpty())) {
            throw new FlinkSchemaPaimonException("Paimon UPSERT requires Kafka schema.table.primaryKeys: " + config.identifier());
        }
    }

    private List<Map<String, Object>> buildRecords(KafkaEnvelope envelope, KafkaRecord record) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> source : envelope.data) {
            Map<String, Object> target = ResolvedTableWritePlan.copyRecord(source);
            if (Boolean.TRUE.equals(sink.includeKafkaMetadataFields)) {
                target.put(KAFKA_TOPIC_FIELD, record.topic);
                target.put(KAFKA_PARTITION_FIELD, record.partition);
                target.put(KAFKA_OFFSET_FIELD, record.offset);
            }
            records.add(target);
        }
        return records;
    }

    private List<ColumnConfig> copyColumns(List<ColumnConfig> columns) {
        return columns.stream().map(ColumnConfig::copy).collect(Collectors.toCollection(ArrayList::new));
    }

    private ColumnConfig kafkaTopicColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = KAFKA_TOPIC_FIELD;
        column.type = "VARCHAR";
        column.length = 512;
        column.nullable = false;
        column.comment = "Kafka topic";
        return column;
    }

    private ColumnConfig kafkaPartitionColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = KAFKA_PARTITION_FIELD;
        column.type = "INT";
        column.nullable = false;
        column.comment = "Kafka partition";
        return column;
    }

    private ColumnConfig kafkaOffsetColumn() {
        ColumnConfig column = new ColumnConfig();
        column.name = KAFKA_OFFSET_FIELD;
        column.type = "BIGINT";
        column.nullable = false;
        column.comment = "Kafka offset";
        return column;
    }

    private static String normalize(String tableName) {
        return tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
    }
}
