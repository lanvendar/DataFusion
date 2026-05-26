package com.datafusion.scheduler.master.trigger.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TriggerTypeEnum 单元测试.
 */
class TriggerTypeEnumTest {

    @Test
    void testValueOfWithValidIndex() {
        assertEquals(TriggerTypeEnum.CRON, TriggerTypeEnum.valueOf(0));
        assertEquals(TriggerTypeEnum.INTERVAL, TriggerTypeEnum.valueOf(1));
    }

    @Test
    void testValueOfWithNegativeIndex() {
        assertEquals(TriggerTypeEnum.INTERVAL, TriggerTypeEnum.valueOf(-1));
    }

    @Test
    void testValueOfWithOutOfBoundsIndex() {
        assertEquals(TriggerTypeEnum.INTERVAL, TriggerTypeEnum.valueOf(99));
    }
}