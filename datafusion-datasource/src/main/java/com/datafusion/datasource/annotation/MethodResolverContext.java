package com.datafusion.datasource.annotation;

import lombok.Getter;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 方法调用的元信息,是{@link SqlGet}的上下文.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/25
 * @since 2025/7/25
 */
@Getter
public class MethodResolverContext {
    /**
     * 方法.
     */
    private final Method method;
    
    /**
     * 方法参数.
     */
    private final Object[] args;
    
    /**
     * 方法所在类.
     */
    private final Class<?> targetClass;
    
    /**
     * 类注解.
     */
    private final Annotation[] targetClassAnnotations;
    
    /**
     * 方法参数注解.
     */
    private final Annotation[][] parameterAnnotations;
    
    /**
     * 代理对象本身.
     */
    private final Object proxy;
    
    /**
     * 构造方法.
     * @param method 调用的方法
     * @param args 方法参数
     * @param targetClass 目标的原始类/接口
     * @param targetClassAnnotations 目标类/接口上的注解
     * @param proxy 代理对象实例
     */
    public MethodResolverContext(Method method, Object[] args, Class<?> targetClass, Annotation[] targetClassAnnotations, Object proxy) {
        this.method = method;
        this.args = args;
        this.targetClass = targetClass;
        this.targetClassAnnotations = targetClassAnnotations;
        this.parameterAnnotations = method.getParameterAnnotations();
        this.proxy = proxy;
    }
}
