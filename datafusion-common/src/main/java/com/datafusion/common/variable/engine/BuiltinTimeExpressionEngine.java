package com.datafusion.common.variable.engine;

import com.datafusion.common.variable.builtin.VariableRenderContext;
import com.datafusion.common.variable.function.BuiltinFunc;
import com.datafusion.common.variable.function.BuiltinFuncFactory;
import com.datafusion.common.variable.FunctionArgumentParser;
import com.datafusion.common.variable.PlaceholderToken;
import com.datafusion.common.variable.PlaceholderTokenType;

import java.util.List;

/**
 * 内置时间函数引擎.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class BuiltinTimeExpressionEngine implements PlaceholderEngine {

    /**
     * 参数解析器.
     */
    private final FunctionArgumentParser argumentParser = new FunctionArgumentParser();

    /**
     * 内置函数工厂.
     */
    private final BuiltinFuncFactory builtinFuncFactory = BuiltinFuncFactory.defaultFactory();

    @Override
    public String name() {
        return "builtin-time";
    }

    @Override
    public boolean supports(PlaceholderToken token) {
        return token != null && token.getType() == PlaceholderTokenType.FUNCTION
                && builtinFuncFactory.getBuiltinFunc(token.getName()) != null;
    }

    @Override
    public String render(PlaceholderToken token, VariableRenderContext context) {
        if (token == null) {
            return null;
        }
        List<String> arguments = argumentParser.parse(token.getArgumentsText());
        BuiltinFunc builtinFunc = builtinFuncFactory.getBuiltinFunc(token.getName());
        if (builtinFunc == null) {
            throw new IllegalArgumentException("Unsupported builtin function: " + token.getName());
        }
        return builtinFunc.call(arguments, context);
    }
}
