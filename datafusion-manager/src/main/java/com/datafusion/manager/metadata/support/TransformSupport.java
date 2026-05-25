package com.datafusion.manager.metadata.support;

import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;

/**
 * 数据源信息转换.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public interface TransformSupport {

    /**
     * 支持的数据库类型.
     *
     * @return 受支持类型
     */
    DatabaseTypeEnum support();

    /**
     * 根据数据源实体转换数据源信息.
     * <pre>
     * 1.jdbcUrl
     * 2.connectType
     * 3.driverClass
     * </pre>
     *
     * @param dsEntity 数据源实体信息
     * @return 数据源信息
     */
    DataSourceInfo transformDataSourceInfo(DataSourceInfoEntity dsEntity);
}
