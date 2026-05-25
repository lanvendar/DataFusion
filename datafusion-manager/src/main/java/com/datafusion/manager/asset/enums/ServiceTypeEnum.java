package com.datafusion.manager.asset.enums;

/**
 * 服务类型.
 * @author xufeng
 * @version 1.0.0, 2026/3/13
 * @since 2026/3/13
 */
public enum ServiceTypeEnum {

    /**
     * VPP.
     */
    K8S_POD("spring", "spring");

    /**
     * 构造函数.
     */
    ServiceTypeEnum(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    /**
     * 服务类型.
     */
    String name;

    /**
     * 服务类型描述.
     */
    String desc;

    /**
     * 返回服务类型.
     *
     * @return 服务类型
     */
    public String getName() {
        return name;
    }

    /**
     * 返回服务类型.
     *
     * @return 服务类型
     */
    public String getDesc() {
        return desc;
    }
}
