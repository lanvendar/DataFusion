package com.datafusion.datasource.resultset.handler.jdbc;

import com.datafusion.datasource.resultset.handler.TypeHandler;

import java.sql.ResultSet;

/**
 * JDBC类型处理器.
 * <p>
 * 将SQL返回的数据转换为目标Java类型.
 * TODO 为了扩展一些特殊的类型转换,例如 UUID 等
 * </p>
 *
 * @param <T> 目标Java类型.
 * @author lanvendar
 * @version 1.0.0, 2025/7/10
 * @since 2025/7/10
 */
public interface JdbcTypeHandler<T> extends TypeHandler<ResultSet, T> {
}
