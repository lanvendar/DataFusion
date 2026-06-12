package com.datafusion.plugin.flink.schema.paimon.sink;

import com.datafusion.plugin.flink.schema.paimon.message.ColumnConfig;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableConfig;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Paimon 记录归一化测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class RecordNormalizerTest {

    /**
     * 必输字段为空时应只丢弃当前记录.
     */
    @Test
    void shouldSkipOnlyRecordWithEmptyRequiredColumn() {
        ResolvedTableConfig tableConfig = tableConfig();
        List<Map<String, Object>> normalized = RecordNormalizer.normalize(List.of(record(" ", "bad"), record("2", "good")), tableConfig);

        assertEquals(1, normalized.size());
        assertEquals("2", normalized.get(0).get("id"));
        assertEquals("good", normalized.get(0).get("name"));
    }

    /**
     * 必输字段配置默认值时应使用默认值并保留记录.
     */
    @Test
    void shouldKeepRequiredColumnWithDefaultValue() {
        ResolvedTableConfig tableConfig = tableConfig();
        tableConfig.columns.get(0).defaultValue = "0";

        List<Map<String, Object>> normalized = RecordNormalizer.normalize(List.of(record(null, "defaulted")), tableConfig);

        assertEquals(1, normalized.size());
        assertEquals("0", normalized.get(0).get("id"));
    }

    private ResolvedTableConfig tableConfig() {
        ResolvedTableConfig config = new ResolvedTableConfig();
        config.database = "dw_dev";
        config.table.name = "configured_table";
        config.columns = List.of(column("id", false), column("name", true));
        return config;
    }

    private ColumnConfig column(String name, boolean nullable) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.type = "VARCHAR";
        column.nullable = nullable;
        return column;
    }

    private Map<String, Object> record(Object id, String name) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        record.put("name", name);
        return record;
    }
}
