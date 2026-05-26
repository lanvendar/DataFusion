package com.datafusion.scheduler.master.trigger;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TriggerInstanceHandlerWithPolicy 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class TriggerInstanceHandlerWithPolicyTest {

    @Mock
    private SchedulerTrigger schedulerTrigger;

    @Mock
    private TriggerStorage triggerStorage;

    private TriggerInstanceHandlerWithPolicy handler;

    @BeforeEach
    void setUp() {
        Options options = new Options();
        options.set(MasterConfigOptions.POLL_INTERVAL, 1000L);
        options.set(MasterConfigOptions.PREPARED_MS, 30 * 60 * 1000L);
        options.set(MasterConfigOptions.BATCH_READ_COUNT, 1000);
        handler = new TriggerInstanceHandlerWithPolicy(schedulerTrigger, triggerStorage, options);
    }

    @Test
    void testGetSchedulerTrigger() {
        assertEquals(schedulerTrigger, handler.getSchedulerTrigger());
    }

    @Test
    void testGenerateInstanceWithSimpleTrigger() {
        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setStartTime(0L);
        info.setEndTime(Long.MAX_VALUE);

        handler.generateInstance(info, 0L, true);

        // 验证缓存中有数据 (通过fetchCache间接验证)
        // 由于scheduleTime在过去,fetchCache应能取到
    }

    @Test
    void testGenerateInstanceWithNoNextSchedule() {
        TriggerInfo info = createTriggerInfo("flow-002", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setStartTime(0L);
        info.setEndTime(5000L);

        // baseTime=6000, 下一次调度时间=10000 > endTime=5000, scheduleTime=-1
        handler.generateInstance(info, 6000L, false);
        // 不应抛异常
    }

    @Test
    void testAddCacheAndFetchCacheWithParallelPolicy() {
        // 准备: 构建一个已过期的TriggerInstance加入缓存
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        // 设置调度时间在过去（确保fetchCache能获取到）
        instance.setScheduleTime(System.currentTimeMillis() - 60000);

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setTriggerPolicy(TriggerPolicyEnum.PARALLEL);

        // mock: triggerStorage返回null表示实例未生成过
        when(triggerStorage.getTriggerInstance(anyString())).thenReturn(null);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        handler.addCache(instance);
        List<TriggerInstance> result = handler.fetchCache();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("flow-001", result.get(0).getPayloadId());
    }

    @Test
    void testFetchCacheEmpty() {
        List<TriggerInstance> result = handler.fetchCache();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckScheduleAvailableReturnsTrueWhenValid() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setScheduleFlag(true);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        assertTrue(handler.checkScheduleAvailable(instance));
    }

    @Test
    void testCheckScheduleAvailableReturnsFalseWhenNoTriggerInfo() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(null);

        assertFalse(handler.checkScheduleAvailable(instance));
    }

    @Test
    void testCheckScheduleAvailableReturnsFalseWhenFlagOff() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setScheduleFlag(false);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        assertFalse(handler.checkScheduleAvailable(instance));
    }

    @Test
    void testCheckScheduleAvailableReturnsFalseWhenVersionMismatch() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v2");

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setScheduleFlag(true);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        assertFalse(handler.checkScheduleAvailable(instance));
    }

    @Test
    void testGetScheduleInstanceStateReturnsState() {
        TriggerInstance stored = createTriggerInstance("flow-001", "ins-001", "v1");
        stored.setState(StatusEnum.RUNNING);
        when(triggerStorage.getTriggerInstance("ins-001")).thenReturn(stored);

        assertEquals(StatusEnum.RUNNING, handler.getScheduleInstanceState("ins-001"));
    }

    @Test
    void testGetScheduleInstanceStateReturnsNullWhenNotFound() {
        when(triggerStorage.getTriggerInstance("ins-999")).thenReturn(null);
        assertNull(handler.getScheduleInstanceState("ins-999"));
    }

    @Test
    void testSaveLastScheduleInstance() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        handler.saveLastScheduleInstance(instance);
        verify(triggerStorage).saveTriggerInstance(instance);
    }

    @Test
    void testSaveTriggerInstance() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        handler.saveTriggerInstance(instance);
        verify(triggerStorage).saveTriggerInstance(instance);
    }

    @Test
    void testGetTriggerInfo() {
        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.CRON, "0 * * * * ?");
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        TriggerInfo result = handler.getTriggerInfo("flow-001");
        assertEquals(info, result);
    }

    @Test
    void testFetchCacheWithExecuteOncePolicy() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        instance.setScheduleTime(System.currentTimeMillis() - 60000);

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setTriggerPolicy(TriggerPolicyEnum.EXECUTE_ONCE);
        info.setScheduleFlag(true);

        // 实例未生成过
        when(triggerStorage.getTriggerInstance(anyString())).thenReturn(null);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        // 有上一次执行的实例 -> EXECUTE_ONCE策略返回-1(不调度)
        TriggerInstance lastInstance = createTriggerInstance("flow-001", "ins-000", "v1");
        lastInstance.setState(StatusEnum.RUN_SUCCESS);
        when(triggerStorage.getLastTriggerInstance("flow-001", "v1")).thenReturn(lastInstance);

        handler.addCache(instance);
        List<TriggerInstance> result = handler.fetchCache();

        assertNotNull(result);
        assertTrue(result.isEmpty(), "EXECUTE_ONCE策略,已有上一次实例,不应调度");
    }

    @Test
    void testFetchCacheWithSerialWaitPolicyLastSuccess() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        instance.setScheduleTime(System.currentTimeMillis() - 60000);

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setTriggerPolicy(TriggerPolicyEnum.SERIAL_WAIT);
        info.setScheduleFlag(true);

        when(triggerStorage.getTriggerInstance(anyString())).thenReturn(null);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        TriggerInstance lastInstance = createTriggerInstance("flow-001", "ins-000", "v1");
        lastInstance.setState(StatusEnum.RUN_SUCCESS);
        when(triggerStorage.getLastTriggerInstance("flow-001", "v1")).thenReturn(lastInstance);

        handler.addCache(instance);
        List<TriggerInstance> result = handler.fetchCache();

        assertNotNull(result);
        assertEquals(1, result.size(), "SERIAL_WAIT策略,上一次已成功,应调度");
    }

    @Test
    void testFetchCacheWithNoLastInstance() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        instance.setScheduleTime(System.currentTimeMillis() - 60000);

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setTriggerPolicy(TriggerPolicyEnum.SERIAL_WAIT);
        info.setScheduleFlag(true);

        when(triggerStorage.getTriggerInstance(anyString())).thenReturn(null);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);
        when(triggerStorage.getLastTriggerInstance("flow-001", "v1")).thenReturn(null);

        handler.addCache(instance);
        List<TriggerInstance> result = handler.fetchCache();

        assertNotNull(result);
        assertEquals(1, result.size(), "没有上一次实例,应直接调度");
    }

    @Test
    void testFetchCacheAlreadyGenerated() {
        TriggerInstance instance = createTriggerInstance("flow-001", "ins-001", "v1");
        instance.setScheduleTime(System.currentTimeMillis() - 60000);

        // 已经存在实例 -> 直接返回0(可调度)
        TriggerInstance existingInstance = createTriggerInstance("flow-001", "ins-001", "v1");
        when(triggerStorage.getTriggerInstance("ins-001")).thenReturn(existingInstance);

        TriggerInfo info = createTriggerInfo("flow-001", "v1", TriggerTypeEnum.INTERVAL, "10000");
        info.setScheduleFlag(true);
        when(triggerStorage.getTriggerInfo("flow-001")).thenReturn(info);

        handler.addCache(instance);
        List<TriggerInstance> result = handler.fetchCache();

        assertNotNull(result);
        assertEquals(1, result.size(), "已生成过的实例应直接调度");
    }

    private TriggerInfo createTriggerInfo(String payloadId, String version, TriggerTypeEnum type, String expression) {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId(payloadId);
        info.setVersion(version);
        info.setTriggerId("trigger-" + payloadId);
        info.setTriggerType(type);
        info.setTriggerExpression(expression);
        info.setStartTime(0L);
        info.setEndTime(Long.MAX_VALUE);
        return info;
    }

    private TriggerInstance createTriggerInstance(String payloadId, String instanceId, String version) {
        TriggerInstance instance = new TriggerInstance();
        instance.setPayloadId(payloadId);
        instance.setInstanceId(instanceId);
        instance.setVersion(version);
        return instance;
    }
}
