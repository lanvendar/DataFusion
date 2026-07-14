package com.datafusion.manager.asset.dto;

import lombok.Data;

/**
 * 血缘节点查询接口.
 * @author xufeng
 * @version 1.0.0, 2026/3/17
 * @since 2026/3/17
 */
@Data
public class AssetNodeRequestVo {

    /*
     * 述求:
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
     * 1、nodeSubType 对应 node_sub_type 搜索
     *
     * 2、searchContent 对应node_urn 的模糊匹配
     *
     * 3、两个条件为空的时候默认返回前20个node
     *
     * 4、返回实体为 List<AssetNodeRichDto>
     *
     * 5、新增接口在  AssetNodeController 类里面
     *
     * 6、 searchType=1的时候过滤 nodeSubType = ’COLUMN‘的数据
     */

    /**
    * nodeSubType  NodeSubTypeEnum枚举选项.
    */
    private String nodeSubType;

    /**
     * searchContent 支持模糊查询，基于urn进行模糊查询.
     */
    private String searchContent;

    /**
     * 0 表级血缘， 1 业务血缘.
     */
    private int searchType;

    /**
     * 维度.
     */
    private String dimension;

    /**
     * 菜单类型.
     */
    private Byte componentType;

    /**
     * 指标类型 un 统一指标；dw 数仓指标.
     */
    private String metricType;

    /**
     * 物理层级(节点级，设备级，场站级，系统级等).
     */
    private String physicalLevel;

    /**
     * 计算时效 t+0;t+1.
     */
    private String timeliness;
}
