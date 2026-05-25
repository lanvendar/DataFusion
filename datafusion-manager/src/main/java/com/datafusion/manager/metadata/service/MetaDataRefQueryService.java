package com.datafusion.manager.metadata.service;

import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

/**
 * MetaData引用查询服务.
 * @author chengtg
 * @version 3.7.4, 2024/12/10
 * @since 3.7.4, 2024/12/10
 */
public interface MetaDataRefQueryService {

    /**
     * 根据数据源ID获取对象名称.
     * @param dataSourceId 数据源ID
     * @return 使用到数据源的对象名称
     */
    default String getObjectByDataSourceId(UUID dataSourceId) {
        return StringUtils.EMPTY;
    }

    /**
     * 根据数据源ID和表ID获取对象名称.
     * @param dataSourceId 数据源ID
     * @param tableId   表ID
     * @return 使用到数据表的对象名称
     */
    default String getObjectByTableId(UUID dataSourceId, UUID tableId) {
        return StringUtils.EMPTY;
    }

    /**
     * 根据数据源ID和表ID获取对象名称.
     * @param dataSourceId 数据源ID
     * @param tableId   表ID
     * @param columnId   列ID
     * @return 使用到数据表的对象名称
     */
    default String getObjectByTableIdAndColumnId(UUID dataSourceId, UUID tableId, UUID columnId) {
        return StringUtils.EMPTY;
    }
}
