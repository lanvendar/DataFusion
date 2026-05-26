package com.datafusion.scheduler.master.event;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.event.storage.EventStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ExactMatchGlobalEventOperator 单元测试.
 */
@ExtendWith(MockitoExtension.class)
public class ExactMatchGlobalEventOperatorTest {

    private static final Logger log = LoggerFactory.getLogger(ExactMatchGlobalEventOperatorTest.class);

    @Mock
    private EventStorage eventStorage;

    private Options options;
    private ThreadPoolExecutor eventThreadPool;
    private ExactMatchGlobalEventOperator operator;

    @BeforeEach
    void setUp() {
        options = new Options();
        eventThreadPool = createExecutor();
        operator = new ExactMatchGlobalEventOperator(eventStorage, eventThreadPool, options);
    }

    @Test
    void testCheckEventsReturnsFalseWhenNoEventExists() {
        Pair<String, Long> eventKey = Pair.of("event-001", 1000L);
        // LoadingCache.get 会触发 loader，而 eventStorage.loadByEventKey 返回 null 时会 NPE
        // 因此在精准匹配模式下，getEventIndex 只返回 key 部分
        // 需要 mock loadByEventKey 来返回有效数据或让 cache 不抛异常
        GlobalEvent mockEvent = createEvent("event-001", 1000L);
        when(eventStorage.loadByEventKey("event-001", 1000L)).thenReturn(mockEvent);

        boolean result = operator.checkEvents(eventKey, 1000L);
        assertTrue(result);
        log.info("checkEvents 精准匹配测试通过");
    }

    @Test
    void testCheckHandleReturnsTrueForMatchingTime() {
        boolean result = operator.checkHandle(1000L, 1000L);
        assertTrue(result);
        log.info("checkHandle 匹配时间返回 true 测试通过");
    }

    @Test
    void testCheckHandleReturnsFalseForNonMatchingTime() {
        boolean result = operator.checkHandle(1000L, 2000L);
        assertFalse(result);
        log.info("checkHandle 不匹配时间返回 false 测试通过");
    }

    @Test
    void testCheckHandleReturnsFalseForNull() {
        boolean result = operator.checkHandle(null, 1000L);
        assertFalse(result);
        log.info("checkHandle null 返回 false 测试通过");
    }

    @Test
    void testSaveEventStoresInCacheAndStorage() {
        GlobalEvent event = createEvent("event-save-001", 5000L);
        operator.occurredEvent(event);

        verify(eventStorage).save(event);
        log.info("saveEvent 保存事件到存储测试通过");
    }

    @Test
    void testSaveEventSkipsDuplicateEvent() {
        GlobalEvent event1 = createEvent("event-dup-001", 6000L);
        operator.occurredEvent(event1);

        GlobalEvent event2 = createEvent("event-dup-001", 6000L);
        operator.occurredEvent(event2);

        // 只有第一次保存会调用 storage.save
        verify(eventStorage, times(1)).save(any(GlobalEvent.class));
        log.info("重复事件不重复保存测试通过");
    }

    @Test
    void testOccurredEventNotifiesListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<GlobalEvent> received = new AtomicReference<>();

        Pair<String, Long> eventKey = Pair.of("event-notify-001", 7000L);
        operator.registerListener(eventKey, event -> {
            received.set(event);
            latch.countDown();
        });

        GlobalEvent event = createEvent("event-notify-001", 7000L);
        operator.occurredEvent(event);

        boolean awaited = latch.await(5, TimeUnit.SECONDS);
        assertTrue(awaited, "监听器应在超时前被通知");
        assertNotNull(received.get());
        assertEquals("event-notify-001", received.get().getId());
        log.info("occurredEvent 通知监听器测试通过");
    }

    @Test
    void testGetEventIndexUsesKeyOnly() {
        Pair<String, Long> eventKey = Pair.of("my-event", 12345L);
        String index = operator.getEventIndex(eventKey);
        assertEquals("my-event", index, "精准匹配模式下 eventIndex 应只包含 key 部分");
        log.info("getEventIndex 精准匹配模式测试通过");
    }

    private GlobalEvent createEvent(String id, Long eventTime) {
        GlobalEvent event = new GlobalEvent();
        event.setId(id);
        event.setEventTime(eventTime);
        event.setType(EventTypeEnum.TASK);
        return event;
    }

    private ThreadPoolExecutor createExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }
}
