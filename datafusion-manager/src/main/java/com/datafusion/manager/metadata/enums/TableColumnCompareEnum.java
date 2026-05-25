package com.datafusion.manager.metadata.enums;

/**
 * TableColumnCompareEnum.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
public enum TableColumnCompareEnum {
    /**
     * 存在差异.
     */
    DIFFERENT("类型不一致"),
    
    /**
     * 新增字段.
     */
    NEW("新增字段"),
    
    /**
     * 缺失字段.
     */
    DELETE("缺失字段"),
    
    /**
     * 完全一致.
     */
    IDENTICAL("完全一致");
    
    /**
     * 描述.
     */
    private final String desc;
    
    /**
     * 构造方法.
     *
     * @param desc 连接方式
     */
    TableColumnCompareEnum(String desc) {
        this.desc = desc;
    }
    
    public String getDesc() {
        return desc;
    }
}
