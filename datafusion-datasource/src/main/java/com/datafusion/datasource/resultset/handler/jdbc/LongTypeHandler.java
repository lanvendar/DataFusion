package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Long类型结果集处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public class LongTypeHandler implements JdbcTypeHandler<Long> {
    @Override
    public Long getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getLong(columnIndex);
    }
    
    @Override
    public Long getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName) == null ? null : rs.getLong(columnName);
    }
}
