package com.datafusion.agent.rpc;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * Agent 内部调度 RPC 适配器.
 *
 * <p>Provider 只负责 HTTP 入站、就绪校验和统一响应包装；任务状态、监听和插件路由均由
 * {@link WorkerService} 管理。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/internal/scheduler")
public class AgentExecutorRpcProvider {

    /** Worker 子系统入口. */
    private final WorkerService workerService;

    /**
     * 创建 Agent 调度 RPC 适配器.
     *
     * @param workerService Worker 子系统入口
     */
    public AgentExecutorRpcProvider(WorkerService workerService) {
        this.workerService = workerService;
    }

    /**
     * 提交任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/submitTask")
    public Result<TaskResult> submitTask(@RequestBody TaskRequest request) {
        return execute("submitTask", request, () -> workerService.submitTask(request));
    }

    /**
     * 停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/stopTask")
    public Result<TaskResult> stopTask(@RequestBody TaskRequest request) {
        return execute("stopTask", request, () -> workerService.stopTask(request));
    }

    /**
     * 强制停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/killTask")
    public Result<TaskResult> killTask(@RequestBody TaskRequest request) {
        return execute("killTask", request, () -> workerService.killTask(request));
    }

    /**
     * 完成并清理任务.
     *
     * @param request 任务请求
     * @return 是否完成清理
     */
    @PostMapping("/finishTask")
    public Result<Boolean> finishTask(@RequestBody TaskRequest request) {
        return execute("finishTask", request, () -> workerService.finishTask(request));
    }

    private <T> Result<T> execute(String operation, TaskRequest request, Supplier<T> action) {
        String taskInstanceId = request == null ? null : request.getTaskInstanceId();
        if (!workerService.acceptsTasks()) {
            log.warn("agent拒绝调度请求, operation={}, taskInstanceId={}, ready={}",
                    operation, taskInstanceId, workerService.isReady());
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "agent尚未完成注册和任务恢复,暂不可调度");
        }
        long startTime = System.currentTimeMillis();
        try {
            T result = action.get();
            if (result instanceof TaskResult taskResult) {
                WorkerResult workerResult = taskResult.getWorkerResult();
                log.info("agent完成调度请求, operation={}, taskInstanceId={}, status={}, appId={}, costMs={}",
                        operation, taskInstanceId, taskResult.getTaskState(),
                        workerResult == null ? null : workerResult.getAppId(),
                        System.currentTimeMillis() - startTime);
            }
            return Result.success(result);
        } catch (RuntimeException e) {
            log.warn("agent任务控制请求执行失败, operation={}, taskInstanceId={}",
                    operation, taskInstanceId, e);
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, e.getMessage());
        }
    }
}
