package com.datafusion.agent.rpc;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.AgentRuntimeState;
import com.datafusion.agent.runtime.worker.reporter.AgentTaskStateListenerRegistry;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.WorkerTaskOperator;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * Agent 内部调度 RPC Provider.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/internal/scheduler")
public class AgentExecutorRpcProvider {

    /**
     * worker 任务操作入口.
     */
    private final WorkerTaskOperator workerTaskOperator;

    /**
     * agent 运行状态.
     */
    private final AgentRuntimeState runtimeState;

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * 任务执行状态存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 任务状态监听注册器.
     */
    private final AgentTaskStateListenerRegistry listenerRegistry;

    /**
     * 构造函数.
     *
     * @param workerTaskOperator worker 任务操作入口
     * @param runtimeState       agent 运行状态
     * @param properties         agent 配置
     * @param stateStore         任务执行状态存储
     * @param listenerRegistry   任务状态监听注册器
     */
    public AgentExecutorRpcProvider(WorkerTaskOperator workerTaskOperator, AgentRuntimeState runtimeState,
            AgentProperties properties, WorkerTaskExecutionStore stateStore,
            AgentTaskStateListenerRegistry listenerRegistry) {
        this.workerTaskOperator = workerTaskOperator;
        this.runtimeState = runtimeState;
        this.properties = properties;
        this.stateStore = stateStore;
        this.listenerRegistry = listenerRegistry;
    }

    /**
     * 提交任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/submitTask")
    public Result<TaskResult> submitTask(@RequestBody TaskRequest request) {
        TaskRequest filledRequest = fillWorkerId(request);
        return execute("submitTask", filledRequest, () -> {
            TaskResult result = workerTaskOperator.submitTask(filledRequest);
            listenerRegistry.register(filledRequest.getTaskInstanceId());
            return result;
        });
    }

    /**
     * 停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/stopTask")
    public Result<TaskResult> stopTask(@RequestBody TaskRequest request) {
        TaskRequest filledRequest = fillWorkerId(request);
        return execute("stopTask", filledRequest, () -> {
            TaskResult result = workerTaskOperator.stopTask(filledRequest);
            listenerRegistry.register(filledRequest.getTaskInstanceId());
            return result;
        });
    }

    /**
     * 强制停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/killTask")
    public Result<TaskResult> killTask(@RequestBody TaskRequest request) {
        TaskRequest filledRequest = fillWorkerId(request);
        return execute("killTask", filledRequest, () -> {
            TaskResult result = workerTaskOperator.killTask(filledRequest);
            listenerRegistry.register(filledRequest.getTaskInstanceId());
            return result;
        });
    }

    /**
     * 完成任务.
     *
     * @param request 任务请求
     * @return 是否完成清理
     */
    @PostMapping("/finishTask")
    public Result<Boolean> finishTask(@RequestBody TaskRequest request) {
        TaskRequest filledRequest = fillWorkerId(request);
        return execute("finishTask", filledRequest, () -> {
            boolean finished = workerTaskOperator.finishTask(filledRequest);
            listenerRegistry.unregister(filledRequest.getTaskInstanceId());
            return finished;
        });
    }

    private <T> Result<T> execute(String operation, TaskRequest request, Supplier<T> action) {
        long startTime = System.currentTimeMillis();
        String taskInstanceId = request == null ? null : request.getTaskInstanceId();
        WorkerResult requestWorkerResult = request == null ? null : request.getWorkerResult();
        log.info("agent收到调度请求, operation={}, taskInstanceId={}, flowInstanceId={}, pluginType={},"
                        + " taskState={}, submitMode={}, workerId={}, appId={}, workDirPath={}",
                operation, taskInstanceId, request == null ? null : request.getFlowInstanceId(),
                request == null ? null : request.getPluginType(), request == null ? null : request.getTaskState(),
                request == null ? null : request.getSubmitMode(),
                requestWorkerResult == null ? null : requestWorkerResult.getWorkerId(),
                requestWorkerResult == null ? null : requestWorkerResult.getAppId(),
                requestWorkerResult == null ? null : requestWorkerResult.getWorkDirPath());
        if (!isReady()) {
            log.warn("agent拒绝调度请求, operation={}, taskInstanceId={}, runtimeReady={},"
                            + " acceptTasksBeforeRegistered={}",
                    operation, taskInstanceId, runtimeState.isReady(),
                    properties.getWorker().isAcceptTasksBeforeRegistered());
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "agent未注册到manager,暂不可调度");
        }
        try {
            log.info("agent开始执行调度请求, operation={}, taskInstanceId={}",
                    operation, taskInstanceId);
            T result = stateStore.withTaskLock(taskInstanceId, action);
            long costMs = System.currentTimeMillis() - startTime;
            if (result instanceof TaskResult taskResult) {
                WorkerResult resultWorker = taskResult.getWorkerResult();
                log.info("agent完成调度请求, operation={}, taskInstanceId={}, taskState={}, submitMode={},"
                                + " workerId={}, appId={}, workDirPath={}, costMs={}",
                        operation, taskInstanceId, taskResult.getTaskState(), taskResult.getSubmitMode(),
                        resultWorker == null ? null : resultWorker.getWorkerId(),
                        resultWorker == null ? null : resultWorker.getAppId(),
                        resultWorker == null ? null : resultWorker.getWorkDirPath(), costMs);
            } else {
                log.info("agent完成调度请求, operation={}, taskInstanceId={}, result={}, costMs={}",
                        operation, taskInstanceId, result, costMs);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.warn("agent任务控制请求执行失败, operation={}, taskInstanceId={}",
                    operation, taskInstanceId, e);
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, e.getMessage());
        }
    }

    private boolean isReady() {
        return runtimeState.isReady() || properties.getWorker().isAcceptTasksBeforeRegistered();
    }

    private TaskRequest fillWorkerId(TaskRequest request) {
        if (request == null) {
            return request;
        }
        if (request.getWorkerResult() != null && request.getWorkerResult().getWorkerId() != null) {
            return request;
        }
        Worker worker = runtimeState.getWorker();
        if (worker != null) {
            if (request.getWorkerResult() == null) {
                request.setWorkerResult(new WorkerResult());
            }
            request.getWorkerResult().setWorkerId(worker.getId());
        }
        return request;
    }

}
