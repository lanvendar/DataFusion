package com.datafusion.manager.asset.service.impl;

import com.datafusion.manager.asset.common.service.BaseServiceImpl;
import com.datafusion.manager.asset.dao.AssetLineageNodeResourceRelationMapper;
import com.datafusion.manager.asset.po.AssetLineageNodeResourceRelationEntity;
import com.datafusion.manager.asset.service.AssetLineageNodeResourceRelationService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Service
public class AssetLineageNodeResourceRelationServiceImpl
        extends BaseServiceImpl<AssetLineageNodeResourceRelationMapper, AssetLineageNodeResourceRelationEntity>
        implements AssetLineageNodeResourceRelationService {

    /**
     * 根据 source_urn, target_urn，resource_id 批量保存或更新边.
     *
     * @param edgeList 节点列表
     */
    public boolean batchSaveOrUpdateRelationByMultipleFields(List<AssetLineageNodeResourceRelationEntity> edgeList) {

        // 调用父类的通用方法
        return this.batchSaveOrUpdateByMultipleFields(
                edgeList,
                Arrays.asList(
                        AssetLineageNodeResourceRelationEntity::getNodeId,
                        AssetLineageNodeResourceRelationEntity::getResourceId
                ),
                AssetLineageNodeResourceRelationEntity::getId,
                AssetLineageNodeResourceRelationEntity::setId,
                entity -> UUID.randomUUID()
        );
    }

}

