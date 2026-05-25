package com.datafusion.common.enums;

/**
 * 数据库连接方式枚举.
 *
 * @author gzy
 * @version 1.0.0, 2021/12/30
 * @since 2021/12/30
 */
public enum ConnectTypeEnum {
    /**
     * jdbc 方式.
     */
    JDBC("jdbc"),
    /**
     * ipPort 方式.
     */
    IP_PORT("ipPort"),

    /**
     * SDK 方式.
     */
    SDK("SDK");
    
    /**
     * 连接方式.
     */
    private final String connectType;
    
    /**
     * 构造方法.
     *
     * @param connectType 连接方式
     */
    ConnectTypeEnum(String connectType) {
        this.connectType = connectType;
    }
    
    public String getConnectType() {
        return connectType;
    }
}
