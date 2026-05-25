package com.datafusion.datasource.executor;

import com.datafusion.datasource.model.ExecuteParam;

/**
 * 抽象执行器.
 *
 * @param <C> 数据源连接的类型，例如 java.sql.Connection 或 com.datastax.driver.core.Session
 * @author lanvendar
 * @version 1.0.0, 2025/7/11
 * @since 2025/7/11
 */
public interface Executor<C> {
    /**
     * 执行查询.
     *
     * @param conn         数据源连接
     * @param executeParam 执行参数
     * @return 查询结果
     */
    Object execute(C conn, ExecuteParam executeParam);
}
