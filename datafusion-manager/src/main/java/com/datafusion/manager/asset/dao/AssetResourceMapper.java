package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.manager.asset.dto.AssetEdgeNodeDto;
import com.datafusion.manager.asset.dto.AssetNodeDto;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 资源Mapper.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Mapper
public interface AssetResourceMapper extends BaseMapper<AssetLineageResourceEntity> {

    /**
     * 根据资源id查询node节点.
     *
     * @param resourceId 查询清求体
     * @return 返回node节点数量
     */
    Integer countNodeByResouceId(UUID resourceId);

    /**
     * 根据资源id查询node节点.
     *
     * @param query 查询清求体
     * @return 返回分页List
     */
    List<AssetNodeDto> nodeByResourceId(PageQuery<UUID> query);

    /**
     * 根据资源id查询边关系.
     *
     * @param resourceId 查询清求体
     * @return 返回总数量
     */
    Integer countEdgesByResouceId(UUID resourceId);

    /**
     * 根据资源id查询边关系.
     *
     * @param query 查询清求体
     * @return 返回所有边关系
     */
    List<AssetEdgeNodeDto> edgesByResourceId(PageQuery<UUID> query);

    /**
     * 根据thirdMetricId查询资源.
     *
     * @param thirdMetricId 外部指标ID
     * @return 资源实体
     */
    AssetLineageResourceEntity selectByThirdMetricId(String thirdMetricId);

    /**
     * 查询状态为"导入完成"的API资源.
     *
     * @param serviceEnName 服务英文名称（可选，为空则查询所有服务）
     * @param startDate     创建开始日期（可选）
     * @param endDate       创建结束日期（可选）
     * @return 资源实体列表
     */
    List<AssetLineageResourceEntity> selectImportCompletedApiResources(
            @Param("serviceEnName") String serviceEnName,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * 批量更新资源状态.
     *
     * @param resourceIds   资源ID列表
     * @param status        目标状态
     * @param result  结果（可选）
     * @return 更新数量
     */
    Integer batchUpdateStatus(@Param("resourceIds") List<UUID> resourceIds,
            @Param("status") Integer status, @Param("result") String result);

    /**
     * 根据资源名称列表查询资源.
     *
     * @param resourceNames 资源名称列表
     * @return 资源实体列表
     */
    List<AssetLineageResourceEntity> selectByResourceNames(@Param("resourceNames") List<String> resourceNames);

    /**
     * 根据单个资源名称列表查询资源.
     *
     * @param resourceName 资源名称
     * @param dagName 流程名称
     * @return 资源实体列表
     */
    List<AssetLineageResourceEntity> selectByResourceName(@Param("resourceName") String resourceName, @Param("dagName") String dagName);

    /**
     * 批量保存资源.
     *
     * @param list 资源实体列表
     */
    void batchSave(@Param("list") List<AssetLineageResourceEntity> list);

    /**
     * 查询最近一天有weLocation的API资源.
     *
     * @return API资源列表
     */
    List<AssetLineageResourceEntity> selectApiResourcesWithWeLocation();

    /**
     * 根据 serviceEnName 查询 API 资源.
     *
     * @param serviceEnName 服务英文名
     * @return API资源列表
     */
    List<AssetLineageResourceEntity> selectApiResourcesByServiceEnName(@Param("serviceEnName") String serviceEnName);

    /**
     * 根据状态和类型查询资源.
     *
     * @param status       资源状态
     * @param resourceType 资源类型（可选）
     * @return 资源列表
     */
    List<AssetLineageResourceEntity> selectResourcesByStatus(@Param("status") Integer status, @Param("resourceType") String resourceType);

    /**
     * 批量保存或更新资源.
     *
     * @param resourceEntities 资源实体列表
     */
    void saveOrUpdateBatch(List<AssetLineageResourceEntity> resourceEntities);
}
