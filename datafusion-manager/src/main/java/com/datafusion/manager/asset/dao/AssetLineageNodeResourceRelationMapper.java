package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.po.AssetLineageNodeResourceRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 节点资源关系Mapper.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Mapper
public interface AssetLineageNodeResourceRelationMapper extends
        BaseMapper<AssetLineageNodeResourceRelationEntity> {

    /**
     * 根据节点ID查询关联数量（排除指定资源ID的关联）.
     *
     * @param nodeId     节点ID
     * @param excludeResourceId 排除的资源ID
     * @return 关联数量
     */
    int countByNodeIdExcludeResource(@Param("nodeId") UUID nodeId, @Param("excludeResourceId") UUID excludeResourceId);

    /**
     * 批量查询节点关联数量（排除指定资源ID的关联）.
     *
     * @param nodeIds           节点ID列表
     * @param excludeResourceId 排除的资源ID
     * @return 节点ID到关联数量的映射
     */
    List<UUID> countByNodeIdsExcludeResource(@Param("nodeIds") List<UUID> nodeIds,
                                                             @Param("excludeResourceId") UUID excludeResourceId);

}
