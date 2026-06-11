package com.datafusion.scheduler.master.trigger.core;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 触发器类,实现延迟队列.
 *
 * @param <T> 触发的payload
 * @author 李正凯
 * @version 3.0 2022/5/18
 * @since 2022/4/24
 */
@Slf4j
@Data
public class TriggerWrapperSet<T> implements Delayed {

    /**
     * 触发时间.
     */
    private long triggerTimeMs;

    /**
     * 调度对象集合.
     */

    private Set<TriggerWrapper<T>> targetSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(triggerTimeMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        long d = this.getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
        return (d == 0) ? 0 : ((d > 0) ? 1 : -1);
    }

    /**
     * 添加调度对象.
     *
     * @param t 调度对象
     */
    void addTarget(TriggerWrapper<T> t) {
        if (!targetSet.add(t)) {
            log.warn("调度对象[{}]已存在set中", t);
        }
    }
}
