package com.datafusion.manager.asset.dto;

import lombok.Data;

/**
 * 血缘查询资源列表请求实体.
 * @author xufeng
 * @version 1.0.0, 2026/3/16
 * @since 2026/3/16
 */
@Data
public class DatasourceAssetRichRequestVo {

    /*
     * 述求：
     * 
     * 表的DDL
     CREATE TABLE datafusion.asset_lineage_node (
     id uuid NOT NULL,
     node_urn varchar(1024) NOT NULL,
     node_name varchar(256) NOT NULL,
     node_type varchar(32) NOT NULL,
     node_sub_type varchar(50) NOT NULL,
     node_prop jsonb NULL,
     creator varchar(100) NOT NULL,
     updater varchar(100) NOT NULL,
     create_time timestamp(6) NOT NULL,
     update_time timestamp(6) NOT NULL,
     CONSTRAINT asset_lineage_node_node_urn_key UNIQUE (node_urn),
     CONSTRAINT asset_lineage_node_pk PRIMARY KEY (id)
     );
     * 
     * 1、searchContent、datasourceName、tableName 都为空的时候，只搜索数据源
     * 
     *         select  (string_to_array(node_urn, ':'))[2] as dataSourceName
     *         from datafusion.asset_lineage_node
     *         where node_type = 'DATABASE'
     *         and node_sub_type = 'TABLE'
     *         group by (string_to_array(node_urn, ':'))[2]
     * 
     * 2、datasourceName 不为空的时候 tableName 为空的时候 钻取查询 数据源下面的表信息
     * 
     *         select * from datafusion.asset_lineage_node
     *         where node_type = 'DATABASE'
     *         and node_sub_type = 'TABLE'
     *         and (string_to_array(node_urn, ':'))[2]='数据源名称'
     * 
     * 3、tableName  不为空的时候  钻取查询  查询表下面的字段信息  
     *   
     *                 select * from datafusion.asset_lineage_node
     *         where node_type = 'DATABASE'
     *         and node_sub_type = 'COLUMN'
     *         and (string_to_array(node_urn, ':'))[2]='数据源名称'
     *         and node_urn like 'tableName%'
     *         
     * 4、searchContent 不为空的时候，基于urn进行模糊查询.,查询  符合条件的 字段和字段信息 （searchType=1 的时候才查询 字段级）
     * 生产树形数据
     * 
     */

    /**
     * searchContent 支持模糊查询，基于urn进行模糊查询.
     */
    private String searchContent;

    /**
     * datasourceName 数据源  精确查询(钻取表信息).
     */
    private String datasourceName;

    /**
     * tableName 表名  精确查询(字段查询).
     */
    private String tableName;

    /**
     * 0 表级查询， 1 字段级查询.
     */
    private int searchType;

}
