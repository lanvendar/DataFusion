package com.datafusion.common.variable.engine;

import com.datafusion.common.variable.PlaceholderToken;
import com.datafusion.common.variable.PlaceholderTokenType;
import com.datafusion.common.variable.VariableRenderContext;
import com.datafusion.common.variable.function.DefaultVariableFunctionRegistry;
import com.datafusion.common.variable.function.VariableFunction;
import com.datafusion.common.variable.function.VariableFunctionRegistry;

import java.util.List;

/**
 * 内置函数引擎.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class BuiltinFunctionEngine implements PlaceholderEngine {

    /**
     * 参数解析器.
     */
    private final FunctionArgumentParser argumentParser = new FunctionArgumentParser();

    /**
     * 函数注册表.
     */
    private final VariableFunctionRegistry functionRegistry;

    /**
     * 构造函数.
     */
    public BuiltinFunctionEngine() {
        this(DefaultVariableFunctionRegistry.defaultRegistry());
    }

    /**
     * 构造函数.
     *
     * @param functionRegistry 函数注册表
     */
    public BuiltinFunctionEngine(VariableFunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    @Override
    public String name() {
        return "builtin-function";
    }

    @Override
    public boolean supports(PlaceholderToken token) {
        return token != null && token.getType() == PlaceholderTokenType.FUNCTION
                && functionRegistry.getFunction(token.getName()) != null;
    }

    @Override
    public String render(PlaceholderToken token, VariableRenderContext context) {
        if (token == null) {
            return null;
        }
        List<String> arguments = argumentParser.parse(token.getArgumentsText());
        VariableFunction function = functionRegistry.getFunction(token.getName());
        if (function == null) {
            throw new IllegalArgumentException("Unsupported builtin function: " + token.getName());
        }
        return function.call(arguments, context);
    }
}
