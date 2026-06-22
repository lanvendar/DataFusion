package com.datafusion.scheduler.master.variable;

/**
 * 调度内置变量枚举.
 *
 * <p>
 * 统一管理 Scheduler 运行期内置变量：
 * <ul>
 *   <li>paramKeyCode：系统变量编码，也是表达式中使用的稳定渲染标识，如 _now_time_</li>
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
    NOW_TIME("_now_time_"),

    /**
     * 当前日期（yyyyMMddHHmmss）.
     */
    NOW_DATE("_now_date_"),

    /**
     * 原始调度时间（毫秒）.
     */
    SCHEDULE_TIME("_schedule_time_"),

    /**
     * 业务时间对齐格式.
     */
    BIZ_ALIGN("_biz_align_"),

    /**
     * 业务时间（毫秒）.
     */
    BIZ_TIME("_biz_time_"),

    /**
     * 业务日期（yyyyMMddHHmmss）.
     */
    BIZ_DATE("_biz_date_"),

    /**
     * 事件时间对齐格式.
     */
    EVENT_ALIGN("_event_align_"),

    /**
     * 事件时间（毫秒）.
     */
    EVENT_TIME("_event_time_"),

    /**
     * 事件日期（yyyyMMddHHmmss）.
     */
    EVENT_DATE("_event_date_");

    /**
     * 内置变量编码.
     */
    private final String paramKeyCode;

    SchedulerBuiltinVariableEnum(String paramKeyCode) {
        this.paramKeyCode = paramKeyCode;
    }

    /**
     * 获取内置变量编码.
     *
     * @return 内置变量编码
     */
    public String getParamKeyCode() {
        return paramKeyCode;
    }

    /**
     * 根据参数编码获取枚举.
     *
     * @param paramKeyCode 参数编码
     * @return 枚举值
     */
    public static SchedulerBuiltinVariableEnum getByParamKeyCode(String paramKeyCode) {
        if (paramKeyCode == null) {
            return null;
        }
        for (SchedulerBuiltinVariableEnum variable : values()) {
            if (variable.getParamKeyCode().equals(paramKeyCode)) {
                return variable;
            }
        }
        return null;
    }
}
