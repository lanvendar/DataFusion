package com.datafusion.manager.scheduler.storage;

import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;
import com.datafusion.manager.scheduler.service.WorkerRegistryService;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.storage.WorkerStorage;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * worker 存储实现, 适配 scheduler WorkerStorage 接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@RequiredArgsConstructor
public class WorkerStorageImpl implements WorkerStorage {

    /**
     * worker 注册Service.
     */
    private final WorkerRegistryService workerRegistryService;

    @Override
    public Worker getWorker(String workerId) {
        WorkerRegistryEntity entity = workerRegistryService.getSchedulableWorkerByCode(workerId);
        return workerRegistryService.toWorker(entity);
    }

    @Override
    public Worker getWorker(String hostName, int port) {
        WorkerRegistryEntity entity = workerRegistryService.getSchedulableWorkerByHostAndPort(hostName, port);
        return workerRegistryService.toWorker(entity);
    }

    @Override
    public List<Worker> getWorkers() {
        return workerRegistryService.listSchedulableWorkers().stream()
                .map(workerRegistryService::toWorker)
                .collect(Collectors.toList());
    }

    @Override
    public void updateWorker(Worker worker) {
        workerRegistryService.saveOrUpdateFromWorker(worker);
    }

    @Override
    public List<TaskRequest> getTaskInsByWorkerId(String workerId) {
        return Collections.emptyList();
    }
}
