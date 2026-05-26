package com.datafusion.scheduler.master.trigger.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TriggerDelayQueue 单元测试.
 */
class TriggerDelayQueueTest {

    private TriggerDelayQueue<String> queue;

    @BeforeEach
    void setUp() {
        queue = new TriggerDelayQueue<>();
    }

    @Test
    void testEnqueueAndDequeueWithTimeout() {
        // 入队一个已过期的元素（triggerTime 在过去）
        long pastTime = System.currentTimeMillis() - 1000;
        queue.enqueue("task-1", pastTime);

        List<String> result = queue.dequeue(2000, TimeUnit.MILLISECONDS);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("task-1", result.get(0));
    }

    @Test
    void testDequeueTimeoutReturnsNull() {
        // 空队列，超时应返回null
        List<String> result = queue.dequeue(100, TimeUnit.MILLISECONDS);
        assertNull(result);
    }

    @Test
    void testEnqueueMultipleWithSameTriggerTime() {
        long pastTime = System.currentTimeMillis() - 1000;
        queue.enqueue("task-1", pastTime);
        queue.enqueue("task-2", pastTime);

        List<String> result = queue.dequeue(2000, TimeUnit.MILLISECONDS);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("task-1"));
        assertTrue(result.contains("task-2"));
    }

    @Test
    void testRemoveCancelsWrapper() {
        long futureTime = System.currentTimeMillis() + 60000;
        queue.enqueue("task-to-remove", futureTime);

        List<TriggerWrapper<String>> removed = queue.remove("task-to-remove");
        assertNotNull(removed);
        assertEquals(1, removed.size());
        assertTrue(removed.get(0).isCancelled());
    }

    @Test
    void testRemoveNonExistent() {
        List<TriggerWrapper<String>> removed = queue.remove("non-existent");
        assertNull(removed);
    }

    @Test
    void testTryDrainWithExpiredElements() {
        long pastTime = System.currentTimeMillis() - 1000;
        queue.enqueue("task-1", pastTime);
        queue.enqueue("task-2", pastTime);

        // 等一小段时间确保元素过期
        List<String> result = queue.tryDrain();
        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testTryDrainEmpty() {
        List<String> result = queue.tryDrain();
        assertNull(result);
    }

    @Test
    void testCancelledElementsFilteredOnDequeue() {
        long pastTime = System.currentTimeMillis() - 1000;
        queue.enqueue("task-keep", pastTime);
        queue.enqueue("task-cancel", pastTime);

        // 取消一个
        queue.remove("task-cancel");

        List<String> result = queue.dequeue(2000, TimeUnit.MILLISECONDS);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("task-keep", result.get(0));
    }
}
