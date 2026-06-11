package com.datafusion.scheduler.master.trigger.core;

import lombok.Data;

import java.util.Objects;

/**
 * 触发目标泛型类.
 *
 * @param <T> 触发器的实体对象
 * @author 李正凯
 * @version 3.0 2022/5/18
 * @since 2022/5/10
 */
@Data
public class TriggerWrapper<T> {

    /**
     * 预计触发时间.
     */
    private long triggerTime;

    /**
     * 触发实体,包含调度器,流程,任务等实体.
     */
    private T payload;

    /**
     * 取消标志,也是队列移除标志.
     */
    private volatile boolean cancelled;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TriggerWrapper<?> that = (TriggerWrapper<?>) o;
        return triggerTime == that.triggerTime && payload.equals(that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(triggerTime, payload);
    }
}
