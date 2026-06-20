package com.datafusion.scheduler.master.variable;

import com.datafusion.common.variable.DefaultVariableRenderer;
import com.datafusion.common.variable.VariableRenderer;
import lombok.extern.slf4j.Slf4j;

/**
 * 调度变量处理门面类.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class SchedulerVariableFacade {

    /**
     * 调度变量解析器.
     */
    private final SchedulerVariableResolver variableResolver = new SchedulerVariableResolver();

    /**
     * 通用变量渲染器.
     */
    private final VariableRenderer variableRenderer = new DefaultVariableRenderer();

    /**
     * 单例实例.
     */
    private static final SchedulerVariableFacade INSTANCE = new SchedulerVariableFacade();

    /**
     * 私有构造函数.
     */
    private SchedulerVariableFacade() {
    }

    /**
     * 获取单例实例.
     *
     * @return SchedulerVariableFacade 实例
     */
    public static SchedulerVariableFacade getInstance() {
        return INSTANCE;
    }

    /**
     * 替换字符串中的所有占位符.
     *
     * @param value   包含占位符的字符串
     * @param context 占位符处理上下文
     * @return 替换后的字符串
     */
    public String replacePlaceholders(String value, PlaceholderContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        variableResolver.resolveBuiltinVariables(context);
        return variableRenderer.render(value, context);
    }
}
