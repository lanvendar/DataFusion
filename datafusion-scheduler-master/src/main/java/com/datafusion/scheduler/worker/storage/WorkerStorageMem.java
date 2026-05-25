package com.datafusion.scheduler.worker.storage;

import com.datafusion.scheduler.worker.model.TaskRequest;
import com.datafusion.scheduler.worker.model.Worker;
import lombok.Getter;

import java.util.List;

/**
 * 工作节点信息,存储默认实现.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/5
 * @since 2024/12/5
 */
@Getter
public class WorkerStorageMem implements WorkerStorage {
    @Override
    public Worker getWorker(String workerId) {
        return null;
    }

    @Override
    public Worker getWorker(String hostName, int port) {
        return null;
    }

    @Override
    public List<Worker> getWorkers() {
        return null;
    }

    @Override
    public void updateWorker(Worker worker) {

    }

    @Override
    public List<TaskRequest> getTaskInsByWorkerId(String workerId) {
        return null;
    }
}
