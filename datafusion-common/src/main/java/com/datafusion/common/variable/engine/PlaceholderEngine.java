package com.datafusion.common.variable.engine;

import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.PlaceholderToken;

/**
 * 占位符渲染引擎.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public interface PlaceholderEngine {

    /**
     * 引擎名称.
     *
     * @return 名称
     */
    String name();

    /**
     * 是否支持 token.
     *
     * @param token token
     * @return 是否支持
     */
    boolean supports(PlaceholderToken token);

    /**
     * 渲染 token.
     *
     * @param token token
     * @param context 上下文
     * @return 渲染结果
     */
    String render(PlaceholderToken token, VariableRenderContext context);
}
