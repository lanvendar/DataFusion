package com.datafusion.datasource.resultset.handler.cassandra;

import com.datafusion.datasource.resultset.handler.TypeHandler;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

import java.sql.SQLException;
import java.util.function.Supplier;

/**
 * Cassandra 数据库结果集处理器适配器.
 *
 * @param <T> 目标Java类型.
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public class CassandraHandlerAdapter<T> implements TypeHandler<ResultSet, T> {
    /**
     * 真正的类型处理器.
     */
    private final CassandraTypeHandler<T> realHandler;
    
    /**
     * 当前行提供者.
     */
    private final Supplier<Row> currentRowSupplier;
    
    /**
     * 构造函数.
     *
     * @param realHandler        真正的类型处理器.
     * @param currentRowSupplier 当前行提供者.
     */
    public CassandraHandlerAdapter(CassandraTypeHandler<T> realHandler, Supplier<Row> currentRowSupplier) {
        this.realHandler = realHandler;
        this.currentRowSupplier = currentRowSupplier;
    }
    
    @Override
    public T getResult(ResultSet rs, int columnIndex) throws SQLException {
        // 忽略传入的 rs，使用 supplier 获取正确的 currentRow
        return realHandler.getResult(currentRowSupplier.get(), columnIndex);
    }
    
    @Override
    public T getResult(ResultSet rs, String columnName) throws SQLException {
        // 忽略传入的 rs，使用 supplier 获取正确的 currentRow
        return realHandler.getResult(currentRowSupplier.get(), columnName);
    }
}
