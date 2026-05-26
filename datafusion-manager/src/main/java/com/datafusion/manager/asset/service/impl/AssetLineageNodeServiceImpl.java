package com.datafusion.manager.asset.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.asset.common.service.BaseServiceImpl;
import com.datafusion.manager.asset.dao.AssetLineageNodeMapper;
import com.datafusion.manager.asset.dto.AssetLineageNodeResourceDto;
import com.datafusion.manager.asset.dto.AssetLineageNodeResourceVo;
import com.datafusion.manager.asset.dto.AssetNodeRequestVo;
import com.datafusion.manager.asset.dto.AssetNodeResourceDto;
import com.datafusion.manager.asset.dto.AssetNodeRichDto;
import com.datafusion.manager.asset.dto.ColumnNodeDto;
import com.datafusion.manager.asset.dto.DatasourceAssetRichDto;
import com.datafusion.manager.asset.dto.DatasourceAssetRichRequestVo;
import com.datafusion.manager.asset.dto.EdgeRequestVo;
import com.datafusion.manager.asset.dto.MenuNodePropVo;
import com.datafusion.manager.asset.dto.TableColumnRequestVo;
import com.datafusion.manager.asset.dto.TableColumnsNodeDto;
import com.datafusion.manager.asset.dto.builder.NodePropBuilder;
import com.datafusion.manager.asset.dto.builder.ResourceSnapshotBuilder;
import com.datafusion.manager.asset.enums.MenuSubTypeEnum;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.enums.NodeTypeEnum;
import com.datafusion.manager.asset.enums.ResourceTagEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.service.AssetLineageNodeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Service
@Slf4j
public class AssetLineageNodeServiceImpl extends BaseServiceImpl<AssetLineageNodeMapper, AssetLineageNodeEntity> implements AssetLineageNodeService {

    @Override
    public PageResponse<TableColumnsNodeDto> pageTableColumns(PageQuery<TableColumnRequestVo> query) {
        int totalCount = baseMapper.pageTableColumnsCount(query);
        if (totalCount == 0 || totalCount <= query.getOffset()) {
            return PageResponse.emptyPage(query);
        }
        return new PageResponse<TableColumnsNodeDto>(baseMapper.pageTableColumnsList(query), query.getSize(), query.getCurrent(), totalCount);
    }

