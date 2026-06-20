package com.datafusion.common.variable;

/**
 * 变量渲染器接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public interface VariableRenderer {

    /**
     * 渲染文本.
     *
     * @param value   文本
     * @param context 变量渲染上下文
     * @return 渲染结果
     */
    String render(String value, VariableRenderContext context);
}
