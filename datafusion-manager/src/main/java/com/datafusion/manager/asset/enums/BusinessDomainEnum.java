package com.datafusion.manager.asset.enums;

/**
 * 业务域.
 * @author xufeng
 * @version 1.0.0, 2026/3/13
 * @since 2026/3/13
 */
public enum BusinessDomainEnum {

    /**
     * VPP.
     */
    VPP("vpp", "vpp");

    /**
     * 构造函数.
     */
    BusinessDomainEnum(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    /**
     * 节点类型.
     */
    String name;

    /**
     * 节点类型描述.
     */
    String desc;

    /**
     * 返回节点类型.
     *
     * @return 节点类型
     */
    public String getName() {
        return name;
    }

    /**
     * 返回资源类型.
     *
     * @return 资源类型
     */
    public String getDesc() {
        return desc;
    }
}
