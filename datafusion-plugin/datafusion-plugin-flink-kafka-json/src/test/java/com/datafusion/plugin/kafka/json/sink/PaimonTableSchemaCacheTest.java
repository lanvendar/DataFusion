package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.core.SystemFieldNames;
import com.datafusion.plugin.kafka.json.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableWritePlan;
import org.apache.paimon.types.DataField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paimon 表结构缓存单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class PaimonTableSchemaCacheTest {

    /**
     * 已存在真实表时，解析阶段未提供业务列也会用真实表结构补齐写入计划.
     *
     * @param tempDir 临时目录
     */
    @Test
    void shouldApplyExistingSchemaWhenResolvedColumnsOmitted(@TempDir Path tempDir) {
        PaimonTableSchemaCache cache = new PaimonTableSchemaCache(sink(tempDir), SchemaMismatchPolicy.FAIL);
        cache.put("dw_dev.ods_test", snapshot(List.of(column("today", "VARCHAR", 32, false),
                column(SystemFieldNames.KAFKA_TOPIC_FIELD, "VARCHAR", 512, false))));
        ResolvedTableWritePlan plan = planWithoutColumns();

        Assertions.assertTrue(cache.validate(plan));

        Assertions.assertEquals(2, plan.tableConfig.columns.size());
        Assertions.assertEquals("2026-06-12", plan.records.get(0).get("today"));
        Assertions.assertEquals("topic-a", plan.records.get(0).get(SystemFieldNames.KAFKA_TOPIC_FIELD));
    }

    private PaimonSinkConfig sink(Path tempDir) {
        PaimonSinkConfig sink = new PaimonSinkConfig();
        sink.options.put("warehouse", tempDir.toUri().toString());
        return sink;
    }

    private ResolvedTableWritePlan planWithoutColumns() {
        ResolvedTableWritePlan plan = new ResolvedTableWritePlan();
        plan.tableConfig = new PaimonTableConfig();
        plan.tableConfig.database = "dw_dev";
        plan.tableConfig.tableName = "ods_test";
        plan.tableConfig.includeKafkaMetadataFields = true;
        plan.topic = "topic-a";
        plan.partition = 0;
        plan.offset = 10L;
        plan.sourceRecords = List.of(Map.of("today", "2026-06-12"));
        return plan;
    }

    private PaimonTableSchemaSnapshot snapshot(List<ColumnConfig> columns) {
        Map<String, DataField> fields = new LinkedHashMap<>();
        int id = 0;
        for (ColumnConfig column : columns) {
            fields.put(column.name.toLowerCase(Locale.ROOT),
                    new DataField(id++, column.name, PaimonTableSchemaValidator.paimonType(column), column.comment));
        }
        return new PaimonTableSchemaSnapshot(fields, List.of(), List.of(), Map.of(), null);
    }

    private ColumnConfig column(String name, String dataType, Integer length, boolean nullable) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.dataType = dataType;
        column.length = length;
        column.nullable = nullable;
        return column;
    }
}
