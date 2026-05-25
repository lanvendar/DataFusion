package com.datafusion.manager.asset.dto;

import com.datafusion.manager.metadata.dto.TableColumnsTreeDto;
import lombok.Data;

import java.util.UUID;

/**
 * 库表资源上下文数据.
 * 用于 BaseResourceService 传递上下文数据，一个 TableResourceInfoDto 对应一个表资源.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/02/28
 * @since 2026/02/28
 */
@Data
public class TableResourceInfoDto {

    /**
     * 数据源id.
     */
    private UUID datasourceId;

    /**
     * 数据连接名称.
     */
    private String datasourceName;

    /**
     * 数据库类型.
     */
    private String databaseType;

    /**
     * 数据库schema名称.
     */
    private String schemaName;

    /**
     * 数据库名称.
     */
    private String databaseName;

    /**
     * 表信息.
     */
    private TableColumnsTreeDto tableColumn;

}
