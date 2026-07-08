package com.datafusion.plugin.kafka.json.util;

import java.util.Locale;

/**
 * 字符串工具.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * 判断字符串是否为空白.
     *
     * @param value 输入字符串
     * @return 是否为空白
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 返回大写字符串.
     *
     * @param value 输入值
     * @param defaultValue 默认值
     * @return 大写字符串
     */
    public static String upper(String value, String defaultValue) {
        String actual = isBlank(value) ? defaultValue : value;
        return actual == null ? null : actual.trim().toUpperCase(Locale.ROOT);
    }
}
