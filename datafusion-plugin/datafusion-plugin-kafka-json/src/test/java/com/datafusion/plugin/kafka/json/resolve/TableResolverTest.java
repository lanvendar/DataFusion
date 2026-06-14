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
        sink.tables.get(0).tableName = JacksonUtils.str2JsonNode("{\"path\":\"schema.table.name\",\"defaultValue\":\"fallback_table\"}");

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
        sink.tables.get(0).tableName = JacksonUtils.str2JsonNode("{\"path\":\"schema.table.name\"}");

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
        table.primaryKey = new PrimaryKeyConfig();
        table.primaryKey.mode = "PROXY";
        table.primaryKey.algorithm = "SHA-256";
        table.primaryKey.defaultValue = List.of("day_pt", "today");

        Optional<ResolvedTableWritePlan> plan = new TableResolver(sink).resolve(
                json("{\"data\":[{\"today\":\"2026-06-12\",\"day_pt\":\"2026-06-12\"}]}"), record());

        Assertions.assertTrue(plan.isPresent());
        Assertions.assertEquals(List.of("_id_", "day_pt"), plan.get().tableConfig.primaryKeys);
        Assertions.assertEquals(64, String.valueOf(plan.get().records.get(0).get("_id_")).length());
    }

    private PaimonSinkConfig baseSink() throws Exception {
        PaimonSinkConfig sink = new PaimonSinkConfig();
        sink.options.put("warehouse", "file:///tmp/paimon");
        PaimonTableConfig table = new PaimonTableConfig();
        table.database = JacksonUtils.str2JsonNode("\"dw_dev\"");
        table.tableName = JacksonUtils.str2JsonNode("\"ods_test\"");
        table.columnsMapping = JacksonUtils.str2JsonNode("\"data\"");
        table.partitionKeys = JacksonUtils.str2JsonNode("[\"day_pt\"]");
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
