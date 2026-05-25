package com.datafusion.datasource.resultset.handler;

import java.sql.SQLException;

/**
 * 类型处理器接口，负责从Result中获取特定类型的值.
 *
 * @param <R> 源 Result 类型
 * @param <T> 目标 Java 类型.
 * @author lanvendar
 * @version 1.0.0, 2025/7/7
 * @since 2025/7/7
 */
public interface TypeHandler<R, T> {
    /**
     * 从ResultSet中按列索引获取并转换数据.
     *
     * @param rs          ResultSet
     * @param columnIndex 列索引 (从1开始)
     * @return 转换后的Java对象
     * @throws SQLException SQL异常
     */
    T getResult(R rs, int columnIndex) throws SQLException;
    
    /**
     * 从ResultSet中按列名获取并转换数据.
     *
     * @param rs         ResultSet
     * @param columnName 列名
     * @return 转换后的Java对象
     * @throws SQLException SQL异常
     */
    T getResult(R rs, String columnName) throws SQLException;
}
