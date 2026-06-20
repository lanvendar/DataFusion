package com.datafusion.common.variable.engine;

import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.PlaceholderToken;
import com.datafusion.common.variable.PlaceholderTokenType;
import com.datafusion.common.variable.VariableUtils;

/**
 * 变量渲染引擎.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class VariableRenderEngine implements PlaceholderEngine {

    @Override
    public String name() {
        return "variable";
    }

    @Override
    public boolean supports(PlaceholderToken token) {
        return token != null && token.getType() == PlaceholderTokenType.VARIABLE;
    }

    @Override
    public String render(PlaceholderToken token, VariableRenderContext context) {
        if (token == null || context == null || context.getVariables() == null) {
            return token == null ? null : token.getRawText();
        }
        String value = VariableUtils.value(context.getVariables().get(token.getName()));
        return value == null ? token.getRawText() : value;
    }
}
