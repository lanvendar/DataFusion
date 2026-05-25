package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * String类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/7
 * @since 2025/7/7
 */
public class StringTypeHandler implements JdbcTypeHandler<String> {
    @Override
    public String getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }
    
    @Override
    public String getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }
}
