package com.datafusion.common.variable.builtin;

import com.datafusion.common.variable.VariableUtils;
import com.datafusion.scheduler.model.Variable;

import java.util.Map;

/**
 * 内置变量解析器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class BuiltinVariableResolver {

    /**
     * 内置时间变量求值工具.
     */
    private final BuiltinTimeParams timeParams = new BuiltinTimeParams();

    /**
     * 解析内置变量.
     *
     * @param context 变量渲染上下文
     */
    public void resolveBuiltinVariables(VariableRenderContext context) {
        if (context == null || context.getVariables() == null) {
            return;
        }

        Map<String, Variable> variables = context.getVariables();
        Long nowTime = System.currentTimeMillis();
        putVariable(variables, BuiltinVariableEnum.NOW_TIME, String.valueOf(nowTime));
        putVariable(variables, BuiltinVariableEnum.NOW_DATE, timeParams.formatTime(nowTime));

        Long scheduleTime = timeParams.scheduleTime(variables, context.getScheduleTime());
        if (scheduleTime != null) {
            putVariable(variables, BuiltinVariableEnum.SCHEDULE_TIME, String.valueOf(scheduleTime));
        }

        String bizAlign = timeParams.bizAlign(variables);
        putVariable(variables, BuiltinVariableEnum.BIZ_ALIGN, bizAlign);

        String eventAlign = timeParams.eventAlign(variables);
        putVariable(variables, BuiltinVariableEnum.EVENT_ALIGN, eventAlign);

        if (scheduleTime != null) {
            Long bizTime = timeParams.alignTime(scheduleTime, bizAlign);
            putVariable(variables, BuiltinVariableEnum.BIZ_TIME, String.valueOf(bizTime));
            putVariable(variables, BuiltinVariableEnum.BIZ_DATE, timeParams.formatTime(bizTime));

            Long eventTime = timeParams.alignTime(scheduleTime, eventAlign);
            putVariable(variables, BuiltinVariableEnum.EVENT_TIME, String.valueOf(eventTime));
            putVariable(variables, BuiltinVariableEnum.EVENT_DATE, timeParams.formatTime(eventTime));
        }
    }

    /**
     * 写入变量.
     *
     * @param variables 变量映射
     * @param variable  内置变量
     * @param value     变量值
     */
    private void putVariable(Map<String, Variable> variables, BuiltinVariableEnum variable, String value) {
        if (value == null) {
            return;
        }
        variables.put(variable.getParamName(), VariableUtils.createVariable(variable.getParamName(), value));
    }
}
