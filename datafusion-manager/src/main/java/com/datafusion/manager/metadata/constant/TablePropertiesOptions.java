package com.datafusion.manager.metadata.constant;

import com.datafusion.common.options.ConfigOption;

import static com.datafusion.common.options.ConfigOptions.key;

/**
 * 表属性参数配置枚举.
 *
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */

public class TablePropertiesOptions {
    /**
     * 私有构造函数.
     */
    private TablePropertiesOptions() {
        throw new IllegalStateException("static configuration class");
    }
    
    /**
     * 大数据库中,数据分布键.
     */
    public static final ConfigOption<String> BUCKET_KEYS = key("bucket_keys")
            .stringType()
            .noDefaultValue()
            .withDescription("大数据库中,数据分桶键.");
    
    /**
     * 大数据库中,数据分桶数量.
     */
    public static final ConfigOption<String> BUCKET_NUM = key("bucket_num")
            .stringType()
            .defaultValue("5")
            .withDescription("大数据库中,数据分桶数量.");
    
    /**
     * 大数据库中,数据主键.
     */
    public static final ConfigOption<String> PRIMARY_KEYS = key("primary_keys")
            .stringType()
            .noDefaultValue()
            .withDescription("大数据库中,数据主键.");
    
    /**
     * Hologres底层存储中,属性是自己放进去的,单独定义名称,数据主键.
     */
    public static final ConfigOption<String> HOLOGRE_PRIMARY_KEYS = key("primary_key")
            .stringType()
            .noDefaultValue()
            .withDescription("Hologres数据库中,数据主键.");
    
    /**
     * 大数据库中,数据分区键.
     */
    public static final ConfigOption<String> PARTITION_KEYS = key("partition_keys")
            .stringType()
            .noDefaultValue()
            .withDescription("大数据库中,数据分区键.");
    
    /**
     * 索引键.
     */
    public static final ConfigOption<String> INDEX_KEYS = key("index_keys")
            .stringType()
            .noDefaultValue()
            .withDescription("索引键.");
    
    /**
     * postgres数据库底层区分表类型.
     */
    public static final ConfigOption<String> REL_KIND = key("relkind")
            .stringType()
            .noDefaultValue()
            .withDescription("postgres数据库底层区分表类型.");
    
    /**
     * starrocks或doris 底层区分表类型.
     */
    public static final ConfigOption<String> TABLE_MODEL = key("table_model")
            .stringType()
            .noDefaultValue()
            .withDescription("StarRocks或Doris数据库底层区分表类型.");
    
    
    
    
    
}
