package com.datafusion.plugin.api.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Trigger mode tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class TriggerModeTest {

    @Test
    public void parseShouldDefaultToOnce() {
        Assertions.assertEquals(TriggerMode.ONCE, TriggerMode.parse(null));
    }

    @Test
    public void schedulerShouldBeSingleRunMode() {
        Assertions.assertTrue(TriggerMode.SCHEDULER.isSingleRun());
    }

    @Test
    public void cronShouldNotBeSingleRunMode() {
        Assertions.assertFalse(TriggerMode.CRON.isSingleRun());
    }

    @Test
    public void parseShouldRejectUnknownMode() {
        Assertions.assertThrows(ApiExtractException.class, () -> TriggerMode.parse("MANUAL"));
    }
}
