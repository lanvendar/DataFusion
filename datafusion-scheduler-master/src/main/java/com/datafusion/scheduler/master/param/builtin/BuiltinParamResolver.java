package com.datafusion.scheduler.master.param.builtin;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.date.DateTimeStamp;
import com.datafusion.common.enums.TimeAlignmentEnum;
import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

/**
 * 内置参数解析器.
 *
 * <p>
 * 负责解析内置参数并填充到 variables 中:
 * <ul>
 *   <li>now_time   → 系统当前时间(毫秒)</li>
 *   <li>now_date   → 格式化后的当前时间</li>
 *   <li>schedule_time → 原始调度时间(毫秒)</li>
 *   <li>biz_align  → 业务时间对齐格式，默认为 original</li>
 *   <li>biz_time   → 业务时间，按 biz_align 计算</li>
 *   <li>biz_date   → 业务日期，按 biz_time 格式化</li>
 *   <li>event_align → 事件时间对齐格式，默认为 original</li>
 *   <li>event_time → 事件时间，按 event_align 计算</li>
 *   <li>event_date → 事件日期，按 event_time 格式化</li>
 * </ul>
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
@Slf4j
public class BuiltinParamResolver {

    /**
     * 默认日期格式.
     */
    private static final String DEFAULT_PATTERN = "yyyyMMddHHmmss";

    /**
     * 默认时间对齐格式.
     */
    private static final String DEFAULT_ALIGN = TimeAlignmentEnum.ORIGINAL.getCode();

    /**
     * 解析内置参数并填充到 context.variables 中.
     *
     * @param context 占位符上下文
     */
    public void resolveBuiltinParams(PlaceholderContext context) {
        if (context == null || context.getVariables() == null) {
            return;
        }

        Map<String, Variable> variables = context.getVariables();
        Long nowTime = System.currentTimeMillis();
        putVariable(variables, BuiltinParamEnum.NOW_TIME, String.valueOf(nowTime), true);
        putVariable(variables, BuiltinParamEnum.NOW_DATE, formatTime(nowTime, DEFAULT_PATTERN), true);

        Long scheduleTime = firstNotNull(context.getScheduleTime(),
                parseLong(variableValue(variables, BuiltinParamEnum.SCHEDULE_TIME)));
        if (scheduleTime != null) {
            putVariable(variables, BuiltinParamEnum.SCHEDULE_TIME, String.valueOf(scheduleTime), true);
        }

        String bizAlign = firstNotBlank(variableValue(variables, BuiltinParamEnum.BIZ_ALIGN), DEFAULT_ALIGN);
        putVariable(variables, BuiltinParamEnum.BIZ_ALIGN, bizAlign, false);

        String eventAlign = firstNotBlank(variableValue(variables, BuiltinParamEnum.EVENT_ALIGN), DEFAULT_ALIGN);
        putVariable(variables, BuiltinParamEnum.EVENT_ALIGN, eventAlign, false);

        if (scheduleTime != null) {
            Long bizTime = alignTime(scheduleTime, bizAlign);
            putVariable(variables, BuiltinParamEnum.BIZ_TIME, String.valueOf(bizTime), true);
            putVariable(variables, BuiltinParamEnum.BIZ_DATE, formatTime(bizTime, DEFAULT_PATTERN), true);

            Long eventTime = alignTime(scheduleTime, eventAlign);
            putVariable(variables, BuiltinParamEnum.EVENT_TIME, String.valueOf(eventTime), true);
            putVariable(variables, BuiltinParamEnum.EVENT_DATE, formatTime(eventTime, DEFAULT_PATTERN), true);
        }

        log.debug("Built-in parameters resolved: {}", variables.keySet());
    }

    /**
     * 解析单个内置参数的值.
     *
     * @param paramName 参数名
     * @param context   上下文
     * @return 参数值
     */
    public String resolveParam(String paramName, PlaceholderContext context) {
        if (paramName == null || context == null || context.getVariables() == null) {
            return null;
        }

        Variable variable = context.getVariables().get(paramName);
        if (variable != null && variable.getValue() != null) {
            return variable.getValue();
        }

        return null;
    }

    /**
     * 创建 Variable 对象.
     *
     * @param name  名称
     * @param value 值
     * @return Variable 对象
     */
    private Variable createVariable(String name, String value) {
        Variable var = new Variable();
        var.setName(name);
        var.setValue(value);
        return var;
    }

    /**
     * 写入内置变量.
     *
     * @param variables 变量映射
     * @param param     内置参数
     * @param value     参数值
     * @param overwrite 是否覆盖已有值
     */
    private void putVariable(Map<String, Variable> variables, BuiltinParamEnum param, String value, boolean overwrite) {
        if (value == null) {
            return;
        }
        Variable existing = variables.get(param.getParamName());
        if (!overwrite && existing != null && existing.getValue() != null && !existing.getValue().trim().isEmpty()) {
            return;
        }
        variables.put(param.getParamName(), createVariable(param.getParamName(), value));
    }

    /**
     * 获取变量值.
     *
     * @param variables 变量映射
     * @param param     内置参数
     * @return 变量值
     */
    private String variableValue(Map<String, Variable> variables, BuiltinParamEnum param) {
        Variable variable = variables.get(param.getParamName());
        return variable == null ? null : variable.getValue();
    }

    /**
     * 返回第一个非空值.
     *
     * @param values 候选值
     * @return 非空值
     */
    private Long firstNotNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 返回第一个非空文本.
     *
     * @param values 候选值
     * @return 非空文本
     */
    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 按对齐格式计算时间.
     *
     * @param timestamp 原始时间戳
     * @param align     对齐格式
     * @return 对齐后时间戳
     */
    private Long alignTime(Long timestamp, String align) {
        if (timestamp == null) {
            return null;
        }
        if (align == null || align.trim().isEmpty()) {
            return timestamp;
        }
        TimeAlignmentEnum alignment = TimeAlignmentEnum.getByCode(align);
        if (alignment == null) {
            return timestamp;
        }
        return DateTimeStamp.getTime(timestamp, align);
    }

    /**
     * 解析长整型.
     *
     * @param value 文本
     * @return 长整型
     */
    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Parse long failed: value={}", value, e);
            return null;
        }
    }

    /**
     * 格式化时间.
     *
     * @param timestamp 时间戳
     * @param pattern   格式
     * @return 格式化后的字符串
     */
    private String formatTime(Long timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        try {
            return DateCalUtil.format(new Date(timestamp), pattern);
        } catch (Exception e) {
            log.warn("Format time failed: timestamp={}, pattern={}", timestamp, pattern, e);
            return String.valueOf(timestamp);
        }
    }
}
