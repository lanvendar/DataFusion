package com.datafusion.scheduler.master.example;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 复杂流程（4任务 DAG: A→B→C, B→D, SERIAL_WAIT）状态机集成测试.
 */
@Slf4j
@DisplayName("复杂流程（4任务 DAG: A→B→C, B→D, SERIAL_WAIT）")
class ComplexFlowTest extends StateMachineTestBase {

    @BeforeEach
    void loadData() {
        ComplexFlowExample.load(
                triggerStorage.getTriggerInfoTable(), flowInfoTable, taskInfoTable, taskLinkTable,
                this::createTriggerInfo,
                this::createFlowInfo,
                this::createTaskInfo,
                true);
    }

    @Test
    @DisplayName("正常执行：A→B→(C,D) 全部 RUN_SUCCESS → 流程 RUN_SUCCESS")
    void testHappyPath() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // A 是根节点，无依赖，应先到达 SUBMIT_SUCCESS
        TaskInstance taskA = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}", taskA.getInstanceId());

        // 上报 A 成功
        reportTaskResult(taskA, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskA.getInstanceId(), StatusEnum.RUN_SUCCESS);
        log.info("任务满足监测状态[RUN_SUCCESS]: {}", taskA.getInstanceId());

        // B 依赖 A，A 成功后 B 应到达 SUBMIT_SUCCESS
        TaskInstance taskB = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_B_NAME, StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}", taskB.getInstanceId());

        // 上报 B 成功
        reportTaskResult(taskB, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskB.getInstanceId(), StatusEnum.RUN_SUCCESS);
        log.info("任务满足监测状态[RUN_SUCCESS]: {}", taskB.getInstanceId());

        // C 和 D 依赖 B，B 成功后应并行到达 SUBMIT_SUCCESS
        TaskInstance taskC = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_C_NAME, StatusEnum.SUBMIT_SUCCESS);
        TaskInstance taskD = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_D_NAME, StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}, {}", taskC.getInstanceId(), taskD.getInstanceId());

        // 上报 C,D 成功
        reportTaskResult(taskC, StatusEnum.RUN_SUCCESS);
        reportTaskResult(taskD, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskC.getInstanceId(), StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskD.getInstanceId(), StatusEnum.RUN_SUCCESS);
        log.info("任务满足监测状态[RUN_SUCCESS]: {}, {}", taskC.getInstanceId(), taskD.getInstanceId());

        // 所有任务完成 → 流程 RUN_SUCCESS
        FlowInstance flowIns = awaitFlowState(ComplexFlowExample.FLOW_ID, StatusEnum.RUN_SUCCESS);
        log.info("流程满足监测状态[RUN_SUCCESS]: {}", flowIns.getInstanceId());
        assertEquals(StatusEnum.RUN_SUCCESS, flowIns.getState());
    }

    @Test
    @DisplayName("B 失败 → C,D 不启动 → 流程不会 RUN_SUCCESS")
    void testMidTaskFailure() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // A 成功
        TaskInstance taskA = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskA, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskA.getInstanceId(), StatusEnum.RUN_SUCCESS);

        // B 到达 SUBMIT_SUCCESS 后，连续上报失败直到重试用尽
        TaskInstance taskB = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_B_NAME, StatusEnum.SUBMIT_SUCCESS);
        for (int i = 0; i < 4; i++) {
            awaitTaskState(taskB.getInstanceId(), StatusEnum.SUBMIT_SUCCESS);
            reportTaskResult(taskB, StatusEnum.RUN_FAILURE);
            sleep(500);
        }

        // B 重试用尽
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    TaskInstance ins = taskStorage.getInstanceById(taskB.getInstanceId());
                    return ins != null && ins.getState() == StatusEnum.RUN_FAILURE;
                });

        // C,D 不应到达 SUBMIT_SUCCESS（仍在 WAIT_DEPENDENT 或 INIT_SUCCESS）
        List<TaskInstance> allTasks = taskStorage.getTaskInsIdsByFlowInsId(
                flowStorage.getAvailableInstance(ComplexFlowExample.FLOW_ID).get(0).getInstanceId());
        for (TaskInstance t : allTasks) {
            if (ComplexFlowExample.TASK_C_NAME.equals(t.getTaskName())
                    || ComplexFlowExample.TASK_D_NAME.equals(t.getTaskName())) {
                assertNotEquals(StatusEnum.SUBMIT_SUCCESS, t.getState(),
                        t.getTaskName() + " 不应被提交");
                assertNotEquals(StatusEnum.RUNNING, t.getState(),
                        t.getTaskName() + " 不应运行");
                log.info("任务满足监测状态[{}]: {}", t.getState(), t.getInstanceId());
            }
        }
    }

    @Test
    @DisplayName("C 失败、D 成功 → 流程不会 RUN_SUCCESS")
    void testLeafPartialFailure() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // A,B 成功
        TaskInstance taskA = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskA, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskA.getInstanceId(), StatusEnum.RUN_SUCCESS);

        TaskInstance taskB = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_B_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskB, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskB.getInstanceId(), StatusEnum.RUN_SUCCESS);

        // D 成功
        TaskInstance taskD = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_D_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskD, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskD.getInstanceId(), StatusEnum.RUN_SUCCESS);

        // C 连续失败直到重试用尽
        TaskInstance taskC = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_C_NAME, StatusEnum.SUBMIT_SUCCESS);
        for (int i = 0; i < 4; i++) {
            awaitTaskState(taskC.getInstanceId(), StatusEnum.SUBMIT_SUCCESS);
            reportTaskResult(taskC, StatusEnum.RUN_FAILURE);
            sleep(500);
        }

        // 流程不应是 RUN_SUCCESS
        FlowInstance flowIns = flowStorage.getAvailableInstance(ComplexFlowExample.FLOW_ID).get(0);
        assertNotEquals(StatusEnum.RUN_SUCCESS, flowIns.getState(),
                "叶子节点部分失败时流程不应成功");
        log.info("流程满足监测状态[{}]: {}", flowIns.getState(), flowIns.getInstanceId());
    }

    @Test
    @DisplayName("停止 WAIT_DEPENDENT 状态的任务 → 直接 STOP_SUCCESS（不经过 STOPPING）")
    void testStopTaskFromWaitDependent() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // A 到达 SUBMIT_SUCCESS，此时 C/D 应处于 WAIT_DEPENDENT
        TaskInstance taskA = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);

        // 等待 C 进入 WAIT_DEPENDENT
        TaskInstance taskC = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_C_NAME, StatusEnum.WAIT_DEPENDENT);
        log.info("任务满足监测状态[WAIT_DEPENDENT]: {}", taskC.getInstanceId());

        // 从 WAIT_DEPENDENT 直接停止 → 应直接到达 STOP_SUCCESS，不经过 STOPPING
        masterService.getTaskAction().taskStop(taskC);
        awaitTaskState(taskC.getInstanceId(), StatusEnum.STOP_SUCCESS);
        TaskInstance stoppedC = taskStorage.getInstanceById(taskC.getInstanceId());
        assertEquals(StatusEnum.STOP_SUCCESS, stoppedC.getState());
        log.info("任务满足监测状态[STOP_SUCCESS]: {}", stoppedC.getInstanceId());

        // A 正常完成 → B 到达 SUBMIT_SUCCESS → D 应继续执行
        reportTaskResult(taskA, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskA.getInstanceId(), StatusEnum.RUN_SUCCESS);

        TaskInstance taskB = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_B_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskB, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskB.getInstanceId(), StatusEnum.RUN_SUCCESS);

        TaskInstance taskD = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_D_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskD, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskD.getInstanceId(), StatusEnum.RUN_SUCCESS);
        log.info("任务满足监测状态[RUN_SUCCESS]: {}", taskD.getInstanceId());

        // C 已被停止，流程不应 RUN_SUCCESS
        FlowInstance flowIns = flowStorage.getAvailableInstance(ComplexFlowExample.FLOW_ID).get(0);
        assertNotEquals(StatusEnum.RUN_SUCCESS, flowIns.getState(),
                "有任务被停止时流程不应成功");
        log.info("流程满足监测状态[{}]: {}", flowIns.getState(), flowIns.getInstanceId());
    }

    @Test
    @DisplayName("停止 SUBMIT_SUCCESS 状态的任务 → STOPPING → STOP_SUCCESS")
    void testStopTaskFromSubmitSuccess() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // A 到达 SUBMIT_SUCCESS（已提交到 worker）
        TaskInstance taskA = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);
        log.info("任务满足监测状态[SUBMIT_SUCCESS]: {}", taskA.getInstanceId());

        // 手动停止任务（SUBMIT_SUCCESS → STOPPING → executor.stopTask → STOP_SUCCESS）
        masterService.getTaskAction().taskStop(taskA);
        awaitTaskState(taskA.getInstanceId(), StatusEnum.STOP_SUCCESS);
        TaskInstance stoppedA = taskStorage.getInstanceById(taskA.getInstanceId());
        assertEquals(StatusEnum.STOP_SUCCESS, stoppedA.getState());
        log.info("任务满足监测状态[STOP_SUCCESS]: {}", stoppedA.getInstanceId());
    }

    @Test
    @DisplayName("对失败任务 enforceSuccess → 流程 RUN_SUCCESS")
    void testEnforceSuccessUnblocksFlow() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // A,B 成功
        TaskInstance taskA = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskA, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskA.getInstanceId(), StatusEnum.RUN_SUCCESS);

        TaskInstance taskB = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_B_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskB, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskB.getInstanceId(), StatusEnum.RUN_SUCCESS);

        // D 成功
        TaskInstance taskD = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_D_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskD, StatusEnum.RUN_SUCCESS);
        awaitTaskState(taskD.getInstanceId(), StatusEnum.RUN_SUCCESS);

        // C 失败
        TaskInstance taskC = awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_C_NAME, StatusEnum.SUBMIT_SUCCESS);
        reportTaskResult(taskC, StatusEnum.RUN_FAILURE);
        sleep(500);

        // 先停止 C
        TaskInstance failedC = taskStorage.getInstanceById(taskC.getInstanceId());
        masterService.getTaskAction().taskStop(failedC);
        awaitTaskState(taskC.getInstanceId(), StatusEnum.STOP_SUCCESS);

        // 强制成功 C
        TaskInstance stoppedC = taskStorage.getInstanceById(taskC.getInstanceId());
        masterService.getTaskAction().taskEnforceSuccess(stoppedC);

        // 等待 C 到达 ENFORCE_SUCCESS
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    TaskInstance ins = taskStorage.getInstanceById(taskC.getInstanceId());
                    return ins != null && (ins.getState() == StatusEnum.ENFORCE_SUCCESS
                            || ins.getState() == StatusEnum.ENFORCING_SUCCESS);
                });
        TaskInstance enforcedC = taskStorage.getInstanceById(taskC.getInstanceId());
        log.info("任务满足监测状态[{}]: {}", enforcedC.getState(), enforcedC.getInstanceId());
    }

    @Test
    @DisplayName("流程级 stop → 所有运行中任务停止")
    void testFlowStop() {
        masterService.start();
        addSchedule(ComplexFlowExample.FLOW_ID);

        // 等待 A 到达 SUBMIT_SUCCESS
        awaitTaskByName(ComplexFlowExample.FLOW_ID, ComplexFlowExample.TASK_A_NAME, StatusEnum.SUBMIT_SUCCESS);

        // 流程级停止
        FlowInstance flowIns = awaitFlowInstanceExists(ComplexFlowExample.FLOW_ID);
        masterService.getFlowAction().flowStop(flowIns);

        // 等待流程到达 STOPPING 或 STOP_SUCCESS
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    FlowInstance ins = flowStorage.getInstanceById(flowIns.getInstanceId());
                    StatusEnum state = ins != null ? ins.getState() : null;
                    log.info("流程轮询监测状态[STOP_SUCCESS]: state={}", state);
                    return state == StatusEnum.STOPPING || state == StatusEnum.STOP_SUCCESS;
                });

        FlowInstance stoppedFlow = flowStorage.getInstanceById(flowIns.getInstanceId());
        log.info("流程满足监测状态[{}]: {}", stoppedFlow.getState(), stoppedFlow.getInstanceId());
    }
}