    //    /**
    //     * 根据 node_urn 批量保存或更新资源
    //     * <p>
    //     * 实现逻辑：
    //     * 1. 根据 node_urn 批量查询已存在的记录
    //     * 2. 为新数据设置正确的 ID（存在则用已有ID，不存在则生成新ID）
    //     * 3. 使用 MyBatis-Plus 的 saveOrUpdateBatch 批量保存或更新
    //     * </p>
    //     *
    //     * @param nodeList 资源列表
    //     */
    //    @Override
    //    @Transactional(rollbackFor = Exception.class)
    //    public void batchSaveOrUpdateByNodeUrn(List<AssetLineageNodeEntity> nodeList) {
    //        if (CollectionUtil.isEmpty(nodeList)) {
    //            log.warn("node列表为空，跳过批量保存");
    //            return;
    //        }
    //
    //        log.info("开始批量保存或更新资源，数量: {}", nodeList.size());
    //
    //        // 1. 提取所有 NodeUrn
    //        Set<String> nodeUrns = nodeList.stream()
    //                .map(AssetLineageNodeEntity::getNodeUrn)
    //                .filter(Objects::nonNull)
    //                .collect(Collectors.toSet());
    //
    //        if (nodeUrns.isEmpty()) {
    //            log.warn("所有node的 node_urn 为空，跳过批量保存");
    //            return;
    //        }
    //
    //        // 2. 批量查询已存在的记录（只查询 id 和 resource_name，减少数据传输）
    //        LambdaQueryWrapper<AssetLineageNodeEntity> queryWrapper = Wrappers.lambdaQuery(AssetLineageNodeEntity.class)
    //                .select(AssetLineageNodeEntity::getId, AssetLineageNodeEntity::getNodeUrn)
    //                .in(AssetLineageNodeEntity::getNodeUrn, nodeUrns);
    //
    //        Map<String, UUID> existingIdMap = this.list(queryWrapper).stream()
    //                .collect(Collectors.toMap(
    //                        AssetLineageNodeEntity::getNodeUrn,
    //                        AssetLineageNodeEntity::getId,
    //                        (oldId, newId) -> oldId  // 如果有重复，保留第一个
    //                ));
    //
    //        log.debug("查询到已存在的资源数量: {}", existingIdMap.size());
    //
    //        // 3. 为每条数据设置正确的 ID
    //        int updateCount = 0;
    //        int insertCount = 0;
    //
    //        for (AssetLineageNodeEntity node : nodeList) {
    //            String nodeUrn = node.getNodeUrn();
    //            UUID existingId = existingIdMap.get(nodeUrn);
    //
    //            if (existingId != null) {
    //                // 已存在：使用数据库中的 ID（后续会执行 UPDATE）
    //                node.setId(existingId);
    //                updateCount++;
    //                log.debug("资源 {} 已存在，将执行更新，ID: {}", nodeUrn, existingId);
    //            } else {
    //                // 不存在：确保有新 ID（后续会执行 INSERT）
    //                if (node.getId() == null) {
    //                    node.setId(UUID.randomUUID());
    //                }
    //                insertCount++;
    //                log.debug("资源 {} 不存在，将执行插入，ID: {}", nodeUrn, node.getId());
    //            }
    //        }
    //
    //        // 4. 使用 MyBatis-Plus 的 saveOrUpdateBatch 批量保存或更新
    //        // 优点：自动根据实体类映射所有字段，无需手动维护字段列表
    //        boolean success = this.saveOrUpdateBatch(nodeList);
    //
    //        if (success) {
    //            log.info("批量保存或更新完成，插入: {} 条，更新: {} 条", insertCount, updateCount);
    //        } else {
    //            log.error("批量保存或更新失败");
    //            throw new RuntimeException("批量保存或更新资源失败");
    //        }
    //    }

    /**
     * 根据 node_urn 批量保存或更新节点.
     *
     * @param nodeList 节点列表
     * @return 保存或更新是否成功
     */
    public boolean batchSaveOrUpdateByNodeUrn(List<AssetLineageNodeEntity> nodeList) {
        log.info("开始根据 node_urn 批量保存或更新节点");

        // 调用父类的通用方法

        return this.batchSaveOrUpdateByUniqueField(
                nodeList,
                AssetLineageNodeEntity::getNodeUrn, // 唯一字段：node_urn
                AssetLineageNodeEntity::getId, // ID getter
                AssetLineageNodeEntity::setId, // ID setter
                entity -> UUID.randomUUID() // 基于 URN 生成确定性 ID
        );
    }

    @Override
    public List<DatasourceAssetRichDto> queryDatasourceAssetRich(DatasourceAssetRichRequestVo request) {
        String searchContent = request.getSearchContent();
        String datasourceName = request.getDatasourceName();
        String tableName = request.getTableName();
        int searchType = request.getSearchType();

        // 1. searchContent、datasourceName、tableName 都为空时，只搜索数据源
        if (org.springframework.util.StringUtils.isEmpty(searchContent)
                && org.springframework.util.StringUtils.isEmpty(datasourceName)
                && org.springframework.util.StringUtils.isEmpty(tableName)) {
            return queryDataSources();
        }

        // 2. datasourceName 不为空，tableName 为空时，钻取查询数据源下的表信息
        if (!org.springframework.util.StringUtils.isEmpty(datasourceName) && org.springframework.util.StringUtils.isEmpty(tableName)) {
            return queryTablesByDatasource(datasourceName);
        }

        // 3. tableName 不为空时，钻取查询表下面的字段信息
        if (!org.springframework.util.StringUtils.isEmpty(tableName)) {
            return queryColumnsByTable(datasourceName, tableName);
        }

        // 4. searchContent 不为空时，基于urn进行模糊查询
        if (!org.springframework.util.StringUtils.isEmpty(searchContent)) {
            return searchByContent(request);
        }

        return new ArrayList<>();
    }

