package com.datafusion.manager.asset.enums;

/**
 * 组织.
 * @author xufeng
 * @version 1.0.0, 2026/3/13
 * @since 2026/3/13
 */
public enum OrganizationEnum {
    /**
     * 固德威.
     */
    GOODWE("goodwe", "goodwe");

    /**
     * 构造函数.
     */
    OrganizationEnum(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    /**
     * 组织类型.
     */
    String name;

    /**
     * 组织类型描述.
     */
    String desc;

    /**
     * 返回组织类型.
     *
     * @return 组织类型
     */
    public String getName() {
        return name;
    }

    /**
     * 返回组织类型.
     *
     * @return 组织类型
     */
    public String getDesc() {
        return desc;
    }
}
