package com.datafusion.scheduler.master.example;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterService;
import com.datafusion.scheduler.master.MasterStorage;
import com.datafusion.scheduler.master.event.storage.EventStorageMem;
import com.datafusion.scheduler.master.flow.enums.FlowTypeEnum;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorageMem;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorageMem;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorageMem;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Variable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 状态机集成测试基类.
 *
 * <p>提供共享的存储组件初始化、辅助方法和工厂方法.
 */
@Slf4j
abstract class StateMachineTestBase {

    protected static final int AWAIT_TIMEOUT_SECONDS = 180;

    // ========================= 存储组件 =========================
    protected MasterService masterService;
    protected TriggerStorageMem triggerStorage;
    protected FlowStorageMem flowStorage;
    protected TaskStorageMem taskStorage;
    protected DummyMasterTaskOperator masterTaskOperator;

    protected Table<String, String, Object> flowInfoTable;
    protected Table<String, String, Object> taskInfoTable;
    protected Table<String, String, Object> taskLinkTable;

    @BeforeEach
    void setUp() {
        flowInfoTable = HashBasedTable.create();
        taskInfoTable = HashBasedTable.create();
        taskLinkTable = HashBasedTable.create();
        Table<String, String, Object> flowInstanceTable = HashBasedTable.create();
        Table<String, String, Object> taskInstanceTable = HashBasedTable.create();

        triggerStorage = new TriggerStorageMem();
        flowStorage = new FlowStorageMem(triggerStorage, flowInstanceTable, flowInfoTable);
        taskStorage = new TaskStorageMem(taskInstanceTable, taskInfoTable, taskLinkTable);
        masterTaskOperator = new DummyMasterTaskOperator();

        MasterStorage masterStorage = new MasterStorage(triggerStorage, flowStorage, taskStorage, new EventStorageMem());
        masterService = new MasterService(masterTaskOperator, masterStorage, new Options());
    }

    @AfterEach
    void tearDown() {
        if (masterService != null) {
            masterService.stop();
        }
    }

    // ========================= 辅助方法 =========================

    /**
     * 添加调度.
     */
    protected void addSchedule(String flowId) {
        TriggerInfo triggerInfo = triggerStorage.getTriggerInfo(flowId);
        assertNotNull(triggerInfo, "TriggerInfo 不存在: " + flowId);
        masterService.addSchedule(triggerInfo, System.currentTimeMillis(), true);
    }

    /**
     * 模拟 worker 上报任务结果.
     */
    protected void reportTaskResult(TaskInstance taskIns, StatusEnum resultState) {
        TaskResult result = TaskResult.builder()
                .taskInstanceId(taskIns.getInstanceId())
                .flowInstanceId(taskIns.getFlowInstanceId())
                .taskName(taskIns.getTaskName())
                .taskState(resultState)
                .workerId("test-worker")
                .appId("test-app")
                .isSync(true)
                .result("test")
                .build();
        masterService.getTaskAction().asyncHandle(result);
    }

