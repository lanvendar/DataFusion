package com.datafusion.common.variable.function;

import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认变量函数注册表.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class DefaultVariableFunctionRegistry implements VariableFunctionRegistry {

    /**
     * 函数映射.
     */
    private final Map<String, VariableFunction> functions = new ConcurrentHashMap<>();

    /**
     * 获取默认函数注册表.
     *
     * @return 默认函数注册表
     */
    public static DefaultVariableFunctionRegistry defaultRegistry() {
        return createDefaultRegistry();
    }

    @Override
    public void register(VariableFunction function) {
        if (function == null || function.name() == null || function.name().trim().isEmpty()) {
            return;
        }
        functions.put(normalizeName(function.name()), function);
    }

    @Override
    public VariableFunction getFunction(String name) {
        if (name == null) {
            return null;
        }
        return functions.get(normalizeName(name));
    }

    /**
     * 创建默认函数注册表.
     *
     * @return 默认函数注册表
     */
    private static DefaultVariableFunctionRegistry createDefaultRegistry() {
        DefaultVariableFunctionRegistry registry = new DefaultVariableFunctionRegistry();
        registry.register(new DayVariableFunction());
        registry.register(new TimestampVariableFunction());
        for (VariableFunction function : ServiceLoader.load(VariableFunction.class)) {
            registry.register(function);
        }
        return registry;
    }

    /**
     * 标准化函数名.
     *
     * @param name 函数名
     * @return 标准化函数名
     */
    private String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
