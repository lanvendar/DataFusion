package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.common.cron.DateUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.asset.common.service.BaseServiceImpl;
import com.datafusion.manager.asset.dao.AssetResourceMapper;
import com.datafusion.manager.asset.dto.AssetEdgeNodeDto;
import com.datafusion.manager.asset.dto.AssetNodeDto;
import com.datafusion.manager.asset.dto.ResourceDto;
import com.datafusion.manager.asset.dto.ResourcePageDto;
import com.datafusion.manager.asset.dto.ResourceVo;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.metadata.dto.KeyValueDto;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 血缘资源通用服务实现类.
 *
 * <p>
 * 包含资源CRUD、录入血缘、资源分页查询、节点/边查询等通用操作。
 * 特定资源类型的操作已拆分到独立的Service中。
 * </p>
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Service
@Slf4j
public class AssetResourceService extends BaseServiceImpl<AssetResourceMapper, AssetLineageResourceEntity> {

    /**
     * 血缘边服务.
     */
    @Autowired
    private AssetLineageEdgeService assetLineageEdgeService;

    /**
     * 资源mapper.
     */
    @Autowired
    private AssetResourceMapper resourceMapper;

    /**
     * 资源服务策略工厂.
     */
    @Lazy
    @Autowired
    private AssetResourceServiceStrategyFactory serviceStrategyFactory;

    /**
     * 删除资源.
     *
     * @param id  资源ID
     * @return 删除结果
     */
    public Boolean deleteAssetResource(UUID id) {
        AssetLineageResourceEntity assetLineageResourceEntity = this.getBaseMapper().selectById(id);
        if (assetLineageResourceEntity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在.");
        }
        serviceStrategyFactory.getStrategy(ResourceTypeEnum.valueOf(assetLineageResourceEntity.getResourceType())).deleteResource(id);
        return true;
    }

