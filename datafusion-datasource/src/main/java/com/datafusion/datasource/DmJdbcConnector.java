package com.datafusion.datasource;

import com.datafusion.datasource.model.DataSourceInfo;

/**
 * 达梦 数据库服务.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/2/7
 * @since 2023/2/7
 */
public class DmJdbcConnector extends AbstractJdbcConnector {
    /**
     * 构造函数，通过依赖注入来初始化其组件.
     *
     * @param dataSourceInfo 数据源信息配置。
     */
    public DmJdbcConnector(DataSourceInfo dataSourceInfo) {
        super(dataSourceInfo);
    }
}
