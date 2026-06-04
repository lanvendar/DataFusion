package com.datafusion.agent.controller;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.AgentRuntimeState;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.WorkerTaskOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Agent 内部调度接口.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/2
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/internal/schedule")
public class InternalScheduleController {

    /**
     * worker 任务操作入口.
     */
    private final WorkerTaskOperator workerTaskOperator;

    /**
     * 任务控制线程池.
     */
    private final ThreadPoolExecutor taskControlPool;

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
     * @param taskControlPool    任务控制线程池
     * @param runtimeState       agent 运行状态
     * @param properties         agent 配置
     */
    public InternalScheduleController(WorkerTaskOperator workerTaskOperator,
            @Qualifier("agentTaskControlPool") ThreadPoolExecutor taskControlPool, AgentRuntimeState runtimeState,
            AgentProperties properties) {
        this.workerTaskOperator = workerTaskOperator;
        this.taskControlPool = taskControlPool;
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
        return execute(request, () -> workerTaskOperator.submitTask(request));
    }

    /**
     * 停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/stopTask")
    public Result<TaskResult> stopTask(@RequestBody TaskRequest request) {
        return execute(request, () -> workerTaskOperator.stopTask(request));
    }

    /**
     * 强制停止任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/killTask")
    public Result<TaskResult> killTask(@RequestBody TaskRequest request) {
        return execute(request, () -> workerTaskOperator.killTask(request));
    }

    /**
     * 完成任务.
     *
     * @param request 任务请求
     * @return 任务结果
     */
    @PostMapping("/finishTask")
    public Result<TaskResult> finishTask(@RequestBody TaskRequest request) {
        return execute(request, () -> workerTaskOperator.finishTask(request));
    }

    private Result<TaskResult> execute(TaskRequest request, Callable<TaskResult> action) {
        if (!isReady()) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "agent未注册到manager,暂不可调度");
        }
        try {
            Future<TaskResult> future = taskControlPool.submit(action);
            TaskResult result = future.get();
            return Result.success(result);
        } catch (RejectedExecutionException e) {
            log.warn("任务控制线程池已满", e);
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "agent任务控制线程池已满");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "agent任务控制请求被中断");
        } catch (ExecutionException e) {
            log.warn("agent任务控制请求执行失败", e);
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, e.getMessage());
        }
    }

    private boolean isReady() {
        return runtimeState.isReady() || properties.getWorker().isAcceptTasksBeforeRegistered();
    }
}
