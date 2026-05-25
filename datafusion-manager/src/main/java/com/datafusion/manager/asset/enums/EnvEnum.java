package com.datafusion.manager.asset.enums;

/**
 * 环境.
 * @author xufeng
 * @version 1.0.0, 2026/3/13
 * @since 2026/3/13
 */
public enum EnvEnum {

    /**
     * VPP.
     */
    K8S_POD("k8s-pod", "k8s-pod");

    /**
     * 构造函数.
     */
    EnvEnum(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    /**
     * 环境类型.
     */
    String name;

    /**
     * 环境类型描述.
     */
    String desc;

    /**
     * 返回环境类型.
     *
     * @return 环境类型
     */
    public String getName() {
        return name;
    }

    /**
     * 返回环境类型.
     *
     * @return 环境类型
     */
    public String getDesc() {
        return desc;
    }
}
