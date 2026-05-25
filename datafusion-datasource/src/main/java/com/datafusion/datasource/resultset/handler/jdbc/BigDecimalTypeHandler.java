package com.datafusion.datasource.resultset.handler.jdbc;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * bigDecimal类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public class BigDecimalTypeHandler implements JdbcTypeHandler<BigDecimal> {
    @Override
    public BigDecimal getResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getBigDecimal(columnIndex);
    }
    
    @Override
    public BigDecimal getResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getBigDecimal(columnName);
    }
}
