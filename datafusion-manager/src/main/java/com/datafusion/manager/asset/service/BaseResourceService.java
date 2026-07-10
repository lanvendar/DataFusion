package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.dao.AssetLineageEdgeMapper;
import com.datafusion.manager.asset.dao.AssetLineageNodeMapper;
import com.datafusion.manager.asset.dao.AssetLineageNodeResourceRelationMapper;
import com.datafusion.manager.asset.dao.AssetResourceMapper;
import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeResourceRelationEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.asset.util.AssetJsonUtils;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 资源服务基类 - 提供资源、节点、边血缘关系更新的模板方法.
 * 子类只需实现三个抽象方法即可完成资源持久化：
 * <ul>
 *   <li>{@link #buildResourceEntity(Object, Date, String)} - 构建资源实体</li>
 *   <li>{@link #buildNodeEntities(T, AssetLineageResourceEntity, Date, String)} - 构建节点列表</li>
 *   <li>{@link #buildEdgeEntities(T, AssetLineageResourceEntity, Date, String)} - 构建并保存边</li>
 * </ul>
 *
 * @param <T> 上下文数据类型
 * @author xufeng
 * @version 1.0.0, 2026/01
 * @since 2026/01
 */
@Slf4j
@Service
public abstract class BaseResourceService<T> extends ServiceImpl<AssetResourceMapper, AssetLineageResourceEntity> {

    /**
     * 节点Mapper.
     */
    @Autowired
    protected AssetLineageNodeMapper nodeMapper;

    /**
     * 节点关系Mapper.
     */
    @Autowired
    protected AssetLineageNodeResourceRelationMapper relationMapper;

    /**
     * 边Mapper.
     */
    @Autowired
    protected AssetLineageEdgeMapper edgeMapper;

    /**
     * 判断是否支持该资源类型.
     *
     * @param resourceType 资源类型枚举
     * @return true-支持，false-不支持
     */
    abstract boolean supports(ResourceTypeEnum resourceType);

    /**
     * 资源服务策略工厂.
     */
    @Lazy
    @Autowired
    private AssetResourceServiceStrategyFactory serviceStrategyFactory;

    /**
     * 更新资源校验.
     *
     * @param resourceId 资源ID
     * @return 资源实体
     */
    protected AssetLineageResourceEntity updateValid(UUID resourceId, T contextData) {
        AssetLineageResourceEntity resource = this.getBaseMapper().selectById(resourceId);
        if (resource == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在");
        }
        // 判断资源状态，只有导入完成状态才可以编辑
        //        if (!resource.getStatus().equals(ResourceStatusEnum.IMPORT_SUCCESS.getStatus())) {
        //            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,
        //                    "导入完成状态才可以进行编辑");
        //        }
        String resourceName = resourceName(contextData);
        AssetLineageResourceEntity existingResource = this.getBaseMapper().selectOne(new LambdaQueryWrapper<AssetLineageResourceEntity>()
                .eq(AssetLineageResourceEntity::getResourceName, resourceName));
        if (existingResource != null) {
            if (!existingResource.getId().equals(resourceId)) {
                log.error("资源名称已存在，请勿重复创建，resourceName:{}", resourceName);
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,
                        "资源名称已存在，资源名称:" + resourceName);
            }
        }
        return resource;
    }

    /**
     * 新增资源校验.
     *
     * @param contextData 上下文数据
     * @return 资源实体
     */
    protected void addValid(T contextData) {
        String resourceName = resourceName(contextData);
        AssetLineageResourceEntity existingResource = this.getBaseMapper().selectOne(new LambdaQueryWrapper<AssetLineageResourceEntity>()
                .eq(AssetLineageResourceEntity::getResourceName, resourceName));
        if (existingResource != null) {
            log.error("资源名称已存在，请勿重复创建，resourceName:{}", resourceName);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,
                    "资源名称已存在，资源名称:" + resourceName);
        }
    }

    /**
     * 获取资源名称.
     *
     * @param contextData 上下文数据
     * @return 资源名称
     */
    protected abstract String resourceName(T contextData);

    /**
     * 模板方法：创建或更新资源，仅仅是资源.
     *
     * @param resourceId  资源ID
     * @param contextData 上下文数据（子类自定义类型）
     */
    @Transactional(rollbackFor = Throwable.class)
    public void saveOrUpdateResource(UUID resourceId, T contextData) {
        // 子类构建资源实体
        AssetLineageResourceEntity resourceEntity = buildResourceEntity(contextData, new Date(), HttpUtils.getCurrentUserName());
        if (resourceId == null) {
            // 校验
            addValid(contextData);
            // uuid
            resourceEntity.setId(UUID.randomUUID());
            this.getBaseMapper().insert(resourceEntity);
        } else {
            // 校验
            AssetLineageResourceEntity existingResource = updateValid(resourceId, contextData);
            resourceEntity.setId(existingResource.getId());
            resourceEntity.setCreateTime(existingResource.getCreateTime());
            resourceEntity.setCreator(existingResource.getCreator());
            // 更新已有资源
            this.getBaseMapper().updateById(resourceEntity);
        }
    }

    /**
     * 模板方法：删除资源及其节点血缘关系.
     *
     * @param resourceId 资源ID
     */
    @Transactional(rollbackFor = Throwable.class)
    public void deleteResource(UUID resourceId) {
        AssetLineageResourceEntity existingResource = this.getBaseMapper().selectById(resourceId);
        if (existingResource != null) {
            // 删除节点 关系 边
            cleanupOldNodes(existingResource);

            // 删除资源
            this.getBaseMapper().deleteById(existingResource);
        }
    }

    /**
     * 资源血缘导入.
     *
     * @param resourceEntities 资源列表
     * @param now              当前时间
     * @param currentUser      当前用户
     */
    @Transactional(rollbackFor = Throwable.class)
    public void importLineage(List<AssetLineageResourceEntity> resourceEntities, Date now, String currentUser) {
        resourceEntities.forEach(resourceEntity -> {

            try {
                // 1. 删除节点 关系 边，理论上不需要这一步，因为新导入血缘都属于新增，防止后续可以重复导入
                cleanupOldNodes(resourceEntity);

                // 构造contextData
                T contextData = buildContextData(resourceEntity);

                // 2. 子类构建节点列表并保存
                List<AssetLineageNodeEntity> nodeList = buildNodeEntities(contextData, resourceEntity, now, currentUser);
                for (AssetLineageNodeEntity node : nodeList) {
                    saveNodeAndRelation(node, resourceEntity, now, currentUser);
                }

                // 3. 子类构建并保存边（支持边的属性合并）
                List<AssetLineageEdgeEntity> assetLineageEdgeEntities = buildEdgeEntities(contextData, resourceEntity, now, currentUser);
                if (CollectionUtil.isNotEmpty(assetLineageEdgeEntities)) {
                    saveEdgesWithPropMerge(assetLineageEdgeEntities);
                }

                // 4. 更新资源状态
                resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_SUCCESS.getStatus());
                if (resourceEntity.getResult() == null) {
                    resourceEntity.setResult(null);
                }
                resourceEntity.setUpdateTime(now);
                resourceEntity.setUpdater(currentUser);
                this.getBaseMapper().updateById(resourceEntity);
            } catch (Exception e) {
                log.error("资源导入失败，resourceName:{}", resourceEntity.getResourceName(), e);
                resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_FAILED.getStatus());
                resourceEntity.setResult(JacksonUtils.pojo2JsonNodeOrNull(e.getMessage()));
                resourceEntity.setUpdateTime(now);
                resourceEntity.setUpdater(currentUser);
                this.getBaseMapper().updateById(resourceEntity);
            }
        });

    }

    /**
     * 模板方法：构建上下文数据.
     *
     * @param resourceEntity 资源实体
     * @return 上下文数据
     */
    protected T buildContextData(AssetLineageResourceEntity resourceEntity) {
        return null;
    }

    /**
     * 模板方法：批量创建，仅仅是资源.
     * 会剔除已经存在的资源
     *
     * @param contextDatas 上下文数据（子类自定义类型）
     * @param now          当前时间
     * @param currentUser  当前用户
     */
    @Transactional(rollbackFor = Throwable.class)
    public void batchSaveResources(List<T> contextDatas, Date now, String currentUser) {
        // 子类构建资源实体
        List<AssetLineageResourceEntity> resourceEntities = buildResourceEntities(contextDatas, now, currentUser);

        List<String> resourceNames = resourceEntities.stream().map(resourceEntity -> resourceEntity.getResourceName()).collect(Collectors.toList());
        List<AssetLineageResourceEntity> existsResources = this.getBaseMapper().selectByResourceNames(resourceNames);
        if (CollectionUtils.isNotEmpty(existsResources)) {
            //已存在的资源删除重新导入
            existsResources.stream().forEach(m -> {
                serviceStrategyFactory.getStrategy(ResourceTypeEnum.valueOf(m.getResourceType())).deleteResource(m.getId());
            });

        }

        resourceEntities.forEach(resourceEntity -> {
            resourceEntity.setId(UUID.randomUUID());
        });
        this.getBaseMapper().batchSave(resourceEntities);
    }

    // ===================== 抽象方法 =====================

    /**
     * 构建资源实体.
     * 子类根据业务数据构建 {@link AssetLineageResourceEntity}，
     * 包括设置资源名称、类型、快照等信息。
     *
     * <p>
     * 注意：构建实体时，如果id存在，则认为是更新，否则是创建，因此创建实体时，id必须为空。
     *
     * @param contextData 上下文数据
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 资源实体
     */
    protected abstract AssetLineageResourceEntity buildResourceEntity(T contextData, Date now, String currentUser);

    /**
     * 批量构建资源实体.
     * 子类根据业务数据构建 {@link AssetLineageResourceEntity}，
     * 包括设置资源名称、类型、快照等信息。
     *
     * @param contextDatas 上下文数据
     * @param now          当前时间
     * @param currentUser  当前用户
     * @return 资源实体
     */
    protected List<AssetLineageResourceEntity> buildResourceEntities(List<T> contextDatas, Date now, String currentUser) {
        List<AssetLineageResourceEntity> resourceEntities = new ArrayList<>();
        for (T contextData : contextDatas) {
            resourceEntities.add(buildResourceEntity(contextData, now, currentUser));
        }
        return resourceEntities;
    }

    /**
     * 构建节点实体列表.
     * 子类根据业务数据构建血缘节点列表。
     *
     * @param contextData 上下文数据
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 节点实体列表
     */
    protected abstract List<AssetLineageNodeEntity> buildNodeEntities(T contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser);

    /**
     * 构建边实体列表.
     * 子类根据业务数据构建边列表并保存到数据库。
     *
     * @param contextData 上下文数据
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     */
    protected abstract List<AssetLineageEdgeEntity> buildEdgeEntities(T contextData, AssetLineageResourceEntity resource, Date now,
            String currentUser);

    // ===================== 通用方法 =====================

    /**
     * 保存节点和关联关系.
     * 如果节点已存在则更新，不存在则新增。
     * 同时创建节点与资源的关联关系。
     *
     * @param node        节点实体
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     */
    private void saveNodeAndRelation(AssetLineageNodeEntity node, AssetLineageResourceEntity resource,
            Date now, String currentUser) {
        // 保存节点
        AssetLineageNodeEntity existingNode = nodeMapper.selectByNodeUrn(node.getNodeUrn());
        if (existingNode == null) {
            nodeMapper.insert(node);
        } else {
            if (java.util.Objects.equals(node.getNodeProp(), existingNode.getNodeProp())) {
                node = existingNode;
            } else {
                node.setId(existingNode.getId());
                nodeMapper.updateById(node);
            }
        }

        // 创建关联
        AssetLineageNodeResourceRelationEntity relation = createRelationEntity(resource, node, now, currentUser);
        relationMapper.insert(relation);
    }

    /**
     * 构造边.
     *
     * @param sourceUrn   源URN
     * @param targetUrn   目标URN
     * @param resource    资源实体
     * @param now         当前时间
     * @param currentUser 当前用户
     */
    protected AssetLineageEdgeEntity buildEdge(String sourceUrn, String targetUrn,
            AssetLineageResourceEntity resource, Date now, String currentUser) {
        AssetLineageEdgeEntity edge = new AssetLineageEdgeEntity();
        edge.setId(UUID.randomUUID());
        edge.setSourceUrn(sourceUrn);
        edge.setTargetUrn(targetUrn);
        edge.setResourceId(resource.getId());
        edge.setCreator(currentUser);
        edge.setUpdater(currentUser);
        edge.setCreateTime(now);
        edge.setUpdateTime(now);
        return edge;
    }

    protected AssetLineageEdgeEntity buildEdge(String sourceUrn, String targetUrn,
            AssetLineageResourceEntity resource, Date now, String currentUser, JsonNode edgeProp) {
        AssetLineageEdgeEntity edge = buildEdge(sourceUrn, targetUrn, resource, now, currentUser);
        edge.setEdgeProp(AssetJsonUtils.wrapTagSet(edgeProp));
        return edge;
    }

    /**
     * 创建节点资源关联实体.
     *
     * @param resource    资源实体
     * @param node        节点实体
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 关联实体
     */
    private AssetLineageNodeResourceRelationEntity createRelationEntity(
            AssetLineageResourceEntity resource, AssetLineageNodeEntity node,
            Date now, String currentUser) {
        AssetLineageNodeResourceRelationEntity relation = new AssetLineageNodeResourceRelationEntity();
        relation.setId(UUID.randomUUID());
        relation.setResourceId(resource.getId());
        relation.setNodeId(node.getId());
        relation.setCreator(currentUser);
        relation.setUpdater(currentUser);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        return relation;
    }

    /**
     * 清理旧节点和关联.
     * 查询资源关联的所有节点，检查是否被其他资源关联，
     * 如果没有则删除。最后删除该资源的所有关联关系和边。
     *
     * @param existingResource 已有资源
     */
    public void cleanupOldNodes(AssetLineageResourceEntity existingResource) {
        // 查询该资源关联的所有节点
        LambdaQueryWrapper<AssetLineageNodeResourceRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetLineageNodeResourceRelationEntity::getResourceId, existingResource.getId());
        List<AssetLineageNodeResourceRelationEntity> relations = relationMapper.selectList(wrapper);

        // 批量查询节点关联数量
        List<UUID> nodeIds = relations.stream()
                .map(AssetLineageNodeResourceRelationEntity::getNodeId)
                .collect(Collectors.toList());

        if (CollectionUtil.isNotEmpty(nodeIds)) {
            List<UUID> usingNodes = relationMapper.countByNodeIdsExcludeResource(
                    nodeIds, existingResource.getId());
            nodeIds.removeAll(usingNodes);
            if (CollectionUtil.isNotEmpty(nodeIds)) {
                // 未被使用的节点需要删除
                nodeMapper.deleteByIds(nodeIds);
            }
        }

        // 删除该资源的所有关联关系
        relationMapper.delete(wrapper);

        // 删除该资源的所有边
        deleteEdgesByResourceId(existingResource.getId());
    }

    /**
     * 根据资源ID删除所有边.
     *
     * @param resourceId 资源ID
     */
    protected void deleteEdgesByResourceId(UUID resourceId) {
        LambdaQueryWrapper<AssetLineageEdgeEntity> edgeWrapper = new LambdaQueryWrapper<>();
        edgeWrapper.eq(AssetLineageEdgeEntity::getResourceId, resourceId);
        edgeMapper.delete(edgeWrapper);
    }

    /**
     * 保存边列表，支持边的属性合并.
     * 对于每条边，先查询是否已存在（按 sourceUrn + targetUrn）：
     * - 已存在且有 prop：将新边的 prop 与已有 prop 合并（Set 去重），更新已有边
     * - 已存在且无 prop：更新已有边
     * - 不存在：新增
     *
     * @param newEdges 新边列表
     */
    private void saveEdgesWithPropMerge(List<AssetLineageEdgeEntity> newEdges) {
        List<AssetLineageEdgeEntity> insertList = new ArrayList<>();
        for (AssetLineageEdgeEntity newEdge : newEdges) {
            AssetLineageEdgeEntity existingEdge = edgeMapper.selectBySourceAndTargetUrn(
                    newEdge.getSourceUrn(), newEdge.getTargetUrn());
            if (existingEdge == null) {
                insertList.add(newEdge);
            } else {
                // 合并属性：将新边的 prop 与已有 prop 合并
                JsonNode mergedProp = mergeEdgeProp(existingEdge.getEdgeProp(), newEdge.getEdgeProp());
                existingEdge.setEdgeProp(mergedProp);
                existingEdge.setResourceId(newEdge.getResourceId());
                existingEdge.setUpdater(newEdge.getUpdater());
                existingEdge.setUpdateTime(newEdge.getUpdateTime());
                edgeMapper.updateById(existingEdge);
            }
        }
        if (CollectionUtil.isNotEmpty(insertList)) {
            edgeMapper.insertBatch(insertList);
        }
    }

    /**
     * 合并边的属性（Set 集合去重合并）.
     * 先从 edgeProp 中解包出 tagSet 数组，分别反序列化为 Set&lt;MetricsTagDto&gt; 去重合并，
     * 再包装回 {"tagSet": [...]} 格式。
     *
     * @param existingProp 已有属性
     * @param newProp      新属性
     * @return 合并后的属性
     */
    private JsonNode mergeEdgeProp(JsonNode existingProp, JsonNode newProp) {
        JsonNode existingArray = unwrapTagSet(existingProp);
        JsonNode newArray = unwrapTagSet(newProp);
        Set<MetricsTagDto> mergedSet = new java.util.HashSet<>();
        Set<MetricsTagDto> existingSet = JacksonUtils.tryObj2Bean(existingArray,
                new com.fasterxml.jackson.core.type.TypeReference<Set<MetricsTagDto>>() {
                });
        Set<MetricsTagDto> newSet = JacksonUtils.tryObj2Bean(newArray,
                new com.fasterxml.jackson.core.type.TypeReference<Set<MetricsTagDto>>() {
                });
        if (existingSet != null) {
            mergedSet.addAll(existingSet);
        }
        if (newSet != null) {
            mergedSet.addAll(newSet);
        }
        return wrapTagSet(JacksonUtils.pojo2JsonNodeOrNull(mergedSet));
    }

    /**
     * 将 tagSet 数组包装为 {"tagSet": [...]} 格式.
     * 兼容旧数据：如果已经是新格式则直接返回。
     *
     * @param tagArray tagSet数组
     * @return 包装后的JsonNode
     */
    protected JsonNode wrapTagSet(JsonNode tagArray) {
        return AssetJsonUtils.wrapTagSet(tagArray);
    }

    /**
     * 从 edgeProp 中解包出 tagSet 数组.
     * 兼容旧数据：如果直接是数组格式则原样返回。
     *
     * @param edgeProp edgeProp字段值
     * @return 解包后的tagSet数组
     */
    protected JsonNode unwrapTagSet(JsonNode edgeProp) {
        return AssetJsonUtils.unwrapTagSet(edgeProp);
    }

    // ===================== 内部类 =====================

    /**
     * 同步结果.
     */
    @Data
    public static class SyncResult {
        /**
         * 是否成功.
         */
        private boolean success;

        /**
         * 是否跳过.
         */
        private boolean skipped;

        /**
         * 错误消息.
         */
        private String errorMessage;

        /**
         * 创建成功结果.
         *
         * @return 成功结果
         */
        public static SyncResult success() {
            SyncResult result = new SyncResult();
            result.success = true;
            return result;
        }

        /**
         * 创建跳过结果.
         *
         * @return 跳过结果
         */
        public static SyncResult skipped() {
            SyncResult result = new SyncResult();
            result.skipped = true;
            return result;
        }

        /**
         * 创建失败结果.
         *
         * @param errorMessage 错误消息
         * @return 失败结果
         */
        public static SyncResult failure(String errorMessage) {
            SyncResult result = new SyncResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }
    }

}
