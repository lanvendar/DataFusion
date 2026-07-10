package com.datafusion.manager.asset.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.dto.EtlReImportRequestVo;
import com.datafusion.manager.asset.dto.EtlResourceInfoDto;
import com.datafusion.manager.asset.dto.EtlSnapshot;
import com.datafusion.manager.asset.dto.request.EtlResourceReq;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ETL资源服务.
 *
 * @author jiexiang.zheng
 * @version 1.0.0 , 2026/03/03
 * @since 2026/03/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetResourceEtlService extends BaseResourceService<EtlResourceInfoDto> {

    /**
     * 血缘边处理服务.
     */
    private final AssetLineageEdgeService assetLineageEdgeService;

    /**
     * 资源服务.
     */
    private final AssetResourceService resourceService;

    /**
     * ETL处理服务.
     */
    private final EtlProcessService etlProcessService;


    /**
     * ETL资源导入.
     * 流程：
     * 1. 页面调用 /etl/import 保存资源（状态=解析成功）
     * 2. 页面调用 /enter/lineage/{id} 录入血缘（生成节点和边）
     *
     * @param etlResourceReq ETL资源导入请求
     * @return 是否导入成功
     */
    @Transactional(rollbackFor = Throwable.class)
    public Boolean etlResourceImport(EtlResourceReq etlResourceReq) {
        try {
            // 将请求转换为上下文数据
            EtlResourceInfoDto context = convertToContext(etlResourceReq);

            // 保存资源
            Date now = new Date();
            String currentUser = HttpUtils.getCurrentUserName();
            batchSaveResources(List.of(context), now, currentUser);

            log.info("ETL资源导入成功: {}", etlResourceReq.getEtlResourceName());
            return true;
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("ETL资源导入失败, 请求体: {}", etlResourceReq, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "ETL资源导入失败");
        }
    }

    /**
     * 将请求转换为上下文数据.
     *
     * @param req 请求体
     * @return 上下文数据
     */
    private EtlResourceInfoDto convertToContext(EtlResourceReq req) {
        EtlResourceInfoDto context = new EtlResourceInfoDto();
        BeanUtil.copyProperties(req, context);
        context.setEtlSnapshot(req.getSql());
        return context;
    }

    /**
     * 判断是否支持该资源类型.
     *
     * @param resourceType 资源类型枚举
     * @return true-支持，false-不支持
     */
    @Override
    protected boolean supports(ResourceTypeEnum resourceType) {
        return ResourceTypeEnum.ETL.equals(resourceType);
    }

    /**
     * 获取资源名称.
     *
     * @param contextData 上下文数据
     * @return 资源名称
     */
    @Override
    protected String resourceName(EtlResourceInfoDto contextData) {
        return contextData.getEtlSnapshot().getTaskName();
    }

    /**
     * 模板方法：构建上下文数据.
     *
     * @param resourceEntity 资源实体
     * @return 上下文数据
     */
    protected EtlResourceInfoDto buildContextData(AssetLineageResourceEntity resourceEntity) {
        return new EtlResourceInfoDto();
    }

    /**
     * 构建资源实体.
     *
     * @param contextData 上下文数据
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 资源实体
     */
    @Override
    protected AssetLineageResourceEntity buildResourceEntity(EtlResourceInfoDto contextData, Date now, String currentUser) {
        EtlSnapshot etlSnapshot = contextData.getEtlSnapshot();

        AssetLineageResourceEntity entity = new AssetLineageResourceEntity();
        entity.setResourceName(etlSnapshot.getTaskName());
        entity.setResourceType(ResourceTypeEnum.ETL.getResouceType());
        entity.setResourceTag(ResourceTagEnum.EDGE.getResourceTagType());
        entity.setResourceSnapshot(JacksonUtils.pojo2JsonNodeOrNull(etlSnapshot));
        // ETL资源默认状态为解析成功
        entity.setStatus(ResourceStatusEnum.PARSE_SUCCESS.getStatus());
        entity.setCreator(currentUser);
        entity.setUpdater(currentUser);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    /**
     * 构建节点实体列表.
     * 复用 assetLineageEdgeService.parseLineage() 进行解析
     *
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 节点实体列表
     */
    @Override
    protected List<AssetLineageNodeEntity> buildNodeEntities(EtlResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
                                                             String currentUser) {
        assetLineageEdgeService.parseEtl(resource, contextData);

        // 对节点list进行去重
        List<AssetLineageNodeEntity> nodeEntities = new ArrayList<>();
        for (AssetLineageNodeEntity nodeEntity : contextData.getNodeEntities()) {
            if (!nodeEntities.contains(nodeEntity)) {
                nodeEntities.add(nodeEntity);
            }
        }
        return nodeEntities;
    }

    /**
     * 构建边实体列表.
     * 由于 parseLineage 已经处理了边的保存，这里返回空列表
     *
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 边实体列表
     */
    @Override
    protected List<AssetLineageEdgeEntity> buildEdgeEntities(EtlResourceInfoDto contextData, AssetLineageResourceEntity resource, Date now,
                                                             String currentUser) {

        //对边list进行去重
        List<AssetLineageEdgeEntity> edgeEntities = new ArrayList<>();
        for (AssetLineageEdgeEntity edgeEntity : contextData.getEdgeEntities()) {
            if (!edgeEntities.contains(edgeEntity)) {
                log.info("---" + edgeEntity.getId());
                edgeEntities.add(edgeEntity);
            }
        }
        return edgeEntities;
    }

    /**
     * 重新导入ETL资源（从GitLab）.
     * 流程：保存资源（状态=解析成功），不解析血缘
     *
     * @param reImportVo 重新导入请求体
     * @return 是否导入成功
     */
    public Boolean reImportEtl(EtlReImportRequestVo reImportVo) {
        List<AssetLineageResourceEntity> resources =
                etlProcessService.gitLabFileProcess(reImportVo.getFilePath(), reImportVo.getFileName());
        //return distinctSaveBatch(resources);
        return this.distinctSaveBatchNew(resources);

    }

    /**
     * 去重更新或新增资源.
     *
     * @param resourceEntities 资源实体列表
     * @return 是否操作成功
     */
    public Boolean distinctSaveBatchNew(List<AssetLineageResourceEntity> resourceEntities) {
        // 判断是否有变化，如果没有变化则不更新血缘
        if (!CollectionUtil.isEmpty(resourceEntities)) {
            List<String> resourceNames = resourceEntities.stream()
                    .map(AssetLineageResourceEntity::getResourceName)
                    .collect(Collectors.toList());

            List<AssetLineageResourceEntity> originResource = this.list(
                    new LambdaQueryWrapper<AssetLineageResourceEntity>()
                            .in(AssetLineageResourceEntity::getResourceName, resourceNames)
            );
            List<AssetLineageResourceEntity> saveList = new ArrayList<>();
            // 判断sql是否发生变化，如果变化了则更新
            List<AssetLineageResourceEntity> needUpdate = null;
            if (CollectionUtil.isNotEmpty(originResource)) {
                needUpdate = resourceEntities.stream().filter(x -> {
                    boolean isNeedUpdate = true;
                    for (AssetLineageResourceEntity resourceEntity : originResource) {
                        if (resourceEntity.getResourceName().equals(x.getResourceName())) {
                            // 判断sql是否发生改变
                            JsonNode resourceSnapshot = resourceEntity.getResourceSnapshot();
                            JsonNode currentSnapshot = x.getResourceSnapshot();
                            EtlSnapshot snapshot = JacksonUtils.tryObj2Bean(resourceSnapshot, EtlSnapshot.class);
                            EtlSnapshot current = JacksonUtils.tryObj2Bean(currentSnapshot, EtlSnapshot.class);
                            if (snapshot.equals(current)) {
                                isNeedUpdate = false;
                            }
                        }
                    }
                    return isNeedUpdate;
                }).collect(Collectors.toList());
            } else {
                needUpdate = resourceEntities;
            }

            if (CollectionUtil.isNotEmpty(needUpdate)) {
                saveList.addAll(needUpdate);
            }
            if (CollectionUtil.isNotEmpty(saveList)) {
                resourceService.batchSaveOrUpdateByResourceName(saveList);
            }
        }
        return true;
    }


    /**
     * 更新ETL资源.
     *
     * @param resourceId     资源ID
     * @param etlResourceReq 请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateEtl(UUID resourceId, EtlResourceReq etlResourceReq) {
        try {
            EtlResourceInfoDto context = convertToContext(etlResourceReq);
            super.saveOrUpdateResource(resourceId, context);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新ETL数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "更新ETL资源数据失败");
        }
    }

}
