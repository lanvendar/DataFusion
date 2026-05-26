package com.datafusion.scheduler.master.event.storage;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * CachedEventStorage 单元测试.
 */
@ExtendWith(MockitoExtension.class)
public class CachedEventStorageTest {

    private static final Logger log = LoggerFactory.getLogger(CachedEventStorageTest.class);

    @Mock
    private EventStorage delegateStorage;

    private CachedEventStorage cachedStorage;

    @BeforeEach
    void setUp() {
        cachedStorage = new CachedEventStorage(delegateStorage, new Options());
    }

    @Test
    void testSaveDelegatesToUnderlyingStorage() {
        GlobalEvent event = createEvent("event-001", 1000L);
        cachedStorage.save(event);
        verify(delegateStorage).save(event);
        log.info("save 委托给底层存储测试通过");
    }

    @Test
    void testLoadByEventIdDelegatesToUnderlyingStorage() {
        GlobalEvent event = createEvent("event-001", 1000L);
        List<GlobalEvent> events = Arrays.asList(event);
        when(delegateStorage.loadByEventId("event-001", 7000L, 100)).thenReturn(events);

        List<GlobalEvent> result = cachedStorage.loadByEventId("event-001", 7000L, 100);
        assertEquals(1, result.size());
        assertEquals("event-001", result.get(0).getId());
        verify(delegateStorage).loadByEventId("event-001", 7000L, 100);
        log.info("loadByEventId 委托给底层存储测试通过");
    }

    @Test
    void testLoadByEventKeyUsesCache() {
        GlobalEvent event = createEvent("event-002", 2000L);
        when(delegateStorage.loadByEventKey("event-002", 2000L)).thenReturn(event);

        // 第一次调用，触发缓存加载
        GlobalEvent result1 = cachedStorage.loadByEventKey("event-002", 2000L);
        assertNotNull(result1);
        assertEquals("event-002", result1.getId());

        // 第二次调用，应命中缓存
        GlobalEvent result2 = cachedStorage.loadByEventKey("event-002", 2000L);
        assertNotNull(result2);

        // 底层存储只被调用一次（第二次命中缓存）
        verify(delegateStorage, times(1)).loadByEventKey("event-002", 2000L);
        log.info("loadByEventKey 缓存命中测试通过");
    }

    @Test
    void testDefaultConstructor() {
        CachedEventStorage defaultStorage = new CachedEventStorage();
        assertNotNull(defaultStorage);
        log.info("默认构造函数测试通过");
    }

    private GlobalEvent createEvent(String id, Long eventTime) {
        GlobalEvent event = new GlobalEvent();
        event.setId(id);
        event.setEventTime(eventTime);
        event.setType(EventTypeEnum.TASK);
        return event;
    }
}
