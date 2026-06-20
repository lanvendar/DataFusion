package com.datafusion.scheduler.master.variable;

import com.datafusion.common.variable.VariableRenderContext;
import com.datafusion.common.variable.VariableResolver;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 调度变量解析器.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
@Slf4j
public class SchedulerVariableResolver implements VariableResolver {

    /**
     * 调度内置时间变量求值器.
     */
    private final SchedulerBuiltinTimeResolver timeResolver = new SchedulerBuiltinTimeResolver();

    /**
     * 解析内置变量并填充到 context.variables 中.
     *
     * @param context 占位符上下文
     */
    public void resolveBuiltinVariables(PlaceholderContext context) {
        if (context == null) {
            return;
        }
        resolve(context);
    }

    @Override
    public void resolve(VariableRenderContext context) {
        if (context == null || context.getVariables() == null) {
            return;
        }

        Map<String, Variable> variables = context.getVariables();
        Long nowTime = System.currentTimeMillis();
        putVariable(variables, SchedulerBuiltinVariableEnum.NOW_TIME, String.valueOf(nowTime));
        putVariable(variables, SchedulerBuiltinVariableEnum.NOW_DATE, timeResolver.formatTime(nowTime));

        Long scheduleTime = timeResolver.scheduleTime(variables, context.getDefaultTimeMillis());
        if (scheduleTime != null) {
            putVariable(variables, SchedulerBuiltinVariableEnum.SCHEDULE_TIME, String.valueOf(scheduleTime));
        }

        String bizAlign = timeResolver.bizAlign(variables);
        putVariable(variables, SchedulerBuiltinVariableEnum.BIZ_ALIGN, bizAlign);

        String eventAlign = timeResolver.eventAlign(variables);
        putVariable(variables, SchedulerBuiltinVariableEnum.EVENT_ALIGN, eventAlign);

        if (scheduleTime != null) {
            Long bizTime = timeResolver.bizTime(variables, scheduleTime);
            putVariable(variables, SchedulerBuiltinVariableEnum.BIZ_TIME, String.valueOf(bizTime));
            putVariable(variables, SchedulerBuiltinVariableEnum.BIZ_DATE, timeResolver.formatTime(bizTime));

            Long eventTime = timeResolver.eventTime(variables, scheduleTime);
            putVariable(variables, SchedulerBuiltinVariableEnum.EVENT_TIME, String.valueOf(eventTime));
            putVariable(variables, SchedulerBuiltinVariableEnum.EVENT_DATE, timeResolver.formatTime(eventTime));
        }

        log.debug("Scheduler variables resolved: {}", variables.keySet());
    }

    /**
     * 解析单个变量的值.
     *
     * @param paramName 参数名
     * @param context   上下文
     * @return 参数值
     */
    public String resolveVariable(String paramName, PlaceholderContext context) {
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
     * 写入变量.
     *
     * @param variables 变量映射
     * @param variable  内置变量
     * @param value     变量值
     */
    private void putVariable(Map<String, Variable> variables, SchedulerBuiltinVariableEnum variable, String value) {
        if (value == null) {
            return;
        }
        Variable target = new Variable();
        target.setName(variable.getParamName());
        target.setValue(value);
        variables.put(variable.getParamName(), target);
    }
}
