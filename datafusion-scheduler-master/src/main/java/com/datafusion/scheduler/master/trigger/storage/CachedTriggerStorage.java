package com.datafusion.scheduler.master.trigger.storage;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.List;

/**
 * TriggerStorage 接口的缓存装饰器.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/10/29
 * @since 2024/10/29
 */
public class CachedTriggerStorage implements TriggerStorage {
    /**
     * 调度信息缓存,String key为 payloadId=flowId.
     */
    private final LoadingCache<String, TriggerInfo> triggerInfoCache;

    /**
     * 调度实例缓存.
     */
    private final LoadingCache<String, TriggerInstance> triggerInstanceCache;

    /**
     * 调度存储接口.
     */
    private final TriggerStorage triggerStorage;

    /**
     * 默认构造函数.
     */
    public CachedTriggerStorage() {
        this(new TriggerStorageMem(), new Options());
    }

    /**
     * 构造函数.
     *
     * @param triggerStorage 调度存储接口
     * @param options        统一调度配置信息
     */
    public CachedTriggerStorage(TriggerStorage triggerStorage, Options options) {
        this.triggerStorage = triggerStorage;
        int flowCacheMaxSize = options.get(MasterConfigOptions.FLOW_INSTANCE_CACHE_MAX_SIZE);
        this.triggerInfoCache = Caffeine.newBuilder().maximumSize(flowCacheMaxSize)
                .build(triggerStorage::getTriggerInfo);
        this.triggerInstanceCache = Caffeine.newBuilder().maximumSize(flowCacheMaxSize)
                .build(triggerStorage::getTriggerInstance);
    }

    @Override
    public List<TriggerInfo> getAllScheduledTriggerInfo() {
        return triggerStorage.getAllScheduledTriggerInfo();
    }

    @Override
    public TriggerInfo getTriggerInfo(String payloadId) {
        return triggerInfoCache.get(payloadId);
    }

    @Override
    public void invalidateTriggerInfo(String payloadId) {
        triggerInfoCache.invalidate(payloadId);
        triggerStorage.invalidateTriggerInfo(payloadId);
    }

    @Override
    public void saveTriggerInfo(TriggerInfo triggerInfo) {
        //只用作更改是否调度
        triggerInfoCache.asMap().compute(triggerInfo.getPayloadId(), (k, v) -> triggerInfo);
        triggerStorage.saveTriggerInfo(triggerInfo);
    }

    @Override
    public TriggerInstance getTriggerInstance(String scheduleInsId) {
        return triggerInstanceCache.get(scheduleInsId);
    }

    @Override
    public TriggerInstance getLastTriggerInstance(String payloadId, String version) {
        return triggerStorage.getLastTriggerInstance(payloadId, version);
    }

    @Override
    public void saveTriggerInstance(TriggerInstance triggerInstance) {
        this.triggerStorage.saveTriggerInstance(triggerInstance);
    }
}
