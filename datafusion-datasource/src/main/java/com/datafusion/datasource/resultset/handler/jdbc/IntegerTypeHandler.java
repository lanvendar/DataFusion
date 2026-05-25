package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Integer类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/10
 * @since 2025/7/10
 */
public class IntegerTypeHandler implements JdbcTypeHandler<Integer> {
    @Override
    public Integer getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getInt(columnIndex);
    }

    @Override
    public Integer getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName) == null ? null : rs.getInt(columnName);
    }
}
