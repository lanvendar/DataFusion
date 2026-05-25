package com.datafusion.manager.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.manager.asset.po.AssetLineageNodeResourceRelationEntity;

import java.util.List;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
public interface AssetLineageNodeResourceRelationService
        extends IService<AssetLineageNodeResourceRelationEntity> {
    /**
     * 根据 resource_id、node_id 批量保存或更新资源.
     * 如果  resource_id、node_id 已存在则更新，否则插入
     * @param relationList 资源列表
     * @return 是否保存或更新成功
     */
    boolean batchSaveOrUpdateRelationByMultipleFields(List<AssetLineageNodeResourceRelationEntity> relationList);

}
