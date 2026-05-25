package com.datafusion.manager.metadata.enums;

/**
 * postgre 底层区分表类型.
 *
 * @author zyw
 * @version 1.0.0 , 2025/8/19
 * @since 2025/8/19
 */
public enum Relkind {
    
    /**
     * 普通表,排除分区表.
     */
    NORMAL_TABLE("r"),
    
    /**
     * 分区表父表.
     */
    PARTITION_PARENT_TABLE("p"),
    
    /**
     * 分区表子表.
     */
    PARTITION_SUB_TABLE("s"),
    
    /**
     * 视图.
     */
    PARTITION_VIEW_TABLE("v"),
    
    /**
     * 视图.
     */
    PARTITION_MATERIALIZED_VIEW_TABLE("m");
    
    /**
     * 私有构造方法.
     */
    private Relkind(String type) {
        this.type = type;
    }
    
    /**
     * 类型.
     */
    private String type;
    
    /**
     * 返回类型.
     *
     * @return 类型
     */
    public String getType() {
        return type;
    }
    
}
