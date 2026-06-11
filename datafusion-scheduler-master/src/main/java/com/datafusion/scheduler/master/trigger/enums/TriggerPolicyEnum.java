package com.datafusion.scheduler.master.trigger.enums;

/**
 * 调度策略枚举.
 *
 * @author lanvendar
 * @version 3.0 2022/5/6
 * @since 2022/5/6
 */
public enum TriggerPolicyEnum {

    /**
     * 执行一次.
     */
    EXECUTE_ONCE,
    /**
     * 顺序执行.
     */
    SERIAL_WAIT,
    /**
     * 重复执行.
     */
    PARALLEL,
    /**
     * 丢弃最新.
     */
    DISCARD_NEW,
    /**
     * 覆盖执行.
     */
    DISCARD_OLD;

    /**
     * 根据int值转换成枚举.
     *
     * @param i int值
     * @return 枚举值
     */
    public static TriggerPolicyEnum valueOf(int i) {
        TriggerPolicyEnum[] policies = TriggerPolicyEnum.values();
        if (i > policies.length - 1 || i < 0) {
            return EXECUTE_ONCE;
        } else {
            return policies[i];
        }
    }
}
