package com.datafusion.manager.scheduler.service.impl;

import com.datafusion.common.exception.CommonException;
import com.datafusion.manager.scheduler.dto.FlowScheduleDto;
import com.datafusion.manager.scheduler.dao.TriggerInfoMapper;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.po.TriggerInfoEntity;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.scheduler.service.TaskLinkService;
import com.datafusion.scheduler.master.MasterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FlowInfoServiceImpl}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/22
 * @since 1.0.0
 */
class FlowInfoServiceImplTest {

    @Test
    void shouldRejectPublishEmptyFlow() {
        UUID flowId = UUID.randomUUID();
        FlowInfoEntity flow = new FlowInfoEntity();
        flow.setId(flowId);
        TestFlowInfoService service = newService(flow, Collections.emptyList());
        CommonException exception = assertThrows(CommonException.class, () -> service.publish(flowId));

        assertEquals("空流程无法发布", exception.getMessage());
        assertFalse(service.isUpdated());
    }

    @Test
    void shouldRejectEnableEmptyFlow() {
        UUID flowId = UUID.randomUUID();
        FlowInfoEntity flow = new FlowInfoEntity();
        flow.setId(flowId);
        flow.setPublishState(true);
        TestFlowInfoService service = newService(flow, Collections.emptyList());

        FlowScheduleDto dto = createScheduleDto(flowId);

        CommonException exception = assertThrows(CommonException.class, () -> service.enable(dto));

        assertEquals("空流程无法开始调度", exception.getMessage());
        assertFalse(service.isUpdated());
    }

    @Test
    void shouldPublishFlowWithTask() {
        UUID flowId = UUID.randomUUID();
        FlowInfoEntity flow = new FlowInfoEntity();
        flow.setId(flowId);
        TaskInfoEntity task = new TaskInfoEntity();
        task.setId(UUID.randomUUID());
        TestFlowInfoService service = newService(flow, List.of(task));
        boolean published = service.publish(flowId);

        assertTrue(published);
        assertTrue(flow.getPublishState());
        assertTrue(service.isUpdated());
    }

    @Test
    void shouldPublishAndEnableUnpublishedFlow() {
        UUID flowId = UUID.randomUUID();
        FlowInfoEntity flow = new FlowInfoEntity();
        flow.setId(flowId);
        flow.setPublishState(false);
        TaskInfoEntity task = new TaskInfoEntity();
        task.setId(UUID.randomUUID());
        TestFlowInfoService service = newService(flow, List.of(task));
        FlowScheduleDto dto = createScheduleDto(flowId);

        boolean enabled = service.enable(dto);

        assertTrue(enabled);
        assertTrue(flow.getPublishState());
        assertTrue(flow.getEnabled());
        assertEquals(dto.getTriggerId(), flow.getTriggerId());
        assertEquals(dto.getStartTime(), flow.getStartTime());
        assertEquals(dto.getEndTime(), flow.getEndTime());
        assertTrue(service.isUpdated());
    }

    @Test
    void shouldKeepPublishVersionWhenEnablingPublishedFlow() {
        UUID flowId = UUID.randomUUID();
        FlowInfoEntity flow = new FlowInfoEntity();
        flow.setId(flowId);
        flow.setPublishState(true);
        flow.setPublishVersion(100L);
        TaskInfoEntity task = new TaskInfoEntity();
        task.setId(UUID.randomUUID());
        TestFlowInfoService service = newService(flow, List.of(task));

        boolean enabled = service.enable(createScheduleDto(flowId));

        assertTrue(enabled);
        assertEquals(100L, flow.getPublishVersion());
    }

    @Test
    void shouldRejectInvalidScheduleWindow() {
        UUID flowId = UUID.randomUUID();
        FlowInfoEntity flow = new FlowInfoEntity();
        flow.setId(flowId);
        TaskInfoEntity task = new TaskInfoEntity();
        task.setId(UUID.randomUUID());
        TestFlowInfoService service = newService(flow, List.of(task));
        FlowScheduleDto dto = createScheduleDto(flowId);
        dto.setEndTime(dto.getStartTime());

        CommonException exception = assertThrows(CommonException.class, () -> service.enable(dto));

        assertEquals("调度结束时间必须晚于开始时间", exception.getMessage());
        assertFalse(service.isUpdated());
    }

