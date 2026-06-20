package com.datafusion.common.variable.function;

import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置函数工厂.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/06/20
 * @since 2026/06/20
 */
public class BuiltinFuncFactory {

    /**
     * 默认函数工厂.
     */
    private static final BuiltinFuncFactory DEFAULT_FACTORY = createDefaultFactory();

    /**
     * 函数映射.
     */
    private final Map<String, BuiltinFunc> builtinFuncs = new ConcurrentHashMap<>();

    /**
     * 获取默认函数工厂.
     *
     * @return 默认函数工厂
     */
    public static BuiltinFuncFactory defaultFactory() {
        return DEFAULT_FACTORY;
    }

    /**
     * 注册内置函数.
     *
     * @param builtinFunc 内置函数
     */
    public void register(BuiltinFunc builtinFunc) {
        if (builtinFunc == null || builtinFunc.name() == null || builtinFunc.name().trim().isEmpty()) {
            return;
        }
        builtinFuncs.put(normalizeName(builtinFunc.name()), builtinFunc);
    }

    /**
     * 根据函数名获取内置函数.
     *
     * @param name 函数名
     * @return 内置函数
     */
    public BuiltinFunc getBuiltinFunc(String name) {
        if (name == null) {
            return null;
        }
        return builtinFuncs.get(normalizeName(name));
    }

    /**
     * 创建默认函数工厂.
     *
     * @return 默认函数工厂
     */
    private static BuiltinFuncFactory createDefaultFactory() {
        BuiltinFuncFactory factory = new BuiltinFuncFactory();
        factory.register(new DayBuiltinFunc());
        factory.register(new TimestampBuiltinFunc());
        for (BuiltinFunc builtinFunc : ServiceLoader.load(BuiltinFunc.class)) {
            factory.register(builtinFunc);
        }
        return factory;
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
