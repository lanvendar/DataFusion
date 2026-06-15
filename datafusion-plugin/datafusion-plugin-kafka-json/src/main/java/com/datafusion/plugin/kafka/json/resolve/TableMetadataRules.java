package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.TableConfig;

/**
 * 表元数据配置规则.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TableMetadataRules {

    private TableMetadataRules() {
    }

    /**
     * 判断 job.json 是否配置了除 database 外的 table 元数据段.
     *
     * @param table table 配置
     * @return true 表示 job.json 启用完整 table 元数据段
     */
    public static boolean hasJobTableMetadata(TableConfig table) {
        if (table == null) {
            return false;
        }
        return table.comment != null
                || table.createIfNotExists != null
                || table.partitionKeys != null
                || table.primaryKeys != null;
    }

    /**
     * 判断单表简化消息是否可以在没有 schema.table.name 时路由.
     *
     * @param tableConfig 表配置
     * @return true 表示 job 已提供完整静态建表定义
     */
    public static boolean canRouteWithoutSchemaTableName(PaimonTableConfig tableConfig) {
        if (tableConfig == null || tableConfig.table == null) {
            return false;
        }
        return hasJobTableMetadata(tableConfig.table) && tableConfig.columns != null && !tableConfig.columns.isEmpty();
    }
}
