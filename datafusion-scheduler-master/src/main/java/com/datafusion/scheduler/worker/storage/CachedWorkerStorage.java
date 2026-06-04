/*
 * Copyright © 2000-2024 Nimbus Corporation All rights reserved.
 *
 * 使本项目源码前请仔细阅读以下协议内容，如果你同意以下协议才能使用本项目所有的功能,
 * 否则如果你违反了以下协议，有可能陷入法律纠纷和赔偿，作者保留追究法律责任的权利.
 *
 * 1、本代码为商业源代码，只允许已授权内部人员查看使用
 * 2、任何人员无权将代码泄露或者授权给其他未被授权人员使用
 * 3、任何修改请保留原始作者信息，不得擅自删除及修改
 *
 * 请保留以上版权信息，否则作者将保留追究法律责任.
 */

package com.datafusion.scheduler.worker.storage;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        return workerCache.get(workerId);
    }

    /**
     * 根据主机名+端口号获取工作节点.
     *
     * @param hostName 主机名
     * @param port     端口号
     * @return 工作节点信息
     */
    @Override
    public Worker getWorker(String hostName, int port) {
        Set<Map.Entry<String, Worker>> entries = workerCache.asMap().entrySet();
        if (CollectionUtil.isNotEmpty(entries)) {
            for (Map.Entry<String, Worker> entry : entries) {
                Worker worker = entry.getValue();
                if (StrUtil.equals(hostName, worker.getHostName()) && port == worker.getPort()) {
                    return worker;
                }
            }
        }
        return workerStorage.getWorker(hostName, port);
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
        workerCache.asMap().compute(worker.getId(), (k, v) -> {
            v = mergeWorker(v, worker);
            workerStorage.updateWorker(v);
            return v;
        });
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
}
