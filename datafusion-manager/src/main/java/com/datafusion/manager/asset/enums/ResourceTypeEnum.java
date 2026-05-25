package com.datafusion.manager.asset.enums;

/**
 * 资源类型枚举类.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */

public enum ResourceTypeEnum {
    
    /**
     * 菜单.
     */
    GUI("GUI", "菜单"),
    
    /**
     * 接口.
     */
    SERVICE("SERVICE", "服务"),
    
    /**
     * 库表.
     */
    DATABASE("DATABASE", "库表"),
    
    /**
     * ETL任务.
     */
    ETL("ETL", "ETL任务"),
    
    /**
     * 指标.
     */
    METRIC("METRIC", "指标"),
    
    /**
     * 接口.
     */
    API("API", "接口");
    
    /**
     * 资源类型.
     */
    String resouceType;
    
    /**
     * 资源类型.
     */
    String resouceTypeDesc;
    
    /**
     * 构造函数.
     */
    ResourceTypeEnum(String resouceType, String resouceTypeDesc) {
        this.resouceType = resouceType;
        this.resouceTypeDesc = resouceTypeDesc;
    }
    
    /**
     * 返回资源类型.
     * @return 资源类型
     */
    public String getResouceType() {
        return resouceType;
    }
    
    /**
     * 返回资源类型.
     * @return 资源类型
     */
    public String getResouceTypeDesc() {
        return resouceTypeDesc;
    }
    
    /**
     * 返回资源类型.
     * @param resourceType 资源类型
     * @return 资源类型
     */
    public static String getTypeDescByType(String resourceType) {
        if (resourceType == null) {
            return null;
        }
        for (ResourceTypeEnum enumEntry : ResourceTypeEnum.values()) {
            if (enumEntry.getResouceType().equalsIgnoreCase(resourceType)) { // 忽略大小写匹配
                return enumEntry.getResouceTypeDesc();
            }
        }
        return null;
    }
    
}
