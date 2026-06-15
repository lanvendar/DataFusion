package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 标准 schema.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class StandardSchema {

    /**
     * 表 schema.
     */
    StandardTableSchema table = new StandardTableSchema();

    /**
     * 字段 schema.
     */
    List<ColumnConfig> columns = new ArrayList<>();

    /**
     * Kafka 标准 table schema.
     */
    static class StandardTableSchema {

        /**
         * database.
         */
        Object database;

        /**
         * 表名.
         */
        Object name;

        /**
         * 表注释.
         */
        Object comment;

        /**
         * 是否建表.
         */
        Object createIfNotExists;

        /**
         * 分区字段.
         */
        Object partitionKeys;

        /**
         * 主键配置.
         */
        PrimaryKeyConfig primaryKeys;
    }
}
