package com.datafusion.plugin.kafka.json.core.enums;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

/**
 * Paimon 主键模式.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum PrimaryKeyMode {

    /**
     * 字段主键.
     */
    FIELDS,

    /**
     * 代理主键.
     */
    PROXY;

    /**
     * 解析主键模式.
     *
     * @param value 配置值
     * @return 主键模式
     */
    public static PrimaryKeyMode parse(String value) {
        String text = TextUtils.upper(value, null);
        if (TextUtils.isBlank(text)) {
            throw new KafkaJsonPaimonException("primaryKey.mode is required");
        }
        try {
            return PrimaryKeyMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new KafkaJsonPaimonException("Unsupported primaryKey.mode: " + value, e);
        }
    }
}
