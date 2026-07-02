package com.datafusion.plugin.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * JSON 工具类,提供 JSON 序列化和反序列化功能.
 *
 * <p>
 * 使用 Jackson ObjectMapper 实现,配置为忽略未知属性.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class JsonUtils {

    /** JSON 对象映射器实例. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 私有构造器,防止实例化.
     */
    private JsonUtils() {
    }

    /**
     * 将 JSON 字符串反序列化为指定类型.
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T> 泛型类型参数
     * @return 反序列化后的对象
     * @throws IllegalArgumentException 当 JSON 格式无效时抛出
     */
    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON config: " + e.getMessage(), e);
        }
    }

    /**
     * 将对象序列化为 JSON 字符串.
     *
     * @param value 待序列化的对象
     * @return JSON 字符串
     * @throws IllegalArgumentException 当序列化失败时抛出
     */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to write JSON: " + e.getMessage(), e);
        }
    }

    /**
     * 将对象转换为普通 Java 对象(Map、List、基本类型等).
     *
     * @param value 原始对象
     * @return 转换后的普通对象
     */
    @SuppressWarnings("unchecked")
    public static Object toPlainObject(Object value) {
        if (value == null || value instanceof Map || value instanceof Iterable
                || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return MAPPER.convertValue(value, Object.class);
    }
}
