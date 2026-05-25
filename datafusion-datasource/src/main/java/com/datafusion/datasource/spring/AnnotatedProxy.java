package com.datafusion.datasource.spring;

import java.lang.annotation.Annotation;

/**
 * 一个内部契约接口，用于让代理对象暴露其原始目标类的信息.
 *
 * <p>
 * 这使得切面（如SqlAspect）可以与代理对象解耦,
 * 无需关心代理的具体实现方式（JDK代理或CGLIB）,
 * 只需检查代理是否实现了此接口，即可安全地获取元数据.
 * </p>
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/25
 * @since 2025/7/25
 */
public interface AnnotatedProxy {
    /**
     * 获取代理对象所代理的原始接口或类的注解.
     *
     * @return 注解数组
     */
    Annotation[] getTargetClassAnnotations();
    
    /**
     * 获取代理对象所代理的原始接口或类.
     *
     * @return 原始的Class对象
     */
    Class<?> getTargetClass();
}
