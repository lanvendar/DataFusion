package com.datafusion.scheduler.master;

import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;

/**
 * actor的类型分类.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/11
 * @since 2026/2/11
 */
public enum ActorType implements ActorTypeEnum {
    /**
     * 流程.
     */
    FLOW {
        @Override
        public String get() {
            return "FLOW";
        }
    },
    /**
     * 任务.
     */
    TASK {
        @Override
        public String get() {
            return "TASK";
        }
    },
    /**
     * 事件.
     */
    EVENT {
        @Override
        public String get() {
            return "EVENT";
        }
    };

    /**
     * 判断两个类型是否相同.
     *
     * @param a : 类型A
     * @param b : 类型B
     * @return 是否相同
     */
    public static boolean same(ActorType a, ActorType b) {
        return a.equals(b);
    }
}
