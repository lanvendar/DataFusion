package com.datafusion.scheduler.master.param.builtin;

import com.datafusion.common.variable.builtin.BuiltinVariableResolver;
import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.scheduler.master.param.PlaceholderContext;
import com.datafusion.scheduler.model.Variable;
import lombok.extern.slf4j.Slf4j;

/**
 * 内置参数解析器.
 *
 * <p>
 * Scheduler 侧仅做调度变量模型适配，通用内置变量求值逻辑位于 datafusion-common.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/11/8
 * @since 2024/11/8
 */
@Slf4j
public class BuiltinParamResolver {

    /**
     * 通用内置变量解析器.
     */
    private final BuiltinVariableResolver builtinVariableResolver = new BuiltinVariableResolver();

    /**
     * 解析内置参数并填充到 context.variables 中.
     *
     * @param context 占位符上下文
     */
    public void resolveBuiltinParams(PlaceholderContext context) {
        if (context == null || context.getVariables() == null) {
            return;
        }

        VariableRenderContext renderContext = new VariableRenderContext();
        renderContext.setScheduleTime(context.getScheduleTime());
        renderContext.setVariables(context.getVariables());
        builtinVariableResolver.resolveBuiltinVariables(renderContext);

        log.debug("Built-in parameters resolved: {}", context.getVariables().keySet());
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
}
