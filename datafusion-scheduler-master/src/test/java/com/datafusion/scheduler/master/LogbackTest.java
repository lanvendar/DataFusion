package com.datafusion.scheduler.master;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Logback logging test.
 *
 * @author DataFusion Team
 * @version 1.0.0
 */
@Slf4j
public class LogbackTest {

    @Test
    public void testLogback() {
        log.trace("This is a TRACE message");
        log.debug("This is a DEBUG message");
        log.info("This is an INFO message");
        log.warn("This is a WARN message");
        log.error("This is an ERROR message");
    }

    @Test
    public void testLogbackWithException() {
        try {
            int result = 10 / 0;
        } catch (Exception e) {
            log.error("Exception occurred while dividing", e);
        }
    }
}