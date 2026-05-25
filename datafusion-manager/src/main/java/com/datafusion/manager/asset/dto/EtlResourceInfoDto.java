package com.datafusion.manager.asset.dto;

import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.google.common.collect.Lists;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * ETL资源上下文数据.
 * 用于 BaseResourceService 传递上下文数据，一个 EtlResourceInfoDto 对应一个ETL资源.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/03/03
 * @since 2026/03/03
 */
@Data
public class EtlResourceInfoDto {

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
     * ETL快照信息.
     */
    private EtlSnapshot etlSnapshot;

    // 以下解析使用

    /**
     * node list.
     */
    private List<AssetLineageNodeEntity> nodeEntities = Lists.newArrayList();

    /**
     * edge list.
     */
    private List<AssetLineageEdgeEntity> edgeEntities = Lists.newArrayList();
}
