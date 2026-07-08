package com.datafusion.plugin.flink.table.expression;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.core.enums.JsonType;
import com.datafusion.plugin.flink.table.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 表达式简写归一化工具.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExpressionSpecNormalizer {

    private ExpressionSpecNormalizer() {
    }

    /**
     * 归一化常量表达式.
     *
     * @param node JSON 配置节点
     * @param defaultJsonType 默认 JSON 类型
     * @return 表达式定义
     */
    public static ExpressionSpec constant(JsonNode node, JsonType defaultJsonType) {
        return normalize(node, defaultJsonType, false, null);
    }

    /**
     * 归一化 JMESPath 表达式.
     *
     * @param node JSON 配置节点
     * @param defaultJsonType 默认 JSON 类型
     * @param defaultPath 默认路径
     * @return 表达式定义
     */
    public static ExpressionSpec path(JsonNode node, JsonType defaultJsonType, String defaultPath) {
        return normalize(node, defaultJsonType, true, defaultPath);
    }

    /**
     * 归一化取值表达式.
     *
     * @param node JSON 配置节点
     * @param defaultJsonType 默认 JSON 类型
     * @param defaultPath 默认路径
     * @return 表达式定义
     */
    public static ExpressionSpec value(JsonNode node, JsonType defaultJsonType, String defaultPath) {
        return normalize(node, defaultJsonType, false, defaultPath);
    }

    private static ExpressionSpec normalize(JsonNode node, JsonType defaultJsonType, boolean textAsPath, String defaultPath) {
        ExpressionSpec spec = new ExpressionSpec();
        spec.jsonType = defaultJsonType.name();
        spec.path = defaultPath;
        if (node == null || node.isNull()) {
            return spec;
        }
        if (node.isObject()) {
            readObjectSpec(spec, node);
            if (TextUtils.isBlank(spec.path)) {
                spec.path = defaultPath;
            }
            if (TextUtils.isBlank(spec.jsonType)) {
                spec.jsonType = defaultJsonType.name();
            }
            return spec;
        }
        Object value = toJavaValue(node);
        if (textAsPath && value instanceof String text) {
            spec.path = text;
        } else {
            spec.defaultValue = value;
        }
        return spec;
    }

    private static void readObjectSpec(ExpressionSpec spec, JsonNode node) {
        JsonNode path = node.get("path");
        if (path != null && !path.isNull()) {
            spec.path = path.asText();
        }
        JsonNode defaultValue = node.get("defaultValue");
        if (defaultValue != null && !defaultValue.isNull()) {
            spec.defaultValue = toJavaValue(defaultValue);
        }
        JsonNode jsonType = node.get("jsonType");
        if (jsonType != null && !jsonType.isNull()) {
            spec.jsonType = jsonType.asText();
        }
    }

    private static Object toJavaValue(JsonNode node) {
        try {
            return JacksonUtils.treeNode2Bean(node, Object.class);
        } catch (Exception e) {
            throw new FlinkTableException("Failed to convert expression value: " + node, e);
        }
    }
}
