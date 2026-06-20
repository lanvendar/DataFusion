package com.datafusion.common.variable;

/**
 * 变量环境预处理接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public interface VariableResolver {

    /**
     * 解析并补充变量环境.
     *
     * @param context 变量渲染上下文
     */
    void resolve(VariableRenderContext context);
}
