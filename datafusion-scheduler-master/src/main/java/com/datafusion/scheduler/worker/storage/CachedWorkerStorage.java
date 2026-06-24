package com.datafusion.scheduler.worker.storage;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.List;

/**
 * 工作节点缓存.
 *
 * @author david
 * @version 3.7.4, 2024/11/19
 * @since 3.7.4, 2024/11/19
 */
public class CachedWorkerStorage implements WorkerStorage {

    /**
     * 工作节点缓存.
     */
    private final LoadingCache<String, Worker> workerCache;

    /**
     * 工作节点存储服务.
     */
    private final WorkerStorage workerStorage;

    /**
     * 构造函数.
     */
    public CachedWorkerStorage() {
        this(new WorkerStorageMem(), new Options());
    }

    /**
     * 构造函数.
     *
     * @param workerStorage 工作节点存储服务
     * @param options       配置信息
     */
    public CachedWorkerStorage(WorkerStorage workerStorage, Options options) {
        this.workerStorage = workerStorage;
        this.workerCache = Caffeine.newBuilder().build(workerStorage::getWorker);
        getWorkers();
    }

    /**
     * 获取工作节点.
     *
     * @param workerId worker节点主键
     * @return 工作节点
     */
    @Override
    public Worker getWorker(String workerId) {
        if (StrUtil.isBlank(workerId)) {
            return null;
        }
        Worker worker = workerCache.getIfPresent(workerId);
        if (worker != null) {
            return worker;
        }
        worker = workerStorage.getWorker(workerId);
        if (worker != null && worker.getId() != null) {
            workerCache.put(worker.getId(), worker);
        }
        return worker;
    }

    /**
     * 获取全部工作节点.
     *
     * @return 工作节点列表
     */
    @Override
    public List<Worker> getWorkers() {
        List<Worker> workers = workerStorage.getWorkers();
        if (CollectionUtil.isNotEmpty(workers)) {
            workers.forEach(worker -> {
                String workerId = worker.getId();
                workerCache.asMap().compute(workerId, (k, v) -> mergeWorker(v, worker));
            });
        }

        return workers;
    }

    /**
     * 保存更新工作节点.
     *
     * @param worker 工作节点信息
     */
    @Override
    public void updateWorker(Worker worker) {
        Worker savedWorker = workerStorage.register(worker);
        refreshCache(savedWorker);
    }

    @Override
    public Worker register(Worker worker) {
        Worker savedWorker = workerStorage.register(worker);
        refreshCache(savedWorker);
        return savedWorker;
    }

    @Override
    public Worker heartbeat(String workerId, Long lastHeartbeatTime) {
        Worker worker = workerStorage.heartbeat(workerId, lastHeartbeatTime);
        refreshCache(worker);
        return worker;
    }

    @Override
    public Worker offline(String workerId) {
        Worker worker = workerStorage.offline(workerId);
        refreshCache(worker);
        return worker;
    }

    @Override
    public int offlineAllWorkers() {
        int updated = workerStorage.offlineAllWorkers();
        workerCache.invalidateAll();
        return updated;
    }

    @Override
    public int timeoutOffline(Long timeoutMs) {
        int updated = workerStorage.timeoutOffline(timeoutMs);
        workerCache.invalidateAll();
        getWorkers();
        return updated;
    }

    @Override
    public Worker active(String workerId) {
        Worker worker = workerStorage.active(workerId);
        refreshCache(worker);
        return worker;
    }

    @Override
    public Worker inactive(String workerId) {
        Worker worker = workerStorage.inactive(workerId);
        refreshCache(worker);
        return worker;
    }

    @Override
    public boolean delete(String workerId) {
        boolean deleted = workerStorage.delete(workerId);
        if (deleted) {
            workerCache.invalidate(workerId);
        }
        return deleted;
    }

    @Override
    public List<TaskRequest> getTaskInsByWorkerId(String workerId) {
        return workerStorage.getTaskInsByWorkerId(workerId);
    }

    /**
     * 合并工作数据.
     *
     * @param raw     原始数据
     * @param updated 更新数据
     * @return 合并后数据
     */
    private static Worker mergeWorker(Worker raw, Worker updated) {
        if (null == raw) {
            raw = updated;
        } else {
            raw.setIp(updated.getIp());
            raw.setWorkerCode(updated.getWorkerCode());
            raw.setHostName(updated.getHostName());
            raw.setPort(updated.getPort());
            raw.setStatus(updated.getStatus());
            raw.setPluginTypes(updated.getPluginTypes());
            raw.setRegisterTime(updated.getRegisterTime());
            raw.setLastHeartbeatTime(updated.getLastHeartbeatTime());
            raw.setUpdateTime(updated.getUpdateTime());
        }

        return raw;
    }

    private void refreshCache(Worker worker) {
        if (worker != null && worker.getId() != null) {
            workerCache.asMap().compute(worker.getId(), (k, v) -> mergeWorker(v, worker));
        }
    }
}
