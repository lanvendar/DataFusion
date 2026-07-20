package com.datafusion.scheduler.master.flow;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterStorage;
import com.datafusion.scheduler.master.actor.ActorSystem;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.event.storage.EventStorage;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 初始化阶段流程实例丢弃测试.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/20
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class FlowActionInitializationDiscardTest {

    /**
     * Actor 系统.
     */
    @Mock
    private ActorSystem actorSystem;

    /**
     * 全局事件操作器.
     */
    @Mock
    private GlobalEventOperator eventOperator;

    /**
     * 流程存储.
     */
    @Mock
    private FlowStorage flowStorage;

    /**
     * 任务存储.
     */
    @Mock
    private TaskStorage taskStorage;

    /**
     * Trigger 存储.
     */
    @Mock
    private TriggerStorage triggerStorage;

    /**
     * 流程动作处理器.
     */
    private FlowAction flowAction;

    @BeforeEach
    void setUp() {
        MasterStorage masterStorage = new MasterStorage(triggerStorage, flowStorage, taskStorage,
                mock(EventStorage.class));
        flowAction = new FlowAction(actorSystem, eventOperator, masterStorage);
    }

    @Test
    void shouldDiscardInitializationInstancesOfUnpublishedVersion() {
        final FlowInstance initialization = createFlowInstance("flow-instance-init", "flow-1", "v1",
                StatusEnum.INIT_SUCCESS);
        initialization.setScheduleTime(1000L);
        FlowInstance running = createFlowInstance("flow-instance-running", "flow-1", "v1", StatusEnum.RUNNING);
        FlowInstance otherVersion = createFlowInstance("flow-instance-other", "flow-1", "v2",
                StatusEnum.INIT_SUCCESS);
        when(flowStorage.getAvailableInstance("flow-1"))
                .thenReturn(List.of(initialization, running, otherVersion));
        when(flowStorage.getInstanceById("flow-instance-init")).thenReturn(initialization);

        Map<String, Long> cleanedScheduleTimes = flowAction.cleanInitializationInstances("flow-1", "v1");

        assertEquals(1000L, cleanedScheduleTimes.get(FlowAction.buildCleanedScheduleKey("flow-1", "v1")));
        verify(taskStorage).removeTaskInsByFlowInsId("flow-instance-init");
        verify(flowStorage).removeInstanceById("flow-instance-init");
        verify(taskStorage, never()).removeTaskInsByFlowInsId("flow-instance-running");
        verify(flowStorage, never()).removeInstanceById("flow-instance-other");
    }

    @Test
    void shouldDiscardUnavailableTriggerInitializationInstance() {
        final FlowInstance initialization = createFlowInstance("flow-instance-init", "flow-1", "v1",
                StatusEnum.INIT_SUCCESS);
        when(flowStorage.getInstanceById("flow-instance-init")).thenReturn(initialization);

        boolean cleaned = flowAction.cleanInitializationInstance("flow-instance-init", "flow-1", "v1");

        assertTrue(cleaned);
        verify(taskStorage).removeTaskInsByFlowInsId("flow-instance-init");
        verify(flowStorage).removeInstanceById("flow-instance-init");
    }

    @Test
    void shouldKeepInitializationInstanceWhenIdentityDoesNotMatch() {
        final FlowInstance initialization = createFlowInstance("flow-instance-init", "flow-1", "v1",
                StatusEnum.INIT_SUCCESS);
        when(flowStorage.getInstanceById("flow-instance-init")).thenReturn(initialization);

        boolean cleaned = flowAction.cleanInitializationInstance("flow-instance-init", "flow-1", "v2");

        assertFalse(cleaned);
        verify(taskStorage, never()).removeTaskInsByFlowInsId("flow-instance-init");
        verify(flowStorage, never()).removeInstanceById("flow-instance-init");
    }

    /**
     * 创建流程实例.
     *
     * @param instanceId 流程实例ID
     * @param flowId     流程ID
     * @param version    发布版本
     * @param state      实例状态
     * @return 流程实例
     */
    private FlowInstance createFlowInstance(String instanceId, String flowId, String version, StatusEnum state) {
        FlowInstance flowInstance = new FlowInstance();
        flowInstance.setInstanceId(instanceId);
        flowInstance.setFlowId(flowId);
        flowInstance.setVersion(version);
        flowInstance.setState(state);
        return flowInstance;
    }
}
