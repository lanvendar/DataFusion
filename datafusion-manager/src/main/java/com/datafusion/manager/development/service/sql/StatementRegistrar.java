package com.datafusion.manager.development.service.sql;

import java.sql.Statement;

/**
 * SQL 执行过程中 Statement 句柄注册器.
 *
 * <p>用于 PG/Hologres 等 JDBC 场景在执行期间暴露 Statement 引用, 以支持 {@link Statement#cancel()}.</p>
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/8
 * @since 2026/5/8
 */
public interface StatementRegistrar {

    /**
     * 注册正在执行的 Statement.
     *
     * @param index 语句索引
     * @param stmt  Statement
     */
    void register(int index, Statement stmt);

    /**
     * 取消注册正在执行的 Statement.
     *
     * @param index 语句索引
     */
    void unregister(int index);
}

