package com.datafusion.scheduler.master.param.builtin;

/**
 * 调度内置参数枚举.
 *
 * <p>
 * 统一管理调度系统中的内置参数：
 * <ul>
 *   <li>参数键（paramKey）：调度上下文中的参数键(内置程序使用,可读可写)，如 _now_time_</li>
 *   <li>参数名（paramName）：表达式中使用的参数名内置参数名(用户使用,只读)，如 now_date</li>
 * </ul>
 *
 * <p>
 * 业务规则：
 * <ul>
 *   <li>now_time / now_date：返回当前系统时间</li>
 *   <li>schedule_time：返回当前流程实例的原始调度时间</li>
 *   <li>biz_time / biz_date：由 schedule_time 按 biz_align 派生</li>
 *   <li>event_time / event_date：由 schedule_time 按 event_align 派生，event_align 默认为 original</li>
 * </ul>
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
public enum BuiltinParamEnum {

    /**
     * 当前时间（毫秒）.
     * 格式：毫秒时间戳，如 1772012833904
     */
    NOW_TIME("_now_time_", "now_time"),

    /**
     * 当前日期（yyyyMMddHHmmss）.
     */
    NOW_DATE("_now_date_", "now_date"),

    /**
     * 原始调度时间（毫秒）.
     */
    SCHEDULE_TIME("_schedule_time_", "schedule_time"),

    /**
     * 业务时间对齐格式.
     * 枚举值：参见 TimeAlignmentEnum.
     */
    BIZ_ALIGN("_biz_align_", "biz_align"),

    /**
     * 业务时间（毫秒）.
     */
    BIZ_TIME("_biz_time_", "biz_time"),

    /**
     * 业务日期（yyyyMMddHHmmss）.
     */
    BIZ_DATE("_biz_date_", "biz_date"),

    /**
     * 事件时间对齐格式.
     * 枚举值：参见 TimeAlignmentEnum.
     */
    EVENT_ALIGN("_event_align_", "event_align"),

    /**
     * 事件时间（毫秒）.
     */
    EVENT_TIME("_event_time_", "event_time"),

    /**
     * 事件日期（yyyyMMddHHmmss）.
     */
    EVENT_DATE("_event_date_", "event_date");

    /**
     * 内置参数名(内置程序使用,可读可写).
     */
    private final String paramKey;

    /**
     * 内置参数名(用户使用,只读).
     */
    private final String paramName;

    BuiltinParamEnum(String paramKey, String paramName) {
        this.paramKey = paramKey;
        this.paramName = paramName;
    }

    public String getParamKey() {
        return paramKey;
    }

    public String getParamName() {
        return paramName;
    }

    /**
     * 根据参数键获取枚举.
     *
     * @param paramKey 参数键
     * @return 枚举值
     */
    public static BuiltinParamEnum getByParamKey(String paramKey) {
        if (paramKey == null) {
            return null;
        }
        for (BuiltinParamEnum param : values()) {
            if (param.getParamKey().equals(paramKey)) {
                return param;
            }
        }
        return null;
    }

    /**
     * 根据参数名获取枚举.
     *
     * @param paramName 参数名
     * @return 枚举值
     */
    public static BuiltinParamEnum getByParamName(String paramName) {
        if (paramName == null) {
            return null;
        }
        for (BuiltinParamEnum param : values()) {
            if (param.getParamName().equals(paramName)) {
                return param;
            }
        }
        return null;
    }
}
