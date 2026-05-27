package com.datafusion.plugin.api.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * In-memory cache mode tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class InMemoryIntermediateCacheTest {

    @Test
    public void putModeShouldNotOverrideExistingValue() {
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();

        cache.put("k", "first", 0, "PUT");
        cache.put("k", "second", 0, "PUT");

        Assertions.assertEquals("first", cache.get("k"));
    }

    @Test
    public void upsertModeShouldOverrideExistingValue() {
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();

        cache.put("k", "first", 0, "UPSERT");
        cache.put("k", "second", 0, "UPSERT");

        Assertions.assertEquals("second", cache.get("k"));
    }

    @Test
    public void appendListModeShouldAppendScalarsAndCollections() {
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();

        cache.put("k", "first", 0, "APPEND_LIST");
        cache.put("k", List.of("second", "third"), 0, "APPEND_LIST");

        Assertions.assertEquals(List.of("first", "second", "third"), cache.get("k"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hashModeShouldMergeMapFields() {
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();

        cache.put("k", Map.of("token", "a"), 0, "HASH");
        cache.put("k", Map.of("expire", 100), 0, "HASH");

        Map<String, Object> value = (Map<String, Object>) cache.get("k");
        Assertions.assertEquals("a", value.get("token"));
        Assertions.assertEquals(100, value.get("expire"));
    }
}
