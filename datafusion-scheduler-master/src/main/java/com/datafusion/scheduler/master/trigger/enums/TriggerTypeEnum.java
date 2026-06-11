package com.datafusion.scheduler.master.trigger.enums;

/**
 * 调度类型枚举.
 *
 * @author lanvendar
 * @version 3.0 2022/5/6
 * @since 2022/5/6
 */
public enum TriggerTypeEnum {
    /**
     * cron表达式.
     */
    CRON,
    /**
     * 时间间隔类型:单位/毫秒.
     */
    INTERVAL;

    /**
     * 根据int值转换成枚举.
     *
     * @param i int值
     * @return 枚举值
     */
    public static TriggerTypeEnum valueOf(int i) {
        TriggerTypeEnum[] policies = TriggerTypeEnum.values();
        if (i > policies.length - 1 || i < 0) {
            return INTERVAL;
        } else {
            return policies[i];
        }
    }
}
