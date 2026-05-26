package com.datafusion.scheduler.master.trigger.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TriggerWrapperSet 单元测试.
 */
class TriggerWrapperSetTest {

    @Test
    void testAddTarget() {
        TriggerWrapperSet<String> set = new TriggerWrapperSet<>();
        set.setTriggerTimeMs(System.currentTimeMillis() + 10000);

        TriggerWrapper<String> wrapper = new TriggerWrapper<>();
        wrapper.setPayload("test");
        wrapper.setTriggerTime(set.getTriggerTimeMs());

        set.addTarget(wrapper);
        assertEquals(1, set.getTargetSet().size());
    }

    @Test
    void testAddDuplicateTarget() {
        TriggerWrapperSet<String> set = new TriggerWrapperSet<>();
        set.setTriggerTimeMs(1000L);

        TriggerWrapper<String> wrapper = new TriggerWrapper<>();
        wrapper.setPayload("test");
        wrapper.setTriggerTime(1000L);

        set.addTarget(wrapper);
        set.addTarget(wrapper);
        assertEquals(1, set.getTargetSet().size(), "重复元素不应被添加");
    }

    @Test
    void testGetDelayPositive() {
        TriggerWrapperSet<String> set = new TriggerWrapperSet<>();
        set.setTriggerTimeMs(System.currentTimeMillis() + 60000);
        assertTrue(set.getDelay(TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void testGetDelayNegative() {
        TriggerWrapperSet<String> set = new TriggerWrapperSet<>();
        set.setTriggerTimeMs(System.currentTimeMillis() - 60000);
        assertTrue(set.getDelay(TimeUnit.MILLISECONDS) < 0);
    }

    @Test
    void testCompareTo() {
        TriggerWrapperSet<String> earlier = new TriggerWrapperSet<>();
        earlier.setTriggerTimeMs(System.currentTimeMillis() + 10000);

        TriggerWrapperSet<String> later = new TriggerWrapperSet<>();
        later.setTriggerTimeMs(System.currentTimeMillis() + 60000);

        assertTrue(earlier.compareTo(later) < 0);
        assertTrue(later.compareTo(earlier) > 0);
    }
}