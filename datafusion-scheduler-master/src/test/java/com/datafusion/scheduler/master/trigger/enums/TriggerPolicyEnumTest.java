package com.datafusion.scheduler.master.trigger.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TriggerPolicyEnum 单元测试.
 */
class TriggerPolicyEnumTest {

    @Test
    void testValueOfWithValidIndex() {
        assertEquals(TriggerPolicyEnum.EXECUTE_ONCE, TriggerPolicyEnum.valueOf(0));
        assertEquals(TriggerPolicyEnum.SERIAL_WAIT, TriggerPolicyEnum.valueOf(1));
        assertEquals(TriggerPolicyEnum.PARALLEL, TriggerPolicyEnum.valueOf(2));
        assertEquals(TriggerPolicyEnum.DISCARD_NEW, TriggerPolicyEnum.valueOf(3));
        assertEquals(TriggerPolicyEnum.DISCARD_OLD, TriggerPolicyEnum.valueOf(4));
    }

    @Test
    void testValueOfWithNegativeIndex() {
        assertEquals(TriggerPolicyEnum.EXECUTE_ONCE, TriggerPolicyEnum.valueOf(-1));
    }

    @Test
    void testValueOfWithOutOfBoundsIndex() {
        assertEquals(TriggerPolicyEnum.EXECUTE_ONCE, TriggerPolicyEnum.valueOf(99));
    }
}