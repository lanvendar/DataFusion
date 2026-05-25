package com.datafusion.manager.asset.enums;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/11
 * @since 2025/10/11
 */

public enum NodeTypeEnum {
    
    /**
     * 菜单.
     */
    GUI("GUI", "菜单"),
    
    /**
     * 服务.
     */
    SERVICE("SERVICE", "服务"),
    
    /**
     * 数据.
     */
    DATABASE("DATABASE", "数据");
    
    /**
     * 构造函数.
     */
    NodeTypeEnum(String nodeType, String nodeTypeDesc) {
        this.nodeType = nodeType;
        this.nodeTypeDesc = nodeTypeDesc;
    }
    
    /**
     * 节点类型.
     */
    String nodeType;
    
    /**
     * 节点类型描述.
     */
    String nodeTypeDesc;
    
    /**
     * 返回节点类型.
     *
     * @return 节点类型
     */
    public String getNodeType() {
        return nodeType;
    }
    
    /**
     * 返回资源类型.
     *
     * @return 资源类型
     */
    public String getNodeTypeDesc() {
        return nodeTypeDesc;
    }
}
