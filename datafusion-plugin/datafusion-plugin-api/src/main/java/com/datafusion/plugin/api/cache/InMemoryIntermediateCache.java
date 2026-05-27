package com.datafusion.plugin.api.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIntermediateCache implements IntermediateCache {
    /** 缓存数据存储. */
    private final Map<String, CacheValue> values = new ConcurrentHashMap<>();

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        long expireAt = ttlSeconds <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + ttlSeconds * 1000;
        values.put(key, new CacheValue(value, expireAt));
    }

    @Override
    public void put(String key, Object value, long ttlSeconds, String mode) {
        String normalized = mode == null ? "UPSERT" : mode.trim().toUpperCase();
        if ("PUT".equals(normalized) && get(key) != null) {
            return;
        }
        IntermediateCache.super.put(key, value, ttlSeconds, normalized);
    }

    @Override
    public void appendList(String key, Object value, long ttlSeconds) {
        List<Object> next = new ArrayList<>();
        Object existing = get(key);
        if (existing instanceof Iterable<?> iterable) {
            iterable.forEach(next::add);
        } else if (existing != null) {
            next.add(existing);
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(next::add);
        } else {
            next.add(value);
        }
        put(key, next, ttlSeconds);
    }

    @Override
    public void putHash(String key, Object value, long ttlSeconds) {
        Map<String, Object> next = new LinkedHashMap<>();
        Object existing = get(key);
        if (existing instanceof Map<?, ?> map) {
            map.forEach((field, item) -> next.put(String.valueOf(field), item));
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((field, item) -> next.put(String.valueOf(field), item));
        } else {
            next.put("value", value);
        }
        put(key, next, ttlSeconds);
    }

    @Override
    public Object get(String key) {
        CacheValue value = values.get(key);
        if (value == null) {
            return null;
        }
        if (value.expireAt < System.currentTimeMillis()) {
            values.remove(key);
            return null;
        }
        return value.value;
    }

    private record CacheValue(Object value, long expireAt) {
    }
}
