package com.datafusion.datasource.manager;

import com.datafusion.common.exception.CommonException;
import com.datafusion.datasource.model.DataSourceInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hikari 数据源工厂.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/11
 * @since 2025/7/11
 */
@Slf4j
public class JdbcDataSourceManager implements DataSourceManager<DataSource, Connection> {
    /**
     * 数据源连接池, HikariDataSource 本身是线程安全的.
     */
    private final HikariDataSource dataSource;
    
    /**
     * 构造函数.
     *
     * @param dataSourceInfo 数据源信息
     */
    public JdbcDataSourceManager(DataSourceInfo dataSourceInfo) {
        this.dataSource = createDataSource(dataSourceInfo);
    }
    
    /**
     * 创建数据源的私有辅助方法.
     *
     * @param info 数据源信息
     * @return 初始化后的 HikariDataSource
     */
    private HikariDataSource createDataSource(DataSourceInfo info) {
        HikariConfig config = new HikariConfig();
        config.setUsername(info.getUsername());
        config.setPassword(info.getPassword());
        config.setJdbcUrl(info.getJdbcUrl());
        config.setDriverClassName(info.getDriverClass());
        
        // 配置参数（这些可以根据需要调整或提取到常量/配置中）
        config.setIdleTimeout(300000);       // 5 minutes
        config.setValidationTimeout(3000);   // 3 seconds
        config.setMaxLifetime(1800000);      // 30 minutes
        config.setMaximumPoolSize(100);
        config.setConnectionTimeout(5000);   // 5 seconds
        config.setMinimumIdle(10);
        
        try {
            log.info("Initializing HikariDataSource for URL: {}", info.getJdbcUrl());
            return new HikariDataSource(config);
        } catch (Exception e) {
            // HikariDataSource 的构造函数会抛出 RuntimeException（如 IllegalArgumentException）
            log.error("Failed to initialize HikariDataSource for URL: {}", info.getJdbcUrl(), e);
            // 3. 优化异常信息
            throw new CommonException("数据库连接池初始化失败，请检查配置或网络。", e);
        }
    }
    
    @Override
    public HikariDataSource getDataSource() {
        return dataSource;
    }
    
    @Override
    public Connection getConnection() {
        try {
            // 将连接池状态日志级别降为 TRACE，避免生产环境日志泛滥
            if (log.isTraceEnabled()) {
                logPoolStats();
            }
            return this.dataSource.getConnection();
        } catch (SQLException e) {
            // 优化异常处理和日志
            log.error("Failed to obtain connection from Hikari Pool: {}", dataSource.getPoolName(), e);
            throw new CommonException("数据库连接获取失败或超时", e);
        }
    }
    
    /**
     * 记录连接池状态,仅在 TRACE 级别启用时调用.
     */
    private void logPoolStats() {
        HikariPoolMXBean poolBean = this.dataSource.getHikariPoolMXBean();
        if (poolBean != null) {
            log.trace("HikariPoolState = PoolName=[{}] Active=[{}] Idle=[{}] Wait=[{}] Total=[{}]",
                    this.dataSource.getPoolName(),
                    poolBean.getActiveConnections(),
                    poolBean.getIdleConnections(),
                    poolBean.getThreadsAwaitingConnection(),
                    poolBean.getTotalConnections());
        }
    }
    
    @Override
    public void close() {
        // 确保只在数据源处于打开状态时才关闭
        if (dataSource != null && !dataSource.isClosed()) {
            log.info("Closing HikariDataSource: {}", dataSource.getPoolName());
            dataSource.close();
        }
    }
}
