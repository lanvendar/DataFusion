package com.datafusion.datasource.resultset.handler.cassandra;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.InvalidTypeException;

/**
 * Cassandra Integer Type Handler.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public class CassandraIntegerTypeHandler implements CassandraTypeHandler<Integer> {
    @Override
    public Integer getResult(Row row, int columnIndex) throws InvalidTypeException {
        return row.isNull(columnIndex) ? null : row.getInt(columnIndex);
    }
    
    @Override
    public Integer getResult(Row row, String columnName) throws InvalidTypeException {
        return row.isNull(columnName) ? null : row.getInt(columnName);
    }
}
