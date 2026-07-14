package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.druid.support.json.JSONUtils;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.config.ResourceSyncConfig;
import com.datafusion.manager.asset.dao.AssetResourceMapper;
import com.datafusion.manager.asset.dto.MetricColumnInfo;
import com.datafusion.manager.asset.dto.MetricInfoDto;
import com.datafusion.manager.asset.dto.builder.NodePropBuilder;
import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import com.datafusion.manager.asset.dto.request.MetricsReq;
import com.datafusion.manager.asset.dto.request.MetricsResourceReq;
import com.datafusion.manager.asset.dto.request.MetricsUpdateResourceReq;
import com.datafusion.manager.asset.dto.response.MetricsResourceResp;
import com.datafusion.manager.asset.dto.response.MetricsResp;
import com.datafusion.manager.asset.dto.skywalking.MetricsTagDto;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.asset.po.MetricSyncRecordEntity;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoQueryDto;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 指标资源服务实现类.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/21
 * @since 2026/01/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetResourceMetricsService extends BaseResourceService<MetricInfoDto> {

    /**
     * 指标同步配置.
     */
    private final ResourceSyncConfig resourceSyncConfig;

    /**
     * 数据源信息服务.
     */
    private final DataSourceInfoService dataSourceInfoService;

    /**
     * 资源搜索服务.
     */
    @Autowired
    private AssetResourceMapper resourceMapper;

    /**
     * 新增指标数据.
     *
     * @param metricList 指标集合
     * @return 同步状态
     */
    @Transactional(rollbackFor = Exception.class)
    public MetricSyncRecordEntity addMetrics(List<MetricInfoDto> metricList) {
        MetricSyncRecordEntity syncRecord = new MetricSyncRecordEntity();
        try {
            int successCount = 0;
            List<String> failMetricIds = new ArrayList<>();

            for (MetricInfoDto metric : metricList) {
                try {
                    // 从配置构建API资源快照
                    ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = buildApiResourceSnapshot(metric);
                    metric.setApiResourceSnapshot(apiSnapshot);
                    SyncResult result = processDbMetric(metric);
                    if (result.isSuccess()) {
                        successCount++;
                    } else if (result.isSkipped()) {
                        // 跳过
                    } else {
                        failMetricIds.add(metric.getThirdMetricId());
                    }
                } catch (Exception e) {
                    log.error("处理指标数据失败, metricCode: {}, dimension: {}",
                            metric.getMetricCode(), metric.getDimension(), e);
                    failMetricIds.add(metric.getThirdMetricId());
                }
            }

            // 更新同步记录状态.
            syncRecord.setSuccessCount(successCount);
            syncRecord.setFailCount(failMetricIds.size());
            syncRecord.setFailMetricIds(failMetricIds.stream().collect(Collectors.joining(",")));
            return syncRecord;
        } catch (Exception e) {
            log.error("同步指标数据异常", e);
            throw e;
        }
    }

    /**
     * 新增指标数据.
     *
     * @param metricsResourceReq 指标请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMetrics(MetricsResourceReq metricsResourceReq) {
        try {
            AssetLineageResourceEntity parentResource = this.getBaseMapper().selectById(metricsResourceReq.getParentResourceId());
            if (parentResource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "父级资源不存在");
            }
            ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = ResourceSnapshotBuilder.builder(parentResource);

            // [V2] 将 MetricsReq 转换为 MetricInfoDto 列表
            List<MetricInfoDto> metricList = new ArrayList<>();
            for (MetricsReq metricsReq : metricsResourceReq.getMetricsReqList()) {
                apiSnapshot.setDimension(metricsReq.getDimension());
                MetricInfoDto metric = getMetricInfoDTO(metricsReq, apiSnapshot);
                metric.setParentResourceId(parentResource.getId());
                metricList.add(metric);
            }

            // [V2] 按 metricCode 分组处理
            List<MetricInfoDto> groupedMetricList = groupByMetricCode(metricList);

            // 保存分组后的指标
            for (MetricInfoDto metric : groupedMetricList) {
                super.saveOrUpdateResource(null, metric);
            }
        } catch (Exception e) {
            log.error("处理指标数据失败, 请求体: " + JSONUtils.toJSONString(metricsResourceReq), e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "处理指标数据失败");
        }
    }

    /**
     * 更新指标数据.
     *
     * @param resourceId         资源ID
     * @param metricsResourceReq 指标请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMetrics(UUID resourceId, MetricsUpdateResourceReq metricsResourceReq) {
        try {
            AssetLineageResourceEntity parentResource = this.getBaseMapper().selectById(metricsResourceReq.getParentResourceId());
            if (parentResource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "父资源不存在");
            }
            ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = ResourceSnapshotBuilder.builder(parentResource);
            MetricInfoDto metric = getMetricInfoDTO(metricsResourceReq.getMetricsReq(), apiSnapshot);
            metric.setParentResourceId(parentResource.getId());
            super.saveOrUpdateResource(resourceId, metric);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("处理指标数据失败, 请求体: " + JSONUtils.toJSONString(metricsResourceReq), e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "更新指标数据失败");
        }
    }

    /**
     * 删除指标数据.
     *
     * @param resourceId 资源ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetrics(UUID resourceId) {
        try {
            AssetLineageResourceEntity resource = this.getBaseMapper().selectById(resourceId);
            if (resource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在");
            }
            super.deleteResource(resourceId);
        } catch (Exception e) {
            log.error("处理指标数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "删除指标数据失败");
        }
    }

    /**
     * 查询指标数据.
     *
     * @param resourceId 资源ID
     * @return 指标数据
     */
    public MetricsResourceResp getMetrics(UUID resourceId) {
        try {
            AssetLineageResourceEntity resource = this.getBaseMapper().selectById(resourceId);
            if (resource == null) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "资源不存在");
            }
            ResourceSnapshotBuilder.MetricResourceSnapshot metricResourceSnapshot = ResourceSnapshotBuilder.builder(resource);
            ResourceSnapshotBuilder.MetricResourceResultSnapshot metricResourceResultSnapshot = ResourceSnapshotBuilder.builder(resource, true);
            MetricsResp metricsResp = new MetricsResp();
            metricsResp.setMetricCode(metricResourceSnapshot.getCode());
            metricsResp.setMetricName(metricResourceSnapshot.getName());
            metricsResp.setDimension(metricResourceSnapshot.getDimension());
            metricsResp.setColumnName(metricResourceResultSnapshot.getMetricDto().getColumnName());
            metricsResp.setTableName(metricResourceResultSnapshot.getMetricDto().getTableName());
            MetricsResourceResp metricsResourceResp = new MetricsResourceResp();
            metricsResourceResp.setMetricsResp(metricsResp);
            metricsResourceResp.setParentResourceId(metricResourceSnapshot.getParentResourceId());
            metricsResourceResp.setRequestUrl(metricResourceSnapshot.getRequestUrl());
            return metricsResourceResp;
        } catch (Exception e) {
            log.error("处理指标数据失败, resourceId: " + resourceId, e);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "查询指标数据失败");
        }
    }

    /**
     * 构建资源名称.
     *
     * @param metricsReq  指标数据请求体
     * @param apiSnapshot API资源快照
     * @return 资源名称
     */
    private static @NotNull MetricInfoDto getMetricInfoDTO(MetricsReq metricsReq, ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot) {
        MetricInfoDto metric = new MetricInfoDto();
        metric.setApiResourceSnapshot(apiSnapshot);
        metric.setThirdMetricId(null);
        metric.setMetricCode(metricsReq.getMetricCode());
        metric.setMetricName(metricsReq.getMetricName());
        metric.setDimension(metricsReq.getDimension());
        metric.setColumnName(metricsReq.getColumnName());
        metric.setApiUrl(apiSnapshot.getRequestUrl());
        metric.setTableName(metricsReq.getTableName());
        metric.setDatasourceName(metricsReq.getDatasourceName());
        return metric;
    }

    /**
     * 按 metricCode 分组处理.
     * 将相同 metricCode 的记录合并为一条，columnInfoList 包含该指标对应的所有字段信息.
     *
     * @param metricList 原始指标列表
     * @return 分组后的指标列表
     */
    private List<MetricInfoDto> groupByMetricCode(List<MetricInfoDto> metricList) {
        if (CollectionUtil.isEmpty(metricList)) {
            return metricList;
        }

        // 按 metricCode + apiUrl 分组
        Map<String, List<MetricInfoDto>> groupedByCode = metricList.stream()
                .collect(Collectors.groupingBy(m -> m.getMetricCode() + "|" + StrUtil.emptyToNull(m.getApiUrl())));

        // 转换为分组后的DTO列表
        List<MetricInfoDto> groupedMetricList = new ArrayList<>();
        for (Map.Entry<String, List<MetricInfoDto>> entry : groupedByCode.entrySet()) {
            List<MetricInfoDto> group = entry.getValue();
            MetricInfoDto representative = group.get(0);

            // 构建 columnInfoList（从分组的所有元素中提取）
            List<MetricColumnInfo> columnInfoList = group.stream()
                    .map(m -> {
                        MetricColumnInfo info = new MetricColumnInfo();
                        info.setColumnName(m.getColumnName());
                        info.setTableName(m.getTableName());
                        info.setDatasourceName(m.getDatasourceName());
                        return info;
                    })
                    .collect(Collectors.toList());

            representative.setColumnInfoList(columnInfoList);
            groupedMetricList.add(representative);
        }

        log.info("按metricCode+apiUrl分组完成: 原始数量={}, 分组后={}",
                metricList.size(), groupedMetricList.size());

        return groupedMetricList;
    }

    /**
     * 处理DB指标.
     *
     * @param metric 指标数据.
     * @return 处理结果.
     */
    private SyncResult processDbMetric(MetricInfoDto metric) {
        String resourceName = resourceName(metric);

        // 查询是否已存在资源.
        AssetLineageResourceEntity existingResource = this.getBaseMapper().selectByThirdMetricId(metric.getThirdMetricId());
        if (existingResource != null) {
            // 比较指标属性是否有变化.
            if (!hasMetricChanged(existingResource, metric)) {
                log.info("指标属性未变化，跳过: {}", resourceName);
                return SyncResult.skipped();
            }
        }

        // 保存或更新资源.
        super.saveOrUpdateResource(existingResource != null ? existingResource.getId() : null, metric);
        return SyncResult.success();
    }

    /**
     * 判断指标属性是否有变化.
     *
     * @param existingResource 已有资源.
     * @param metric           指标数据.
     * @return true-有变化, false-无变化.
     */
    private boolean hasMetricChanged(AssetLineageResourceEntity existingResource, MetricInfoDto metric) {
        ResourceSnapshotBuilder.MetricResourceSnapshot snapshot = ResourceSnapshotBuilder.builder(existingResource);
        ResourceSnapshotBuilder.MetricResourceResultSnapshot resultSnapshot = ResourceSnapshotBuilder.builder(existingResource, true);

        // 比较属性.
        return !StrUtil.equals(snapshot.getRequestUrl(), metric.getApiUrl())
                || !StrUtil.equals(snapshot.getCode(), metric.getMetricCode())
                || !StrUtil.equals(snapshot.getDimension(), metric.getDimension())
                || !StrUtil.equals(snapshot.getName(), metric.getMetricName())
                || !StrUtil.equals(snapshot.getTimeliness(), metric.getTimeliness())
                || !StrUtil.equals(snapshot.getPhysicalLevel(), metric.getPhysicalLevel())
                || !StrUtil.equals(snapshot.getType(), metric.getType())
                || !StrUtil.equals(snapshot.getTagInfoId(), metric.getTagInfoId())
                || !isSameColumns(resultSnapshot.getMetricDto().getColumnInfoList(), metric.getColumnInfoList());
    }

    /**
     * 判断两个list对象有没有变化.
     * @param list1 list1
     * @param list2 list2
     * @return boolean
     */
    public boolean isSameColumns(List<MetricColumnInfo> list1, List<MetricColumnInfo> list2) {
        if (list1 == list2) {
            return true;
        }
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        // 将 list 转换为以 "tableName:columnName" 为格式的 Set
        Set<String> set1 = list1.stream()
                .map(info -> info.getTableName() + ":" + info.getColumnName())
                .collect(Collectors.toSet());

        Set<String> set2 = list2.stream()
                .map(info -> info.getTableName() + ":" + info.getColumnName())
                .collect(Collectors.toSet());

        // 比较两个集合是否相等
        return set1.equals(set2);
    }

    // ===================== 实现父类抽象方法 =====================

    /**
     * 判断是否支持该资源类型.
     *
     * @param resourceType 资源类型枚举
     * @return true-支持，false-不支持
     */
    @Override
    boolean supports(ResourceTypeEnum resourceType) {
        return ResourceTypeEnum.METRIC.equals(resourceType);
    }

    @Override
    protected AssetLineageResourceEntity buildResourceEntity(MetricInfoDto metric, Date now, String currentUser) {
        ResourceSnapshotBuilder.MetricDtoInfo metricDto = new ResourceSnapshotBuilder.MetricDtoInfo();
        metricDto.setCode(metric.getMetricCode());
        metricDto.setDimension(metric.getDimension());
        metricDto.setName(metric.getMetricName());
        metricDto.setColumnName(metric.getColumnName());
        metricDto.setTableName(metric.getTableName());
        metricDto.setTimeliness(metric.getTimeliness());
        metricDto.setPhysicalLevel(metric.getPhysicalLevel());
        metricDto.setType(metric.getType());
        metricDto.setTagInfoId(metric.getTagInfoId());
        // 当datasourceName不为空时，查询数据库获取DataSourceInfoDto
        String datasourceName = metric.getDatasourceName();
        if (StrUtil.isNotEmpty(datasourceName)) {
            metricDto.setDatasourceName(datasourceName);
            DataSourceInfoQueryDto queryDto = new DataSourceInfoQueryDto();
            queryDto.setName(datasourceName);
            List<DataSourceInfoDto> dataSourceInfoList = dataSourceInfoService.listDataSource(queryDto);
            if (dataSourceInfoList != null && !dataSourceInfoList.isEmpty()) {
                metricDto.setDataSourceInfo(dataSourceInfoList.get(0));
            }
        }

        // [V2] 设置 columnInfoList（多字段支持）
        if (CollectionUtil.isNotEmpty(metric.getColumnInfoList())) {
            metricDto.setColumnInfoList(metric.getColumnInfoList());
        }

        ResourceSnapshotBuilder.MetricResourceSnapshot metricResourceSnapshot = ResourceSnapshotBuilder.builder(metric.getApiResourceSnapshot());

        metricResourceSnapshot.setThirdMetricId(metric.getThirdMetricId());
        metricResourceSnapshot.setParentResourceId(metric.getParentResourceId());
        metricResourceSnapshot.setDimension(metric.getDimension());
        metricResourceSnapshot.setCode(metric.getMetricCode());
        metricResourceSnapshot.setName(metric.getMetricName());
        metricResourceSnapshot.setTimeliness(metric.getTimeliness());
        metricResourceSnapshot.setPhysicalLevel(metric.getPhysicalLevel());
        metricResourceSnapshot.setType(metric.getType());
        metricResourceSnapshot.setTagInfoId(metric.getTagInfoId());
        String resourceName = resourceName(metric);
        AssetLineageResourceEntity entity = new AssetLineageResourceEntity();
        entity.setResourceName(resourceName);
        entity.setResourceType(ResourceTypeEnum.METRIC.getResouceType());
        entity.setResourceTag(ResourceTagEnum.NODE_AND_EDGE.getResourceTagType());
        entity.setResourceSnapshot(JacksonUtils.pojo2JsonNodeOrNull(metricResourceSnapshot));
        ResourceSnapshotBuilder.MetricResourceResultSnapshot metricResourceResultSnapshot =
                new ResourceSnapshotBuilder.MetricResourceResultSnapshot();
        metricResourceResultSnapshot.setMetricDto(metricDto);
        entity.setResultSnapshot(JacksonUtils.pojo2JsonNodeOrNull(metricResourceResultSnapshot));
        // 指标默认直接就是导入血缘成功
        entity.setStatus(ResourceStatusEnum.PARSE_SUCCESS.getStatus());
        entity.setCreator(currentUser);
        entity.setUpdater(currentUser);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        return entity;
    }

    @Override
    protected List<AssetLineageNodeEntity> buildNodeEntities(MetricInfoDto metric, AssetLineageResourceEntity resource, Date now,
            String currentUser) {
        List<AssetLineageNodeEntity> nodeList = new ArrayList<>();
        ResourceSnapshotBuilder.MetricResourceSnapshot metricResourceSnapshot = ResourceSnapshotBuilder.builder(resource);
        final ResourceSnapshotBuilder.MetricResourceResultSnapshot metricResourceResultSnapshot =
                ResourceSnapshotBuilder.builder(resource, true);

        // API节点和指标节点（始终创建）
        nodeList.add(createApiNode(metricResourceSnapshot, now, currentUser));
        nodeList.add(createMetricNode(metricResourceSnapshot, now, currentUser));

        //添加这段逻辑，如果metricResourceSnapshot 快照中的tag_info_id不为空，说明是数仓指标，需要创建关联的统一指标
        //搜索方法 tag_info_id+快照中的dimension  拼接，去资源表中搜索  资源快照中 thirdMetricId = tag_info_id+“_”+dimension 的资源，创建createMetricNode 统一指标的节点

        if (metricResourceSnapshot.getTagInfoId() != null) {
            AssetLineageResourceEntity unAssetLineageResourceEntity = resourceMapper
                    .selectByThirdMetricId(metricResourceSnapshot.getTagInfoId() + SystemConstant.UNDER_LINE + metricResourceSnapshot.getDimension());
            if (unAssetLineageResourceEntity != null) {
                nodeList.add(createMetricNode(ResourceSnapshotBuilder.builder(unAssetLineageResourceEntity), now, currentUser));
            }
        }

        // [V2] 判断是否使用columnInfoList（多字段支持）
        List<MetricColumnInfo> columnInfoList = metricResourceResultSnapshot.getMetricDto().getColumnInfoList();
        if (CollectionUtil.isNotEmpty(columnInfoList)) {
            // [V2修复] 先按tableName去重，只创建唯一的表节点
            Map<String, MetricColumnInfo> uniqueTableMap = columnInfoList.stream()
                    .collect(Collectors.toMap(MetricColumnInfo::getTableName, v -> v, (v1, v2) -> v1));

            // 创建去重后的表节点
            for (MetricColumnInfo tableInfo : uniqueTableMap.values()) {
                nodeList.add(createTableNodeByColumnInfo(metricResourceResultSnapshot, tableInfo, now, currentUser));
            }

            // [V2修复] 再按 tableName+columnName 去重，只创建唯一的字段节点
            Map<String, MetricColumnInfo> uniqueColumnMap = columnInfoList.stream()
                    .collect(Collectors.toMap(
                            c -> c.getTableName() + ":" + c.getColumnName(),
                            v -> v, (v1, v2) -> v1));

            // 为每个去重后的字段创建字段节点
            for (MetricColumnInfo columnInfo : uniqueColumnMap.values()) {
                nodeList.add(createColumnNodeByColumnInfo(metricResourceResultSnapshot, columnInfo, now, currentUser));
            }
        } else {
            if (metricResourceSnapshot.getType().equals("dw")) {
                // 兼容旧版：单个表/字段
                nodeList.add(createTableNode(metricResourceResultSnapshot, now, currentUser));
                nodeList.add(createColumnNode(metricResourceResultSnapshot, now, currentUser));
            }
        }

        return nodeList;
    }

    @Override
    protected List<AssetLineageEdgeEntity> buildEdgeEntities(MetricInfoDto metric, AssetLineageResourceEntity resource,
            Date now, String currentUser) {
        List<AssetLineageEdgeEntity> edgeList = new ArrayList<>();
        ResourceSnapshotBuilder.MetricResourceSnapshot metricResourceSnapshot = ResourceSnapshotBuilder.builder(resource);
        ResourceSnapshotBuilder.MetricResourceResultSnapshot metricResourceResultSnapshot = ResourceSnapshotBuilder.builder(resource, true);
        //统一指标不需要产生边，数仓指标出现的时候才需要产生边
        if (metricResourceSnapshot.getType().equals("un")) {
            return edgeList;
        }

        // [V2] 判断是否使用columnInfoList（多字段支持）
        List<MetricColumnInfo> columnInfoList = metricResourceResultSnapshot.getMetricDto().getColumnInfoList();
        String apiNodeUrn = buildApiNodeUrn(metricResourceSnapshot);
        String metricNodeUrn = buildMetricNodeUrn(metricResourceSnapshot);

        if (CollectionUtil.isNotEmpty(columnInfoList)) {
            // [V2修复] 先按tableName去重，只创建唯一的表→API边
            Map<String, MetricColumnInfo> uniqueTableMap = columnInfoList.stream()
                    .collect(Collectors.toMap(MetricColumnInfo::getTableName, v -> v, (v1, v2) -> v1));

            // 创建去重后的表→API边
            for (MetricColumnInfo tableInfo : uniqueTableMap.values()) {
                String tableNodeUrn = buildTableNodeUrnByColumnInfo(metricResourceResultSnapshot, tableInfo);
                edgeList.add(super.buildEdge(tableNodeUrn, apiNodeUrn, resource, now, currentUser));
            }

            // [V2修复] 再按 tableName+columnName 去重，只创建唯一的字段→指标边
            Map<String, MetricColumnInfo> uniqueColumnMap = columnInfoList.stream()
                    .collect(Collectors.toMap(
                            c -> c.getTableName() + ":" + c.getColumnName(),
                            v -> v, (v1, v2) -> v1));

            // 为每个去重后的字段创建字段→指标边
            for (MetricColumnInfo columnInfo : uniqueColumnMap.values()) {
                String columnNodeUrn = buildColumnNodeUrnByColumnInfo(metricResourceResultSnapshot, columnInfo);
                edgeList.add(super.buildEdge(columnNodeUrn, metricNodeUrn, resource, now, currentUser));
            }
        } else {
            // 兼容旧版：单个表/字段的边
            String tableNodeUrn = buildTableNodeUrn(metricResourceResultSnapshot);
            edgeList.add(super.buildEdge(tableNodeUrn, apiNodeUrn, resource, now, currentUser));

            String columnNodeUrn = buildColumnNodeUrn(metricResourceResultSnapshot);
            edgeList.add(super.buildEdge(columnNodeUrn, metricNodeUrn, resource, now, currentUser));
        }

        if (metricResourceSnapshot.getTagInfoId() != null) {
            AssetLineageResourceEntity unAssetLineageResourceEntity = resourceMapper
                    .selectByThirdMetricId(metricResourceSnapshot.getTagInfoId() + SystemConstant.UNDER_LINE + metricResourceSnapshot.getDimension());
            if (unAssetLineageResourceEntity != null) {
                //这里需要单独产生2条边
                //指标的边
                //API之间的边，并行API之间的边需要携带边的属性
                ResourceSnapshotBuilder.MetricResourceSnapshot unMetricResourceSnapshot = ResourceSnapshotBuilder
                        .builder(unAssetLineageResourceEntity);
                String unApiNodeUrn = buildApiNodeUrn(unMetricResourceSnapshot);
                String unMetricNodeUrn = buildMetricNodeUrn(unMetricResourceSnapshot);
                edgeList.add(super.buildEdge(metricNodeUrn, unMetricNodeUrn, resource, now, currentUser));
                Set<MetricsTagDto> tagSet = new HashSet<>();
                tagSet.add(new MetricsTagDto(unMetricResourceSnapshot.getCode(), unMetricResourceSnapshot.getDimension()));
                edgeList.add(super.buildEdge(apiNodeUrn, unApiNodeUrn, resource, now, currentUser,
                        wrapTagSet(JacksonUtils.pojo2JsonNodeOrNull(tagSet))));
                //构建边的属性

            }
        }

        return edgeList;
    }

    // ===================== 节点构建方法 =====================

    /**
     * 创建API节点.
     */
    private AssetLineageNodeEntity createApiNode(ResourceSnapshotBuilder.MetricResourceSnapshot snapshot, Date now, String currentUser) {
        String nodeUrn = buildApiNodeUrn(snapshot);

        NodePropBuilder.ApiNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.API);
        prop.setUrn(nodeUrn);
        prop.setServiceCode(snapshot.getServiceEnName());
        prop.setServiceName(snapshot.getServiceEnName());
        prop.setUrl(snapshot.getRequestUrl());
        prop.setUrlName(snapshot.getCode() + "+" + snapshot.getDimension());

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(snapshot.getRequestUrl());
        node.setNodeType("SERVICE");
        node.setNodeSubType(NodeSubTypeEnum.API.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(prop));
        node.setCreator(currentUser);
        node.setUpdater(currentUser);
        node.setCreateTime(now);
        node.setUpdateTime(now);

        return node;
    }

    /**
     * 创建指标节点.
     */
    private AssetLineageNodeEntity createMetricNode(ResourceSnapshotBuilder.MetricResourceSnapshot snapshot, Date now, String currentUser) {
        String nodeUrn = buildMetricNodeUrn(snapshot);

        NodePropBuilder.MetricNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.METRIC);
        prop.setUrn(nodeUrn);
        prop.setServiceCode(snapshot.getServiceEnName());
        prop.setServiceName(snapshot.getServiceEnName());
        prop.setUrl(snapshot.getRequestUrl());
        prop.setUrlName("");
        prop.setDimension(snapshot.getDimension());
        prop.setMetricCode(snapshot.getCode());
        prop.setMetricName(snapshot.getName());
        prop.setTimeliness(snapshot.getTimeliness());
        prop.setPhysicalLevel(snapshot.getPhysicalLevel());
        prop.setType(snapshot.getType());

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(snapshot.getCode());
        node.setNodeType("SERVICE");
        node.setNodeSubType(NodeSubTypeEnum.METRIC.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(prop));
        node.setCreator(currentUser);
        node.setUpdater(currentUser);
        node.setCreateTime(now);
        node.setUpdateTime(now);

        return node;
    }

    /**
     * 创建表节点.
     */
    private AssetLineageNodeEntity createTableNode(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot, Date now, String currentUser) {
        String nodeUrn = buildTableNodeUrn(snapshot);

        NodePropBuilder.TableNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.TABLE);
        prop.setUrn(nodeUrn);

        // 优先使用dataSourceInfo，为空则使用resourceSyncConfig
        DataSourceInfoDto dataSourceInfo = snapshot.getMetricDto().getDataSourceInfo();
        if (dataSourceInfo != null) {
            prop.setDatabaseType(dataSourceInfo.getDatabaseType());
            prop.setDatasourceName(dataSourceInfo.getName());
            prop.setDatabaseName(dataSourceInfo.getDatabaseName());
            prop.setSchemaName(dataSourceInfo.getSchemaName());
        } else {
            prop.setDatabaseType(resourceSyncConfig.getDatabaseType());
            prop.setDatasourceName(resourceSyncConfig.getDatasourceName());
            prop.setDatabaseName(resourceSyncConfig.getDatabaseName());
            prop.setSchemaName(resourceSyncConfig.getSchemaName());
        }
        prop.setTableName(snapshot.getMetricDto().getTableName());
        prop.setTableDesc("");

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(snapshot.getMetricDto().getTableName());
        node.setNodeType("DATABASE");
        node.setNodeSubType(NodeSubTypeEnum.TABLE.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(prop));
        node.setCreator(currentUser);
        node.setUpdater(currentUser);
        node.setCreateTime(now);
        node.setUpdateTime(now);

        return node;
    }

    /**
     * 创建字段节点.
     */
    private AssetLineageNodeEntity createColumnNode(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot, Date now, String currentUser) {
        String nodeUrn = buildColumnNodeUrn(snapshot);

        NodePropBuilder.ColumnNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.COLUMN);
        prop.setUrn(nodeUrn);

        // 优先使用dataSourceInfo，为空则使用resourceSyncConfig
        DataSourceInfoDto dataSourceInfo = snapshot.getMetricDto().getDataSourceInfo();
        if (dataSourceInfo != null) {
            prop.setDatabaseType(dataSourceInfo.getDatabaseType());
            prop.setDatasourceName(dataSourceInfo.getName());
            prop.setDatabaseName(dataSourceInfo.getDatabaseName());
            prop.setSchemaName(dataSourceInfo.getSchemaName());
        } else {
            prop.setDatabaseType(resourceSyncConfig.getDatabaseType());
            prop.setDatasourceName(resourceSyncConfig.getDatasourceName());
            prop.setDatabaseName(resourceSyncConfig.getDatabaseName());
            prop.setSchemaName(resourceSyncConfig.getSchemaName());
        }
        prop.setTableName(snapshot.getMetricDto().getTableName());
        prop.setTableDesc("");
        prop.setColumnName(snapshot.getMetricDto().getColumnName());
        prop.setColumnDesc("");

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(snapshot.getMetricDto().getColumnName());
        node.setNodeType("DATABASE");
        node.setNodeSubType(NodeSubTypeEnum.COLUMN.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(prop));
        node.setCreator(currentUser);
        node.setUpdater(currentUser);
        node.setCreateTime(now);
        node.setUpdateTime(now);

        return node;
    }

    /**
     * [V2] 根据字段信息创建表节点.
     *
     * @param snapshot       资源快照
     * @param columnInfo     字段信息
     * @param now            当前时间
     * @param currentUser    当前用户
     * @return 表节点
     */
    private AssetLineageNodeEntity createTableNodeByColumnInfo(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot,
            MetricColumnInfo columnInfo, Date now, String currentUser) {
        String nodeUrn = buildTableNodeUrnByColumnInfo(snapshot, columnInfo);

        NodePropBuilder.TableNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.TABLE);
        prop.setUrn(nodeUrn);

        // 优先使用dataSourceInfo，为空则使用resourceSyncConfig
        DataSourceInfoDto dataSourceInfo = snapshot.getMetricDto().getDataSourceInfo();
        if (dataSourceInfo != null) {
            prop.setDatabaseType(dataSourceInfo.getDatabaseType());
            prop.setDatasourceName(dataSourceInfo.getName());
            prop.setDatabaseName(dataSourceInfo.getDatabaseName());
            prop.setSchemaName(dataSourceInfo.getSchemaName());
        } else {
            prop.setDatabaseType(resourceSyncConfig.getDatabaseType());
            prop.setDatasourceName(resourceSyncConfig.getDatasourceName());
            prop.setDatabaseName(resourceSyncConfig.getDatabaseName());
            prop.setSchemaName(resourceSyncConfig.getSchemaName());
        }
        prop.setTableName(columnInfo.getTableName());
        prop.setTableDesc("");

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(columnInfo.getTableName());
        node.setNodeType("DATABASE");
        node.setNodeSubType(NodeSubTypeEnum.TABLE.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(prop));
        node.setCreator(currentUser);
        node.setUpdater(currentUser);
        node.setCreateTime(now);
        node.setUpdateTime(now);

        return node;
    }

    /**
     * [V2] 根据字段信息创建字段节点.
     *
     * @param snapshot    资源快照
     * @param columnInfo  字段信息
     * @param now         当前时间
     * @param currentUser 当前用户
     * @return 字段节点
     */
    private AssetLineageNodeEntity createColumnNodeByColumnInfo(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot,
            MetricColumnInfo columnInfo, Date now, String currentUser) {
        String nodeUrn = buildColumnNodeUrnByColumnInfo(snapshot, columnInfo);

        NodePropBuilder.ColumnNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.COLUMN);
        prop.setUrn(nodeUrn);

        // 优先使用dataSourceInfo，为空则使用resourceSyncConfig
        DataSourceInfoDto dataSourceInfo = snapshot.getMetricDto().getDataSourceInfo();
        if (dataSourceInfo != null) {
            prop.setDatabaseType(dataSourceInfo.getDatabaseType());
            prop.setDatasourceName(dataSourceInfo.getName());
            prop.setDatabaseName(dataSourceInfo.getDatabaseName());
            prop.setSchemaName(dataSourceInfo.getSchemaName());
        } else {
            prop.setDatabaseType(resourceSyncConfig.getDatabaseType());
            prop.setDatasourceName(resourceSyncConfig.getDatasourceName());
            prop.setDatabaseName(resourceSyncConfig.getDatabaseName());
            prop.setSchemaName(resourceSyncConfig.getSchemaName());
        }
        prop.setTableName(columnInfo.getTableName());
        prop.setTableDesc("");
        prop.setColumnName(columnInfo.getColumnName());
        prop.setColumnDesc("");

        AssetLineageNodeEntity node = new AssetLineageNodeEntity();
        node.setId(UUID.randomUUID());
        node.setNodeUrn(nodeUrn);
        node.setNodeName(columnInfo.getColumnName());
        node.setNodeType("DATABASE");
        node.setNodeSubType(NodeSubTypeEnum.COLUMN.getNodeSubType());
        node.setNodeProp(JacksonUtils.pojo2JsonNodeOrNull(prop));
        node.setCreator(currentUser);
        node.setUpdater(currentUser);
        node.setCreateTime(now);
        node.setUpdateTime(now);

        return node;
    }

    // ===================== URN构建方法 =====================

    /**
     * 构建API节点URN.
     */
    private String buildApiNodeUrn(ResourceSnapshotBuilder.MetricResourceSnapshot snapshot) {
        return String.join(SystemConstant.COLON,
                snapshot.getOrganization(),
                snapshot.getBusinessDomain(),
                snapshot.getEnv(),
                snapshot.getServiceType(),
                snapshot.getServiceEnName(),
                snapshot.getRequestType(),
                snapshot.getRequestUrl());
    }

    /**
     * 构建指标节点URN.
     */
    private String buildMetricNodeUrn(ResourceSnapshotBuilder.MetricResourceSnapshot snapshot) {
        return buildApiNodeUrn(snapshot) + SystemConstant.COLON + snapshot.getCode() + SystemConstant.COMMA + snapshot.getDimension();
    }

    /**
     * 构建表节点URN.
     */
    private String buildTableNodeUrn(ResourceSnapshotBuilder.MetricResourceResultSnapshot resultSnapshot) {
        DataSourceInfoDto dataSourceInfo = resultSnapshot.getMetricDto().getDataSourceInfo();
        if (dataSourceInfo != null) {
            return String.join(SystemConstant.COLON,
                    dataSourceInfo.getDatabaseType(),
                    dataSourceInfo.getName(),
                    dataSourceInfo.getDatabaseName(),
                    dataSourceInfo.getSchemaName(),
                    resultSnapshot.getMetricDto().getTableName());
        } else {
            return String.join(SystemConstant.COLON,
                    resourceSyncConfig.getDatabaseType(),
                    resourceSyncConfig.getDatasourceName(),
                    resourceSyncConfig.getDatabaseName(),
                    resourceSyncConfig.getSchemaName(),
                    resultSnapshot.getMetricDto().getTableName());
        }

    }

    /**
     * 构建字段节点URN.
     */
    private String buildColumnNodeUrn(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot) {
        return buildTableNodeUrn(snapshot) + SystemConstant.COLON + snapshot.getMetricDto().getColumnName();
    }

    /**
     * [V2] 根据字段信息构建表节点URN.
     */
    private String buildTableNodeUrnByColumnInfo(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot, MetricColumnInfo columnInfo) {
        // 使用与原版相同的构建方式，只是tableName从columnInfo获取
        DataSourceInfoDto dataSourceInfo = snapshot.getMetricDto().getDataSourceInfo();
        if (dataSourceInfo != null) {
            return String.join(SystemConstant.COLON,
                    dataSourceInfo.getDatabaseType(),
                    dataSourceInfo.getName(),
                    dataSourceInfo.getDatabaseName(),
                    dataSourceInfo.getSchemaName(),
                    columnInfo.getTableName());
        } else {
            return String.join(SystemConstant.COLON,
                    resourceSyncConfig.getDatabaseType(),
                    resourceSyncConfig.getDatasourceName(),
                    resourceSyncConfig.getDatabaseName(),
                    resourceSyncConfig.getSchemaName(),
                    columnInfo.getTableName());
        }
    }

    /**
     * [V2] 根据字段信息构建字段节点URN.
     */
    private String buildColumnNodeUrnByColumnInfo(ResourceSnapshotBuilder.MetricResourceResultSnapshot snapshot, MetricColumnInfo columnInfo) {
        return buildTableNodeUrnByColumnInfo(snapshot, columnInfo) + SystemConstant.COLON + columnInfo.getColumnName();
    }

    // ===================== 辅助方法 =====================

    /**
     * 从配置构建API资源快照.
     */
    private ResourceSnapshotBuilder.ApiResourceSnapshot buildApiResourceSnapshot(MetricInfoDto metric) {
        ResourceSnapshotBuilder.ApiResourceSnapshot apiSnapshot = new ResourceSnapshotBuilder.ApiResourceSnapshot();
        apiSnapshot.setOrganization(resourceSyncConfig.getOrganization());
        apiSnapshot.setBusinessDomain(resourceSyncConfig.getBusinessDomain());
        apiSnapshot.setEnv(resourceSyncConfig.getEnv());
        apiSnapshot.setServiceType(resourceSyncConfig.getServiceType());

        // 根据 type 判断使用哪个 serviceEnName
        if ("un".equals(metric.getType())) {
            apiSnapshot.setServiceEnName(resourceSyncConfig.getServiceEnNameUN());
        } else {
            apiSnapshot.setServiceEnName(resourceSyncConfig.getServiceEnName());
        }

        apiSnapshot.setRequestType(resourceSyncConfig.getRequestType());
        apiSnapshot.setRequestUrl(metric.getApiUrl());
        apiSnapshot.setDimension(metric.getDimension());
        apiSnapshot.setPhysicalLevel(metric.getPhysicalLevel());
        apiSnapshot.setTimeliness(metric.getTimeliness());
        apiSnapshot.setType(metric.getType());
        apiSnapshot.setTagInfoId(metric.getTagInfoId());
        return apiSnapshot;
    }

    /**
     * 构建资源名称.
     */
    protected String resourceName(MetricInfoDto metric) {
        return String.join(SystemConstant.COLON,
                metric.getApiResourceSnapshot().getServiceEnName(),
                metric.getApiResourceSnapshot().getRequestType(),
                metric.getApiUrl(),
                String.join(SystemConstant.COMMA, metric.getMetricCode(), metric.getDimension()));
    }

}
