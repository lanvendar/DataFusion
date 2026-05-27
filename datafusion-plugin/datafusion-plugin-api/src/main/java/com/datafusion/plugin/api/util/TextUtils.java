package com.datafusion.plugin.api.util;

/**
 * 文本工具类,提供字符串判断和转换功能.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TextUtils {
    
    /**
     * 私有构造器,防止实例化.
     */
    private TextUtils() {
    }

    /**
     * 判断字符串是否为空或空白.
     *
     * @param value 待判断的字符串
     * @return true 表示空或空白
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 将字符串转换为大写,如果为空则返回默认值.
     *
     * @param value 原始字符串
     * @param defaultValue 默认值
     * @return 大写字符串或默认值
     */
    public static String upper(String value, String defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        return value.trim().toUpperCase();
    }
}
