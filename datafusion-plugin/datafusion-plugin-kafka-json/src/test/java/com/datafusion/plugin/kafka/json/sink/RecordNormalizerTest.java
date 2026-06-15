package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * RecordNormalizer 单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class RecordNormalizerTest {

    /**
     * nullable 由真实 Paimon rowType 最终裁决,RecordNormalizer 不应基于配置提前跳过.
     */
    @Test
    void shouldNotRejectEmptyValueByConfiguredNullable() {
        ResolvedTableConfig tableConfig = new ResolvedTableConfig();
        tableConfig.database = "dw_dev";
        tableConfig.tableName = "ods_test";
        tableConfig.columns.add(column("category_name", false));

        List<PaimonRecord> records = RecordNormalizer.normalize(List.of(PaimonRecord.of(null, "topic-a", 0, 10L, 0)),
                tableConfig, RecordErrorPolicy.SKIP);

        Assertions.assertEquals(1, records.size());
        Assertions.assertTrue(records.get(0).values.containsKey("category_name"));
        Assertions.assertNull(records.get(0).values.get("category_name"));
    }

    private ColumnConfig column(String name, boolean nullable) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.dataType = "VARCHAR";
        column.length = 64;
        column.nullable = nullable;
        return column;
    }
}
