package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.core.ApiExtractException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Sink mode tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class SinkModeTest {

    @Test
    public void parseShouldDefaultToAppend() {
        Assertions.assertEquals(SinkMode.APPEND, SinkMode.parse(null));
    }

    @Test
    public void parseShouldRejectUnknownMode() {
        Assertions.assertThrows(ApiExtractException.class, () -> SinkMode.parse("MERGE"));
    }
}
