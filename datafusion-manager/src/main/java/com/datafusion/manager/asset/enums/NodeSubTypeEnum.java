package com.datafusion.manager.asset.enums;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/11
 * @since 2025/10/11
 */

public enum NodeSubTypeEnum {
    
    /**
     * 菜单.
     */
    MENU("MENU", "菜单"),
    
    /**
     * 表.
     */
    TABLE("TABLE", "表"),
    
    /**
     * 字段.
     */
    COLUMN("COLUMN", "字段"),
    
    /**
     * 接口.
     */
    API("API", "接口"),
    
    /**
     * ETL任务.
     */
    METRIC("METRIC", "指标");

    /**
     * 资源类型.
     */
    String nodeSubType;
    
    /**
     * 资源类型.
     */
    String nodeSubTypeDesc;
    
    /**
     * 构造函数.
     */
    NodeSubTypeEnum(String nodeSubType, String nodeSubTypeDesc) {
        this.nodeSubType = nodeSubType;
        this.nodeSubTypeDesc = nodeSubTypeDesc;
    }
    
    /**
     * 返回资源类型.
     *
     * @return 资源类型
     */
    public String getNodeSubType() {
        return nodeSubType;
    }
    
    /**
     * 返回资源类型.
     *
     * @return 资源类型
     */
    public String getNodeSubTypeDesc() {
        return nodeSubTypeDesc;
    }
}