    /**
     * 批量删除资源.
     *
     * @param ids 资源ID列表
     * @return 删除结果
     */
    public Boolean batchDeleteAssetResource(List<UUID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return true;
        }
        for (UUID id : ids) {
            deleteAssetResource(id);
        }
        return true;
    }

    /**
     * 资源血缘导入.
     *
     * @param resourceIds 资源ID列表
     * @throws Exception 抛出异常
     */
    public void importLineage(List<UUID> resourceIds) {
        List<AssetLineageResourceEntity> resourceEntities = this.getBaseMapper().selectBatchIds(resourceIds);
        if (CollectionUtils.isNotEmpty(resourceEntities)) {
            // 判断状态，只有为准备完成才允许导入
            if (resourceEntities.stream().anyMatch(resource -> !ResourceStatusEnum.IMPORT_EDGE_FAILED.getStatus().equals(resource.getStatus())
                    && !ResourceStatusEnum.PARSE_SUCCESS.getStatus().equals(resource.getStatus()))) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源状态为准备完成才支持导入血缘.");
            }

            Map<String, List<AssetLineageResourceEntity>> resourceMap = resourceEntities.stream()
                    .collect(Collectors.groupingBy(AssetLineageResourceEntity::getResourceType));
            resourceMap.forEach((resourceType, resources) -> {
                serviceStrategyFactory.getStrategy(ResourceTypeEnum.valueOf(resourceType))
                        .importLineage(resources, new Date(), HttpUtils.getCurrentUserName());
            });
        }
    }

    /**
     * 自动导入血缘.
     * @param resourceType 资源类型
     */
    public void autoImportLingage(String resourceType) {
        List<AssetLineageResourceEntity> resources = resourceMapper.selectResourcesByStatus(
                ResourceStatusEnum.PARSE_SUCCESS.getStatus(), resourceType);
        if (cn.hutool.core.collection.CollectionUtil.isEmpty(resources)) {
            log.info("没有需要导入血缘的资源");
            return;
        }
        log.info("开始导入 {} 个资源的血缘", resources.size());
        int successCount = 0;
        int failCount = 0;
        for (AssetLineageResourceEntity resource : resources) {
            try {
                importLineage(List.of(resource.getId()));
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("导入资源 {} 血缘失败: {}", resource.getId(), e.getMessage());
            }
        }
        log.info("血缘导入完成，成功: {}，失败: {}", successCount, failCount);
    }

    /**
     * 资源分页查询.
     *
     * @param query 查询参数
     * @return 资源列表
     */
    public PageResponse<ResourceVo> pageResouces(PageQuery<ResourcePageDto> query) {
        ResourcePageDto queryResourceDto = query.getOption();
        IPage<AssetLineageResourceEntity> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<AssetLineageResourceEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (queryResourceDto != null) {
            if (StrUtil.isNotEmpty(queryResourceDto.getResourceName())) {
                queryWrapper.like(AssetLineageResourceEntity::getResourceName, queryResourceDto.getResourceName());
            }
            if (StrUtil.isNotEmpty(queryResourceDto.getResourceType())) {
                queryWrapper.eq(AssetLineageResourceEntity::getResourceType, queryResourceDto.getResourceType());
            }
            if (queryResourceDto.getStatus() != null) {
                queryWrapper.eq(AssetLineageResourceEntity::getStatus, queryResourceDto.getStatus());
            }
        }
        queryWrapper.orderByDesc(AssetLineageResourceEntity::getUpdateTime);
        page = baseMapper.selectPage(page, queryWrapper);
        PageResponse<ResourceVo> pageResponse = new PageResponse<>();
        pageResponse.setTotal((int) page.getTotal());
        pageResponse.setCurrent((int) page.getCurrent());
        pageResponse.setSize((int) page.getSize());
        ObjectMapper mapper = new ObjectMapper();
        List<AssetLineageResourceEntity> records = page.getRecords();
        pageResponse.setDataList(CollectionUtil.isNotEmpty(records) ? records.stream()
                .map(dto -> {
                    ResourceVo vo = new ResourceVo();
                    BeanUtils.copyProperties(dto, vo);
                    vo.setResourceTagDesc(ResourceTagEnum.getResourceTagTypeDesc(vo.getResourceTag()));
                    vo.setResourceTypeDesc(ResourceTypeEnum.getTypeDescByType(vo.getResourceType()));
                    try {
                        vo.setResourceSnapshot(mapper.writeValueAsString(dto.getResourceSnapshot()));
                        vo.setResultSnapshot(mapper.writeValueAsString(dto.getResultSnapshot()));
                    } catch (JsonProcessingException e) {
                        log.error("setResourceSnapshot writeValueAsString occr exception:", e);
                    }
                    vo.setUpdateTime(DateUtil.toStr(dto.getUpdateTime()));

                    return vo;
                }).collect(Collectors.toList()) : Collections.emptyList());
        return pageResponse;
    }

    /**
     * 资源列表查询.
     *
     * @param query 查询参数
     * @return 资源列表
     */
    public List<ResourceDto> resourceList(ResourceDto query) {
        LambdaQueryWrapper<AssetLineageResourceEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotEmpty(query.getResourceName())) {
            queryWrapper.like(AssetLineageResourceEntity::getResourceName, query.getResourceName());
        }
        if (StrUtil.isNotEmpty(query.getRequestUrl())) {
            queryWrapper.like(AssetLineageResourceEntity::getResourceName, query.getRequestUrl());
        }
        queryWrapper.eq(AssetLineageResourceEntity::getResourceType, ResourceTypeEnum.API.getResouceType());
        if (StrUtil.isNotEmpty(query.getResourceTag())) {
            queryWrapper.eq(AssetLineageResourceEntity::getResourceTag, query.getResourceTag());
        }
        List<AssetLineageResourceEntity> resourceEntityList = baseMapper.selectList(queryWrapper);
        if (!CollectionUtil.isEmpty(resourceEntityList)) {
            return resourceEntityList.stream().map(resouce -> {
                ResourceDto resourceDto = new ResourceDto();
                BeanUtils.copyProperties(resouce, resourceDto);
                resourceDto.setResourceType(ResourceTypeEnum.getTypeDescByType(resouce.getResourceType()));
                resourceDto.setResourceId(resouce.getId());
                return resourceDto;
            }).collect(Collectors.toList());
        }
        return null;
    }

    /**
     * 资源节点分页查询.
     *
     * @param query 节点查询参数
     * @return 资源节点列表
     */
    public PageResponse<AssetNodeDto> pageNodesByResourceId(PageQuery<UUID> query) {
        Integer totalCount = resourceMapper.countNodeByResouceId(query.getOption());
        if (totalCount == 0) {
            return PageResponse.emptyPage(query);
        }
        List<AssetNodeDto> dtos = resourceMapper.nodeByResourceId(query);
        return new PageResponse<>(dtos, query.getSize(), query.getCurrent(), totalCount);
    }

    /**
     * 资源边分页查询.
     *
     * @param query 边查询参数
     * @return 资源边列表
     */
    public PageResponse<AssetEdgeNodeDto> pageEdgesByResourceId(PageQuery<UUID> query) {
        Integer totalCount = resourceMapper.countEdgesByResouceId(query.getOption());
        if (totalCount == 0) {
            return PageResponse.emptyPage(query);
        }
        List<AssetEdgeNodeDto> dtos = resourceMapper.edgesByResourceId(query);
        return new PageResponse<>(dtos, query.getSize(), query.getCurrent(), totalCount);
    }

    /**
     * 资源类型列表.
     *
     * @return 资源类型列表
     */
    public List<KeyValueDto> resourceTypeList() {
        return Arrays.stream(ResourceTypeEnum.values()).map(
                x -> new KeyValueDto(x.getResouceType(), x.getResouceTypeDesc())).collect(Collectors.toList());
    }

    /**
     * 资源状态列表.
     *
     * @return 资源状态列表
     */
    public List<KeyValueDto> resourceStatusList() {
        // 需要导入ResourceStatusEnum
        return Collections.emptyList();
    }

    //    /**
    //     * 根据 resource_name 批量保存或更新资源
    //     * <p>
    //     * 实现逻辑：
    //     * 1. 根据 resource_name 批量查询已存在的记录
    //     * 2. 为新数据设置正确的 ID（存在则用已有ID，不存在则生成新ID）
    //     * 3. 使用 MyBatis-Plus 的 saveOrUpdateBatch 批量保存或更新
    //     * </p>
    //     *
    //     * @param resourceList 资源列表
    //     */
    //    @Override
    //    @Transactional(rollbackFor = Exception.class)
    //    public void batchSaveOrUpdateByResourceName(List<AssetLineageResourceEntity> resourceList) {
    //        if (CollectionUtil.isEmpty(resourceList)) {
    //            log.warn("资源列表为空，跳过批量保存");
    //            return;
    //        }
    //
    //        log.info("开始批量保存或更新资源，数量: {}", resourceList.size());
    //
    //        // 1. 提取所有 resource_name
    //        Set<String> resourceNames = resourceList.stream()
    //                .map(AssetLineageResourceEntity::getResourceName)
    //                .filter(Objects::nonNull)
    //                .collect(Collectors.toSet());
    //
    //        if (resourceNames.isEmpty()) {
    //            log.warn("所有资源的 resource_name 为空，跳过批量保存");
    //            return;
    //        }
    //
    //        // 2. 批量查询已存在的记录（只查询 id 和 resource_name，减少数据传输）
    //        LambdaQueryWrapper<AssetLineageResourceEntity> queryWrapper = Wrappers.lambdaQuery(AssetLineageResourceEntity.class)
    //                .select(AssetLineageResourceEntity::getId, AssetLineageResourceEntity::getResourceName)
    //                .in(AssetLineageResourceEntity::getResourceName, resourceNames);
    //
    //        Map<String, UUID> existingIdMap = this.list(queryWrapper).stream()
    //                .collect(Collectors.toMap(
    //                        AssetLineageResourceEntity::getResourceName,
    //                        AssetLineageResourceEntity::getId,
    //                        (oldId, newId) -> oldId  // 如果有重复，保留第一个
    //                ));
    //
    //        log.debug("查询到已存在的资源数量: {}", existingIdMap.size());
    //
    //        // 3. 为每条数据设置正确的 ID
    //        int updateCount = 0;
    //        int insertCount = 0;
    //
    //        for (AssetLineageResourceEntity resource : resourceList) {
    //            String resourceName = resource.getResourceName();
    //            UUID existingId = existingIdMap.get(resourceName);
    //
    //            if (existingId != null) {
    //                // 已存在：使用数据库中的 ID（后续会执行 UPDATE）
    //                resource.setId(existingId);
    //                updateCount++;
    //                log.debug("资源 {} 已存在，将执行更新，ID: {}", resourceName, existingId);
    //            } else {
    //                // 不存在：确保有新 ID（后续会执行 INSERT）
    //                if (resource.getId() == null) {
    //                    resource.setId(UUID.randomUUID());
    //                }
    //                insertCount++;
    //                log.debug("资源 {} 不存在，将执行插入，ID: {}", resourceName, resource.getId());
    //            }
    //        }
    //
    //        // 4. 使用 MyBatis-Plus 的 saveOrUpdateBatch 批量保存或更新
    //        // 优点：自动根据实体类映射所有字段，无需手动维护字段列表
    //        boolean success = this.saveOrUpdateBatch(resourceList);
    //
    //        if (success) {
    //            log.info("批量保存或更新完成，插入: {} 条，更新: {} 条", insertCount, updateCount);
    //        } else {
    //            log.error("批量保存或更新失败");
    //            throw new RuntimeException("批量保存或更新资源失败");
    //        }
    //    }
    //

    /**
     * 根据 resource_name 批量保存或更新资源.
     *
     * @param resourceList 资源列表
     * @return 是否保存成功
     */
    public boolean batchSaveOrUpdateByResourceName(List<AssetLineageResourceEntity> resourceList) {
        log.info("开始根据 resource_name 批量保存或更新资源");

        // ✅ 调用父类的通用方法

        return this.batchSaveOrUpdateByUniqueField(
                resourceList,
                AssetLineageResourceEntity::getResourceName, // 唯一字段：resource_name
                AssetLineageResourceEntity::getId, // ID getter
                AssetLineageResourceEntity::setId, // ID setter
                entity -> UUID.randomUUID() // 随机生成 UUID
        );
    }
}
