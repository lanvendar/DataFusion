package com.datafusion.scheduler.master.event.enmus;

import lombok.Getter;

/**
 * 事件类型枚举.
 * 说明：1:task;2:flow.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/9/13
 * @since 2024/9/13
 */
@Getter
public enum EventTypeEnum {
    /**
     * 任务事件.
     */
    TASK(1),
    /**
     * 流程事件.
     */
    FLOW(2);

    /**
     * 类型枚举值.
     */
    private final int type;

    /**
     * 构造函数.
     *
     * @param type 类型枚举值
     */
    EventTypeEnum(int type) {
        this.type = type;
    }
}