    /**
     * 构建调度配置.
     *
     * @param flowId 流程ID
     * @return 调度配置
     */
    private FlowScheduleDto createScheduleDto(UUID flowId) {
        FlowScheduleDto dto = new FlowScheduleDto();
        dto.setId(flowId);
        dto.setTriggerId(UUID.randomUUID());
        dto.setStartTime(System.currentTimeMillis());
        dto.setEndTime(dto.getStartTime() + 60_000L);
        return dto;
    }

    /**
     * 构建流程测试服务.
     *
     * @param flow  流程实体
     * @param tasks 流程任务
     * @return 流程测试服务
     */
    private TestFlowInfoService newService(FlowInfoEntity flow, List<TaskInfoEntity> tasks) {
        TaskInfoService taskInfoService = proxyService(TaskInfoService.class, (proxy, method, args) -> {
            if ("listByFlowId".equals(method.getName())) {
                return tasks;
            }
            return defaultValue(method.getReturnType());
        });
        TaskLinkService taskLinkService = proxyService(TaskLinkService.class, (proxy, method, args) -> {
            if ("listByFlowId".equals(method.getName())) {
                return Collections.emptyList();
            }
            return defaultValue(method.getReturnType());
        });
        TriggerInfoMapper triggerInfoMapper = proxyService(TriggerInfoMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) {
                TriggerInfoEntity trigger = new TriggerInfoEntity();
                trigger.setId((UUID) args[0]);
                return trigger;
            }
            return defaultValue(method.getReturnType());
        });
        return new TestFlowInfoService(taskInfoService, taskLinkService, triggerInfoMapper,
                new EmptyMasterServiceProvider(), flow);
    }

    /**
     * 构建JDK代理服务.
     *
     * @param type    服务类型
     * @param handler 方法处理器
     * @param <T>     服务类型
     * @return 代理服务
     */
    private <T> T proxyService(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    /**
     * 获取默认返回值.
     *
     * @param returnType 返回类型
     * @return 默认返回值
     */
    private Object defaultValue(Class<?> returnType) {
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (int.class.equals(returnType) || long.class.equals(returnType) || double.class.equals(returnType)
                || float.class.equals(returnType) || short.class.equals(returnType) || byte.class.equals(returnType)) {
            return 0;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return null;
    }

    /**
     * 流程测试服务.
     */
    private static class TestFlowInfoService extends FlowInfoServiceImpl {

        /**
         * 流程实体.
         */
        private final FlowInfoEntity flow;

        /**
         * 是否已更新.
         */
        private boolean updated;

        TestFlowInfoService(TaskInfoService taskInfoService, TaskLinkService taskLinkService,
                TriggerInfoMapper triggerInfoMapper, ObjectProvider<MasterService> masterServiceProvider,
                FlowInfoEntity flow) {
            super(taskInfoService, taskLinkService, triggerInfoMapper, masterServiceProvider);
            this.flow = flow;
        }

        @Override
        public FlowInfoEntity getById(java.io.Serializable id) {
            if (flow.getId().equals(id)) {
                return flow;
            }
            return null;
        }

        @Override
        public boolean updateById(FlowInfoEntity entity) {
            updated = true;
            return true;
        }

        boolean isUpdated() {
            return updated;
        }
    }

    /**
     * 空 master 服务提供者.
     */
    private static class EmptyMasterServiceProvider implements ObjectProvider<MasterService> {

        @Override
        public MasterService getObject(Object... args) {
            return null;
        }

        @Override
        public MasterService getIfAvailable() {
            return null;
        }

        @Override
        public MasterService getIfUnique() {
            return null;
        }

        @Override
        public MasterService getObject() {
            return null;
        }

        @Override
        public Iterator<MasterService> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public Stream<MasterService> stream() {
            return Stream.empty();
        }

        @Override
        public Stream<MasterService> orderedStream() {
            return Stream.empty();
        }
    }
}
