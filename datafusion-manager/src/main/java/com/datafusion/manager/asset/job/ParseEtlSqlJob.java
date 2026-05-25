package com.datafusion.manager.asset.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.asset.service.AssetLineageEdgeService;
import com.datafusion.manager.asset.service.AssetResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Slf4j
@Component
public class ParseEtlSqlJob {
    
    /**
     * resourceService.
     */
    @Autowired
    private AssetResourceService resourceService;
    
    /**
     * edgeService.
     */
    @Autowired
    private AssetLineageEdgeService edgeService;
    
    /**
     * 解析etl资源血缘.
     * @param resourceName 资源名称
     */
    //@Scheduled(fixedRate = 1000000)
    public void parseEtl(String resourceName) {
        LambdaQueryWrapper<AssetLineageResourceEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetLineageResourceEntity::getStatus, ResourceStatusEnum.PARSE_SUCCESS.getStatus());
        queryWrapper.eq(AssetLineageResourceEntity::getResourceType, ResourceTypeEnum.ETL.getResouceType());
        if (resourceName != null && !resourceName.isEmpty()) {
            queryWrapper.in(AssetLineageResourceEntity::getResourceName, resourceName);
        }
        List<AssetLineageResourceEntity> list = resourceService.list(queryWrapper);
        List<AssetLineageResourceEntity> errorResource = new ArrayList<>();
        List<AssetLineageResourceEntity> successResource = new ArrayList<>();
        for (AssetLineageResourceEntity resourceEntity : list) {
            log.info("开始解析任务:{}", resourceEntity.getResourceName());
            try {
                resourceService.importLineage(Collections.singletonList(resourceEntity.getId()));
                successResource.add(resourceEntity);
            } catch (Exception e) {
                e.printStackTrace();
                errorResource.add(resourceEntity);
            }
        }
        log.info("解析错误的总数量:{}", errorResource.size());
        log.info("解析成功的总数量:{}", successResource.size());
        log.error("错误的任务{}", errorResource);
    }

    /**
     * 通过etl资源id，解析etl资源血缘.
     *
     * @param id etl资源id
     * @return  true
     */
    //@Scheduled(fixedRate = 1000000)
    public boolean parseEtlById(UUID id) {
        LambdaQueryWrapper<AssetLineageResourceEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetLineageResourceEntity::getResourceType, ResourceTypeEnum.ETL.getResouceType());
        if (id != null) {
            queryWrapper.in(AssetLineageResourceEntity::getId, id);
        }
        List<AssetLineageResourceEntity> list = resourceService.list(queryWrapper);
        List<AssetLineageResourceEntity> errorResource = new ArrayList<>();
        List<AssetLineageResourceEntity> successResource = new ArrayList<>();
        for (AssetLineageResourceEntity resourceEntity : list) {
            log.info("开始解析任务:{}", resourceEntity.getResourceName());
            try {
                resourceService.importLineage(Collections.singletonList(resourceEntity.getId()));
                successResource.add(resourceEntity);
            } catch (Exception e) {
                e.printStackTrace();
                errorResource.add(resourceEntity);
            }
        }
        log.info("解析错误的总数量:{}", errorResource.size());
        log.info("解析成功的总数量:{}", successResource.size());
        log.error("错误的任务{}", errorResource);
        return true;
    }

}