    /**
     * 等待流程实例出现.
     */
    protected FlowInstance awaitFlowInstanceExists(String flowId) {
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    List<FlowInstance> instances = flowStorage.getAvailableInstance(flowId);
                    return instances != null && !instances.isEmpty();
                });
        return flowStorage.getAvailableInstance(flowId).get(0);
    }

    /**
     * 等待流程到达指定状态.
     */
    protected FlowInstance awaitFlowState(String flowId, StatusEnum targetState) {
        awaitFlowInstanceExists(flowId);
        String flowInsId = flowStorage.getAvailableInstance(flowId).get(0).getInstanceId();
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    FlowInstance ins = flowStorage.getInstanceById(flowInsId);
                    StatusEnum state = ins != null ? ins.getState() : null;
                    log.info("awaitFlowState: flowInsId={}, state={}, target={}", flowInsId, state, targetState);
                    return state == targetState;
                });
        return flowStorage.getInstanceById(flowInsId);
    }

    /**
     * 等待任何一个任务到达指定状态.
     */
    protected TaskInstance awaitAnyTaskState(String flowId, StatusEnum targetState) {
        FlowInstance flowIns = awaitFlowInstanceExists(flowId);
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    List<TaskInstance> tasks = taskStorage.getTaskInsIdsByFlowInsId(flowIns.getInstanceId());
                    if (tasks == null || tasks.isEmpty()) {
                        return false;
                    }
                    return tasks.stream().anyMatch(t -> t.getState() == targetState);
                });
        List<TaskInstance> tasks = taskStorage.getTaskInsIdsByFlowInsId(flowIns.getInstanceId());
        return tasks.stream()
                .filter(t -> t.getState() == targetState)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到目标状态的任务"));
    }

    /**
     * 等待指定名称的任务到达指定状态.
     */
    protected TaskInstance awaitTaskByName(String flowId, String taskName, StatusEnum targetState) {
        FlowInstance flowIns = awaitFlowInstanceExists(flowId);
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    List<TaskInstance> tasks = taskStorage.getTaskInsIdsByFlowInsId(flowIns.getInstanceId());
                    if (tasks == null || tasks.isEmpty()) {
                        return false;
                    }
                    return tasks.stream()
                            .anyMatch(t -> taskName.equals(t.getTaskName()) && t.getState() == targetState);
                });
        List<TaskInstance> tasks = taskStorage.getTaskInsIdsByFlowInsId(flowIns.getInstanceId());
        return tasks.stream()
                .filter(t -> taskName.equals(t.getTaskName()) && t.getState() == targetState)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到任务 " + taskName + " 状态 " + targetState));
    }

    /**
     * 等待指定任务实例到达指定状态.
     */
    protected void awaitTaskState(String taskInsId, StatusEnum targetState) {
        await().atMost(AWAIT_TIMEOUT_SECONDS, SECONDS)
                .pollInterval(1, SECONDS)
                .until(() -> {
                    TaskInstance ins = taskStorage.getInstanceById(taskInsId);
                    StatusEnum state = ins != null ? ins.getState() : null;
                    log.info("awaitTaskState: taskInsId={}, state={}, target={}", taskInsId, state, targetState);
                    return state == targetState;
                });
    }

    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================= 工厂方法 =========================

    protected TriggerInfo createTriggerInfo(String payloadId, String triggerId, String version,
                                            TriggerTypeEnum type, String expression, TriggerPolicyEnum policy) {
        TriggerInfo info = new TriggerInfo();
        info.setTriggerId(triggerId);
        info.setPayloadId(payloadId);
        info.setVersion(version);
        info.setStartTime(System.currentTimeMillis());
        info.setEndTime(info.getStartTime() + 60 * 60 * 1000);
        info.setTriggerType(type);
        info.setTriggerExpression(expression);
        info.setTriggerPolicy(policy);
        return info;
    }

    protected FlowInfo createFlowInfo(String flowId, String flowName, String version) {
        FlowInfo info = new FlowInfo();
        info.setFlowId(flowId);
        info.setFlowName(flowName);
        info.setFlowType(FlowTypeEnum.BATCH);
        info.setVersion(version);
        info.setFlowParam(createDefaultParamData());
        info.setEventId("flow_event_" + flowId);
        info.setPluginData(new PluginData());
        return info;
    }

    protected TaskInfo createTaskInfo(String taskId, String flowId, String taskName, String taskType, String eventId) {
        TaskInfo info = new TaskInfo();
        info.setTaskId(taskId);
        info.setFlowId(flowId);
        info.setTaskName(taskName);
        info.setTaskType(taskType);
        info.setIsAble(true);
        info.setEventId(eventId);
        info.setTaskParam(createDefaultParamData());
        info.setPluginData(new PluginData());
        return info;
    }

    private ParamData createDefaultParamData() {
        ParamData paramData = new ParamData();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode emptyJsonNode = objectMapper.createObjectNode();
        paramData.setParams(emptyJsonNode);
        Map<String, Variable> vars = new HashMap<>();
        paramData.setVars(vars);
        return paramData;
    }
}