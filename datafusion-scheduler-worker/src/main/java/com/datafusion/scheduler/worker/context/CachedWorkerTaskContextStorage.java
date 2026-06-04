package com.datafusion.scheduler.worker.context;

import com.datafusion.scheduler.model.TaskRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker 运行中任务上下文内存实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
public class CachedWorkerTaskContextStorage implements WorkerTaskContextStorage {

    /**
     * 运行中任务上下文.
     */
    private final Map<String, RunningTaskContext> contextMap = new ConcurrentHashMap<>();

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        return contextMap.get(contextKey(taskInstanceId));
    }

    @Override
    public RunningTaskContext getOrCreate(TaskRequest request) {
        return contextMap.computeIfAbsent(contextKey(request.getTaskInstanceId()),
                key -> RunningTaskContext.fromRequest(request));
    }

    @Override
    public void save(RunningTaskContext context) {
        if (context == null) {
            return;
        }
        contextMap.put(contextKey(context.getTaskInstanceId()), context);
    }

    @Override
    public void remove(String taskInstanceId) {
        contextMap.remove(contextKey(taskInstanceId));
    }

    private static String contextKey(String taskInstanceId) {
        return taskInstanceId;
    }
}
