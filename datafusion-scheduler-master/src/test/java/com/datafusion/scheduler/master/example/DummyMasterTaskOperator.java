package com.datafusion.scheduler.master.example;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.exception.SchedulerException;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.model.TaskResult;
import lombok.extern.slf4j.Slf4j;

/**
 * 模拟 MasterTaskOperator，同步立即返回.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/3/10
 * @since 2026/3/10
 */
@Slf4j
public class DummyMasterTaskOperator implements MasterTaskOperator {

    @Override
    public TaskResult runTask(TaskInstance taskIns) throws SchedulerException {
        log.info("runTask: InstanceId={},Name={},State={}", taskIns.getInstanceId(), taskIns.getTaskName(), taskIns.getState());
        return buildResult(taskIns, StatusEnum.SUBMIT_SUCCESS);
    }

    @Override
    public TaskResult stopTask(TaskInstance taskIns) throws SchedulerException {
        log.info("stopTask: InstanceId={},Name={},State={}", taskIns.getInstanceId(), taskIns.getTaskName(), taskIns.getState());
        return buildResult(taskIns, StatusEnum.STOP_SUCCESS);
    }

    @Override
    public TaskResult killTask(TaskInstance taskIns) throws SchedulerException {
        log.info("killTask: InstanceId={},Name={},State={}", taskIns.getInstanceId(), taskIns.getTaskName(), taskIns.getState());
        return buildResult(taskIns, StatusEnum.KILLED);
    }

    @Override
    public TaskResult finishTask(TaskInstance taskIns) throws SchedulerException {
        log.info("finishTask: InstanceId={},Name={},State={}", taskIns.getInstanceId(), taskIns.getTaskName(), taskIns.getState());
        return buildResult(taskIns, StatusEnum.RUN_SUCCESS);
    }

    private TaskResult buildResult(TaskInstance taskIns, StatusEnum state) {
        return TaskResult.builder()
                .taskInstanceId(taskIns.getInstanceId())
                .flowInstanceId(taskIns.getFlowInstanceId())
                .taskName(taskIns.getTaskName())
                .taskState(state)
                .outputVars(taskIns.getTaskData().getVars())
                .workerId("dummyWorkerId")
                .appId("dummyAppId")
                .isSync(true)
                .result("dummyResult")
                .build();
    }
}
