package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * double类型结果集处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public class DoubleTypeHandler implements JdbcTypeHandler<Double> {
    
    @Override
    public Double getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getDouble(columnIndex);
    }
    
    @Override
    public Double getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName) == null ? null : rs.getDouble(columnName);
    }
}
