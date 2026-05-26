package com.datafusion.scheduler.master.trigger.model;

import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TriggerInfo 单元测试.
 */
class TriggerInfoTest {

    @Test
    void testCalScheduleTimeWithCronType() {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-001");
        info.setTriggerType(TriggerTypeEnum.CRON);
        // 每分钟执行一次
        info.setTriggerExpression("0 * * * * ?");
        info.setStartTime(0L);
        info.setEndTime(Long.MAX_VALUE);

        long baseTime = 1700000000000L;
        long scheduleTime = info.calScheduleTime(baseTime, false);
        assertTrue(scheduleTime > baseTime, "CRON下一次调度时间应大于基准时间");
    }

    @Test
    void testCalScheduleTimeWithSimpleType() {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-002");
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        // 每10秒
        info.setTriggerExpression("10000");
        info.setStartTime(1000L);
        info.setEndTime(Long.MAX_VALUE);

        // 基准时间 = startTime + 25000 = 26000，不包含基准
        long scheduleTime = info.calScheduleTime(26000L, false);
        // 下一个间隔: startTime + ceil((26000 - 1000) / 10000 + 1) * 10000
        // pass = ((25000) / 10000 + 1) * 10000 = 30000
        // scheduleTime = 1000 + 30000 = 31000
        assertEquals(31000L, scheduleTime);
    }

    @Test
    void testCalScheduleTimeWithSimpleTypeIncluded() {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-003");
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("10000");
        info.setStartTime(1000L);
        info.setEndTime(Long.MAX_VALUE);

        // baseTime正好在调度点上: startTime + 20000 = 21000
        long scheduleTime = info.calScheduleTime(21000L, true);
        assertEquals(21000L, scheduleTime);
    }

    @Test
    void testCalScheduleTimeBaseTimeLessThanStartTime() {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-004");
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("5000");
        info.setStartTime(10000L);
        info.setEndTime(Long.MAX_VALUE);

        // baseTime < startTime, 应使用startTime作为基准
        long scheduleTime = info.calScheduleTime(5000L, true);
        assertEquals(10000L, scheduleTime);
    }

    @Test
    void testCalScheduleTimeExceedsEndTime() {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-005");
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("10000");
        info.setStartTime(0L);
        info.setEndTime(5000L);

        // 下一次调度时间为10000 > endTime(5000), 返回-1
        long scheduleTime = info.calScheduleTime(1000L, false);
        assertEquals(-1L, scheduleTime);
    }

    @Test
    void testHasNextScheduleReturnsTrue() {
        TriggerInfo info = new TriggerInfo();
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("5000");
        info.setStartTime(0L);
        info.setEndTime(100000L);

        assertTrue(info.hasNextSchedule(0L, true));
    }

    @Test
    void testHasNextScheduleReturnsFalse() {
        TriggerInfo info = new TriggerInfo();
        info.setTriggerType(TriggerTypeEnum.INTERVAL);
        info.setTriggerExpression("10000");
        info.setStartTime(0L);
        info.setEndTime(5000L);

        // 下一次调度时间 > endTime
        assertFalse(info.hasNextSchedule(1000L, false));
    }

    @Test
    void testDefaultTriggerPolicy() {
        TriggerInfo info = new TriggerInfo();
        assertEquals(TriggerPolicyEnum.EXECUTE_ONCE, info.getTriggerPolicy());
    }

    @Test
    void testToString() {
        TriggerInfo info = new TriggerInfo();
        info.setPayloadId("flow-001");
        info.setVersion("v1");
        info.setTriggerId("trigger-001");
        info.setTriggerType(TriggerTypeEnum.CRON);
        info.setTriggerExpression("0 * * * * ?");

        String result = info.toString();
        assertTrue(result.contains("flow-001"));
        assertTrue(result.contains("trigger-001"));
    }
}