package com.datafusion.manager.scheduler.rpc;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.scheduler.master.MasterService;
import com.datafusion.scheduler.model.TaskResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调度内部 RPC Provider.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/internal/schedule")
@RequiredArgsConstructor
public class SchedulerRpcProvider {

    /**
     * master 服务.
     */
    private final MasterService masterService;

    /**
     * 上报任务结果.
     *
     * @param taskResult 任务结果
     * @return 是否成功
     */
    @PostMapping("/reportTaskResult")
    public Result<Boolean> reportTaskResult(@RequestBody TaskResult taskResult) {
        if (taskResult == null || taskResult.getTaskState() == null) {
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务结果或状态不能为空");
        }
        boolean success = masterService.getTaskAction().asyncHandle(taskResult);
        log.debug("接收任务结果上报,taskInstanceId={},state={},success={}",
                taskResult.getTaskInstanceId(), taskResult.getTaskState(), success);
        return Result.success(success);
    }
}
