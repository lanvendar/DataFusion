package com.datafusion.common.variable.function;

/**
 * 变量函数注册表.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public interface VariableFunctionRegistry {

    /**
     * 注册函数.
     *
     * @param function 函数
     */
    void register(VariableFunction function);

    /**
     * 获取函数.
     *
     * @param name 函数名
     * @return 函数
     */
    VariableFunction getFunction(String name);
}
