package com.datafusion.manager.asset.enums;

import lombok.Getter;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/11
 * @since 2025/10/11
 */

@Getter
public enum ResourceTagEnum {
    
    /**
     * 节点.
     */
    NODE(1, "节点"),
    
    /**
     * 表.
     */
    EDGE(2, "边"),
    
    /**
     * 节点和边.
     */
    NODE_AND_EDGE(3, "节点和边");
    
    /**
     * 资源标签.
     */
    Integer resourceTagType;
    
    /**
     * 资源标签描述.
     */
    String resourceTagTypeDesc;
    
    /**
     * 构造函数.
     */
    ResourceTagEnum(int resourceTagType, String resourceTagTypeDesc) {
        this.resourceTagType = resourceTagType;
        this.resourceTagTypeDesc = resourceTagTypeDesc;
    }
    
    /**
     * 根据tag返回tag的描述.
     *
     * @param resourceTagType 标签说明
     * @return 返回tag的描述
     */
    public static String getResourceTagTypeDesc(Integer resourceTagType) {
        if (resourceTagType == null) {
            return null;
        }
        for (ResourceTagEnum value : values()) {
            if (value.getResourceTagType() == resourceTagType) {
                return value.getResourceTagTypeDesc();
            }
        }
        return null;
    }
}