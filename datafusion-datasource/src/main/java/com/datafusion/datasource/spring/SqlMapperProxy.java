package com.datafusion.datasource.spring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * DefaultRepository默认代理加载器.
 *
 * @param <T> 注册类泛型
 * @author lanvendar
 * @version 1.0.0, 2021/03/07
 * @since 2021/03/07
 **/
@Slf4j
public class SqlMapperProxy<T> implements InvocationHandler {
    
    /**
     * 这里声明一个Class,用来接收接口声明的泛型实际类型的class,T是声明的实体类类型.
     */
    private Class<T> mapperInterface;
    
    /**
     * 原始类注解数据.
     */
    Annotation[] classAnnotations;
    
    /**
     * 实体类类型.
     */
    private final Class<?> entityType;
    
    /**
     * 默认代理加载器.
     *
     * @param mapperInterface 被代理接口
     */
    public SqlMapperProxy(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
        this.classAnnotations = mapperInterface.getAnnotations();
        
        // 使用Spring的ResolvableType健壮地解析泛型
        this.entityType = ResolvableType.forClass(mapperInterface)
                .as(SqlMapper.class)
                .getGeneric(0)
                .resolve();
        
        if (entityType != null) {
            log.debug("成功解析接口 {} 的实体类型为: {}", mapperInterface.getSimpleName(), entityType.getSimpleName());
        }
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 优先处理Object的内置方法
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        
        // 实现AnnotatedProxy接口的方法
        if ("getTargetClass".equals(method.getName())) {
            return this.mapperInterface;
        }
        if ("getTargetClassAnnotations".equals(method.getName())) {
            return this.classAnnotations;
        }
        
        // 示例：可以为toString()提供一个更有意义的实现
        if ("toString".equals(method.getName())) {
            return "SqlMapperProxy for " + mapperInterface.getName();
        }
        
        // 在这里，将来所有被代理的接口方法调用都会被拦截
        // 这是与SqlAspect和Connector集成的关键入口点
        log.warn("方法 {} 被调用，但尚未实现执行逻辑。参数: {}", method.getName(), args);
        
        // 根据方法的返回类型返回一个默认值，避免NPE
        return getDefaultValue(method.getReturnType());
    }
    
    /**
     * 获取默认值.
     *
     * @param returnType 返回类型
     * @return 默认值
     */
    private Object getDefaultValue(Class<?> returnType) {
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return 0;
        }
        return null;
    }
}
