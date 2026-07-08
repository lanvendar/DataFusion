package com.datafusion.plugin.flink.table.core.enums;

import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.util.TextUtils;

/**
 * 代理主键生成类型.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ProxyPrimaryKeyType {

    /**
     * UUID.
     */
    UUID,

    /**
     * SHA-256.
     */
    SHA_256,

    /**
     * SHA-512.
     */
    SHA_512;

    /**
     * 解析代理主键生成类型.
     *
     * @param value 配置值
     * @return 代理主键生成类型
     */
    public static ProxyPrimaryKeyType parse(String value) {
        String text = TextUtils.upper(value, UUID.name()).replace('-', '_');
        try {
            return ProxyPrimaryKeyType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkTableException("Unsupported proxyPrimaryKeyType: " + value, e);
        }
    }
}
