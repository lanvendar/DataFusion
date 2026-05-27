package com.datafusion.plugin.api.cache;

/**
 * 中间缓存接口,用于在多步骤 API 抽取任务中暂存上游步骤的输出.
 *
 * <p>
 * 支持 Redis 和内存两种实现,供下游步骤引用上游数据.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public interface IntermediateCache extends AutoCloseable {
    
    /**
     * 将值存入缓存.
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 过期时间(秒),0 或负数表示永不过期
     */
    void put(String key, Object value, long ttlSeconds);

    /**
     * 从缓存中获取值.
     *
     * @param key 缓存键
     * @return 缓存值,不存在或已过期时返回 null
     */
    Object get(String key);

    /**
     * 关闭缓存并释放资源.
     */
    @Override
    default void close() {
    }
}
