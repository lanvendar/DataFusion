package com.datafusion.common.variable.function;

import com.datafusion.common.variable.builtin.VariableRenderContext;

import java.util.List;

/**
 * timestamp 内置函数.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class TimestampBuiltinFunc extends AbstractTimeBuiltinFunc {

    @Override
    public String name() {
        return "timestamp";
    }

    @Override
    public String call(List<String> arguments, VariableRenderContext context) {
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("Wrong number parameter of #timestamp directive");
        }
        Long baseTime = resolveBaseTime(argument(arguments, 0), context);
        return baseTime == null ? null : String.valueOf(baseTime);
    }
}
