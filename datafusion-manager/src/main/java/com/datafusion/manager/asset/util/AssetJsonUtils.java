package com.datafusion.manager.asset.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.datafusion.manager.asset.constant.AssetLineageConstant.TAG_SET;

/**
 * 资产 JSON 工具类.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/10
 * @since 1.0.0
 */
public final class AssetJsonUtils {

    private AssetJsonUtils() {
    }

    /**
     * 将标签数组包装为标准属性结构.
     *
     * @param tagArray 标签数组或标准属性结构
     * @return 标准属性结构，无有效标签时返回 null
     */
    public static JsonNode wrapTagSet(JsonNode tagArray) {
        if (tagArray == null || tagArray.isNull() || (tagArray.isArray() && tagArray.isEmpty())) {
            return null;
        }
        if (tagArray.has(TAG_SET)) {
            JsonNode value = tagArray.get(TAG_SET);
            return value.isArray() && value.isEmpty() ? null : tagArray;
        }
        ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
        wrapper.set(TAG_SET, tagArray);
        return wrapper;
    }

    /**
     * 从标准属性结构中获取标签数组，兼容旧数组格式.
     *
     * @param edgeProp 边属性
     * @return 标签数组或原始旧格式
     */
    public static JsonNode unwrapTagSet(JsonNode edgeProp) {
        if (edgeProp == null || edgeProp.isNull()) {
            return JsonNodeFactory.instance.arrayNode();
        }
        return edgeProp.has(TAG_SET) ? edgeProp.get(TAG_SET) : edgeProp;
    }
}
