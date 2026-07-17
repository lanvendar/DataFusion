package com.datafusion.scheduler.worker.storage;

import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    public Worker register(Worker worker) {
        if (worker == null) {
            return null;
        }
        if (worker.getId() == null) {
            worker.setId(UUID.randomUUID().toString());
        }
        long now = System.currentTimeMillis();
        if (worker.getRegisterTime() == null) {
            worker.setRegisterTime(now);
        }
        worker.setStatus(Worker.STATUS_UP);
        worker.setLastHeartbeatTime(now);
        worker.setUpdateTime(now);
        updateWorker(worker);
        return worker;
    }

    @Override
    public Worker heartbeat(String workerId, Long lastHeartbeatTime) {
        Worker worker = getWorker(workerId);
        if (worker == null) {
            return null;
        }
        worker.setStatus(Worker.STATUS_UP);
        worker.setLastHeartbeatTime(lastHeartbeatTime == null ? System.currentTimeMillis() : lastHeartbeatTime);
        worker.setUpdateTime(System.currentTimeMillis());
        updateWorker(worker);
        return worker;
    }

    @Override
    public Worker offline(String workerId) {
        Worker worker = getWorker(workerId);
        if (worker == null) {
            return null;
        }
        worker.setStatus(Worker.STATUS_DOWN);
        worker.setUpdateTime(System.currentTimeMillis());
        updateWorker(worker);
        return worker;
    }

    @Override
    public int offlineAllWorkers() {
        int count = 0;
        for (Worker worker : workerMap.values()) {
            if (worker.isAlive()) {
                offline(worker.getId());
                count++;
            }
        }
        return count;
    }

    @Override
    public int timeoutOffline(Long timeoutMs) {
        long timeout = timeoutMs == null ? 0L : timeoutMs;
        long expireBefore = System.currentTimeMillis() - timeout;
        int count = 0;
        for (Worker worker : workerMap.values()) {
            Long heartbeatTime = worker.getLastHeartbeatTime();
            if (worker.isAlive() && (heartbeatTime == null || heartbeatTime < expireBefore)) {
                offline(worker.getId());
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean active(String workerId) {
        return getWorker(workerId) != null;
    }

    @Override
    public boolean inactive(String workerId) {
        return getWorker(workerId) != null;
    }

    @Override
    public boolean delete(String workerId) {
        return workerMap.remove(workerId) != null;
    }

    @Override
    public List<TaskRequest> getTaskInsByWorkerId(String workerId) {
        return workerTaskMap.getOrDefault(workerId, new ArrayList<>()).stream()
                .collect(Collectors.toList());
    }
}
