package com.datafusion.scheduler.master.param.exp.func;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置函数工厂.
 *
 * @author lanvendar
 * @version 3.0, 2022/6/22
 * @since 2022/6/22
 */
public class BuiltinFuncFactory {

    /**
     * 函数名与BuiltinFunc的映射缓存.
     */
    private static final Map<String, BuiltinFunc> FUNC_MAP = new ConcurrentHashMap<>();

    static {
        for (BuiltinFunc builtinFunc : ServiceLoader.load(BuiltinFunc.class)) {
            FUNC_MAP.put(builtinFunc.name(), builtinFunc);
        }
    }

    /**
     * 根据name获取BuiltinFunc.
     *
     * @param name 函数名称
     * @return BuiltinFunc
     */
    public static BuiltinFunc getBuiltinFunc(String name) {
        return FUNC_MAP.get(name);
    }
}
