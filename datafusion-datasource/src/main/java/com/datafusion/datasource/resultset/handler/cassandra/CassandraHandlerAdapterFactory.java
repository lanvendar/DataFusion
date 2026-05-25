package com.datafusion.datasource.resultset.handler.cassandra;

import com.datafusion.datasource.resultset.handler.TypeHandler;
import com.datafusion.datasource.resultset.handler.TypeHandlerFactory;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

import java.util.function.Supplier;

/**
 * CassandraHandlerAdapterFactory 是一个工厂类的适配器,用于创建 CassandraHandlerAdapter 实例.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public class CassandraHandlerAdapterFactory implements TypeHandlerFactory<TypeHandler<ResultSet, ?>> {
    /**
     * 真正的工厂，用于获取 CassandraTypeHandler.
     */
    private final CassandraTypeHandlerFactory realFactory = new CassandraTypeHandlerFactory();
    
    /**
     * 当前行 suppliers.
     */
    private final Supplier<Row> currentRowSupplier;
    
    /**
     * 构造函数，接受一个当前行 suppliers.
     *
     * @param currentRowSupplier 当前行 suppliers
     */
    public CassandraHandlerAdapterFactory(Supplier<Row> currentRowSupplier) {
        this.currentRowSupplier = currentRowSupplier;
    }
    
    @Override
    public <T> void register(Class<T> javaType, TypeHandler<ResultSet, ?> handler) {
        throw new UnsupportedOperationException("Registration should be done on the real CassandraTypeHandlerFactory");
    }
    
    @Override
    public <T> TypeHandler<ResultSet, T> getHandler(Class<T> javaType) {
        // 1. 从真正的工厂获取一个 CassandraTypeHandler
        CassandraTypeHandler<T> realHandler = realFactory.getHandler(javaType);
        // 2. 将其包装在适配器中
        return new CassandraHandlerAdapter<>(realHandler, currentRowSupplier);
    }
}
