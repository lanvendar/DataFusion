package com.datafusion.scheduler.master.event;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.options.Options;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AbstractGlobalEventOperator 中 getEventIndex / revertEventKey 方法的单元测试.
 */
public class AbstractGlobalEventOperatorTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractGlobalEventOperatorTest.class);

    @Test
    void testGetEventIndexDefaultMode() {
        Options options = new Options();
        // 默认 eventClass = DefaultGlobalEventOperator
        DefaultGlobalEventOperator operator = new DefaultGlobalEventOperator(
                new com.datafusion.scheduler.master.event.storage.EventStorageMem(), createExecutor(), options);

        Pair<String, Long> eventKey = Pair.of("myEvent", 12345L);
        String index = operator.getEventIndex(eventKey);
        assertEquals("myEvent_12345", index, "默认模式下 eventIndex 应为 key_value 格式");
        log.info("getEventIndex 默认模式测试通过");
    }

    @Test
    void testGetEventIndexExactMatchMode() {
        Options options = new Options();
        ExactMatchGlobalEventOperator operator = new ExactMatchGlobalEventOperator(
                new com.datafusion.scheduler.master.event.storage.EventStorageMem(), createExecutor(), options);

        Pair<String, Long> eventKey = Pair.of("myEvent", 12345L);
        String index = operator.getEventIndex(eventKey);
        assertEquals("myEvent", index, "精准匹配模式下 eventIndex 应只包含 key 部分");
        log.info("getEventIndex 精准匹配模式测试通过");
    }

    @Test
    void testRevertEventKeyDefaultMode() {
        Options options = new Options();
        DefaultGlobalEventOperator operator = new DefaultGlobalEventOperator(
                new com.datafusion.scheduler.master.event.storage.EventStorageMem(), createExecutor(), options);

        Pair<String, Long> result = operator.revertEventKey("myEvent_12345");
        assertEquals("myEvent", result.getKey());
        assertEquals(12345L, result.getValue());
        log.info("revertEventKey 默认模式测试通过");
    }

    @Test
    void testGetEventIndexWithDefaultOptions() {
        Options options = new Options();
        DefaultGlobalEventOperator operator = new DefaultGlobalEventOperator(
                new com.datafusion.scheduler.master.event.storage.EventStorageMem(), createExecutor(), options);

        Pair<String, Long> eventKey = Pair.of("evt", 999L);
        String index = operator.getEventIndex(eventKey);
        assertEquals("evt_999", index, "默认配置下应使用默认格式");
        log.info("getEventIndex 默认配置测试通过");
    }

    private ThreadPoolExecutor createExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }
}
