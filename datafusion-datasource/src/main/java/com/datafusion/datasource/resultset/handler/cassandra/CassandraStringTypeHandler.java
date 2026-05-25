package com.datafusion.datasource.resultset.handler.cassandra;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.InvalidTypeException;

/**
 * Cassandra String TypeHandler.
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public class CassandraStringTypeHandler implements CassandraTypeHandler<String> {
    @Override
    public String getResult(Row row, int columnIndex) throws InvalidTypeException {
        // Cassandra Row is 0-indexed for columns
        return row.isNull(columnIndex) ? null : row.getString(columnIndex);
    }
    
    @Override
    public String getResult(Row row, String columnName) throws InvalidTypeException {
        return row.isNull(columnName) ? null : row.getString(columnName);
    }
}
