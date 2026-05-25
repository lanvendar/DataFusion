package com.datafusion.datasource;

import com.datafusion.common.exception.CommonException;
import com.datafusion.common.template.SqlParamRender;
import com.datafusion.datasource.executor.Executor;
import com.datafusion.datasource.executor.JdbcExecutor;
import com.datafusion.datasource.manager.DataSourceManager;
import com.datafusion.datasource.manager.JdbcDataSourceManager;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.datasource.model.ExecuteParam;
import com.datafusion.datasource.resultset.JdbcResultSetResolver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.stream.Collectors;

/**
 * JDBC 连接器的抽象实现类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
@Slf4j
public abstract class AbstractJdbcConnector implements Connector {
    /**
     * 数据源管理器，负责提供数据库连接.
     */
    @Getter
    private final DataSourceManager<DataSource, Connection> dataSourceManager;
    
    /**
     * 执行器，负责在给定的连接上执行SQL.
     */
    private final Executor<Connection> executor;
    
    /**
     * 构造函数，通过依赖注入来初始化其组件.
     *
     * @param dataSourceInfo 数据源信息配置。
     */
    public AbstractJdbcConnector(DataSourceInfo dataSourceInfo) {
        this.dataSourceManager = new JdbcDataSourceManager(dataSourceInfo);
        this.executor = new JdbcExecutor(new JdbcResultSetResolver());
    }
    
    /**
     * 执行一个数据库操作.
     * 这个方法完美地封装了连接管理的生命周期。
     *
     * @param executeParam 包含SQL和参数的执行对象。
     * @return 执行结果。
     */
    @Override
    public Object execute(ExecuteParam executeParam) {
        // 1. 使用 try-with-resources 安全地获取连接。
        //    连接在代码块结束时会自动关闭，即使发生异常。
        try (Connection conn = dataSourceManager.getConnection()) {
            
            // 2. 将执行任务委托给内部的 Executor。
            //    Connector 本身不关心如何执行，只关心调用 Executor 并返回结果。
            return executor.execute(conn, executeParam);
            
        } catch (Exception e) {
            // 3. 捕获所有可能的异常（包括获取连接失败、执行失败等）。
            //    包装成自定义的通用异常，向上层抛出。
            log.error("Database operation failed for SQL: {}", executeParam.getRenders().stream()
                    .map(SqlParamRender::getSql)
                    .collect(Collectors.joining("\n")), e);
            
            // 如果已经是 CommonException，直接重新抛出，避免过度包装
            if (e instanceof CommonException) {
                throw (CommonException) e;
            }
            
            throw new CommonException("A database error occurred during execution.", e);
        }
    }
    
    @Override
    public void destroy() {
        log.info("Destroying connector and its underlying data source manager.");
        dataSourceManager.close();
    }
}
