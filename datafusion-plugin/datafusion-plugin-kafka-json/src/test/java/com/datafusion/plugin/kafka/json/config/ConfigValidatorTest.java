package com.datafusion.plugin.kafka.json.config;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * ConfigValidator 单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class ConfigValidatorTest {

    /**
     * columns[].value 为空时允许按列名默认取值.
     */
    @Test
    void shouldAllowColumnValueOmitted() throws Exception {
        KafkaJsonPaimonJobConfig config = new KafkaJsonPaimonJobConfig();
        config.job.id = "job-a";
        config.source.bootstrapServers = "localhost:9092";
        config.source.groupId = "group-a";
        config.source.topics.add("topic-a");
        config.sink = sink();

        Assertions.assertDoesNotThrow(() -> new ConfigValidator().validate(config));
    }

    private PaimonSinkConfig sink() throws Exception {
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
}
