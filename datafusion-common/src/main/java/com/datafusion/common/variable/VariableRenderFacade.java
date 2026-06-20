package com.datafusion.common.variable;

import com.datafusion.common.variable.builtin.BuiltinVariableResolver;
import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.engine.AviatorExpressionEngine;
import com.datafusion.common.variable.engine.BuiltinTimeExpressionEngine;
import com.datafusion.common.variable.engine.PlaceholderEngine;
import com.datafusion.common.variable.engine.VariableRenderEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 变量渲染门面.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class VariableRenderFacade {

    /**
     * tokenizer.
     */
    private final PlaceholderTokenizer tokenizer = new PlaceholderTokenizer();

    /**
     * 内置变量解析器.
     */
    private final BuiltinVariableResolver builtinVariableResolver = new BuiltinVariableResolver();

    /**
     * 引擎列表.
     */
    private final List<PlaceholderEngine> engines = new ArrayList<>();

    /**
     * 构造函数.
     */
    public VariableRenderFacade() {
        engines.add(new VariableRenderEngine());
        engines.add(new BuiltinTimeExpressionEngine());
        engines.add(new AviatorExpressionEngine());
    }

    /**
     * 渲染文本.
     *
     * @param value   文本
     * @param context 渲染上下文
     * @return 渲染结果
     */
    public String render(String value, VariableRenderContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        builtinVariableResolver.resolveBuiltinVariables(context);
        List<PlaceholderToken> tokens = tokenizer.scan(value);
        if (tokens.isEmpty()) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (PlaceholderToken token : tokens) {
            result.append(value, cursor, token.getStartIndex());
            result.append(renderToken(token, context));
            cursor = token.getEndIndex();
        }
        result.append(value.substring(cursor));
        return result.toString();
    }

    /**
     * 渲染 token.
     *
     * @param token token
     * @param context 上下文
     * @return 渲染结果
     */
    private String renderToken(PlaceholderToken token, VariableRenderContext context) {
        for (PlaceholderEngine engine : engines) {
            if (engine.supports(token)) {
                return engine.render(token, context);
            }
        }
        throw new IllegalArgumentException("Unsupported placeholder token: " + token.getRawText());
    }
}
