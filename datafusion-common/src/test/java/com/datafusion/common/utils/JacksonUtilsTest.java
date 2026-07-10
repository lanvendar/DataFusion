package com.datafusion.common.utils;

import com.datafusion.common.exception.CommonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson 工具类测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/10
 * @since 1.0.0
 */
class JacksonUtilsTest {

    @Test
    void shouldUseConfiguredLocalDateTimeFormat() throws Exception {
        Map<String, LocalDateTime> value = Map.of("time", LocalDateTime.of(2026, 7, 10, 12, 34, 56));

        String json = JacksonUtils.compactJson(value);

        assertEquals("{\"time\":\"2026-07-10 12:34:56\"}", json);
    }

    @Test
    void shouldMinifyJsonString() throws Exception {
        String json = "{\n  \"id\": 1,\n  \"nested\": {\n    \"value\": \"a\"\n  }\n}";

        String compactJson = JacksonUtils.minifyJson(json);

        assertEquals("{\"id\":1,\"nested\":{\"value\":\"a\"}}", compactJson);
    }

    @Test
    void shouldCompactStringAsJsonValue() throws Exception {
        String json = JacksonUtils.compactJson("plain-text");

        assertEquals("\"plain-text\"", json);
    }

    @Test
    void shouldPrettyPrintJsonValue() throws Exception {
        String json = JacksonUtils.prettyJson(Map.of("id", 1));

        assertTrue(json.contains(System.lineSeparator()));
        assertEquals(Map.of("id", 1), JacksonUtils.str2Map(json));
    }

    @Test
    void shouldReadStrictJsonByClass() throws Exception {
        StrictValue value = JacksonUtils.readStrict("{\"id\":1}", StrictValue.class);

        assertEquals(1, value.id());
        assertThrows(UnrecognizedPropertyException.class,
                () -> JacksonUtils.readStrict("{\"id\":1,\"extra\":2}", StrictValue.class));
        assertThrows(JsonProcessingException.class,
                () -> JacksonUtils.readStrict("{'id':1}", StrictValue.class));
    }

    @Test
    void shouldReadStrictJsonByTypeReference() throws Exception {
        Map<String, Integer> value = JacksonUtils.readStrict("{\"id\":1}",
                new TypeReference<Map<String, Integer>>() {
                });

        assertEquals(Map.of("id", 1), value);
    }

    @Test
    void shouldValidateStandardJson() {
        assertTrue(JacksonUtils.isValidJson("{\"id\":1}"));
        assertTrue(JacksonUtils.isValidJson("null"));
        assertFalse(JacksonUtils.isValidJson("{'id':1}"));
        assertFalse(JacksonUtils.isValidJson("{\"id\":1} true"));
        assertFalse(JacksonUtils.isValidJson("{\"id\":1,\"id\":2}"));
        assertFalse(JacksonUtils.isValidJson(""));
        assertFalse(JacksonUtils.isValidJson(null));
    }

    @Test
    void shouldPreserveConversionExceptionCause() {
        CommonException exception = assertThrows(CommonException.class,
                () -> JacksonUtils.tryObj2Bean(Map.of("value", "invalid"), Integer.class));

        assertEquals("JSON反序列化异常", exception.getMessage());
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void shouldPreserveSerializationExceptionCause() {
        CommonException exception = assertThrows(CommonException.class,
                () -> JacksonUtils.tryObj2Str(new Object()));

        assertEquals("JSON序列化异常", exception.getMessage());
        assertTrue(exception.getCause() != null);
    }

    @Test
    void shouldTreatStringPojoAsTextNode() {
        JsonNode jsonNode = JacksonUtils.pojo2JsonNode("plain-text");

        assertTrue(jsonNode.isTextual());
        assertEquals("plain-text", jsonNode.asText());
    }

    @Test
    void shouldReturnNullWhenJsonNodeConversionFails() {
        JsonNode jsonNode = JacksonUtils.createObjectNode().put("value", "invalid");

        Integer value = JacksonUtils.jsonNode2PojoOrNull(jsonNode, Integer.class);

        assertNull(value);
    }

    /**
     * 严格解析测试对象.
     *
     * @param id 标识
     */
    private record StrictValue(int id) {
    }
}
