package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * boolean类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public class BooleanTypeHandler implements JdbcTypeHandler<Boolean> {
    
    @Override
    public Boolean getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getBoolean(columnIndex);
    }
    
    @Override
    public Boolean getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName) == null ? null : rs.getBoolean(columnName);
    }
}
