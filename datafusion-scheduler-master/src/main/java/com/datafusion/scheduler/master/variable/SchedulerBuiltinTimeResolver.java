package com.datafusion.scheduler.master.variable;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.date.DateTimeStamp;
import com.datafusion.common.enums.TimeAlignmentEnum;
import com.datafusion.common.variable.function.AbstractTimeVariableFunction;
import com.datafusion.scheduler.model.Variable;

import java.util.Date;
import java.util.Map;

/**
 * 调度内置时间变量求值器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class SchedulerBuiltinTimeResolver {

    /**
     * 默认时间对齐格式.
     */
    public static final String DEFAULT_ALIGN = TimeAlignmentEnum.ORIGINAL.getCode();

    /**
     * 解析调度时间.
     *
     * @param variables            变量映射
     * @param fallbackScheduleTime 兜底调度时间
     * @return 调度时间
     */
    public Long scheduleTime(Map<String, Variable> variables, Long fallbackScheduleTime) {
        if (fallbackScheduleTime != null) {
            return fallbackScheduleTime;
        }
        return parseLong(variableValue(variables, SchedulerBuiltinVariableEnum.SCHEDULE_TIME));
    }

    /**
     * 解析业务时间对齐格式.
     *
     * @param variables 变量映射
     * @return 业务时间对齐格式
     */
    public String bizAlign(Map<String, Variable> variables) {
        return alignOrDefault(variableValue(variables, SchedulerBuiltinVariableEnum.BIZ_ALIGN));
    }

    /**
     * 解析事件时间对齐格式.
     *
     * @param variables 变量映射
     * @return 事件时间对齐格式
     */
    public String eventAlign(Map<String, Variable> variables) {
        return alignOrDefault(variableValue(variables, SchedulerBuiltinVariableEnum.EVENT_ALIGN));
    }

    /**
     * 解析业务时间.
     *
     * @param variables    变量映射
     * @param scheduleTime 调度时间
     * @return 业务时间
     */
    public Long bizTime(Map<String, Variable> variables, Long scheduleTime) {
        return alignTime(scheduleTime, bizAlign(variables));
    }

    /**
     * 解析事件时间.
     *
     * @param variables    变量映射
     * @param scheduleTime 调度时间
     * @return 事件时间
     */
    public Long eventTime(Map<String, Variable> variables, Long scheduleTime) {
        return alignTime(scheduleTime, eventAlign(variables));
    }

    /**
     * 按对齐格式计算时间.
     *
     * @param timestamp 原始时间戳
     * @param align     对齐格式
     * @return 对齐后时间戳
     */
    public Long alignTime(Long timestamp, String align) {
        if (timestamp == null) {
            return null;
        }
        String resolvedAlign = alignOrDefault(align);
        if (TimeAlignmentEnum.ORIGINAL.getCode().equals(resolvedAlign)) {
            return timestamp;
        }
        return DateTimeStamp.getTime(timestamp, resolvedAlign);
    }

    /**
     * 格式化时间.
     *
     * @param timestamp 时间戳
     * @return 格式化后的时间
     */
    public String formatTime(Long timestamp) {
        if (timestamp == null) {
            return null;
        }
        return DateCalUtil.format(new Date(timestamp), AbstractTimeVariableFunction.DEFAULT_PATTERN);
    }

    /**
     * 解析长整型.
     *
     * @param value 文本值
     * @return 长整型
     */
    public Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取变量值.
     *
     * @param variables 变量映射
     * @param variable  内置变量
     * @return 变量值
     */
    private String variableValue(Map<String, Variable> variables, SchedulerBuiltinVariableEnum variable) {
        if (variables == null) {
            return null;
        }
        Variable target = variables.get(variable.getParamName());
        return target == null ? null : target.getValue();
    }

    /**
     * 解析对齐格式.
     *
     * @param align 对齐格式
     * @return 对齐格式
     */
    private String alignOrDefault(String align) {
        if (align == null || align.trim().isEmpty()) {
            return DEFAULT_ALIGN;
        }
        String resolvedAlign = align.trim();
        if (TimeAlignmentEnum.getByCode(resolvedAlign) == null) {
            throw new IllegalArgumentException("Invalid time alignment: " + align);
        }
        return resolvedAlign;
    }
}
