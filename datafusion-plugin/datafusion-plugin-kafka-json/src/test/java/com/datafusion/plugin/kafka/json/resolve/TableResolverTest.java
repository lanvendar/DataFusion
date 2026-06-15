package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
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
     * tableName.path 取不到时使用 defaultValue.
     */
    @Test
    void shouldUseTableNameDefaultWhenPathMissed() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.tables.get(0).table.name = JacksonUtils.str2JsonNode("{\"path\":\"schema.table.name\",\"defaultValue\":\"fallback_table\"}");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("{\"schema\":{},\"data\":[{\"today\":\"2026-06-12\"}]}"),
                record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals("fallback_table", plan.get().tableConfig.tableName);
        Assertions.assertEquals("2026-06-12", plan.get().records.get(0).get("today"));
    }

    /**
     * tableName path 与 defaultValue 都为空时过滤消息.
     */
    @Test
    void shouldSkipMessageWhenTableNameIsEmpty() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.tables.get(0).table.name = JacksonUtils.str2JsonNode("{\"path\":\"schema.table.name\"}");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("{\"schema\":{},\"data\":[{\"today\":\"2026-06-12\"}]}"),
                record());

        Assertions.assertTrue(plan.isEmpty());
    }

    /**
     * columnsMapping 返回单层对象时自动包装成一条记录.
     */
    @Test
    void shouldWrapSingleObjectColumnsMapping() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.tables.get(0).columnsMapping = JacksonUtils.str2JsonNode("\"payload\"");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("{\"payload\":{\"today\":\"2026-06-12\"}}"), record());

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
        table.table.primaryKeys = new PrimaryKeyConfig();
        table.table.primaryKeys.mode = "PROXY";
        table.table.primaryKeys.algorithm = "SHA-256";
        table.table.primaryKeys.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                json("{\"data\":[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]}"), record());

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
        table.table.primaryKeys = new PrimaryKeyConfig();
        table.table.primaryKeys.mode = "PROXY";
        table.table.primaryKeys.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                json("{\"data\":[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]}"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertTrue(plan.get().records.get(0).containsKey("_id_"));
    }

    /**
     * Kafka 标准 schema 可以提供表名和字段结构.
     */
    @Test
    void shouldUseKafkaSchemaWhenJobTableAndColumnsOmitted() throws Exception {
        PaimonSinkConfig sink = baseSink();
        PaimonTableConfig table = sink.tables.get(0);
        table.table.name = null;
        table.columns.clear();

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_from_schema","partitionKeys":["day_pt"]},
                "columns":[{"name":"today","type":"VARCHAR","length":32,"nullable":false},
                {"name":"day_pt","type":"DATE","nullable":false}]},"data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals("ods_from_schema", plan.get().tableConfig.tableName);
        Assertions.assertEquals(2, plan.get().tableConfig.columns.size());
        Assertions.assertEquals("VARCHAR", plan.get().tableConfig.columns.get(0).dataType);
    }

    /**
     * job.json 字段配置按字段名覆盖 Kafka 标准 schema 字段.
     */
    @Test
    void shouldOverrideKafkaSchemaColumnByJobColumn() throws Exception {
        PaimonSinkConfig sink = baseSink();
        PaimonTableConfig table = sink.tables.get(0);
        table.table.name = null;
        table.columns.clear();
        table.columns.add(column("today", "VARCHAR", false));
        table.columns.get(0).length = 64;

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_from_schema","partitionKeys":["day_pt"]},
                "columns":[{"name":"today","type":"VARCHAR","length":32,"nullable":true},
                {"name":"day_pt","type":"DATE","nullable":false}]},"data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
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
        table.table.primaryKeys = new PrimaryKeyConfig();
        table.table.primaryKeys.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                json("{\"data\":[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]}"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(List.of("day_pt", "today"), plan.get().tableConfig.primaryKeys);
    }

    /**
     * Kafka schema.table.primaryKeys 可以提供主键配置.
     */
    @Test
    void shouldUseKafkaSchemaPrimaryKeysConfig() throws Exception {
        PaimonSinkConfig sink = baseSink();
        sink.loadMode = "UPSERT";
        PaimonTableConfig table = sink.tables.get(0);
        table.table.name = null;

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(json("""
                {"schema":{"table":{"name":"ods_from_schema","partitionKeys":["day_pt"],
                "primaryKeys":{"defaultValue":["day_pt","today"]}}},
                "data":[{"today":"2026-06-12","day_pt":"2026-06-12"}]}
                """), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(List.of("day_pt", "today"), plan.get().tableConfig.primaryKeys);
    }

    private PaimonSinkConfig baseSink() throws Exception {
        PaimonSinkConfig sink = new PaimonSinkConfig();
        sink.options.put("warehouse", "file:///tmp/paimon");
        PaimonTableConfig table = new PaimonTableConfig();
        table.table.database = JacksonUtils.str2JsonNode("\"dw_dev\"");
        table.table.name = JacksonUtils.str2JsonNode("\"ods_test\"");
        table.columnsMapping = JacksonUtils.str2JsonNode("\"data\"");
        table.table.partitionKeys = JacksonUtils.str2JsonNode("[\"day_pt\"]");
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

    private JsonNode json(String text) throws Exception {
        return JacksonUtils.str2JsonNode(text);
    }

    private KafkaRecord record() {
        return new KafkaRecord("topic-a", 0, 10L, "{}");
    }
}
