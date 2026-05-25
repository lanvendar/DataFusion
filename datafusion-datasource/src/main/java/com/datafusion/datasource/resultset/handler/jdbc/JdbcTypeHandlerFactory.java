package com.datafusion.datasource.resultset.handler.jdbc;

import com.datafusion.datasource.resultset.handler.TypeHandlerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 类型处理器工厂，用于获取类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/7
 * @since 2025/7/7
 */
public class JdbcTypeHandlerFactory implements TypeHandlerFactory<JdbcTypeHandler<?>> {
    /**
     * 类型处理器工厂注册表.
     */
    private final Map<Class<?>, JdbcTypeHandler<?>> typeHandlerMap = new HashMap<>();
    
    /**
     * 构造函数，初始化常用类型的处理器.
     */
    public JdbcTypeHandlerFactory() {
        // 注册常用类型的处理器
        register(String.class, new StringTypeHandler());
        register(Integer.class, new IntegerTypeHandler());
        register(int.class, new IntegerTypeHandler());
        register(Long.class, new LongTypeHandler());
        register(long.class, new LongTypeHandler());
        register(Double.class, new DoubleTypeHandler());
        register(double.class, new DoubleTypeHandler());
        register(Float.class, new FloatTypeHandler());
        register(float.class, new FloatTypeHandler());
        register(Boolean.class, new BooleanTypeHandler());
        register(boolean.class, new BooleanTypeHandler());
        register(Date.class, new DateTypeHandler());
        register(LocalDateTime.class, new LocalDateTimeTypeHandler());
        register(BigDecimal.class, new BigDecimalTypeHandler());
        register(UUID.class, new UuidTypeHandler());
        register(LocalDate.class, new LocalDateTypeHandler());
        register(Object.class, new ObjectTypeHandler());

        // 可以继续添加其他类型
    }
    
    /**
     * 注册一个类型处理器.
     *
     * @param javaType    Java类型
     * @param <T>         泛型
     * @param typeHandler 类型处理器
     */
    @Override
    public <T> void register(Class<T> javaType, JdbcTypeHandler<?> typeHandler) {
        typeHandlerMap.put(javaType, typeHandler);
    }
    
    /**
     * 获取一个类型处理器.
     *
     * @param javaType Java类型
     * @param <T>      泛型
     * @return 类型处理器
     */
    @Override
    public <T> JdbcTypeHandler<T> getHandler(Class<T> javaType) {
        return (JdbcTypeHandler<T>) typeHandlerMap.getOrDefault(javaType, typeHandlerMap.get(Object.class));
    }
}
