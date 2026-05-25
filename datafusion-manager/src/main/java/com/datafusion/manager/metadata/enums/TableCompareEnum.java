package com.datafusion.manager.metadata.enums;

/**
 * TableCompareEnum.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
public enum TableCompareEnum {
    
    /**
     * 存在差异.
     */
    DIFFERENT("存在差异"),
    /**
     * 目标不存在.
     */
    TARGET_NOT_EXIST("目标不存在"),
    
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
    TableCompareEnum(String desc) {
        this.desc = desc;
    }
    
    public String getDesc() {
        return desc;
    }
}
