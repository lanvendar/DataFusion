package com.datafusion.common.variable.function;

import com.datafusion.common.date.DateCalUtil;
import com.datafusion.common.variable.builtin.BuiltinVariableEnum;
import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.builtin.BuiltinTimeParams;
import com.datafusion.common.variable.VariableUtils;

import java.util.Date;
import java.util.List;

/**
 * 时间内置函数抽象类.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public abstract class AbstractTimeBuiltinFunc implements BuiltinFunc {

    /**
     * 时间求值工具.
     */
    protected final BuiltinTimeParams timeParams = new BuiltinTimeParams();

    /**
     * 获取参数.
     *
     * @param arguments 参数列表
     * @param index     参数下标
     * @return 参数值
     */
    protected String argument(List<String> arguments, int index) {
        if (index >= arguments.size()) {
            return null;
        }
        String value = arguments.get(index);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        return trimmed;
    }

    /**
     * 解析基础日期.
     *
     * @param base    基础参数
     * @param context 上下文
     * @return 基础日期
     */
    protected Date resolveBaseDate(String base, VariableRenderContext context) {
        if (base == null || base.trim().isEmpty()) {
            Long scheduleTime = context == null ? null : context.getScheduleTime();
            return scheduleTime == null ? null : new Date(scheduleTime);
        }
        String trimmedBase = base.trim();
        String value = resolveBaseValue(trimmedBase, context);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        BuiltinVariableEnum builtinVariable = BuiltinVariableEnum.getByParamName(trimmedBase);
        if (isTimestampVariable(builtinVariable) || isTimestampLiteral(value)) {
            Long baseTime = timeParams.parseLong(value);
            return baseTime == null ? null : new Date(baseTime);
        }
        return DateCalUtil.checkStringDate(value);
    }

    /**
     * 解析基础时间戳.
     *
     * @param base    基础参数
     * @param context 上下文
     * @return 基础时间戳
     */
    protected Long resolveBaseTime(String base, VariableRenderContext context) {
        if (base == null || base.trim().isEmpty()) {
            return context == null ? null : context.getScheduleTime();
        }
        String value = resolveBaseValue(base, context);
        Long parsed = timeParams.parseLong(value);
        if (parsed != null) {
            return parsed;
        }
        return null;
    }

    /**
     * 解析基础参数值.
     *
     * @param base    基础参数
     * @param context 上下文
     * @return 基础参数值
     */
    private String resolveBaseValue(String base, VariableRenderContext context) {
        if (base == null || base.trim().isEmpty()) {
            return context == null || context.getScheduleTime() == null ? null : String.valueOf(context.getScheduleTime());
        }
        String trimmed = base.trim();
        if (context != null && context.getVariables() != null) {
            String value = VariableUtils.value(context.getVariables().get(trimmed));
            if (value != null) {
                return value;
            }
        }
        return trimmed;
    }

    /**
     * 判断是否是时间戳变量.
     *
     * @param builtinVariable 内置变量
     * @return 是否是时间戳变量
     */
    private boolean isTimestampVariable(BuiltinVariableEnum builtinVariable) {
        return BuiltinVariableEnum.NOW_TIME == builtinVariable
                || BuiltinVariableEnum.SCHEDULE_TIME == builtinVariable
                || BuiltinVariableEnum.BIZ_TIME == builtinVariable
                || BuiltinVariableEnum.EVENT_TIME == builtinVariable;
    }

    /**
     * 判断是否是毫秒时间戳字面量.
     *
     * @param value 文本值
     * @return 是否是毫秒时间戳字面量
     */
    private boolean isTimestampLiteral(String value) {
        return value != null && value.trim().matches("^\\d{13}$");
    }
}
