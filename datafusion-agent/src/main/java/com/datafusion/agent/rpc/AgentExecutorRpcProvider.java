package com.datafusion.agent.rpc;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.AgentRuntimeState;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.WorkerTaskOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

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
     * 构造函数.
     *
     * @param workerTaskOperator worker 任务操作入口
     * @param runtimeState       agent 运行状态
     * @param properties         agent 配置
     */
    public AgentExecutorRpcProvider(WorkerTaskOperator workerTaskOperator, AgentRuntimeState runtimeState,
            AgentProperties properties) {
        this.workerTaskOperator = workerTaskOperator;
        this.runtimeState = runtimeState;
        this.properties = properties;
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
        return execute("submitTask", filledRequest, () -> workerTaskOperator.submitTask(filledRequest));
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
        return execute("stopTask", filledRequest, () -> workerTaskOperator.stopTask(filledRequest));
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
        return execute("killTask", filledRequest, () -> workerTaskOperator.killTask(filledRequest));
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
        return execute("finishTask", filledRequest, () -> workerTaskOperator.finishTask(filledRequest));
    }

    private <T> Result<T> execute(String operation, TaskRequest request, Callable<T> action) {
        long startTime = System.currentTimeMillis();
        log.info("agent收到调度请求, operation={}, taskInstanceId={}, flowInstanceId={}, pluginType={},"
                        + " taskState={}, submitMode={}, workerId={}, appId={}, workDirPath={}",
                operation, taskInstanceId(request), flowInstanceId(request), pluginType(request), taskState(request),
                submitMode(request), workerId(request), appId(request), workDirPath(request));
        if (!isReady()) {
            log.warn("agent拒绝调度请求, operation={}, taskInstanceId={}, runtimeReady={},"
                            + " acceptTasksBeforeRegistered={}",
                    operation, taskInstanceId(request), runtimeState.isReady(),
                    properties.getWorker().isAcceptTasksBeforeRegistered());
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "agent未注册到manager,暂不可调度");
        }
        try {
            log.info("agent开始执行调度请求, operation={}, taskInstanceId={}",
                    operation, taskInstanceId(request));
            T result = action.call();
            long costMs = System.currentTimeMillis() - startTime;
            if (result instanceof TaskResult taskResult) {
                log.info("agent完成调度请求, operation={}, taskInstanceId={}, taskState={}, submitMode={},"
                                + " workerId={}, appId={}, workDirPath={}, costMs={}",
                        operation, taskInstanceId(request), resultTaskState(taskResult), resultSubmitMode(taskResult),
                        resultWorkerId(taskResult), resultAppId(taskResult), resultWorkDirPath(taskResult), costMs);
            } else {
                log.info("agent完成调度请求, operation={}, taskInstanceId={}, result={}, costMs={}",
                        operation, taskInstanceId(request), result, costMs);
            }
            return Result.success(result);
        } catch (Exception e) {
            log.warn("agent任务控制请求执行失败, operation={}, taskInstanceId={}",
                    operation, taskInstanceId(request), e);
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

    private String taskInstanceId(TaskRequest request) {
        return request == null ? null : request.getTaskInstanceId();
    }

    private String flowInstanceId(TaskRequest request) {
        return request == null ? null : request.getFlowInstanceId();
    }

    private String pluginType(TaskRequest request) {
        return request == null ? null : request.getPluginType();
    }

    private Object taskState(TaskRequest request) {
        return request == null ? null : request.getTaskState();
    }

    private Object submitMode(TaskRequest request) {
        return request == null ? null : request.getSubmitMode();
    }

    private String workerId(TaskRequest request) {
        WorkerResult workerResult = requestWorkerResult(request);
        return workerResult == null ? null : workerResult.getWorkerId();
    }

    private String appId(TaskRequest request) {
        WorkerResult workerResult = requestWorkerResult(request);
        return workerResult == null ? null : workerResult.getAppId();
    }

    private String workDirPath(TaskRequest request) {
        WorkerResult workerResult = requestWorkerResult(request);
        return workerResult == null ? null : workerResult.getWorkDirPath();
    }

    private WorkerResult requestWorkerResult(TaskRequest request) {
        return request == null ? null : request.getWorkerResult();
    }

    private Object resultTaskState(TaskResult result) {
        return result == null ? null : result.getTaskState();
    }

    private Object resultSubmitMode(TaskResult result) {
        return result == null ? null : result.getSubmitMode();
    }

    private String resultWorkerId(TaskResult result) {
        WorkerResult workerResult = resultWorkerResult(result);
        return workerResult == null ? null : workerResult.getWorkerId();
    }

    private String resultAppId(TaskResult result) {
        WorkerResult workerResult = resultWorkerResult(result);
        return workerResult == null ? null : workerResult.getAppId();
    }

    private String resultWorkDirPath(TaskResult result) {
        WorkerResult workerResult = resultWorkerResult(result);
        return workerResult == null ? null : workerResult.getWorkDirPath();
    }

    private WorkerResult resultWorkerResult(TaskResult result) {
        return result == null ? null : result.getWorkerResult();
    }
}
