package com.datafusion.datasource.spring;

import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;

/**
 * 默认代理加载器工厂.
 *
 * @param <T> 泛型
 * @author lanvendar
 * @version 1.0.0, 2021/03/07
 * @since 2021/03/07
 */
public class SqlMapperProxyFactory<T> implements FactoryBean<T> {
    
    /**
     * 构建 DefaultCustomRepository 需要使用的参数.
     */
    private Class<T> mapperInterface;
    
    /**
     * 代理加载器工厂.
     *
     * @param interfaceType 被代理接口
     */
    public SqlMapperProxyFactory(Class<T> interfaceType) {
        this.mapperInterface = interfaceType;
    }
    
    public Class<T> getMapperInterface() {
        return mapperInterface;
    }
    
    public void setMapperInterface(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }
    
    @Override
    public T getObject() throws Exception {
        SqlMapperProxy invocationHandler = new SqlMapperProxy(mapperInterface);
        
        // 确保代理对象同时实现被代理接口和AnnotatedProxy接口
        Class<?>[] proxyInterfaces = new Class<?>[]{mapperInterface, AnnotatedProxy.class};
        
        return (T) Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                proxyInterfaces,
                invocationHandler
        );
        
    }
    
    @Override
    public Class<?> getObjectType() {
        // 该方法返回的getObject()方法返回对象的类型,这里是基于interfaceClass生成的代理对象,所以类型就是interfaceClass
        return this.mapperInterface;
    }
    
    @Override
    public boolean isSingleton() {
        // 单例模式
        return true;
    }
}
