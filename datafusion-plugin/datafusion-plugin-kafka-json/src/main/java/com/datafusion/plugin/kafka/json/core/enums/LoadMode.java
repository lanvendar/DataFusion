package com.datafusion.plugin.kafka.json.core.enums;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

/**
 * Paimon 写入模式.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum LoadMode {

    /**
     * 追加写入.
     */
    APPEND,

    /**
     * 主键更新写入.
     */
    UPSERT;

    /**
     * 解析写入模式.
     *
     * @param value 配置值
     * @param defaultValue 默认值
     * @return 写入模式
     */
    public static LoadMode parse(String value, LoadMode defaultValue) {
        String text = TextUtils.upper(value, defaultValue.name());
        try {
            return LoadMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new KafkaJsonPaimonException("Unsupported loadMode: " + value, e);
        }
    }
}
