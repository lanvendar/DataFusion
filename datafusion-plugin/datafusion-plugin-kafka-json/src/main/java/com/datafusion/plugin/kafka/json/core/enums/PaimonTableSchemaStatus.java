package com.datafusion.plugin.kafka.json.core.enums;

/**
 * Paimon 表结构缓存状态.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum PaimonTableSchemaStatus {

    /**
     * 真实 Paimon 表存在.
     */
    EXISTS,

    /**
     * job 已配置该表,但启动时真实 Paimon 表不存在.
     */
    MISSING_CONFIGURED
}
