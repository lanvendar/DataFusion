package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Object类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/7
 * @since 2025/7/7
 */
public class ObjectTypeHandler implements JdbcTypeHandler<Object> {
    @Override
    public Object getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex);
    }
    
    @Override
    public Object getResult(ResultSet rs, String columnName) throws SQLException {
        return  rs.getObject(columnName);
    }
}
