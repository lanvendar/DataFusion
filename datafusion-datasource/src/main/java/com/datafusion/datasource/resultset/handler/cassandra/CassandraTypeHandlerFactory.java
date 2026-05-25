package com.datafusion.datasource.resultset.handler.cassandra;

import com.datafusion.datasource.resultset.handler.TypeHandlerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Cassandra 类型工厂.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public class CassandraTypeHandlerFactory implements TypeHandlerFactory<CassandraTypeHandler<?>> {
    /**
     * 类型处理器映射表.
     */
    private final Map<Class<?>, CassandraTypeHandler<?>> typeHandlerMap = new HashMap<>();
    
    /**
     * 构造函数.
     */
    public CassandraTypeHandlerFactory() {
        // Register your Cassandra handlers here
        register(String.class, new CassandraStringTypeHandler());
        register(Integer.class, new CassandraIntegerTypeHandler());
        register(int.class, new CassandraIntegerTypeHandler());
        // register(Long.class, new CassandraLongTypeHandler());
        // ... and so on for other types
    }
    
    @Override
    public <T> void register(Class<T> javaType, CassandraTypeHandler<?> handler) {
        typeHandlerMap.put(javaType, handler);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> CassandraTypeHandler<T> getHandler(Class<T> javaType) {
        // You might want a default handler like ObjectTypeHandler
        return (CassandraTypeHandler<T>) typeHandlerMap.get(javaType);
    }
}
