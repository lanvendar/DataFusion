package com.datafusion.scheduler.master.variable;

/**
 * 调度内置变量枚举.
 *
 * <p>
 * 统一管理 Scheduler 运行期内置变量：
 * <ul>
 *   <li>paramKey：系统变量目录编码，如 _now_time_</li>
 *   <li>paramName：表达式中使用的变量名，如 now_time</li>
 * </ul>
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public enum SchedulerBuiltinVariableEnum {

    /**
     * 当前时间（毫秒）.
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
     * 内置变量编码.
     */
    private final String paramKey;

    /**
     * 表达式变量名.
     */
    private final String paramName;

    SchedulerBuiltinVariableEnum(String paramKey, String paramName) {
        this.paramKey = paramKey;
        this.paramName = paramName;
    }

    /**
     * 获取内置变量编码.
     *
     * @return 内置变量编码
     */
    public String getParamKey() {
        return paramKey;
    }

    /**
     * 获取表达式变量名.
     *
     * @return 表达式变量名
     */
    public String getParamName() {
        return paramName;
    }

    /**
     * 根据参数键获取枚举.
     *
     * @param paramKey 参数键
     * @return 枚举值
     */
    public static SchedulerBuiltinVariableEnum getByParamKey(String paramKey) {
        if (paramKey == null) {
            return null;
        }
        for (SchedulerBuiltinVariableEnum variable : values()) {
            if (variable.getParamKey().equals(paramKey)) {
                return variable;
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
    public static SchedulerBuiltinVariableEnum getByParamName(String paramName) {
        if (paramName == null) {
            return null;
        }
        for (SchedulerBuiltinVariableEnum variable : values()) {
            if (variable.getParamName().equals(paramName)) {
                return variable;
            }
        }
        return null;
    }
}
