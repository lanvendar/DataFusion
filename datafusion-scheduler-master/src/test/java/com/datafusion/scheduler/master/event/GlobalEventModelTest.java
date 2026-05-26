package com.datafusion.scheduler.master.event;

import cn.hutool.core.lang.Pair;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GlobalEvent 模型和 EventTypeEnum 枚举的单元测试.
 */
public class GlobalEventModelTest {

    private static final Logger log = LoggerFactory.getLogger(GlobalEventModelTest.class);

    @Test
    void testGlobalEventGetGlobalEventKey() {
        GlobalEvent event = new GlobalEvent();
        event.setId("test-event-001");
        event.setEventTime(1000L);

        Pair<String, Long> key = event.getGlobalEventKey();
        assertEquals("test-event-001", key.getKey());
        assertEquals(1000L, key.getValue());
        log.info("GlobalEvent getGlobalEventKey 测试通过");
    }

    @Test
    void testGlobalEventSettersAndGetters() {
        GlobalEvent event = new GlobalEvent();
        event.setId("test-id");
        event.setType(EventTypeEnum.FLOW);
        event.setFlowInstanceId("flow-001");
        event.setTaskInstanceId("task-001");
        event.setEventTime(2000L);
        event.setTimeSegment("H");
        event.setBeginTime(1000L);
        event.setEndTime(3000L);

        assertEquals("test-id", event.getId());
        assertEquals(EventTypeEnum.FLOW, event.getType());
        assertEquals("flow-001", event.getFlowInstanceId());
        assertEquals("task-001", event.getTaskInstanceId());
        assertEquals(2000L, event.getEventTime());
        assertEquals("H", event.getTimeSegment());
        assertEquals(1000L, event.getBeginTime());
        assertEquals(3000L, event.getEndTime());
        log.info("GlobalEvent setter/getter 测试通过");
    }

    @Test
    void testEventTypeEnum() {
        assertEquals(1, EventTypeEnum.TASK.getType());
        assertEquals(2, EventTypeEnum.FLOW.getType());
        assertEquals(2, EventTypeEnum.values().length);
        log.info("EventTypeEnum 枚举测试通过");
    }
}
