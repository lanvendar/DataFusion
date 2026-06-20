package com.datafusion.common.variable.engine;

import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.PlaceholderToken;
import com.datafusion.common.variable.PlaceholderTokenType;

/**
 * Aviator 表达式引擎占位.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class AviatorExpressionEngine implements PlaceholderEngine {

    @Override
    public String name() {
        return "aviator";
    }

    @Override
    public boolean supports(PlaceholderToken token) {
        return token != null && token.getType() == PlaceholderTokenType.FUNCTION
                && "expr".equalsIgnoreCase(token.getName());
    }

    @Override
    public String render(PlaceholderToken token, VariableRenderContext context) {
        throw new UnsupportedOperationException("Aviator expression engine is not implemented yet.");
    }
}
