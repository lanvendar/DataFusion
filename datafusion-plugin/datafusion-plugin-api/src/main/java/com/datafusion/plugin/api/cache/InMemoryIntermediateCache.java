package com.datafusion.plugin.api.cache;

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
