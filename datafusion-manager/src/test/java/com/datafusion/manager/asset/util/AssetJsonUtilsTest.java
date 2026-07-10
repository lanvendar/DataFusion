package com.datafusion.manager.asset.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资产 JSON 工具类测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/10
 * @since 1.0.0
 */
class AssetJsonUtilsTest {

    @Test
    void shouldWrapTagArray() {
        JsonNode tagArray = JsonNodeFactory.instance.arrayNode().addObject().put("key", "value");

        JsonNode edgeProp = AssetJsonUtils.wrapTagSet(tagArray);

        assertSame(tagArray, edgeProp.get("tagSet"));
    }

    @Test
    void shouldKeepStandardTagSetStructure() {
        JsonNode edgeProp = JsonNodeFactory.instance.objectNode()
                .set("tagSet", JsonNodeFactory.instance.arrayNode().add("value"));

        assertSame(edgeProp, AssetJsonUtils.wrapTagSet(edgeProp));
        assertEquals("value", AssetJsonUtils.unwrapTagSet(edgeProp).get(0).asText());
    }

    @Test
    void shouldHandleEmptyTagSet() {
        assertNull(AssetJsonUtils.wrapTagSet(JsonNodeFactory.instance.arrayNode()));
        assertTrue(AssetJsonUtils.unwrapTagSet(null).isEmpty());
    }
}
