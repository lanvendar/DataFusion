package com.datafusion.scheduler.master.param;

import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.VariableRenderFacade;
import com.datafusion.scheduler.master.param.builtin.BuiltinParamResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * 占位符处理门面类.
 * 统一处理变量和表达式占位符，按照变量优先、表达式在后的顺序处理.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/21
 * @since 2022/6/21
 */
@Slf4j
public class PlaceholderFacade {

    /**
     * 内置参数解析器.
     */
    private final BuiltinParamResolver builtinParamResolver = new BuiltinParamResolver();

    /**
     * 通用变量渲染门面.
     */
    private final VariableRenderFacade variableRenderFacade = new VariableRenderFacade();

    /**
     * 单例实例.
     */
    private static final PlaceholderFacade INSTANCE = new PlaceholderFacade();

    /**
     * 私有构造函数，初始化处理器列表.
     */
    private PlaceholderFacade() {
    }

    /**
     * 获取单例实例.
     *
     * @return PlaceholderFacade实例
     */
    public static PlaceholderFacade getInstance() {
        return INSTANCE;
    }

    /**
     * 替换字符串中的所有占位符.
     * 处理顺序：
     * 1. 解析内置参数并填充到 context.variables
     * 2. 处理新语法 #(变量名) / #函数名(...)
     *
     * @param value   包含占位符的字符串
     * @param context 占位符处理上下文
     * @return 替换后的字符串
     */
    public String replacePlaceholders(String value, PlaceholderContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Step 1: 解析内置参数
        builtinParamResolver.resolveBuiltinParams(context);

        // Step 2: 处理新语法 #(变量名) / #函数名(...)
        return variableRenderFacade.render(value, toVariableRenderContext(context));
    }

    /**
     * 转换为通用变量渲染上下文.
     *
     * @param context scheduler 占位符上下文
     * @return 通用变量渲染上下文
     */
    private VariableRenderContext toVariableRenderContext(PlaceholderContext context) {
        return VariableRenderContext.builder()
                .scheduleTime(context == null ? null : context.getScheduleTime())
                .variables(context == null ? null : context.getVariables())
                .build();
    }

}
