package com.datafusion.plugin.flink.schema.paimon;

import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonSinkGroupConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonTableSinkConfig;
import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.message.ColumnConfig;
import com.datafusion.plugin.flink.schema.paimon.message.KafkaEnvelope;
import com.datafusion.plugin.flink.schema.paimon.message.MessageSchema;
import com.datafusion.plugin.flink.schema.paimon.message.TableConfig;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableWritePlan;
import com.datafusion.plugin.flink.schema.paimon.resolve.TableResolver;
import com.datafusion.plugin.flink.schema.paimon.source.KafkaRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表白名单解析测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class TableResolverTest {

    /**
     * 未配置表应默认跳过.
     */
    @Test
    void shouldSkipUnmatchedTableByDefault() {
        TableResolver resolver = new TableResolver(sink("configured_table", false, "APPEND"));

        Optional<ResolvedTableWritePlan> plan = resolver.resolve(envelope("other_table", List.of("id")), record());

        assertFalse(plan.isPresent());
        assertEquals(1, resolver.skippedCount());
    }

    /**
     * UPSERT 写入必须携带主键.
     */
    @Test
    void shouldFailUpsertWithoutPrimaryKeys() {
        TableResolver resolver = new TableResolver(sink("configured_table", false, "UPSERT"));

        assertThrows(FlinkSchemaPaimonException.class, () -> resolver.resolve(envelope("configured_table", List.of()), record()));
    }

    /**
     * 开启 Kafka metadata 字段后应补充字段和记录值.
     */
    @Test
    void shouldAppendKafkaMetadataFields() {
        TableResolver resolver = new TableResolver(sink("configured_table", true, "UPSERT"));

        ResolvedTableWritePlan plan = resolver.resolve(envelope("configured_table", List.of("id")), record()).orElseThrow();

        assertEquals(4, plan.tableConfig.columns.size());
        assertTrue(plan.records.get(0).containsKey(TableResolver.KAFKA_TOPIC_FIELD));
        assertTrue(plan.records.get(0).containsKey(TableResolver.KAFKA_PARTITION_FIELD));
        assertTrue(plan.records.get(0).containsKey(TableResolver.KAFKA_OFFSET_FIELD));
    }

    /**
     * 表级 options 应覆盖全局 options.
     */
    @Test
    void shouldMergeTableOptionsOverGlobalOptions() {
        TableResolver resolver = new TableResolver(sink("configured_table", false, "APPEND"));

        ResolvedTableWritePlan plan = resolver.resolve(envelope("configured_table", List.of("id")), record()).orElseThrow();

        assertEquals("1", plan.tableConfig.options.get("bucket"));
        assertEquals("parquet", plan.tableConfig.options.get("file.format"));
    }

    private PaimonSinkGroupConfig sink(String tableName, boolean includeKafkaMetadata, String loadMode) {
        PaimonSinkGroupConfig sink = new PaimonSinkGroupConfig();
        sink.loadMode = loadMode;
        sink.includeKafkaMetadataFields = includeKafkaMetadata;
        sink.options.put("warehouse", "file:///tmp/paimon");
        sink.options.put("bucket", "2");
        sink.options.put("file.format", "parquet");
        PaimonTableSinkConfig table = new PaimonTableSinkConfig();
        table.database = "dw_dev";
        table.tableName = tableName;
        table.options.put("bucket", "1");
        sink.tables.add(table);
        return sink;
    }

    private KafkaEnvelope envelope(String tableName, List<String> primaryKeys) {
        KafkaEnvelope envelope = new KafkaEnvelope();
        envelope.schema = new MessageSchema();
        envelope.schema.table = new TableConfig();
        envelope.schema.table.name = tableName;
        envelope.schema.table.primaryKeys = primaryKeys;
        envelope.schema.columns = List.of(column("id"));
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "1");
        envelope.data = List.of(record);
        return envelope;
    }

    private ColumnConfig column(String name) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.type = "VARCHAR";
        column.length = 32;
        column.nullable = false;
        return column;
    }

    private KafkaRecord record() {
        return new KafkaRecord("topic_a", 2, 100L, "{}");
    }
}
