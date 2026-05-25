package com.datafusion.datasource;

import com.datafusion.datasource.model.DataSourceInfo;

/**
 * Postgres 数据源连接器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/7/18
 * @since 2025/7/18
 */
public class PostgresJdbcConnector extends AbstractJdbcConnector {
    /**
     * 构造函数，通过依赖注入来初始化其组件.
     *
     * @param dataSourceInfo 数据源信息配置。
     */
    public PostgresJdbcConnector(DataSourceInfo dataSourceInfo) {
        super(dataSourceInfo);
    }
}
