package com.datafusion.plugin.kafka.json.expression;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 表达式结果 JSON 类型.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum JsonType {

    /**
     * 任意类型.
     */
    ANY,

    /**
     * 字符串.
     */
    STRING,

    /**
     * 数值.
     */
    NUMBER,

    /**
     * 布尔.
     */
    BOOLEAN,

    /**
     * 数组.
     */
    ARRAY,

    /**
     * 对象.
     */
    OBJECT;

    /**
     * 解析 JSON 类型.
     *
     * @param value 配置值
     * @return JSON 类型
     */
    public static JsonType parse(String value) {
        String text = TextUtils.upper(value, ANY.name());
        try {
            return JsonType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new KafkaJsonPaimonException("Unsupported jsonType: " + value, e);
        }
    }

    /**
     * 校验值是否匹配当前类型.
     *
     * @param value 待校验值
     * @return true 表示匹配
     */
    public boolean matches(Object value) {
        if (value == null || this == ANY) {
            return true;
        }
        return switch (this) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof Collection<?>;
            case OBJECT -> value instanceof Map<?, ?>;
            default -> true;
        };
    }
}
