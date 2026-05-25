package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.dto.EdgeNodeRequestVo;
import com.datafusion.manager.asset.dto.LineageEdgeDto;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 血缘边关系Mapper.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
@Mapper
public interface AssetLineageEdgeMapper extends BaseMapper<AssetLineageEdgeEntity> {

    /**
     * 血缘关系查询.
     *
     * @param req 血缘查询请求体
     * @return 资源明细结果
     */
    List<LineageEdgeDto> link(@Param("req") EdgeNodeRequestVo req);

    /**
     * 血缘关系查询.
     *
     * @param req 血缘查询请求体
     * @return 资源明细结果
     */
    List<LineageEdgeDto> linkV2(@Param("req") EdgeNodeRequestVo req);

    /**
     * 根据source_urn和target_urn查询边.
     *
     * @param sourceUrn 源URN
     * @param targetUrn 目标URN
     * @return 边实体
     */
    AssetLineageEdgeEntity selectBySourceAndTargetUrn(@Param("sourceUrn") String sourceUrn, @Param("targetUrn") String targetUrn);

    /**
     * 批量插入边.
     *
     * @param edges 边实体列表
     * @return 影响行数
     */
    int insertBatch(@Param("edges") List<AssetLineageEdgeEntity> edges);

}
