package com.datafusion.datasource.resultset;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一个高效、带缓存的、用于递归解析java.lang.reflect.Type的解析器.
 * 主要优化点：
 * 1. 缓存：避免对同一Type的重复解析，显著提升复杂类型解析的性能。
 * 2. 结构化：将解析逻辑封装在实例方法中，使用内部缓存，代码更清晰。
 * 3. 简洁：合并了相似类型的处理逻辑。
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/7
 * @since 2025/7/7
 */
public class ReturnTypeParser {
    
    /**
     * 全局静态缓存，用于缓存已经完全解析过的Type -> ReturnType的最终结果.
     * 这使得对同一个Type的多次解析请求可以立即返回，性能极高.
     */
    private static final Map<Type, ReturnType> GLOBAL_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 公共的静态入口方法，利用了全局缓存.
     *
     * @param type 要解析的类型
     * @return 构建好的 ReturnType 对象
     */
    public static ReturnType parseType(Type type) {
        if (type == null) {
            return null;
        }
        // computeIfAbsent 确保线程安全和高性能的缓存获取
        return GLOBAL_CACHE.computeIfAbsent(type, t -> new ReturnTypeParser().parseInternal(t));
    }
    
    /**
     * 实例缓存，用于在单次（可能存在循环引用）的递归解析过程中跟踪和缓存结果.
     */
    private final Map<Type, ReturnType> localCache = new HashMap<>();
    
    /**
     * 私有构造函数，强制通过静态方法入口使用.
     */
    private ReturnTypeParser() {
    }
    
    /**
     * 内部的、带本地缓存的递归解析实现.
     *
     * @param type 要解析的类型
     * @return 构建好的 ReturnType 对象
     */
    private ReturnType parseInternal(Type type) {
        // 首先检查本地缓存
        if (localCache.containsKey(type)) {
            return localCache.get(type);
        }
        
        ReturnType result;
        if (type instanceof Class) {
            result = parseClass((Class<?>) type);
        } else if (type instanceof ParameterizedType) {
            result = parseParameterizedType((ParameterizedType) type);
        } else if (type instanceof GenericArrayType) {
            result = parseGenericArrayType((GenericArrayType) type);
        } else if (type instanceof WildcardType || type instanceof TypeVariable) {
            // 合并WildcardType和TypeVariable的处理逻辑
            result = parseBoundedType(type);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
        }
        
        // 将解析结果存入本地缓存
        localCache.put(type, result);
        return result;
    }
    
    /**
     * 处理Class类型,对于数组，递归解析数组的组件类型.
     *
     * @param clazz 要解析的Class对象
     * @return 构建好的 ReturnType 对象
     */
    private ReturnType parseClass(Class<?> clazz) {
        List<ReturnType> componentParam = null;
        if (clazz.isArray()) {
            // 递归解析数组的组件类型
            componentParam = Collections.singletonList(parseInternal(clazz.getComponentType()));
        }
        return ReturnType.builder()
                .originalType(clazz)
                .rawClass(clazz)
                .genericParameters(componentParam)
                .build();
    }
    
    /**
     * 处理ParameterizedType类型，递归解析参数化类型的参数类型.
     *
     * @param pType 要解析的ParameterizedType对象
     * @return 构建好的 ReturnType 对象
     */
    private ReturnType parseParameterizedType(ParameterizedType pType) {
        Class<?> rawClass = (Class<?>) pType.getRawType();
        List<ReturnType> genericParams = new ArrayList<>();
        for (Type argType : pType.getActualTypeArguments()) {
            genericParams.add(parseInternal(argType)); // 递归解析
        }
        return ReturnType.builder()
                .originalType(pType)
                .rawClass(rawClass)
                .genericParameters(Collections.unmodifiableList(genericParams))
                .build();
    }
    
    /**
     * 处理GenericArrayType类型，递归解析数组的组件类型.
     *
     * @param gType 要解析的GenericArrayType对象
     * @return 构建好的 ReturnType 对象
     */
    private ReturnType parseGenericArrayType(GenericArrayType gType) {
        ReturnType componentType = parseInternal(gType.getGenericComponentType());
        // 通过反射创建数组实例以获取其Class对象
        Class<?> rawClass = Array.newInstance(componentType.getRawClass(), 0).getClass();
        
        return ReturnType.builder()
                .originalType(gType)
                .rawClass(rawClass)
                .genericParameters(Collections.singletonList(componentType))
                .build();
    }
    
    /**
     * 处理WildcardType和TypeVariable类型，递归解析边界类型.
     *
     * @param boundedType 要解析的WildcardType或TypeVariable对象
     * @return 构建好的 ReturnType 对象
     */
    private ReturnType parseBoundedType(Type boundedType) {
        Type[] bounds;
        if (boundedType instanceof WildcardType) {
            bounds = ((WildcardType) boundedType).getUpperBounds();
        } else { // TypeVariable
            bounds = ((TypeVariable<?>) boundedType).getBounds();
        }
        // 递归解析上界，默认取第一个（通常也只有一个，或者是Object）
        return parseInternal(bounds[0]);
    }
}