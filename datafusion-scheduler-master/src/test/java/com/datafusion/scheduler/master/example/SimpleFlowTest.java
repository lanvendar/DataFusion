package com.datafusion.scheduler.master.example;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单流程（单任务 EXECUTE_ONCE）状态机集成测试.
 */
@Slf4j
@DisplayName("简单流程（单任务 EXECUTE_ONCE）")
class SimpleFlowTest extends StateMachineTestBase {

    @BeforeEach
    void loadData() {
        SimpleFlowExample.load(
                triggerStorage.getTriggerInfoTable(), flowInfoTable, taskInfoTable,
                this::createTriggerInfo,
                this::createFlowInfo,
                this::createTaskInfo,
                true);
    }

    @Test
    @DisplayName("正常执行：任务 SUBMIT_SUCCESS → worker上报 RUN_SUCCESS → 流程 RUN_SUCCESS")
    void testHappyPath() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        // 等待任务到达 SUBMIT_SUCCESS（submitTask 已调用）
        TaskInstance taskIns = awaitAnyTaskState(SimpleFlowExample.FLOW_ID, StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}", taskIns.getInstanceId());

        // 模拟 worker 上报 RUN_SUCCESS
        reportTaskResult(taskIns, StatusEnum.RUN_SUCCESS);

        // 等待任务到达 RUN_SUCCESS
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.RUN_SUCCESS);
        log.info("任务满足监测状态[RUN_SUCCESS]: {}", taskIns.getInstanceId());

        // 等待流程到达 RUN_SUCCESS
        FlowInstance flowIns = awaitFlowState(SimpleFlowExample.FLOW_ID, StatusEnum.RUN_SUCCESS);
        log.info("流程满足监测状态[RUN_SUCCESS]: {}", flowIns.getInstanceId());
        assertEquals(StatusEnum.RUN_SUCCESS, flowIns.getState());
        log.info("简单流程正常完成: flowState={}", flowIns.getState());
    }

    @Test
    @DisplayName("任务失败 → worker上报 RUN_FAILURE → 自动重试（RESTART → SUBMIT）")
    void testRunFailureAutoRetry() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        // 等待任务到达 SUBMIT_SUCCESS
        TaskInstance taskIns = awaitAnyTaskState(SimpleFlowExample.FLOW_ID, StatusEnum.SUBMIT_SUCCESS);

        // 模拟 worker 上报 RUN_FAILURE
        reportTaskResult(taskIns, StatusEnum.RUN_FAILURE);

        // 自动重试后应该重新到达 SUBMIT_SUCCESS（重试次数 +1）
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.SUBMIT_SUCCESS);

        TaskInstance retried = taskStorage.getInstanceById(taskIns.getInstanceId());
        log.info("任务满足监测状态[SUBMIT_SUCCESS](自动重试): {}, retryTimes={}",
                retried.getInstanceId(), retried.getRetryTimes());
        assertTrue(retried.getRetryTimes() > 0, "重试次数应大于0");
    }

    @Test
    @DisplayName("任务失败超过最大重试次数 → RETRY_FAILURE")
    void testExceedMaxRetry() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        TaskInstance taskIns = awaitAnyTaskState(SimpleFlowExample.FLOW_ID, StatusEnum.SUBMIT_SUCCESS);

        // 默认 maxRetryTimes=3，连续上报 RUN_FAILURE 4 次来触发 3 次自动重试
        for (int i = 0; i < 4; i++) {
            // 等待到达 SUBMIT_SUCCESS（说明已重新提交）
            awaitTaskState(taskIns.getInstanceId(), StatusEnum.SUBMIT_SUCCESS);
            reportTaskResult(taskIns, StatusEnum.RUN_FAILURE);
            log.info("任务上报状态[RUN_FAILURE]: 第 {} 次", i + 1);
            sleep(500);
        }

        // 第4次失败后应到达 RUN_FAILURE
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    TaskInstance ins = taskStorage.getInstanceById(taskIns.getInstanceId());
                    StatusEnum state = ins != null ? ins.getState() : null;
                    log.info("任务轮询监测状态[RUN_FAILURE]: state={}, retryTimes={}", state, ins != null ? ins.getRetryTimes() : null);
                    return state == StatusEnum.RUN_FAILURE;
                });

        List<FlowInstance> flowInstances = flowStorage.getAvailableInstance(SimpleFlowExample.FLOW_ID);
        String flowInsId = flowInstances.get(0).getInstanceId();
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    FlowInstance ins = flowStorage.getInstanceById(flowInsId);
                    StatusEnum state = ins != null ? ins.getState() : null;
                    log.info("流程轮询监测状态[RUN_FAILURE]: state={}", state);
                    return state == StatusEnum.RUN_FAILURE;
                });
    }

    @Test
    @DisplayName("手动停止 SUBMIT_SUCCESS 任务 → STOPPING → STOP_SUCCESS，流程也到达 STOP_SUCCESS")
    void testTaskStopFromSubmitSuccess() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        TaskInstance taskIns = awaitAnyTaskState(SimpleFlowExample.FLOW_ID, StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}", taskIns.getInstanceId());

        // 手动停止任务（SUBMIT_SUCCESS → STOPPING → executor.stopTask → STOP_SUCCESS）
        masterService.getTaskAction().taskStop(taskIns);
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.STOPPING);
        // 等待任务到达 STOP_SUCCESS
        reportTaskResult(taskIns, StatusEnum.STOP_SUCCESS);
        TaskInstance stopped = taskStorage.getInstanceById(taskIns.getInstanceId());
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.STOP_SUCCESS);
        log.info("任务满足监测状态[STOP_SUCCESS]: {}", stopped.getInstanceId());

        // 验证流程也到达 STOP_SUCCESS
        FlowInstance flowIns = awaitFlowState(SimpleFlowExample.FLOW_ID, StatusEnum.STOP_SUCCESS);
        assertEquals(StatusEnum.STOP_SUCCESS, flowIns.getState());
        log.info("流程满足监测状态[STOP_SUCCESS]: {}", flowIns.getInstanceId());
    }

    @Test
    @DisplayName("手动强制成功失败任务 → ENFORCING_SUCCESS → ENFORCE_SUCCESS")
    void testTaskEnforceSuccess() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        TaskInstance taskIns = awaitAnyTaskState(SimpleFlowExample.FLOW_ID, StatusEnum.SUBMIT_SUCCESS);

        // 先停止任务
        masterService.getTaskAction().taskStop(taskIns);
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.STOPPING);
        // 等待任务到达 STOP_SUCCESS
        reportTaskResult(taskIns, StatusEnum.STOP_SUCCESS);
        TaskInstance stopped = taskStorage.getInstanceById(taskIns.getInstanceId());
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.STOP_SUCCESS);
        log.info("任务满足监测状态[STOP_SUCCESS]: {}", stopped.getInstanceId());
        // 强制成功
        TaskInstance stoppedTask = taskStorage.getInstanceById(taskIns.getInstanceId());
        masterService.getTaskAction().taskEnforceSuccess(stoppedTask);

        // 等待到达 ENFORCING_SUCCESS
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.ENFORCING_SUCCESS);
        reportTaskResult(taskIns, StatusEnum.ENFORCE_SUCCESS);
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.ENFORCE_SUCCESS);
        log.info("任务满足监测状态[ENFORCE_SUCCESS]: {}", taskIns.getInstanceId());

        // 验证流程也到达 RUN_SUCCESS
        FlowInstance flowIns = awaitFlowState(SimpleFlowExample.FLOW_ID, StatusEnum.RUN_SUCCESS);
        assertEquals(StatusEnum.RUN_SUCCESS, flowIns.getState());
        log.info("流程满足监测状态[RUN_SUCCESS]: {}", flowIns.getInstanceId());
    }

    @Test
    @DisplayName("手动重启已停止任务 → RESTARTING → 重新 SUBMIT")
    void testTaskRestart() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        TaskInstance taskIns = awaitAnyTaskState(SimpleFlowExample.FLOW_ID, StatusEnum.SUBMIT_SUCCESS);

        // 先停止任务
        masterService.getTaskAction().taskStop(taskIns);
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.STOPPING);
        reportTaskResult(taskIns, StatusEnum.STOP_SUCCESS);
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.STOP_SUCCESS);
        log.info("任务满足监测状态[STOP_SUCCESS]: {}", taskIns.getInstanceId());

        // 手动重启
        TaskInstance stoppedTask = taskStorage.getInstanceById(taskIns.getInstanceId());
        masterService.getTaskAction().taskRestart(stoppedTask);

        // 等待重新到达 SUBMIT_SUCCESS
        awaitTaskState(taskIns.getInstanceId(), StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}", taskIns.getInstanceId());

        TaskInstance restarted = taskStorage.getInstanceById(taskIns.getInstanceId());
        assertEquals(0, restarted.getRetryTimes(), "手动重启应重置重试次数");
    }

    @Test
    @DisplayName("stopSchedule 停止调度 → 不再产生新实例")
    void testStopSchedule() {
        masterService.start();
        addSchedule(SimpleFlowExample.FLOW_ID);

        // 等待产生流程实例
        awaitFlowInstanceExists(SimpleFlowExample.FLOW_ID);
        // 记录当前实例数
        int count = flowStorage.getAvailableInstance(SimpleFlowExample.FLOW_ID).size();
        log.info("流程停止调度完成, 实例数: {}", count);

        // 停止调度
        masterService.stopSchedule(SimpleFlowExample.FLOW_ID);

        TriggerInfo triggerInfo = triggerStorage.getTriggerInfo(SimpleFlowExample.FLOW_ID);
        assertFalse(triggerInfo.isScheduleFlag(), "调度标志应为 false");
    }
}
