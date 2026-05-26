package com.datafusion.scheduler.master.trigger.model;

import com.datafusion.scheduler.enums.StatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TriggerInstance 单元测试.
 */
class TriggerInstanceTest {

    @Test
    void testEquals() {
        TriggerInstance a = createInstance("payload-1", "ins-1", "v1", 1000L);
        TriggerInstance b = createInstance("payload-1", "ins-1", "v1", 2000L);
        assertEquals(a, b, "payloadId + instanceId + version相同则相等");
    }

    @Test
    void testNotEquals() {
        TriggerInstance a = createInstance("payload-1", "ins-1", "v1", 1000L);
        TriggerInstance b = createInstance("payload-1", "ins-2", "v1", 1000L);
        assertNotEquals(a, b);
    }

    @Test
    void testNotEqualsWithDifferentVersion() {
        TriggerInstance a = createInstance("payload-1", "ins-1", "v1", 1000L);
        TriggerInstance b = createInstance("payload-1", "ins-1", "v2", 1000L);
        assertNotEquals(a, b);
    }

    @Test
    void testHashCodeConsistency() {
        TriggerInstance a = createInstance("payload-1", "ins-1", "v1", 1000L);
        TriggerInstance b = createInstance("payload-1", "ins-1", "v1", 2000L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testCompareTo() {
        TriggerInstance earlier = createInstance("p1", "i1", "v1", 1000L);
        TriggerInstance later = createInstance("p1", "i2", "v1", 2000L);
        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
    }

    @Test
    void testCompareToEqual() {
        TriggerInstance a = createInstance("p1", "i1", "v1", 1000L);
        TriggerInstance b = createInstance("p2", "i2", "v1", 1000L);
        assertEquals(0, a.compareTo(b));
    }

    @Test
    void testStateGetterSetter() {
        TriggerInstance instance = createInstance("p1", "i1", "v1", 1000L);
        instance.setState(StatusEnum.INIT_SUCCESS);
        assertEquals(StatusEnum.INIT_SUCCESS, instance.getState());
    }

    private TriggerInstance createInstance(String payloadId, String instanceId, String version, long scheduleTime) {
        TriggerInstance instance = new TriggerInstance();
        instance.setPayloadId(payloadId);
        instance.setInstanceId(instanceId);
        instance.setVersion(version);
        instance.setScheduleTime(scheduleTime);
        return instance;
    }
}