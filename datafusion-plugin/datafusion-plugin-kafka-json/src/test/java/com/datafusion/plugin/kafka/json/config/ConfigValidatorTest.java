package com.datafusion.plugin.kafka.json.config;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.SystemFieldNames;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    /**
     * 表配置必须静态声明 database 和 name.
     */
    @Test
    void shouldRejectMissingStaticTableName() throws Exception {
        KafkaJsonPaimonJobConfig config = baseConfig();
        PaimonTableConfig table = config.sink.tables.get(0);
        table.table.name = null;

        KafkaJsonPaimonException exception = Assertions.assertThrows(KafkaJsonPaimonException.class,
                () -> new ConfigValidator().validate(config));

        Assertions.assertTrue(exception.getMessage().contains("sink.tables[].table.name"));
    }

    /**
     * 真实表存在时允许只配置静态库表和字段映射.
     */
    @Test
    void shouldAllowStaticTableIdentityWithoutCreateMetadata() throws Exception {
        KafkaJsonPaimonJobConfig config = baseConfig();
        config.sink.tables.get(0).table.comment = null;
        config.sink.tables.get(0).table.createIfNotExists = null;
        config.sink.tables.get(0).table.partitionKeys = null;
        config.sink.tables.get(0).table.primaryKeys = null;

        Assertions.assertDoesNotThrow(() -> new ConfigValidator().validate(config));
    }

    /**
     * job table 元数据启用整段覆盖时必须补齐建表关键配置.
     */
    @Test
    void shouldRejectPartialJobTableMetadataOverride() throws Exception {
        KafkaJsonPaimonJobConfig config = baseConfig();
        config.sink.loadMode = "UPSERT";
        PaimonTableConfig table = config.sink.tables.get(0);
        table.table.partitionKeys = null;

        KafkaJsonPaimonException exception = Assertions.assertThrows(KafkaJsonPaimonException.class,
                () -> new ConfigValidator().validate(config));

        Assertions.assertTrue(exception.getMessage().contains("partitionKeys"));
    }

    /**
     * PROXY 主键自动字段不能和用户字段重名.
     */
    @Test
    void shouldRejectUserColumnConflictingWithProxyPrimaryKey() throws Exception {
        KafkaJsonPaimonJobConfig config = baseConfig();
        PaimonTableConfig table = config.sink.tables.get(0);
        table.table.primaryKeys.mode = "PROXY";
        table.columns.add(column(SystemFieldNames.PROXY_PRIMARY_KEY_FIELD, "VARCHAR", false));

        KafkaJsonPaimonException exception = Assertions.assertThrows(KafkaJsonPaimonException.class,
                () -> new ConfigValidator().validate(config));

        Assertions.assertTrue(exception.getMessage().contains(SystemFieldNames.PROXY_PRIMARY_KEY_FIELD));
    }

    /**
     * Kafka 元数据自动字段不能和用户字段重名.
     */
    @Test
    void shouldRejectUserColumnConflictingWithKafkaMetadataField() throws Exception {
        KafkaJsonPaimonJobConfig config = baseConfig();
        PaimonTableConfig table = config.sink.tables.get(0);
        table.table.includeKafkaMetadataFields = true;
        table.columns.add(column(SystemFieldNames.KAFKA_TOPIC_FIELD, "VARCHAR", false));

        KafkaJsonPaimonException exception = Assertions.assertThrows(KafkaJsonPaimonException.class,
                () -> new ConfigValidator().validate(config));

        Assertions.assertTrue(exception.getMessage().contains(SystemFieldNames.KAFKA_TOPIC_FIELD));
    }

    private KafkaJsonPaimonJobConfig baseConfig() throws Exception {
        KafkaJsonPaimonJobConfig config = new KafkaJsonPaimonJobConfig();
        config.job.id = "job-a";
        config.source.bootstrapServers = "localhost:9092";
        config.source.groupId = "group-a";
        config.source.topics.add("topic-a");
        config.sink = sink();
        return config;
    }

    private PaimonSinkConfig sink() throws Exception {
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

    private PrimaryKeyConfig primaryKeys(String... fields) {
        PrimaryKeyConfig primaryKeys = new PrimaryKeyConfig();
        primaryKeys.defaultValue = List.of(fields);
        return primaryKeys;
    }
}
