package com.datafusion.scheduler.worker.storage;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工作节点信息,存储默认实现.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/5
 * @since 2024/12/5
 */
@Getter
public class WorkerStorageMem implements WorkerStorage {

    /**
     * worker 节点信息.
     */
    private final Map<String, Worker> workerMap = new ConcurrentHashMap<>();

    /**
     * worker 运行中任务.
     */
    private final Map<String, List<TaskRequest>> workerTaskMap = new ConcurrentHashMap<>();

    @Override
    public Worker getWorker(String workerId) {
        return workerMap.get(workerId);
    }

    @Override
    public Worker getWorker(String hostName, int port) {
        return workerMap.values().stream()
                .filter(worker -> hostName.equals(worker.getHostName()) && port == worker.getPort())
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Worker> getWorkers() {
        return new ArrayList<>(workerMap.values());
    }

    @Override
    public void updateWorker(Worker worker) {
        if (worker != null && worker.getId() != null) {
            workerMap.put(worker.getId(), worker);
        }
    }

    @Override
    public List<TaskRequest> getTaskInsByWorkerId(String workerId) {
        return workerTaskMap.getOrDefault(workerId, new ArrayList<>()).stream()
                .collect(Collectors.toList());
    }
}
