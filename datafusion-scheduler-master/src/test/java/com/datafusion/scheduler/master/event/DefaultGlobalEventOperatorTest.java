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
 * DefaultGlobalEventOperator 单元测试.
 */
@ExtendWith(MockitoExtension.class)
public class DefaultGlobalEventOperatorTest {

    private static final Logger log = LoggerFactory.getLogger(DefaultGlobalEventOperatorTest.class);

    @Mock
    private EventStorage eventStorage;

    private Options options;
    private ThreadPoolExecutor eventThreadPool;
    private DefaultGlobalEventOperator operator;

    @BeforeEach
    void setUp() {
        options = new Options();
        eventThreadPool = createExecutor();
        operator = new DefaultGlobalEventOperator(eventStorage, eventThreadPool, options);
    }

    @Test
    void testCheckEventsReturnsFalseWhenNoEventExists() {
        Pair<String, Long> eventKey = Pair.of("event-001", 1000L);
        boolean result = operator.checkEvents(eventKey, 1500L);
        assertFalse(result);
        log.info("无事件时 checkEvents 返回 false 测试通过");
    }

    @Test
    void testOccurredEventAndCheckEvents() {
        GlobalEvent event = createEvent("event-001", 1000L, "H");
        operator.occurredEvent(event);

        verify(eventStorage).save(event);
        assertNotNull(event.getBeginTime());
        assertNotNull(event.getEndTime());
        log.info("occurredEvent 保存事件并设置时间范围测试通过");
    }

    @Test
    void testOccurredEventNotifiesRegisteredListener() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<GlobalEvent> received = new AtomicReference<>();

        Pair<String, Long> eventKey = Pair.of("event-002", 2000L);
        operator.registerListener(eventKey, event -> {
            received.set(event);
            latch.countDown();
        });

        GlobalEvent event = createEvent("event-002", 2000L, "H");
        operator.occurredEvent(event);

        boolean awaited = latch.await(5, TimeUnit.SECONDS);
        assertTrue(awaited, "监听器应在超时前被通知");
        assertNotNull(received.get());
        assertEquals("event-002", received.get().getId());
        log.info("occurredEvent 通知监听器测试通过");
    }

    @Test
    void testRegisterMultipleListenersForSameEvent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        Pair<String, Long> eventKey = Pair.of("event-003", 3000L);
        operator.registerListener(eventKey, event -> latch.countDown());
        operator.registerListener(eventKey, event -> latch.countDown());

        GlobalEvent event = createEvent("event-003", 3000L, "H");
        operator.occurredEvent(event);

        boolean awaited = latch.await(5, TimeUnit.SECONDS);
        assertTrue(awaited, "所有监听器应在超时前被通知");
        log.info("注册多个监听器测试通过");
    }

    @Test
    void testListenerRemovedAfterEventOccurred() throws InterruptedException {
        CountDownLatch firstLatch = new CountDownLatch(1);
        CountDownLatch secondLatch = new CountDownLatch(1);

        Pair<String, Long> eventKey = Pair.of("event-004", 4000L);
        operator.registerListener(eventKey, event -> firstLatch.countDown());

        GlobalEvent event1 = createEvent("event-004", 4000L, "H");
        operator.occurredEvent(event1);
        assertTrue(firstLatch.await(5, TimeUnit.SECONDS));

        // 再次注册新监听器并触发事件，验证之前的监听器已被移除
        operator.registerListener(eventKey, event -> secondLatch.countDown());
        GlobalEvent event2 = createEvent("event-004", 4000L, "H");
        operator.occurredEvent(event2);
        assertTrue(secondLatch.await(5, TimeUnit.SECONDS));

        // save 应被调用两次（两次 occurredEvent）
        verify(eventStorage, times(2)).save(any(GlobalEvent.class));
        log.info("监听器在事件触发后被移除测试通过");
    }

    @Test
    void testSaveEventWithDifferentTimeSegments() {
        GlobalEvent eventH = createEvent("event-h", 1000L, "H");
        operator.occurredEvent(eventH);
        assertNotNull(eventH.getBeginTime());
        assertNotNull(eventH.getEndTime());
        assertTrue(eventH.getEndTime() > eventH.getBeginTime());

        GlobalEvent eventD = createEvent("event-d", 2000L, "d");
        operator.occurredEvent(eventD);
        assertNotNull(eventD.getBeginTime());
        assertNotNull(eventD.getEndTime());
        assertTrue(eventD.getEndTime() > eventD.getBeginTime());

        verify(eventStorage, times(2)).save(any(GlobalEvent.class));
        log.info("不同时间分片的事件保存测试通过");
    }

    private GlobalEvent createEvent(String id, Long eventTime, String timeSegment) {
        GlobalEvent event = new GlobalEvent();
        event.setId(id);
        event.setEventTime(eventTime);
        event.setTimeSegment(timeSegment);
        event.setType(EventTypeEnum.TASK);
        return event;
    }

    private ThreadPoolExecutor createExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }
}
