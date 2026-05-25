package com.datafusion.datasource.resultset.handler;

/**
 * 类型处理器工厂的通用接口.
 *
 * @param <H> 它生产的 TypeHandler 的类型，例如 JdbcTypeHandler
 * @author lanvendar
 * @version 1.0.0, 2025/7/10
 * @since 2025/7/10
 */
public interface TypeHandlerFactory<H extends TypeHandler<?, ?>> {
    
    /**
     * 注册一个类型处理器.
     *
     * @param javaType Java类型
     * @param handler  处理器实例
     * @param <T>      目标Java类型
     */
    <T> void register(Class<T> javaType, H handler);
    
    /**
     * 获取一个指定Java类型的处理器.
     *
     * @param javaType Java类型
     * @param <T>      目标Java类型
     * @return 处理器实例
     */
    <T> H getHandler(Class<T> javaType);
}
