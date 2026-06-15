package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.source.KafkaRecord;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * TableResolver 单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class TableResolverTest {

    /**
     * Kafka schema.table.name 与配置表名匹配时命中.
     */
    @Test
    void shouldMatchStaticTableName() throws Exception {
        PaimonSinkConfig sink = baseSink();

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(message("[{\"today\":\"2026-06-12\"}]"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals("ods_test", plan.get().tableConfig.tableName);
        Assertions.assertEquals("2026-06-12", plan.get().records.get(0).get("today"));
    }

    /**
     * 单表且 job 已完整配置表结构时,允许没有 schema.table.name 的简化消息.
     */
    @Test
    void shouldRouteSimplifiedSingleTableMessageWithoutSchemaTableName() throws Exception {
        PaimonSinkConfig sink = baseSink();

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals("ods_test", plan.get().tableConfig.tableName);
        Assertions.assertEquals("2026-06-12", plan.get().records.get(0).get("today"));
    }

    /**
     * Kafka schema.table.name 与配置表名不匹配时过滤消息.
     */
    @Test
    void shouldSkipMessageWhenTableNameNotMatched() throws Exception {
        PaimonSinkConfig sink = baseSink();

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"other_table"}},"data":[{"today":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isEmpty());
    }

    /**
     * columnsMapping 返回单层对象时自动包装成一条记录.
     */
    @Test
    void shouldWrapSingleObjectColumnsMapping() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.tables.get(0).columnsMapping = JacksonUtils.str2JsonNode("\"payload\"");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test"}},"payload":{"today":"2026-06-12"}}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(1, plan.get().records.size());
        Assertions.assertEquals("2026-06-12", plan.get().records.get(0).get("today"));
    }

    /**
     * PROXY 主键模式会补充代理主键列和真实 Paimon 主键.
     */
    @Test
    void shouldGenerateProxyPrimaryKeyAndPaimonPrimaryKeys() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.loadMode = "UPSERT";
        PaimonTableConfig table = sink.tables.get(0);
        useFullJobTableMetadata(table);
        table.table.primaryKeys = new PrimaryKeyConfig();
        table.table.primaryKeys.mode = "PROXY";
        table.table.primaryKeys.algorithm = "SHA-256";
        table.table.primaryKeys.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                message("[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(List.of("_id_", "day_pt"), plan.get().tableConfig.primaryKeys);
        Assertions.assertEquals(64, String.valueOf(plan.get().records.get(0).get("_id_")).length());
    }

    /**
     * PROXY 主键固定使用 _id_ 字段,不需要在配置里声明 field.
     */
    @Test
    void shouldUseFixedProxyPrimaryKeyFieldWithoutFieldConfig() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.loadMode = "UPSERT";
        PaimonTableConfig table = sink.tables.get(0);
        useFullJobTableMetadata(table);
        table.table.primaryKeys = new PrimaryKeyConfig();
        table.table.primaryKeys.mode = "PROXY";
        table.table.primaryKeys.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                message("[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertTrue(plan.get().records.get(0).containsKey("_id_"));
    }

    /**
     * Kafka 标准 schema 可以提供字段结构.
     */
    @Test
    void shouldUseKafkaSchemaColumnsWhenJobColumnsOmitted() throws Exception {
        PaimonSinkConfig sink = baseSink();
        PaimonTableConfig table = sink.tables.get(0);
        table.table.comment = null;
        table.table.createIfNotExists = null;
        table.table.partitionKeys = null;
        table.table.primaryKeys = null;
        table.columns.clear();

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test","partitionKeys":["day_pt"]},
                "columns":[{"name":"today","type":"VARCHAR","length":32,"nullable":false},
                {"name":"day_pt","type":"DATE","nullable":false}]},"data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals("ods_test", plan.get().tableConfig.tableName);
        Assertions.assertEquals(2, plan.get().tableConfig.columns.size());
        Assertions.assertEquals("VARCHAR", plan.get().tableConfig.columns.get(0).dataType);
    }

    /**
     * job.json 一旦配置 columns[] 就按完整字段定义处理.
     */
    @Test
    void shouldUseJobColumnsAsCompleteDefinition() throws Exception {
        PaimonSinkConfig sink = baseSink();
        PaimonTableConfig table = sink.tables.get(0);
        table.table.comment = null;
        table.table.createIfNotExists = null;
        table.table.partitionKeys = null;
        table.table.primaryKeys = null;
        table.columns.clear();
        table.columns.add(column("today", "VARCHAR", false));
        table.columns.get(0).length = 64;

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test","partitionKeys":["day_pt"]},
                "columns":[{"name":"today","type":"VARCHAR","length":32,"nullable":true},
                {"name":"day_pt","type":"DATE","nullable":false}]},"data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(1, plan.get().tableConfig.columns.size());
        Assertions.assertEquals(64, plan.get().tableConfig.columns.get(0).length);
        Assertions.assertFalse(plan.get().tableConfig.columns.get(0).nullable);
    }

    /**
     * primaryKeys.mode 省略时默认按 FIELDS 处理.
     */
    @Test
    void shouldDefaultPrimaryKeyModeToFields() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.loadMode = "UPSERT";
        PaimonTableConfig table = sink.tables.get(0);
        useFullJobTableMetadata(table);
        table.table.primaryKeys = new PrimaryKeyConfig();
        table.table.primaryKeys.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                message("[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(List.of("day_pt", "today"), plan.get().tableConfig.primaryKeys);
    }

    /**
     * 未启用 job table 元数据覆盖时允许回退到 Kafka schema.table.primaryKeys.
     */
    @Test
    void shouldUseKafkaSchemaPrimaryKeysConfigWhenJobMetadataOmitted() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.loadMode = "UPSERT";
        PaimonTableConfig table = sink.tables.get(0);
        table.table.comment = null;
        table.table.createIfNotExists = null;
        table.table.partitionKeys = null;
        table.table.primaryKeys = null;

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test","partitionKeys":["day_pt"],
                "primaryKeys":{"defaultValue":["day_pt","today"]}}},
                "data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(List.of("day_pt"), plan.get().tableConfig.partitionKeys);
        Assertions.assertEquals(List.of("day_pt", "today"), plan.get().tableConfig.primaryKeys);
    }

    /**
     * job.json table 元数据不受 Kafka schema.table 覆盖.
     */
    @Test
    void shouldUseJobTableMetadataAsCompleteDefinition() throws Exception {
        PaimonSinkConfig sink = baseSink();
        PaimonTableConfig table = sink.tables.get(0);
        useFullJobTableMetadata(table);
        table.table.primaryKeys = primaryKeys("day_pt");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test","comment":"schema comment","partitionKeys":["schema_pt"],
                "primaryKeys":{"defaultValue":["schema_pt"]}}},
                "data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals("ods_test", plan.get().tableConfig.tableName);
        Assertions.assertEquals("job comment", plan.get().tableConfig.tableComment);
        Assertions.assertEquals(List.of("day_pt"), plan.get().tableConfig.partitionKeys);
        Assertions.assertEquals(List.of("day_pt"), plan.get().tableConfig.primaryKeys);
    }

    /**
     * 表级 includeKafkaMetadataFields 打开时追加 Kafka 元数据列和值.
     */
    @Test
    void shouldAppendKafkaMetadataFieldsByTableConfig() throws Exception {
        PaimonSinkConfig sink = baseSink();
        PaimonTableConfig table = sink.tables.get(0);
        table.table.includeKafkaMetadataFields = true;

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(message("[{\"today\":\"2026-06-12\"}]"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertTrue(plan.get().tableConfig.columns.stream().anyMatch(column -> TableResolver.KAFKA_TOPIC_FIELD.equals(column.name)));
        Assertions.assertEquals("topic-a", plan.get().records.get(0).get(TableResolver.KAFKA_TOPIC_FIELD));
        Assertions.assertEquals(0, plan.get().records.get(0).get(TableResolver.KAFKA_PARTITION_FIELD));
        Assertions.assertEquals(10L, plan.get().records.get(0).get(TableResolver.KAFKA_OFFSET_FIELD));
    }

    /**
     * recordErrorPolicy=SKIP 时 columnsMapping 顶层类型错误会跳过消息.
     */
    @Test
    void shouldSkipInvalidTopLevelColumnsMappingWhenPolicySkip() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.recordErrorPolicy = "SKIP";

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test"}},"data":"not-object"}
                """), record());

        Assertions.assertTrue(plan.isEmpty());
    }

    /**
     * recordErrorPolicy=FAIL 时 columnsMapping 顶层类型错误会抛出异常.
     */
    @Test
    void shouldFailInvalidTopLevelColumnsMappingWhenPolicyFail() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.recordErrorPolicy = "FAIL";

        Assertions.assertThrows(KafkaJsonPaimonException.class, () -> new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_test"}},"data":"not-object"}
                """), record()));
    }

    private PaimonSinkConfig baseSink() throws Exception {
        PaimonSinkConfig sink = new PaimonSinkConfig();
        sink.options.put("warehouse", "file:///tmp/paimon");
        PaimonTableConfig table = new PaimonTableConfig();
        table.table.database = JacksonUtils.str2JsonNode("\"dw_dev\"");
        table.table.name = JacksonUtils.str2JsonNode("\"ods_test\"");
        table.table.comment = JacksonUtils.str2JsonNode("\"job comment\"");
        table.table.createIfNotExists = JacksonUtils.str2JsonNode("true");
        table.columnsMapping = JacksonUtils.str2JsonNode("\"data\"");
        table.table.partitionKeys = JacksonUtils.str2JsonNode("[\"day_pt\"]");
        table.table.primaryKeys = primaryKeys("day_pt");
        table.columns.add(column("today", "VARCHAR", false));
        table.columns.add(column("day_pt", "DATE", false));
        sink.tables.add(table);
        return sink;
    }

    private ColumnConfig column(String name, String dataType, boolean nullable) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.dataType = dataType;
        column.nullable = nullable;
        return column;
    }

    private void useFullJobTableMetadata(PaimonTableConfig table) throws Exception {
        table.table.comment = JacksonUtils.str2JsonNode("\"job comment\"");
        table.table.createIfNotExists = JacksonUtils.str2JsonNode("true");
        if (table.table.name == null) {
            table.table.name = JacksonUtils.str2JsonNode("\"ods_test\"");
        }
        if (table.table.partitionKeys == null) {
            table.table.partitionKeys = JacksonUtils.str2JsonNode("[\"day_pt\"]");
        }
    }

    private PrimaryKeyConfig primaryKeys(String... fields) {
        PrimaryKeyConfig primaryKeys = new PrimaryKeyConfig();
        primaryKeys.defaultValue = List.of(fields);
        return primaryKeys;
    }

    private JsonNode json(String text) throws Exception {
        return JacksonUtils.str2JsonNode(text);
    }

    private JsonNode message(String data) throws Exception {
        return json("{\"schema\":{\"table\":{\"name\":\"ods_test\"}},\"data\":" + data + "}");
    }

    private KafkaRecord record() {
        return new KafkaRecord("topic-a", 0, 10L, "{}");
    }
}
