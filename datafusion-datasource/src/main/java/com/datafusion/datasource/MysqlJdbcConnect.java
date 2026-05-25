package com.datafusion.datasource;

import com.datafusion.datasource.model.DataSourceInfo;

/**
 * Mysql 数据库服务.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/2/7
 * @since 2023/2/7
 */
public class MysqlJdbcConnect extends AbstractJdbcConnector {
    
    /**
     * 抽象类构造方法.
     *
     * @param dataSourceInfo 数据源对象
     */
    public MysqlJdbcConnect(DataSourceInfo dataSourceInfo) {
        super(dataSourceInfo);
    }
}
