package com.datafusion.common.variable;

import com.datafusion.common.variable.engine.AviatorExpressionEngine;
import com.datafusion.common.variable.engine.BuiltinFunctionEngine;
import com.datafusion.common.variable.engine.PlaceholderEngine;
import com.datafusion.common.variable.engine.VariableRenderEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认变量渲染器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class DefaultVariableRenderer implements VariableRenderer {

    /**
     * tokenizer.
     */
    private final PlaceholderTokenizer tokenizer = new PlaceholderTokenizer();

    /**
     * 引擎列表.
     */
    private final List<PlaceholderEngine> engines = new ArrayList<>();

    /**
     * 构造函数.
     */
    public DefaultVariableRenderer() {
        engines.add(new VariableRenderEngine());
        engines.add(new BuiltinFunctionEngine());
        engines.add(new AviatorExpressionEngine());
    }

    @Override
    public String render(String value, VariableRenderContext context) {
        if (value == null || value.isEmpty()) {
            return value;
        }
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
     * @param token   token
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