    /**
     * 查询所有数据源列表.
     */
    private List<DatasourceAssetRichDto> queryDataSources() {
        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetLineageNodeEntity::getNodeType, NodeTypeEnum.DATABASE.getNodeType())
                .eq(AssetLineageNodeEntity::getNodeSubType, NodeSubTypeEnum.TABLE.getNodeSubType());

        List<AssetLineageNodeEntity> nodes = this.list(wrapper);
        if (CollectionUtil.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        // 按数据源名称分组
        Map<String, List<AssetLineageNodeEntity>> groupByDs = nodes.stream()
                .collect(java.util.stream.Collectors.groupingBy(node -> extractDatasourceName(node.getNodeUrn())));

        List<DatasourceAssetRichDto> result = new ArrayList<>();
        for (Map.Entry<String, List<AssetLineageNodeEntity>> entry : groupByDs.entrySet()) {
            DatasourceAssetRichDto dto = new DatasourceAssetRichDto();
            dto.setDatasourceName(entry.getKey());
            dto.setDatasourceId(entry.getKey());
            // 表列表为空，等后续按需查询
            dto.setTableList(new ArrayList<>());
            result.add(dto);
        }
        return result;
    }

    /**
     * 根据数据源名称查询表列表.
     */
    private List<DatasourceAssetRichDto> queryTablesByDatasource(String datasourceName) {
        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetLineageNodeEntity::getNodeType, NodeTypeEnum.DATABASE.getNodeType())
                .eq(AssetLineageNodeEntity::getNodeSubType, NodeSubTypeEnum.TABLE.getNodeSubType())
                .likeRight(AssetLineageNodeEntity::getNodeUrn, "maxcompute:" + datasourceName + ":");

        List<AssetLineageNodeEntity> nodes = this.list(wrapper);
        if (CollectionUtil.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        DatasourceAssetRichDto dto = new DatasourceAssetRichDto();
        dto.setDatasourceName(datasourceName);

        // 按表名分组，构建表和字段结构
        Map<String, List<AssetLineageNodeEntity>> groupByTable = nodes.stream()
                .collect(java.util.stream.Collectors.groupingBy(node -> extractTableName(node.getNodeUrn())));

        List<TableColumnsNodeDto> tableList = new ArrayList<>();
        for (Map.Entry<String, List<AssetLineageNodeEntity>> entry : groupByTable.entrySet()) {
            TableColumnsNodeDto tableDto = new TableColumnsNodeDto();
            tableDto.setTableNodeName(entry.getKey());
            // 取第一个节点的URN作为表级URN
            tableDto.setTableNodeUrn(entry.getValue().get(0).getNodeUrn());
            // 字段列表为空，等后续按需查询
            tableDto.setColumns(new ArrayList<>());
            tableList.add(tableDto);
        }
        dto.setTableList(tableList);

        return Collections.singletonList(dto);
    }

    /**
     * 根据数据源名称和表名查询字段列表.
     */
    private List<DatasourceAssetRichDto> queryColumnsByTable(String datasourceName, String tableName) {
        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetLineageNodeEntity::getNodeType, NodeTypeEnum.DATABASE.getNodeType())
                .eq(AssetLineageNodeEntity::getNodeSubType, NodeSubTypeEnum.COLUMN.getNodeSubType())
                .likeRight(AssetLineageNodeEntity::getNodeUrn, "maxcompute:" + datasourceName + ":" + tableName + ":");

        List<AssetLineageNodeEntity> nodes = this.list(wrapper);
        if (CollectionUtil.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        DatasourceAssetRichDto dto = new DatasourceAssetRichDto();
        dto.setDatasourceName(datasourceName);

        TableColumnsNodeDto tableDto = new TableColumnsNodeDto();
        tableDto.setTableNodeName(tableName);
        // 构建表级URN
        String tableUrn = "maxcompute:" + datasourceName + ":::" + tableName;
        tableDto.setTableNodeUrn(tableUrn);

        // 转换为字段列表
        List<ColumnNodeDto> columns = nodes.stream()
                .map(node -> {
                    ColumnNodeDto columnDto = new ColumnNodeDto();
                    columnDto.setColumnNodeName(extractColumnName(node.getNodeUrn()));
                    columnDto.setColumnNodeUrn(node.getNodeUrn());
                    return columnDto;
                })
                .collect(java.util.stream.Collectors.toList());
        tableDto.setColumns(columns);

        dto.setTableList(Collections.singletonList(tableDto));
        return Collections.singletonList(dto);
    }

