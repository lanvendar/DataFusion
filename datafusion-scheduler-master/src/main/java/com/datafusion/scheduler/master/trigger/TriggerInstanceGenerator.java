package com.datafusion.scheduler.master.trigger;

import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;

import java.util.Collections;
import java.util.List;

/**
 * 调度实例生成器接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/25
 * @since 2024/12/25
 */
public interface TriggerInstanceGenerator {
    /**
     * 生成调度实例.
     *
     * @param schedule 调度信息
     * @param baseTime 调度时间
     * @param included 是否包含调度时间
     */
    void generateInstance(TriggerInfo schedule, long baseTime, boolean included);

    /**
     * 添加调度实例.
     *
     * @param instance 调度实例
     */
    default void addCache(TriggerInstance instance) {
    }

    /**
     * 从缓存中获取调度实例集合.
     *
     * @return 调度实例列表
     */
    default List<TriggerInstance> fetchCache() {
        return Collections.emptyList();
    }

    /**
     * 添加调度实例到延迟队列.
     *
     * @param instance 调度实例
     */
    void enqueue(TriggerInstance instance);

    /**
     * 从延迟队列中取出调度实例.
     *
     * @return 调度实例列表
     */
    List<TriggerInstance> dequeue();
}
