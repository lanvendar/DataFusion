package com.datafusion.manager.scheduler.master.worker;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.WorkerListener;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * worker 内部 RPC Provider.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@RestController
@RequestMapping("/internal/schedule/worker")
@RequiredArgsConstructor
public class WorkerRpcProvider {

    /**
     * worker 运行时服务.
     */
    private final WorkerListener workerListener;

    /**
     * worker 注册.
     *
     * @param worker worker 信息
     * @return 是否成功
     */
    @PostMapping("/register")
    public Result<Worker> register(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        return Result.success(workerListener.register(worker));
    }

    /**
     * worker 心跳.
     *
     * @param worker worker 信息
     * @return 是否成功
     */
    @PostMapping("/heartbeat")
    public Result<Worker> heartbeat(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        if (StringUtils.isBlank(worker.getId())) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "workerId不能为空");
        }
        return Result.success(workerListener.heartbeat(worker.getId(), worker.getLastHeartbeatTime()));
    }

    /**
     * worker 下线.
     *
     * @param worker worker 信息
     * @return 是否成功
     */
    @PostMapping("/offline")
    public Result<Worker> offline(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        if (StringUtils.isBlank(worker.getId())) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "workerId不能为空");
        }
        return Result.success(workerListener.offline(worker.getId()));
    }

    /**
     * 查询 worker 未完成任务清单.
     *
     * @param worker worker 信息
     * @return 未完成任务清单
     */
    @PostMapping("/tasks")
    public Result<List<TaskRequest>> tasks(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        if (StringUtils.isBlank(worker.getId())) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "workerId不能为空");
        }
        return Result.success(workerListener.getTaskInsByWorkerId(worker.getId()));
    }
}