    /**
     * 根据searchContent模糊查询.
     */
    private List<DatasourceAssetRichDto> searchByContent(DatasourceAssetRichRequestVo request) {
        String searchContent = request.getSearchContent();
        int searchType = request.getSearchType();

        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(AssetLineageNodeEntity::getNodeUrn, searchContent);

        // searchType=1 时查询字段级，否则只查询表级
        if (searchType == 1) {
            wrapper.eq(AssetLineageNodeEntity::getNodeType, NodeTypeEnum.DATABASE.getNodeType());
        } else {
            wrapper.eq(AssetLineageNodeEntity::getNodeType, NodeTypeEnum.DATABASE.getNodeType())
                    .eq(AssetLineageNodeEntity::getNodeSubType, NodeSubTypeEnum.TABLE.getNodeSubType());
        }

        List<AssetLineageNodeEntity> nodes = this.list(wrapper);
        if (CollectionUtil.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        // 构建树形结构
        return buildTreeData(nodes, searchType);
    }

    /**
     * 构建形数据（搜索结果）.
     */
    private List<DatasourceAssetRichDto> buildTreeData(List<AssetLineageNodeEntity> nodes, int searchType) {
        // 按数据源分组
        Map<String, List<AssetLineageNodeEntity>> groupByDs = nodes.stream()
                .collect(java.util.stream.Collectors.groupingBy(node -> extractDatasourceName(node.getNodeUrn())));

        List<DatasourceAssetRichDto> result = new ArrayList<>();
        for (Map.Entry<String, List<AssetLineageNodeEntity>> entry : groupByDs.entrySet()) {
            DatasourceAssetRichDto dto = new DatasourceAssetRichDto();
            dto.setDatasourceName(entry.getKey());

            List<AssetLineageNodeEntity> dsNodes = entry.getValue();

            if (searchType == 1) {
                // 字段级搜索，返回表和字段
                Map<String, List<AssetLineageNodeEntity>> groupByTable = dsNodes.stream()
                        .collect(java.util.stream.Collectors.groupingBy(node -> extractTableName(node.getNodeUrn())));

                List<TableColumnsNodeDto> tableList = new ArrayList<>();
                for (Map.Entry<String, List<AssetLineageNodeEntity>> tableEntry : groupByTable.entrySet()) {
                    TableColumnsNodeDto tableDto = new TableColumnsNodeDto();
                    tableDto.setTableNodeName(tableEntry.getKey());
                    tableDto.setTableNodeUrn(tableEntry.getValue().get(0).getNodeUrn());

                    List<ColumnNodeDto> columns = tableEntry.getValue().stream()
                            .map(node -> {
                                ColumnNodeDto columnDto = new ColumnNodeDto();
                                columnDto.setColumnNodeName(extractColumnName(node.getNodeUrn()));
                                columnDto.setColumnNodeUrn(node.getNodeUrn());
                                return columnDto;
                            })
                            .collect(java.util.stream.Collectors.toList());
                    tableDto.setColumns(columns);
                    tableList.add(tableDto);
                }
                dto.setTableList(tableList);
            } else {
                // 表级搜索，只返回表
                List<TableColumnsNodeDto> tableList = dsNodes.stream()
                        .map(node -> {
                            TableColumnsNodeDto tableDto = new TableColumnsNodeDto();
                            tableDto.setTableNodeName(extractTableName(node));
                            tableDto.setTableNodeUrn(node.getNodeUrn());
                            tableDto.setColumns(new ArrayList<>());
                            return tableDto;
                        })
                        .collect(java.util.stream.Collectors.toList());
                dto.setTableList(tableList);
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 从URN中提取数据源名称.
     * URN格式: maxcompute:数据源名称:库名:schema名:表名[:字段名]
     */
    private String extractDatasourceName(String urn) {
        if (urn == null) {
            return "";
        }
        String[] parts = urn.split(":");
        return parts.length > 1 ? parts[1] : "";
    }

    /**
     * 从URN中提取表名称.
     */
    private String extractTableName(AssetLineageNodeEntity node) {
        return extractTableName(node.getNodeUrn());
    }

    private String extractTableName(String urn) {
        if (urn == null) {
            return "";
        }
        String[] parts = urn.split(":");
        // 表级URN: maxcompute:ds:db:schema:table (5 parts)
        // 字段级URN: maxcompute:ds:db:schema:table:column (6+ parts)
        if (parts.length > 4) {
            return parts[4];
        }
        return "";
    }

    /**
     * 从URN中提取字段名称.
     */
    private String extractColumnName(String urn) {
        if (urn == null) {
            return "";
        }
        String[] parts = urn.split(":");
        if (parts.length > 5) {
            return parts[5];
        }
        return "";
    }

    @Override
    public List<AssetNodeRichDto> queryAssetNode(AssetNodeRequestVo request) {
        String nodeSubType = request.getNodeSubType();
        String searchContent = request.getSearchContent();
        int searchType = request.getSearchType();
        String dimension = request.getDimension();
        String timeliness = request.getTimeliness();
        String physicalLevel = request.getPhysicalLevel();
        String metricType = request.getMetricType();

        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();

        // searchType=1时过滤nodeSubType = 'COLUMN'的数据
        if (searchType == 1) {
            wrapper.ne(AssetLineageNodeEntity::getNodeSubType, "COLUMN");
            if ("TABLE".equals(nodeSubType)) {
                wrapper.notLike(AssetLineageNodeEntity::getNodeUrn, ":tmp_");
            }
        }

        // nodeSubType 不为空时，按 node_sub_type 搜索
        if (!org.springframework.util.StringUtils.isEmpty(nodeSubType)) {
            wrapper.eq(AssetLineageNodeEntity::getNodeSubType, nodeSubType);
        }

        // searchContent 不为空时，按 node_urn 模糊匹配
        if (!org.springframework.util.StringUtils.isEmpty(searchContent)) {
            if (searchType == 1 && StringUtils.isNotEmpty(request.getDimension())) {
                wrapper.likeLeft(AssetLineageNodeEntity::getNodeUrn,
                        SystemConstant.COLON+searchContent + SystemConstant.COMMA + request.getDimension());
            } else {
                wrapper.eq(AssetLineageNodeEntity::getNodeUrn, searchContent);
            }
        }

        // [新增] nodeSubType = 'METRIC' 且 dimension 不为空时，按 node_prop->>'dimension' 过滤
        if ("METRIC".equals(nodeSubType)) {
            if (!org.springframework.util.StringUtils.isEmpty(dimension)) {
                wrapper.apply("node_prop->>'dimension' = {0}", dimension);
            }
            if (!org.springframework.util.StringUtils.isEmpty(timeliness)) {
                wrapper.apply("node_prop->>'timeliness' = {0}", timeliness);
            }
            if (!org.springframework.util.StringUtils.isEmpty(metricType)) {
                wrapper.apply("node_prop->>'type' = {0}", metricType);
            }
            if (!org.springframework.util.StringUtils.isEmpty(physicalLevel)) {
                wrapper.apply("node_prop->>'physicalLevel' = {0}", physicalLevel);
            }

        }
        // [新增] nodeSubType = 'MENU' 且 componentType 不为空时，按 node_prop->>'componentType' 过滤
        if ("MENU".equals(nodeSubType) && request.getComponentType() != null) {
            wrapper.apply("(node_prop#>>'{componentType}')::int = {0}", request.getComponentType());
        }

        // 两个条件都为空时，默认返回前20个节点
        if (org.springframework.util.StringUtils.isEmpty(nodeSubType) && org.springframework.util.StringUtils.isEmpty(searchContent)) {
            wrapper.last("LIMIT 20");
        } else {
            wrapper.last("LIMIT 1000");
        }

        List<AssetLineageNodeEntity> nodes = this.list(wrapper);
        if (CollectionUtil.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        // 转换为DTO并设置nodeSubTypeDesc
        return nodes.stream()
                .map(node -> {
                    AssetNodeRichDto dto = new AssetNodeRichDto();
                    dto.setNodeUrn(node.getNodeUrn());
                    dto.setNodeName(node.getNodeName());
                    dto.setNodeType(node.getNodeType());
                    dto.setNodeSubType(node.getNodeSubType());
                    dto.setNodeProp(node.getNodeProp());
                    // 设置子类型描述
                    dto.setNodeSubTypeDesc(getNodeSubTypeDesc(node.getNodeSubType()));
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据nodeUrn查询资产节点.
     */
    @Override
    public List<AssetNodeRichDto> queryAssetNodeByNodeUrn(AssetNodeRequestVo request) {
        String nodeSubType = request.getNodeSubType();
        String searchContent = request.getSearchContent();
        int searchType = request.getSearchType();

        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();

        // searchType=1时过滤nodeSubType = 'COLUMN'的数据
        if (searchType == 1) {
            wrapper.ne(AssetLineageNodeEntity::getNodeSubType, "COLUMN");
        }

        // nodeSubType 不为空时，按 node_sub_type 搜索
        if (!org.springframework.util.StringUtils.isEmpty(nodeSubType)) {
            wrapper.eq(AssetLineageNodeEntity::getNodeSubType, nodeSubType);
        }

        // searchContent 不为空时，按 node_urn 模糊匹配
        if (!org.springframework.util.StringUtils.isEmpty(searchContent)) {
            wrapper.eq(AssetLineageNodeEntity::getNodeUrn, searchContent);
        }

        List<AssetLineageNodeEntity> nodes = this.list(wrapper);
        if (CollectionUtil.isEmpty(nodes)) {
            return new ArrayList<>();
        }

        // 转换为DTO并设置nodeSubTypeDesc
        return nodes.stream()
                .map(node -> {
                    AssetNodeRichDto dto = new AssetNodeRichDto();
                    dto.setNodeUrn(node.getNodeUrn());
                    dto.setNodeName(node.getNodeName());
                    dto.setNodeType(node.getNodeType());
                    dto.setNodeSubType(node.getNodeSubType());
                    dto.setNodeProp(node.getNodeProp());
                    // 设置子类型描述
                    dto.setNodeSubTypeDesc(getNodeSubTypeDesc(node.getNodeSubType()));
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 根据nodeUrn查询节点资源信息.
     */
    @Override
    public List<AssetLineageNodeResourceVo> selectNodeResourceByNodeUrn(AssetNodeResourceDto request) {
        List<AssetLineageNodeResourceVo> nodeResourcesVos = new ArrayList<>();
        List<AssetLineageNodeResourceDto> nodeResources = baseMapper.selectNodeResourceByNodeUrn(request.getNodeUrn());
        nodeResources.forEach(nodeResource -> {
            try {
                nodePropConvert(nodeResource, request);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            nodeResource.setNodeSubType(NodeSubTypeEnum.valueOf(nodeResource.getNodeSubType()).getNodeSubTypeDesc());
            nodeResource.setNodeType(NodeTypeEnum.valueOf(nodeResource.getNodeType()).getNodeTypeDesc());
            nodeResource.setResourceTag(ResourceTagEnum.getResourceTagTypeDesc(Integer.parseInt(nodeResource.getResourceTag())));
            nodeResource.setResourceType(ResourceTypeEnum.getTypeDescByType(nodeResource.getResourceType()));
        });
        nodeResourcesVos = nodeResources.stream()
                .map(nodeResource -> {
                    AssetLineageNodeResourceVo nodeResourceVo = new AssetLineageNodeResourceVo();
                    BeanUtils.copyProperties(nodeResource, nodeResourceVo);
                    return nodeResourceVo;
                })
                .collect(java.util.stream.Collectors.toList());
        return nodeResourcesVos;
    }

    private void nodePropConvert(AssetLineageNodeResourceDto nodeResource, AssetNodeResourceDto request) throws JsonProcessingException {

        if (nodeResource.getNodeSubType().equals(NodeSubTypeEnum.MENU.getNodeSubType())) {
            MenuNodePropVo nodePropVo = new MenuNodePropVo();
            // 假设要将 JsonNode 转为 NodePropDto 对象
            JsonNode jsonNodeProp = nodeResource.getNodeProp();
            NodePropBuilder.MenuNodeProp nodeProp = new ObjectMapper().treeToValue(jsonNodeProp, NodePropBuilder.MenuNodeProp.class);
            nodePropVo.setUrn(nodeProp.getUrn())
                    .setAppCode(nodeProp.getAppCode())
                    .setAppName(nodeProp.getAppName())
                    .setMenu(nodeProp.getMenu())
                    .setComponentType(MenuSubTypeEnum.getByComponentType(nodeProp.getComponentType()).getComponentName());
            JsonNode jsonNode = new ObjectMapper().valueToTree(nodePropVo);
            nodeResource.setNodeProp(jsonNode);

        }
        if (nodeResource.getNodeSubType().equals(NodeSubTypeEnum.API.getNodeSubType())) {
            NodePropBuilder.ApiNodeProp nodeProp = new ObjectMapper()
                    .treeToValue(nodeResource.getNodeProp(), NodePropBuilder.ApiNodeProp.class);
            String requestTag = request.getTag();
            if (org.apache.commons.lang3.StringUtils.isEmpty(requestTag)) {
                nodeProp.setWeLocationList(null);
            } else if (CollectionUtil.isNotEmpty(nodeProp.getWeLocationList())) {
                List<ResourceSnapshotBuilder.WeLocation> filtered = nodeProp.getWeLocationList().stream()
                        .filter(loc -> loc.getTagSet() != null
                                && loc.getTagSet().stream()
                                        .anyMatch(metricsTag -> requestTag.equals(metricsTag.getTag())))
                        .collect(java.util.stream.Collectors.toList());
                if (CollectionUtil.isNotEmpty(filtered)) {
                    filtered.forEach(loc -> loc.setTagSet(null));
                }
                nodeProp.setWeLocationList(filtered);
            }
            nodeResource.setNodeProp(new ObjectMapper().valueToTree(nodeProp));
        }
    }

    /**
     * 获取节点子类型描述.
     */
    private String getNodeSubTypeDesc(String nodeSubType) {
        if (nodeSubType == null) {
            return "";
        }
        try {
            NodeSubTypeEnum enumValue = NodeSubTypeEnum.valueOf(nodeSubType);
            return enumValue.getNodeSubTypeDesc();
        } catch (IllegalArgumentException e) {
            return nodeSubType;
        }
    }

    @Override
    public List<Map<String, String>> getNodeSubTypeSelect(Integer searchType) {
        List<Map<String, String>> result = new ArrayList<>();

        for (NodeSubTypeEnum enumValue : NodeSubTypeEnum.values()) {
            // searchType=1时过滤COLUMN
            if (searchType != null && searchType == 1 && "COLUMN".equals(enumValue.getNodeSubType())) {
                continue;
            }

            Map<String, String> item = new java.util.HashMap<>();
            item.put("value", enumValue.getNodeSubType());
            item.put("label", enumValue.getNodeSubTypeDesc());
            result.add(item);
        }

        return result;
    }

    @Override
    public String getNodeUrn(EdgeRequestVo requestVo) {
        String tableNameColumnName = null;
        if (requestVo.getTableName() != null && requestVo.getColumnName() != null) {
            tableNameColumnName = requestVo.getTableName().toLowerCase() + ":" + requestVo.getColumnName().toLowerCase();
        }
        return baseMapper.getNodeUrn(requestVo.getDatasourceName(), tableNameColumnName);
    }

}
