package com.datafusion.common.variable.function;

import com.datafusion.common.variable.VariableRenderContext;

import java.util.List;

/**
 * 变量函数接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public interface VariableFunction {

    /**
     * 函数名.
     *
     * @return 函数名
     */
    String name();

    /**
     * 调用函数.
     *
     * @param arguments 参数列表
     * @param context   渲染上下文
     * @return 渲染结果
     */
    String call(List<String> arguments, VariableRenderContext context);
}
