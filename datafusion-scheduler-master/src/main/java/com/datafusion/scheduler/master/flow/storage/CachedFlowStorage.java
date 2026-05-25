package com.datafusion.scheduler.master.flow.storage;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 流程存储缓存装饰器.
 * 基于 Caffeine 缓存提供高性能的查询能力.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class CachedFlowStorage implements FlowStorage {

    /**
     * schedule info 调度信息缓存.String key为 (flowId).
     */
    private final LoadingCache<String, FlowInfo> flowInfoCache;

    /**
     * flow instance 流程实例缓存. String key为 flowInstanceId = flowId+scheduleTime的 UUID
     */
    private final LoadingCache<String, FlowInstance> flowInstanceCache;

    /**
     * 流程信息 和 流程实例 持久化功能接口.
     */
    private final FlowStorage flowStorage;

    /**
     * 默认构造函数.
     */
    public CachedFlowStorage() {
        this(new FlowStorageMem(), new Options());
    }

    /**
     * 构造函数.
     *
     * @param flowStorage 流程信息 和 流程实例 持久化功能接口
     * @param options     配置信息
     */
    public CachedFlowStorage(FlowStorage flowStorage, Options options) {
        this.flowStorage = flowStorage;
        int flowCacheMaxSize = options.get(MasterConfigOptions.FLOW_INSTANCE_CACHE_MAX_SIZE);
        this.flowInfoCache = Caffeine.newBuilder().maximumSize(flowCacheMaxSize)
                .build(flowStorage::getFlowInfo);
        this.flowInstanceCache = Caffeine.newBuilder().maximumSize(flowCacheMaxSize)
                .build(flowStorage::getInstanceById);
    }

    @Override
    public FlowInfo getFlowInfo(String flowId) {
        return this.flowInfoCache.get(flowId);
    }

    @Override
    public List<FlowInfo> getAllFlowInfo() {
        List<FlowInfo> flowInfos = this.flowStorage.getAllFlowInfo();
        if (CollectionUtil.isNotEmpty(flowInfos)) {
            for (FlowInfo flowInfo : flowInfos) {
                if (flowInfo != null) {
                    //caffeine 二级缓存
                    this.flowInfoCache.asMap().putIfAbsent(flowInfo.getFlowId(), flowInfo);
                }
            }
        }
        return flowInfos;
    }

    @Override
    public FlowInstance getInstanceById(String flowInsId) {
        return this.flowInstanceCache.get(flowInsId);
    }

    @Override
    public void saveInstance(FlowInstance flowIns) {
        //原子性的持久化flow instance并更新缓存
        this.flowInstanceCache.asMap().compute(flowIns.getInstanceId(), (k, v) -> {
            this.flowStorage.saveInstance(flowIns);
            if (v != null) {
                BeanUtil.copyProperties(flowIns, v, CopyOptions.create().setIgnoreNullValue(true));
                return v;
            } else {
                return flowStorage.getInstanceById(k);
            }
        });
    }

    @Override
    public void removeInstanceById(String flowInsId) {
        //caffeine 二级缓存
        this.flowStorage.removeInstanceById(flowInsId);
        this.flowInstanceCache.invalidate(flowInsId);
    }

    @Override
    public List<FlowInstance> getAvailableInstance(String flowId) {
        List<FlowInstance> availableInstances;
        if (flowId == null) {
            availableInstances = this.flowStorage.getAvailableInstance(null);
        } else {
            availableInstances = this.flowStorage.getAvailableInstance(flowId);
        }
        if (CollectionUtil.isNotEmpty(availableInstances)) {
            for (FlowInstance flowInstance : availableInstances) {
                if (flowInstance != null) {
                    //caffeine 二级缓存
                    this.flowInstanceCache.asMap().putIfAbsent(flowInstance.getInstanceId(), flowInstance);
                }
            }
        }
        return availableInstances;
    }

    @Override
    public FlowInstance getLastInstance(String flowId, String version) {
        return flowStorage.getLastInstance(flowId, version);
    }
}
