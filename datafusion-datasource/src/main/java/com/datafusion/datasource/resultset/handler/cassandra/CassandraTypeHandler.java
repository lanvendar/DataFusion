package com.datafusion.datasource.resultset.handler.cassandra;

import com.datafusion.datasource.resultset.handler.TypeHandler;
import com.datastax.driver.core.Row;

/**
 * Cassandra类型处理器.
 *
 * @param <T> 目标Java类型.
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public interface CassandraTypeHandler<T> extends TypeHandler<Row, T> {
}
