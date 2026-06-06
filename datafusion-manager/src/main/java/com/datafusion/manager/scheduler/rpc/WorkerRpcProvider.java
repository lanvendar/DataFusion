package com.datafusion.manager.scheduler.rpc;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.worker.WorkerManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * worker 管理器.
     */
    private final WorkerManager workerManager;

    /**
     * worker 注册.
     *
     * @param worker worker 信息
     * @return 是否成功
     */
    @PostMapping("/register")
    public Result<Boolean> register(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        workerManager.onActive(worker);
        return Result.success(true);
    }

    /**
     * worker 心跳.
     *
     * @param worker worker 信息
     * @return 是否成功
     */
    @PostMapping("/heartbeat")
    public Result<Boolean> heartbeat(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        workerManager.onActive(worker);
        return Result.success(true);
    }

    /**
     * worker 下线.
     *
     * @param worker worker 信息
     * @return 是否成功
     */
    @PostMapping("/offline")
    public Result<Boolean> offline(@RequestBody Worker worker) {
        if (worker == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "worker不能为空");
        }
        workerManager.onInactive(worker);
        return Result.success(true);
    }
}
