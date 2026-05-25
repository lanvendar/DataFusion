package com.datafusion.manager.asset.service;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.asset.dao.AssetEtlProcessMapper;
import com.datafusion.manager.asset.dao.AssetLineageEdgeMapper;
import com.datafusion.manager.asset.dao.AssetResourceMapper;
import com.datafusion.manager.asset.dto.AssetNodeAttributesDto;
import com.datafusion.manager.asset.dto.AttributeEdgeVo;
import com.datafusion.manager.asset.dto.DruidParseResultDto;
import com.datafusion.manager.asset.dto.EdgeNodeRequestVo;
import com.datafusion.manager.asset.dto.EdgeNodeVo;
import com.datafusion.manager.asset.dto.EdgeNodeVoV2;
import com.datafusion.manager.asset.dto.EdgeRequestVo;
import com.datafusion.manager.asset.dto.EdgeTableColumnDto;
import com.datafusion.manager.asset.dto.EntityEdgeVo;
import com.datafusion.manager.asset.dto.EtlResourceInfoDto;
import com.datafusion.manager.asset.dto.EtlSnapshot;
import com.datafusion.manager.asset.dto.LineEdgeNodeVoV2;
import com.datafusion.manager.asset.dto.LineEdgeNodeVoV3;
import com.datafusion.manager.asset.dto.LineageEdgeDto;
import com.datafusion.manager.asset.dto.builder.NodePropBuilder;
import com.datafusion.manager.asset.enums.NodeSubTypeEnum;
import com.datafusion.manager.asset.enums.NodeTypeEnum;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.handler.CalcitePareseHandler;
import com.datafusion.manager.asset.handler.DruidSqlParseEdgeHandler;
import com.datafusion.manager.asset.handler.SqlDruidHandler;
import com.datafusion.manager.asset.handler.sql.SqlConverter;
import com.datafusion.manager.asset.handler.sql.SqlVariableResolver;
import com.datafusion.manager.asset.po.AssetEtlProcessEntity;
import com.datafusion.manager.asset.po.AssetLineageEdgeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import com.datafusion.manager.asset.po.AssetLineageNodeResourceRelationEntity;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceTableColumnDto;
import com.datafusion.manager.metadata.dto.EdgeColumnInfoDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.service.MetaDataService;
import com.datafusion.manager.metadata.service.TableInfoService;
import com.datafusion.manager.utils.GraphSortUtil;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.datafusion.manager.asset.constant.AssetLineageConstant.TAG_SET;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/13
 * @since 2025/10/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetLineageEdgeService {

    /**
     * maxcompute数据源Id.
     */
    @Value("${etl.maxcompute.datasourceId}")
    private String maxcomputeDatasourceId;

    /**
     * 元数据service.
     */
    private final MetaDataService metaDataService;

    /**
     * 资源处理service.
     */
    private final TableInfoService tableInfoService;

    /**
     * 节点service.
     */
    private final AssetLineageNodeService nodeService;

    /**
     * 数据源service.
     */
    private final DataSourceInfoService dataSourceInfoService;

    /**
     * ETL 过程记录 Mapper.
     */
    private final AssetEtlProcessMapper etlProcessMapper;

    /**
     * 血缘边Mapper.
     */
    private final AssetLineageEdgeMapper edgeMapper;

    /**
     * 资源 Mapper.
     */
    private final AssetResourceMapper resourceMapper;

    /**
     * 检查tablenames的特殊情况.
     *
     * @param sql sql 语句
     * @return  void
     */
    private Set<String> checkTableNames(String sql, AssetLineageResourceEntity resourceEntity,
            EtlSnapshot etlSnapshot) {
        Set<String> tableNames = SqlDruidHandler.getTables(sql, etlSnapshot.getDatabaseType());
        if (CollectionUtil.isEmpty(tableNames)) {
            return null;
        }
        //单表没有血缘
        if (tableNames.size() == 1) {
            resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_SUCCESS.getStatus());
            saveEtlProcess(resourceEntity, 0, DruidParseResultDto.success(null, "只有目标表不需要处理血缘"));
            return null;
        }
        //去掉单个sql中sourcetable和targettable一样的情况
        if (tableNames.size() == 2) {
            HashSet<Object> tmpSet = new HashSet<>();
            for (String tableName : tableNames) {
                if (tableName.contains(".")) {
                    tmpSet.add(tableName.split("\\.")[1]);
                } else {
                    tmpSet.add(tableName);
                }
            }
            if (tmpSet.size() == 1) {
                return null;
            }
        }
        return tableNames;
    }

    /**
     * 解析ETL.
     *
     * @param foreignTables    外表
     * @param notForeignTables 非外表
     * @param etlSnapshot      etl snapshot
     * @param resourceEntity   资源
     * @param foreignTableMappings 外表映射
     * @param tableNames       表名
     * @return  Map 表名-列信息.
     */
    public Map<String, List<DataSourceTableColumnDto>> getTableColumns(Map<String, ForeignTableMapping> foreignTableMappings,
            Set<String> foreignTables,
            Set<String> notForeignTables,
            Set<String> tableNames, EtlSnapshot etlSnapshot,
            AssetLineageResourceEntity resourceEntity) {
        Map<String, List<DataSourceTableColumnDto>> tableColumns;
        if (CollectionUtil.isNotEmpty(foreignTables)) {
            Map<String, List<DataSourceTableColumnDto>> notForeTableColumns;
            try {
                notForeTableColumns = getTableColumnInfo(notForeignTables, etlSnapshot);
                tableColumns = getTableColumnInfo(foreignTables, etlSnapshot);
            } catch (CommonException ex) {
                //异常之前记录下过程
                resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_FAILED.getStatus());
                saveEtlProcess(resourceEntity, 0, DruidParseResultDto.failure(ex.getMessage()));
                throw ex;
            }
            // 获取 MaxCompute 数据源
            DataSourceInfoDto mcDataSource = dataSourceInfoService.getDataSource(UUID.fromString(maxcomputeDatasourceId));
            EtlSnapshot mcEtlSnapshot = new EtlSnapshot();
            BeanUtils.copyProperties(etlSnapshot, mcEtlSnapshot);
            //将holo的数据源替换成maxcompute的数据源
            mcEtlSnapshot.setDatabaseName(mcDataSource.getDatabaseName())
                    .setDatabaseType(mcDataSource.getDatabaseType())
                    .setDatasourceId(mcDataSource.getId())
                    .setSchemaName(mcDataSource.getSchemaName())
                    .setDatasourceName(mcDataSource.getName());

            //为每个外表注册元数据并记录映射
            for (String foreignTable : foreignTables) {
                String mcTableName = foreignTable;
                if (foreignTable.contains(".")) {
                    mcTableName = foreignTable.split("\\.")[1];
                }
                mcTableName = mcTableName.substring(0, mcTableName.lastIndexOf("_foreign"));

                // 获取 MaxCompute 表的元数据
                Set<String> singleTable = new HashSet<>();
                singleTable.add(mcTableName);
                Map<String, List<DataSourceTableColumnDto>> mcColumns = getTableColumnInfo(singleTable, mcEtlSnapshot);
                if (mcColumns.isEmpty()) {
                    Set<String> foreignTableOne = new HashSet<>();
                    foreignTableOne.add(foreignTable);
                    Map<String, List<DataSourceTableColumnDto>> holoForeTableColumns = getTableColumnInfo(foreignTableOne, etlSnapshot);
                    notForeTableColumns.put(foreignTable, holoForeTableColumns.get(foreignTable));
                    continue;
                }
                List<DataSourceTableColumnDto> foreignTableCoulumns = new ArrayList<>();
                for (DataSourceTableColumnDto column : mcColumns.get(mcTableName)) {
                    column.setTableName(foreignTable.split("\\.")[1]);
                    // 将数据源信息改成 Hologres 的
                    column.setDatasourceName(etlSnapshot.getDatasourceName());
                    column.setDatabaseType(etlSnapshot.getDatabaseType());
                    column.setDatabaseName(etlSnapshot.getDatabaseName());
                    column.setSchemaName(etlSnapshot.getSchemaName());
                    foreignTableCoulumns.add(column);
                }
                if (mcColumns.containsKey(mcTableName) && CollectionUtil.isNotEmpty(mcColumns.get(mcTableName))) {
                    // 1.用外表名注册元数据（SQL 解析使用）
                    //notForeTableColumns.put(foreignTable, mcColumns.get(mcTableName));
                    notForeTableColumns.put(foreignTable, foreignTableCoulumns);

                    // 2.记录映射关系（后续生成映射边使用）
                    ForeignTableMapping mapping = new ForeignTableMapping();
                    mapping.setForeignTableName(foreignTable);
                    mapping.setMcTableName(mcTableName);
                    mapping.setMcDataSource(mcDataSource);
                    mapping.setMcColumns(mcColumns.get(mcTableName));
                    foreignTableMappings.put(foreignTable, mapping);

                    log.info("注册外表映射: {} → MaxCompute.{}", foreignTable, mcTableName);
                }
            }
            tableColumns = notForeTableColumns;
        } else {
            try {
                tableColumns = getTableColumnInfo(tableNames, etlSnapshot);
            } catch (CommonException ex) {
                //异常之前记录下过程
                resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_FAILED.getStatus());
                saveEtlProcess(resourceEntity, 0, DruidParseResultDto.failure(ex.getMessage()));
                throw ex;
            }

        }
        return tableColumns;
    }

    /**
     * 解析ETL资源元数据.
     *
     * @param resourceEntity 资源实体
     * @param contextData    上下文数据
     */
    public void parseEtl(AssetLineageResourceEntity resourceEntity, EtlResourceInfoDto contextData) {
        EtlSnapshot etlSnapshot = null;
        //1.获取etl快照信息
        etlSnapshot = JacksonUtils.tryObj2Bean(resourceEntity.getResourceSnapshot(), EtlSnapshot.class);

        //获取所有的表名
        //2.需要对sql进行前置处理,针对部分sql,直接解析报错
        List<String> sqls = sqlPreProcess(resourceEntity, etlSnapshot);

        //3.获取 HG_CREATE_TABLE_LIKE 表名映射关系 <文件名, <临时表, 源表>>
        Map<String, Map<String, String>> tableRenameMap = getTableRenameMap();

        //4.获取alter table的表映射关系
        List<Map<String, String>> allRenameTables = getAlterTableSourceAndTargetTableName(resourceEntity, etlSnapshot);

        for (String sql : sqls) {
            log.info("开始对sql进行解析:" + sql);
            // 替换SQL中的临时表名为目标表名（兼容重命名导致的血缘断裂）
            sql = replaceTempTableNamesInSql(sql, tableRenameMap, etlSnapshot.getFileName());
            // 替换rename表名
            if (!allRenameTables.isEmpty() && !sql.contains("rename to")) {
                sql = replaceRenameTableNamesInSql(sql, allRenameTables);
            }
            Set<String> tableNames = checkTableNames(sql, resourceEntity, etlSnapshot);
            if (tableNames == null) {
                continue;
            }
            log.info("解析的表名" + tableNames);
            //处理一些特殊情况,包含HBASE的旧仓库,以及hologres外表,映射maxcompute表
            boolean isHasHbaseTable = false;
            Set<String> foreignTables = new HashSet<>();
            Set<String> notForeignTables = new HashSet<>();
            for (String tableName : tableNames) {
                if (tableName.endsWith("_foreign")) {
                    foreignTables.add(tableName);
                } else {
                    notForeignTables.add(tableName);
                }
            }
            //外表映射关系（外表名 → MaxCompute 表映射信息）
            Map<String, ForeignTableMapping> foreignTableMappings = new HashMap<>();
            //处理外表元数据和映射关系
            Map<String, List<DataSourceTableColumnDto>> tableColumns = getTableColumns(foreignTableMappings,
                    foreignTables, notForeignTables, tableNames, etlSnapshot, resourceEntity);

            //写节点表（包括外表节点）
            log.info("将解析的节点信息写入到节点表");
            List<AssetLineageNodeEntity> nodeEntities = saveNodes(tableColumns, resourceEntity.getId());
            contextData.getNodeEntities().addAll(nodeEntities);

            //如果有外表，需要额外保存 MaxCompute 表节点
            if (!foreignTableMappings.isEmpty()) {
                log.info("保存外表对应的 MaxCompute 表节点");
                contextData.getNodeEntities().addAll(saveForeignTableMcNodes(foreignTableMappings, etlSnapshot));
            }

            //先用CalcitePareseHandler解析sql,如果报错,则用DruidSqlParseEdgeHandler继续解析
            List<EdgeTableColumnDto> edgeDtos;
            DruidParseResultDto druidResult = null;
            String exceptionMsg = null;
            boolean isCalciteSuccess = false;
            try {
                edgeDtos = CalcitePareseHandler.parseSql(sql, tableColumns);
                isCalciteSuccess = true;
            } catch (Exception e) {
                e.printStackTrace();
                if (e.getMessage().contains("not found")) {
                    exceptionMsg = e.getMessage();
                }
                druidResult = DruidSqlParseEdgeHandler.parseEdges(sql, etlSnapshot.getDatabaseType(), tableColumns);
                edgeDtos = druidResult.getEdges();
            }

            // 保存 ETL 过程数据
            if (edgeDtos.isEmpty() && exceptionMsg != null) {
                saveEtlProcess(resourceEntity, isCalciteSuccess ? 1 : 2, druidResult.failure(exceptionMsg));
            } else {
                saveEtlProcess(resourceEntity, isCalciteSuccess ? 1 : 2, druidResult);
            }
            if (CollectionUtil.isNotEmpty(edgeDtos)) {
                List<EdgeTableColumnDto> actualEdges = edgeDtos.stream()
                        .filter(x -> tableColumns.get(x.getTargetColumnInfo().getTableName()) != null)
                        .collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(actualEdges)) {
                    //保存 SQL 解析的边（结果表 → 外表）
                    List<AssetLineageEdgeEntity> edges = getEdges(actualEdges, resourceEntity.getId(), tableColumns);
                    contextData.getEdgeEntities().addAll(edges);

                    //保存外表到 MaxCompute 的映射边
                    if (!foreignTableMappings.isEmpty()) {
                        log.info("保存外表到 MaxCompute 的映射边");
                        contextData.getEdgeEntities()
                                .addAll(getForeignTableMappingEdges(actualEdges, foreignTableMappings, tableColumns, resourceEntity.getId()));
                    }
                } else {
                    throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "edges为0");
                }
            } else {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "edges为0" + exceptionMsg);
            }
        }
    }

    /**
     * 替换rename的表名.
     *
     * @param sql sql
     * @param  allRenameTables   该流程中所有的rename表
     * @return sql
     */
    private String replaceRenameTableNamesInSql(String sql, List<Map<String, String>> allRenameTables) {
        for (Map<String, String> renameTable : allRenameTables) {
            for (Map.Entry<String, String> entry : renameTable.entrySet()) {
                sql = sql.replace(entry.getKey(), entry.getValue());
            }
        }
        return sql;
    }

    /**
     * 获取alter table的源表和目标表名.
     *
     * @param resourceEntity 资源实体
     * @param etlSnapshot    ETL快照
     * @return alter table的源表和目标表名
     */
    private List<Map<String, String>> getAlterTableSourceAndTargetTableName(AssetLineageResourceEntity resourceEntity, EtlSnapshot etlSnapshot) {
        List<AssetLineageResourceEntity> resources = resourceMapper.selectByResourceName(resourceEntity.getResourceName(), etlSnapshot.getDagName());
        List<Map<String, String>> allTableResults = new ArrayList<>();
        for (AssetLineageResourceEntity resource : resources) {
            EtlSnapshot resourceSnapshot = JacksonUtils.tryObj2Bean(resource.getResourceSnapshot(), EtlSnapshot.class);
            List<Map<String, String>> tableResults = SqlDruidHandler.getAlterTableSourceToTargetTableName(resourceSnapshot.getSql(),
                    etlSnapshot.getDatabaseType());
            allTableResults.addAll(tableResults);
        }
        allTableResults = GraphSortUtil.sortTableResults(allTableResults);
        return allTableResults;
    }

    /**
     * sql预处理.
     *
     * @param resourceEntity 资源实体
     * @param etlSnapshot    ETL快照
     * @return sql集合
     */
    private List<String> sqlPreProcess(AssetLineageResourceEntity resourceEntity, EtlSnapshot etlSnapshot) {
        String originSql = etlSnapshot.getSql();
        log.info("开始对sql进行预处理");
        List<String> sqls;
        try {
            sqls = SqlDruidHandler.preParseRecursive(originSql, etlSnapshot.getDatabaseType());
        } catch (Exception ex) {
            resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_FAILED.getStatus());
            saveEtlProcess(resourceEntity, 0, DruidParseResultDto.failure(ex.getMessage()));
            throw ex;
        }
        log.info("预处理结束" + sqls.size());

        if (CollectionUtil.isEmpty(sqls)) {
            resourceEntity.setStatus(ResourceStatusEnum.IMPORT_EDGE_SUCCESS.getStatus());
            saveEtlProcess(resourceEntity, 0, DruidParseResultDto.success(null, "没有需要处理的血缘SQL"));
        }
        log.info("预处理结束，开始逐行对sql进行解析");
        return sqls;
    }

    /**
     * 保存 ETL 过程数据.
     *
     * @param resourceEntity 资源实体
     * @param engine         解析引擎类型: 1=calcite, 2=druid
     * @param druidResult    Druid 解析结果（包含日志信息）
     */
    private void saveEtlProcess(AssetLineageResourceEntity resourceEntity, Integer engine, DruidParseResultDto druidResult) {
        AssetEtlProcessEntity etlProcess = new AssetEtlProcessEntity();
        etlProcess.setId(resourceEntity.getId());
        etlProcess.setResourceName(resourceEntity.getResourceName());
        etlProcess.setStatus((engine.equals(1)
                || (engine.equals(2) && druidResult != null && CollectionUtil.isNotEmpty(druidResult.getEdges()))) ? 2
                        : resourceEntity.getStatus());
        etlProcess.setResourceSnapshot(resourceEntity.getResourceSnapshot());
        etlProcess.setEngine(engine);
        // Calcite 解析成功时 remark 为空，Druid 解析时记录日志
        if (druidResult != null && CollectionUtil.isNotEmpty(druidResult.getLogs())) {
            etlProcess.setRemark(String.join("\n", druidResult.getLogs()));
        } else {
            etlProcess.setRemark(null);
        }
        // 设置更新人和更新时间
        etlProcess.setUpdater(HttpUtils.getCurrentUserName());
        etlProcess.setUpdateTime(new Date());

        // 查询是否存在
        AssetEtlProcessEntity existing = etlProcessMapper.selectById(resourceEntity.getId());
        if (existing != null) {
            // 存在则更新
            etlProcess.setCreator(existing.getCreator());
            etlProcess.setCreateTime(existing.getCreateTime());
            etlProcessMapper.updateById(etlProcess);
        } else {
            // 不存在则插入
            etlProcess.setCreator(HttpUtils.getCurrentUserName());
            etlProcess.setCreateTime(new Date());
            etlProcessMapper.insert(etlProcess);
        }
    }

    /**
     * 获取父节点URN.
     * COLUMN去掉最后一个:后的部分为父TABLE
     * METRIC去掉最后一个:后的部分为父API
     *
     * @param urn 当前节点urn
     * @return 父节点urn
     */
    private String getParentUrn(String urn) {
        if (urn == null || !urn.contains(":")) {
            return urn;
        }
        return urn.substring(0, urn.lastIndexOf(":"));
    }

    /**
     * 新版血缘链路查询（数组格式）表级血缘.
     *
     * @param req 查询请求
     * @return 血缘链路结果（包含节点、实体边、属性边）
     */
    public LineEdgeNodeVoV3 linkTable(EdgeNodeRequestVo req) {
        // 进行校验
        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetLineageNodeEntity::getNodeUrn, req.getNodeUrn());
        AssetLineageNodeEntity nodeEntity = nodeService.getOne(wrapper);
        if (nodeEntity == null || nodeEntity.getNodeSubType() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "nodeUrn不存在,或者为叶子节点");
        }
        if (req.getIsLeafNode()) {
            // 只允许-1到1
            if (req.getDepth() != 1) {
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "下层节点展示时,只允许为1");
            }
        }
        // 返回单个对象
        LineEdgeNodeVoV3 result = new LineEdgeNodeVoV3();
        List<LineageEdgeDto> edgeNodes = edgeMapper.link(req);

        // 根据isUp参数过滤数据，同时限制深度范围
        Boolean isUp = req.getIsUp();
        Integer depth = req.getDepth() != null ? req.getDepth() : 1;
        if (isUp != null) {
            if (isUp) {
                // 只返回上游，限制深度范围 [-depth, -1]
                int minDepth = -depth;
                edgeNodes = edgeNodes.stream()
                        .filter(e -> e.getDepth() < 0 && e.getDepth() >= minDepth)
                        .collect(Collectors.toList());
            } else {
                // 只返回下游，限制深度范围 [1, depth]
                edgeNodes = edgeNodes.stream()
                        .filter(e -> e.getDepth() > 0 && e.getDepth() <= depth)
                        .collect(Collectors.toList());
            }
        } else {
            // isUp为空时，默认返回当前节点、上游一层、下游一层（即 depth ∈ {-1, 0, 1}）
            edgeNodes = edgeNodes.stream()
                    .filter(e -> e.getDepth() >= -1 && e.getDepth() <= 1)
                    .collect(Collectors.toList());
        }
        //当查询边关系为空时返回当前节点
        if (CollectionUtil.isEmpty(edgeNodes)) {
            emptyLineResult(req, result, nodeEntity);
            return result;
        }
        boolean isLeafNode = req.getIsLeafNode();
        Map<Integer, List<LineageEdgeDto>> edgeMaps = edgeNodes.stream().collect(Collectors.groupingBy(LineageEdgeDto::getDepth));

        List<AssetLineageNodeEntity> nodeList = getResourceNode(edgeMaps);
        Map<String, AssetLineageNodeEntity> nodeMap = nodeList.stream().collect(Collectors.toMap(AssetLineageNodeEntity::getNodeUrn, v -> v));
        // 收集所有深度中的节点和边
        List<EdgeNodeVoV2> allNodeVos = new ArrayList<>();
        List<EntityEdgeVo> allEntityEdgeVos = new ArrayList<>();
        List<AttributeEdgeVo> allAttributeEdgeVos = new ArrayList<>();
        Map<String, EdgeNodeVoV2> nodeVoMap = new HashMap<>();
        for (Map.Entry<Integer, List<LineageEdgeDto>> entry : edgeMaps.entrySet()) {
            List<LineageEdgeDto> edgeDtos = entry.getValue();
            for (LineageEdgeDto edgeDto : edgeDtos) {
                String sourceUrn = edgeDto.getSourceUrn();
                String targetUrn = edgeDto.getTargetUrn();
                // 判断是实体边还是属性边
                boolean isSourceAttributeEdge = isAttributeEdge(sourceUrn, nodeMap);
                boolean isTargetAttributeEdge = isAttributeEdge(targetUrn, nodeMap);

                if (!isLeafNode) {
                    // 则所有关系,都是表节点
                    if (nodeVoMap.get(sourceUrn) == null) {
                        EdgeNodeVoV2 nodeVo = new EdgeNodeVoV2();
                        nodeVo.setNodeUrn(sourceUrn)
                                .setNodeName(sourceUrn.substring(sourceUrn.lastIndexOf(SystemConstant.COLON) + 1));
                        allNodeVos.add(nodeVo);
                        nodeVoMap.put(sourceUrn, nodeVo);
                    }

                    if (nodeVoMap.get(targetUrn) == null) {
                        EdgeNodeVoV2 nodeVo = new EdgeNodeVoV2();
                        nodeVo.setNodeUrn(targetUrn)
                                .setNodeName(targetUrn.substring(targetUrn.lastIndexOf(SystemConstant.COLON) + 1));
                        allNodeVos.add(nodeVo);
                        nodeVoMap.put(targetUrn, nodeVo);
                    }

                    if (isSourceAttributeEdge || isTargetAttributeEdge) {
                        // 属性边（字段到字段）
                        AttributeEdgeVo attrEdge = new AttributeEdgeVo();
                        attrEdge.setId(sourceUrn + "->" + targetUrn);
                        attrEdge.setSource(getTableUrn(sourceUrn));
                        attrEdge.setTarget(getTableUrn(targetUrn));
                        attrEdge.setSourceHandle(sourceUrn);
                        attrEdge.setTargetHandle(targetUrn);
                        allAttributeEdgeVos.add(attrEdge);
                    } else {
                        // 实体边（表到表）
                        EntityEdgeVo entityEdge = new EntityEdgeVo();
                        entityEdge.setId(sourceUrn + "->" + targetUrn);
                        entityEdge.setSource(sourceUrn);
                        entityEdge.setTarget(targetUrn);
                        allEntityEdgeVos.add(entityEdge);
                    }
                } else {
                    // 返回的都是子节点关系,根据nodeUrn获取父节点
                    String parentSourceUrn = null;
                    String parentTargetUrn = null;
                    if (isSourceAttributeEdge || isTargetAttributeEdge) {
                        parentSourceUrn = getTableUrn(sourceUrn);
                        parentTargetUrn = getTableUrn(targetUrn);
                    } else {
                        parentSourceUrn = sourceUrn;
                        parentTargetUrn = targetUrn;
                    }
                    // 处理source节点（父子关系）
                    if (!sourceUrn.equals(parentSourceUrn)) {
                        processParentChildNodeNew(sourceUrn, parentSourceUrn, allNodeVos, nodeVoMap);
                    }
                    // 处理target节点（父子关系）
                    if (!targetUrn.equals(parentTargetUrn)) {
                        processParentChildNodeNew(targetUrn, parentTargetUrn, allNodeVos, nodeVoMap);
                    }
                    if (isSourceAttributeEdge || isTargetAttributeEdge) {
                        // 属性边（字段到字段）
                        AttributeEdgeVo attrEdge = new AttributeEdgeVo();
                        attrEdge.setId(sourceUrn + "->" + targetUrn);
                        attrEdge.setSource(parentSourceUrn);
                        attrEdge.setTarget(parentTargetUrn);
                        attrEdge.setSourceHandle(sourceUrn);
                        attrEdge.setTargetHandle(targetUrn);
                        allAttributeEdgeVos.add(attrEdge);
                        //当target是metric时，添加一条实体边关系
                        if (isTargetAttributeEdge) {
                            EntityEdgeVo entityEdge = new EntityEdgeVo();
                            entityEdge.setId(sourceUrn + "->" + targetUrn);
                            entityEdge.setSource(parentSourceUrn);
                            entityEdge.setTarget(parentTargetUrn);
                            allEntityEdgeVos.add(entityEdge);
                        }
                    } else {
                        if (nodeVoMap.get(sourceUrn) == null) {
                            EdgeNodeVoV2 nodeVo = new EdgeNodeVoV2();
                            nodeVo.setNodeUrn(sourceUrn)
                                    .setNodeName(sourceUrn.substring(sourceUrn.lastIndexOf(SystemConstant.COLON) + 1));
                            allNodeVos.add(nodeVo);
                            nodeVoMap.put(sourceUrn, nodeVo);
                        }

                        if (nodeVoMap.get(targetUrn) == null) {
                            EdgeNodeVoV2 nodeVo = new EdgeNodeVoV2();
                            nodeVo.setNodeUrn(targetUrn)
                                    .setNodeName(targetUrn.substring(targetUrn.lastIndexOf(SystemConstant.COLON) + 1));
                            allNodeVos.add(nodeVo);
                            nodeVoMap.put(targetUrn, nodeVo);
                        }
                        // 实体边（表到表）
                        EntityEdgeVo entityEdge = new EntityEdgeVo();
                        entityEdge.setId(sourceUrn + "->" + targetUrn);
                        entityEdge.setSource(parentSourceUrn);
                        entityEdge.setTarget(parentTargetUrn);
                        allEntityEdgeVos.add(entityEdge);
                    }
                }
            }
        }
        //对属性节点进行排序，便于前端展示
        for (EdgeNodeVoV2 node : allNodeVos) {
            if (node.getAttributes() == null) {
                continue;
            }
            node.setAttributes(node.getAttributes().stream().sorted(Comparator.comparing(AssetNodeAttributesDto::getAttributeName))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        result.setNodeVos(allNodeVos);
        result.setEntityEdgeVos(allEntityEdgeVos);
        result.setAttributeEdgeVos(allAttributeEdgeVos);
        return result;
    }

    /**
     * 当边关系为空时，返回当前查询节点.
     *
     * @param req 请求参数.
     * @param result 返回值 .
     * @param nodeEntity 节点信息.
     *
     */
    private void emptyLineResult(EdgeNodeRequestVo req, LineEdgeNodeVoV3 result, AssetLineageNodeEntity nodeEntity) {
        EdgeNodeVoV2 nodeVoV2 = new EdgeNodeVoV2();
        String nodeSubType = nodeEntity.getNodeSubType();
        if (nodeSubType.equals(NodeSubTypeEnum.COLUMN.getNodeSubType()) || nodeSubType.equals(NodeSubTypeEnum.METRIC.getNodeSubType())) {
            Set<AssetNodeAttributesDto> attSet = new LinkedHashSet<>();
            AssetNodeAttributesDto assetNodeAttributes = new AssetNodeAttributesDto();
            assetNodeAttributes.setAttributeName(nodeEntity.getNodeName())
                    .setAttributeUrn(nodeEntity.getNodeUrn());
            attSet.add(assetNodeAttributes);
            nodeVoV2.setNodeUrn(getTableUrn(req.getNodeUrn()))
                    .setNodeName(getTableUrn(getTableUrn(req.getNodeUrn())))
                    .setAttributes(attSet);
            List<EdgeNodeVoV2> allNodeVos = new ArrayList<>();
            allNodeVos.add(nodeVoV2);
            result.setNodeVos(allNodeVos);
            result.setEntityEdgeVos(new ArrayList<>());
        } else {
            nodeVoV2.setNodeUrn(req.getNodeUrn())
                    .setNodeName(nodeEntity.getNodeName());
            List<EdgeNodeVoV2> allNodeVos = new ArrayList<>();
            allNodeVos.add(nodeVoV2);
            result.setNodeVos(allNodeVos);
            result.setEntityEdgeVos(new ArrayList<>());
            result.setEntityEdgeVos(new ArrayList<>());
        }
    }

    /**
     * 批量获取node信息.
     *
     * @param edgeMaps 边关系.
     *
     */
    private List<AssetLineageNodeEntity> getResourceNode(Map<Integer, List<LineageEdgeDto>> edgeMaps) {
        List<String> nodelists = new ArrayList<>();
        for (Map.Entry<Integer, List<LineageEdgeDto>> entry1 : edgeMaps.entrySet()) {
            List<LineageEdgeDto> edgeDtos1 = entry1.getValue();
            for (LineageEdgeDto edgeDto1 : edgeDtos1) {
                String sourceUrn1 = edgeDto1.getSourceUrn();
                String targetUrn1 = edgeDto1.getTargetUrn();
                nodelists.add(sourceUrn1);
                nodelists.add(targetUrn1);
            }
        }

        LambdaQueryWrapper<AssetLineageNodeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AssetLineageNodeEntity::getNodeUrn, nodelists);
        List<AssetLineageNodeEntity> nodeEntity = nodeService.list(wrapper);
        return nodeEntity;
    }

    /**
     * 判断URN是否为列级URN（包含字段信息）.
     * 表级URN: databaseType:datasourceName:databaseName:schemaName:tableName
     * 列级URN: databaseType:datasourceName:databaseName:schemaName:tableName:columnName
     *
     * @param urn URN
     * @return 是否为列级URN
     */
    private boolean isColumnUrn(String urn) {
        if (urn == null) {
            return false;
        }
        // 通过冒号分割后判断段数，6段及以上为列级URN
        String[] parts = urn.split(":");
        return parts.length > 5;
    }

    /**
     * 判断是否为属性边.
     *
     * @param urn URN
     * @return 是否为属性边
     */
    private boolean isAttributeEdge(String urn, Map<String, AssetLineageNodeEntity> nodeMap) {
        if (!nodeMap.containsKey(urn)) {
            return false;
        }
        String nodeSubType = nodeMap.get(urn).getNodeSubType();
        if (nodeSubType.equals(NodeSubTypeEnum.COLUMN.getNodeSubType()) || nodeSubType.equals(NodeSubTypeEnum.METRIC.getNodeSubType())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 从列级URN提取表级URN.
     *
     * @param urn 列级URN
     * @return 表级URN
     */
    private String getTableUrn(String urn) {
        if (urn == null) {
            return null;
        }
        if (isColumnUrn(urn)) {
            // 去除最后一列名段
            return urn.substring(0, urn.lastIndexOf(":"));
        }
        return urn;
    }

    /**
     * 处理父子节点关系，构建节点树形结构.
     *
     * @param childUrn  子节点URN（完整URN，包含字段名）
     * @param parentUrn 父节点URN（表级URN，不含字段名）
     * @param nodeVos   节点列表
     * @param nodeVoMap 节点映射表
     */
    private void processParentChildNodeNew(String childUrn, String parentUrn, List<EdgeNodeVoV2> nodeVos, Map<String, EdgeNodeVoV2> nodeVoMap) {
        if (nodeVoMap.get(parentUrn) == null) {
            // 父节点不存在，创建父节点
            EdgeNodeVoV2 parentNodeVo = new EdgeNodeVoV2();
            parentNodeVo.setNodeUrn(parentUrn)
                    .setNodeName(parentUrn.substring(parentUrn.lastIndexOf(SystemConstant.COLON) + 1));
            nodeVos.add(parentNodeVo);
            nodeVoMap.put(parentUrn, parentNodeVo);

            // 初始化子节点集合
            Set<AssetNodeAttributesDto> subEdgeNodeVos = new HashSet<>();
            parentNodeVo.setAttributes(subEdgeNodeVos);

            // 添加子节点
            subEdgeNodeVos.add(new AssetNodeAttributesDto()
                    .setAttributeName(childUrn.substring(childUrn.lastIndexOf(SystemConstant.COLON) + 1))
                    .setAttributeUrn(childUrn));
        } else {
            // 父节点已存在，直接添加子节点
            if (nodeVoMap.get(parentUrn).getAttributes() == null) {
                Set<AssetNodeAttributesDto> assetNodeAttributes = new HashSet<>();
                assetNodeAttributes.add(new AssetNodeAttributesDto()
                        .setAttributeName(childUrn.substring(childUrn.lastIndexOf(SystemConstant.COLON) + 1))
                        .setAttributeUrn(childUrn));
                nodeVoMap.get(parentUrn).setAttributes(assetNodeAttributes);
            }
            nodeVoMap.get(parentUrn).getAttributes().add(new AssetNodeAttributesDto()
                    .setAttributeName(childUrn.substring(childUrn.lastIndexOf(SystemConstant.COLON) + 1))
                    .setAttributeUrn(childUrn));
        }
    }

    /**
     * 保存节点信息.
     *
     * @param tableColumns 元数据信息
     */
    private List<AssetLineageNodeEntity> saveNodes(Map<String, List<DataSourceTableColumnDto>> tableColumns, UUID resourceId) {
        List<AssetLineageNodeEntity> results = new ArrayList<>();
        List<AssetLineageNodeResourceRelationEntity> relationResults = new ArrayList<>();
        for (Map.Entry<String, List<DataSourceTableColumnDto>> entry : tableColumns.entrySet()) {
            String tableName = getSimpleTabelName(entry.getKey());
            List<DataSourceTableColumnDto> value = entry.getValue();
            DataSourceTableColumnDto dataSourceTableColumnDto = value.get(0);
            String tableUrn = String.join(SystemConstant.COLON,
                    dataSourceTableColumnDto.getDatabaseType(), dataSourceTableColumnDto.getDatasourceName(),
                    dataSourceTableColumnDto.getDatabaseName(), dataSourceTableColumnDto.getSchemaName(),
                    tableName);
            // 构建table节点属性信息
            NodePropBuilder.TableNodeProp prop = NodePropBuilder.builder(NodePropBuilder.NodePropType.TABLE);
            prop.setUrn(tableUrn);
            prop.setDatabaseType(dataSourceTableColumnDto.getDatabaseType());
            prop.setDatabaseName(dataSourceTableColumnDto.getDatabaseName());
            prop.setSchemaName(dataSourceTableColumnDto.getSchemaName());
            prop.setDatasourceName(dataSourceTableColumnDto.getDatasourceName());
            prop.setTableName(tableName);
            prop.setTableDesc("");
            AssetLineageNodeEntity tableNodeEntity = new AssetLineageNodeEntity();
            tableNodeEntity.setNodeName(tableName)
                    .setNodeUrn(tableUrn)
                    .setNodeType(NodeTypeEnum.DATABASE.getNodeType())
                    .setNodeSubType(NodeSubTypeEnum.TABLE.getNodeSubType())
                    .setNodeProp(JacksonUtils.convertPojoToJsonNodeSafely(prop))
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date())
                    .setUpdater(HttpUtils.getCurrentUserName())
                    .setCreator(HttpUtils.getCurrentUserName());
            tableNodeEntity.setId(UUID.randomUUID());
            results.add(tableNodeEntity);
            //node_resource_relation
            AssetLineageNodeResourceRelationEntity relationTable = new AssetLineageNodeResourceRelationEntity();
            relationTable.setId(UUID.randomUUID());
            relationTable.setResourceId(resourceId);
            relationTable.setNodeId(tableNodeEntity.getId());
            relationTable.setCreateTime(new Date());
            relationTable.setUpdateTime(new Date());
            relationTable.setUpdater(HttpUtils.getCurrentUserName());
            relationTable.setCreator(HttpUtils.getCurrentUserName());
            relationResults.add(relationTable);
            for (DataSourceTableColumnDto tableColumnDto : value) {
                String columnUrn = String.join(SystemConstant.COLON, tableUrn, tableColumnDto.getColumnName());
                // 构建column节点属性信息
                NodePropBuilder.ColumnNodeProp columnProp = NodePropBuilder.builder(NodePropBuilder.NodePropType.COLUMN);
                columnProp.setUrn(columnUrn);
                columnProp.setDatabaseType(dataSourceTableColumnDto.getDatabaseType());
                columnProp.setDatabaseName(dataSourceTableColumnDto.getDatabaseName());
                columnProp.setSchemaName(dataSourceTableColumnDto.getSchemaName());
                columnProp.setDatasourceName(dataSourceTableColumnDto.getDatasourceName());
                columnProp.setTableName(tableName);
                columnProp.setTableDesc("");
                columnProp.setColumnName(tableColumnDto.getColumnName());
                columnProp.setColumnDesc("");
                AssetLineageNodeEntity columnNodeEntity = new AssetLineageNodeEntity();
                columnNodeEntity.setNodeName(tableColumnDto.getColumnName())
                        .setNodeUrn(columnUrn)
                        .setNodeType(NodeTypeEnum.DATABASE.getNodeType())
                        .setNodeSubType(NodeSubTypeEnum.COLUMN.getNodeSubType())
                        .setNodeProp(JacksonUtils.convertPojoToJsonNodeSafely(columnProp))
                        .setCreateTime(new Date())
                        .setUpdateTime(new Date())
                        .setUpdater(HttpUtils.getCurrentUserName())
                        .setCreator(HttpUtils.getCurrentUserName());
                columnNodeEntity.setId(UUID.randomUUID());
                results.add(columnNodeEntity);
                //node_resource_relation
                AssetLineageNodeResourceRelationEntity relationTableColumn = new AssetLineageNodeResourceRelationEntity();
                relationTableColumn.setId(UUID.randomUUID());
                relationTableColumn.setResourceId(resourceId);
                relationTableColumn.setNodeId(columnNodeEntity.getId());
                relationTableColumn.setCreateTime(new Date());
                relationTableColumn.setUpdateTime(new Date());
                relationTableColumn.setUpdater(HttpUtils.getCurrentUserName());
                relationTableColumn.setCreator(HttpUtils.getCurrentUserName());
                relationResults.add(relationTableColumn);
            }
        }
        return results;
    }

    /**
     * 通过表名,以及datasourceId,获取表信息,未注册的表信息进行注册.
     *
     * @param setTableNames 表名称集合
     * @param datasourceId  数据源ID
     * @return 表信息, 供注册和以后使用
     */
    private Map<String, List<DataSourceTableColumnDto>> getTableColumnInfoByDatasourceId(Set<String> setTableNames, UUID datasourceId) {
        if (CollectionUtil.isNotEmpty(setTableNames)) {
            List<String> tableNames = new ArrayList<>(setTableNames);
            String schemaName = null;
            if (tableNames.get(0).contains(".")) {
                schemaName = tableNames.get(0).split("\\.")[0];
                tableNames = tableNames.stream().map(x -> x.split("\\.")[1]).collect(Collectors.toList());
            }
            List<DataSourceTableColumnDto> dataSourceTableColumns = tableInfoService.getDataSourceTableColumns(datasourceId,
                    new ArrayList<>(tableNames));
            Map<String, List<DataSourceTableColumnDto>> tableColumns = dataSourceTableColumns.stream()
                    .collect(Collectors.groupingBy(DataSourceTableColumnDto::getTableName, Collectors.toList()));
            if (tableColumns.size() < tableNames.size()) {
                //获取没有注册的表（meta table里面没查到的表）
                List<String> notRegisterTableNames = tableNames.stream().filter(x -> !tableColumns.keySet().contains(x)).collect(Collectors.toList());
                RetrieveMetaDataDto retrieveMetaDataDto = new RetrieveMetaDataDto();
                retrieveMetaDataDto.setDatasourceId(datasourceId);
                retrieveMetaDataDto.setTableNames(notRegisterTableNames);
                metaDataService.registerTables(retrieveMetaDataDto);
                //再重新获取
                List<DataSourceTableColumnDto> registerTableColumnDto = tableInfoService.getDataSourceTableColumns(datasourceId,
                        new ArrayList<>(notRegisterTableNames));
                Map<String, List<DataSourceTableColumnDto>> newRegisterTableColumn = registerTableColumnDto.stream()
                        .collect(Collectors.groupingBy(DataSourceTableColumnDto::getTableName, Collectors.toList()));
                int notRegisterSize = notRegisterTableNames.size();
                if (newRegisterTableColumn.size() < notRegisterSize) {
                    //仍然有表未找到,可能是tmp表
                    notRegisterTableNames.removeAll(newRegisterTableColumn.keySet());
                    // 安全地处理 tmp 表名：只处理以 "tmp_" 开头且长度足够的表名
                    Set<String> tmpTableNames = notRegisterTableNames.stream()
                            .filter(x -> x != null && x.length() > 4 && x.startsWith("tmp_"))
                            .map(x -> x.substring(4))
                            .collect(Collectors.toSet());
                    // 检查是否有有效的 tmp 表
                    if (CollectionUtil.isEmpty(tmpTableNames)) {
                        throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,
                                "元数据缺失，且未找到有效的临时表: " + String.join(", ", notRegisterTableNames));
                    }
                    Map<String, List<DataSourceTableColumnDto>> tmpTableColumns = getTableColumnInfoByDatasourceId(tmpTableNames, datasourceId);
                    if (CollectionUtil.isEmpty(tmpTableColumns) || (tmpTableColumns.size() + newRegisterTableColumn.size()) < notRegisterSize) {
                        throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "元数据缺失");
                    }
                    //借用主表的元数据进行注册
                    Map<String, List<DataSourceTableColumnDto>> tmpActualResultMap = new HashMap<>();
                    for (Map.Entry<String, List<DataSourceTableColumnDto>> entry : tmpTableColumns.entrySet()) {
                        String tmpTableName = String.join("_", "tmp", entry.getKey());
                        if (tmpActualResultMap.get(tmpTableName) == null) {
                            List<DataSourceTableColumnDto> tableColumnInfos = entry.getValue();
                            List<DataSourceTableColumnDto> actualTmpList = tableColumnInfos.stream().map(x -> {
                                x.setTableName(tmpTableName);
                                return x;
                            }).collect(Collectors.toList());
                            tmpActualResultMap.put(tmpTableName, actualTmpList);
                        }
                    }
                    tableColumns.putAll(newRegisterTableColumn);
                    tableColumns.putAll(tmpActualResultMap);
                } else {
                    tableColumns.putAll(newRegisterTableColumn);
                }
            }
            if (schemaName != null) {
                Map<String, List<DataSourceTableColumnDto>> newTableColumns = new HashMap<>();
                for (Map.Entry<String, List<DataSourceTableColumnDto>> entry : tableColumns.entrySet()) {
                    newTableColumns.put(String.join(".", schemaName, entry.getKey()), entry.getValue());
                }
                return newTableColumns;
            }
            return tableColumns;
        }
        return new HashMap<>();
    }

    /**
     * 保存血缘信息.
     *
     * @param edgeDtos    血缘关系对象
     * @param resourceId  资源id
     * @param tableColums 表信息
     */
    private List<AssetLineageEdgeEntity> getEdges(List<EdgeTableColumnDto> edgeDtos, UUID resourceId,
            Map<String, List<DataSourceTableColumnDto>> tableColums) {
        Map<String, Set<String>> tableEdges = new HashMap<>();
        List<AssetLineageEdgeEntity> edges = new ArrayList<>();
        for (EdgeTableColumnDto edgeDto : edgeDtos) {
            List<EdgeColumnInfoDto> sourceColumnInfos = edgeDto.getSourceColumnInfos();
            if (CollectionUtils.isEmpty(sourceColumnInfos)) {
                //表示该字段并无来源
                continue;
            }
            String originTargetTableName = edgeDto.getTargetColumnInfo().getTableName();
            String targetTableName = getSimpleTabelName(originTargetTableName);
            List<DataSourceTableColumnDto> dataSourceTableColumnDtos = tableColums.get(originTargetTableName);
            DataSourceTableColumnDto dataSourceTableColumnDto = dataSourceTableColumnDtos.get(0);
            String urn = String.join(SystemConstant.COLON,
                    dataSourceTableColumnDto.getDatabaseType(), dataSourceTableColumnDto.getDatasourceName(),
                    dataSourceTableColumnDto.getDatabaseName(), dataSourceTableColumnDto.getSchemaName(),
                    targetTableName);

            if (tableEdges.get(originTargetTableName) == null) {
                tableEdges.put(originTargetTableName, new HashSet<>());
            }
            for (EdgeColumnInfoDto sourceColumnInfo : sourceColumnInfos) {
                //拼装字段级Node节点
                AssetLineageEdgeEntity edgeEntity = new AssetLineageEdgeEntity();
                String targetUrn = String.join(SystemConstant.COLON, urn, edgeDto.getTargetColumnInfo().getColumnName());
                String sourceTableName = sourceColumnInfo.getTableName();
                List<DataSourceTableColumnDto> sourceTableColumns = tableColums.get(sourceTableName);
                if (sourceTableColumns == null) {
                    continue;
                }
                DataSourceTableColumnDto sourceTableColumn = sourceTableColumns.get(0);
                String columnName = sourceColumnInfo.getColumnName();
                String tmpTableName = getSimpleTabelName(sourceTableName);
                String sourceUrn = String.join(SystemConstant.COLON,
                        sourceTableColumn.getDatabaseType(), sourceTableColumn.getDatasourceName(),
                        sourceTableColumn.getDatabaseName(), sourceTableColumn.getSchemaName(),
                        tmpTableName, columnName);
                edgeEntity.setResourceId(resourceId)
                        .setSourceUrn(sourceUrn)
                        .setTargetUrn(targetUrn)
                        .setCreateTime(new Date())
                        .setUpdateTime(new Date())
                        .setUpdater(HttpUtils.getCurrentUserName())
                        .setCreator(HttpUtils.getCurrentUserName());
                edgeEntity.setId(UUID.randomUUID());
                edges.add(edgeEntity);
                tableEdges.get(originTargetTableName).add(sourceTableName);
            }
        }
        //写表级血缘,
        for (Map.Entry<String, Set<String>> entry : tableEdges.entrySet()) {
            String targetTableName = entry.getKey();
            Set<String> sourceTables = entry.getValue();
            for (String sourceTableName : sourceTables) {
                List<DataSourceTableColumnDto> targetTableColumns = tableColums.get(targetTableName);
                DataSourceTableColumnDto targetTableColumn = targetTableColumns.get(0);
                String targetUrn = String.join(SystemConstant.COLON,
                        targetTableColumn.getDatabaseType(), targetTableColumn.getDatasourceName(),
                        targetTableColumn.getDatabaseName(), targetTableColumn.getSchemaName(),
                        getSimpleTabelName(targetTableColumn.getTableName()));
                List<DataSourceTableColumnDto> sourceTableColumns = tableColums.get(sourceTableName);
                DataSourceTableColumnDto sourceTableColumn = sourceTableColumns.get(0);
                String sourceUrn = String.join(SystemConstant.COLON,
                        sourceTableColumn.getDatabaseType(), sourceTableColumn.getDatasourceName(),
                        sourceTableColumn.getDatabaseName(), sourceTableColumn.getSchemaName(),
                        getSimpleTabelName(sourceTableColumn.getTableName()));

                AssetLineageEdgeEntity edgeEntity = new AssetLineageEdgeEntity();
                edgeEntity.setResourceId(resourceId)
                        .setSourceUrn(sourceUrn)
                        .setTargetUrn(targetUrn)
                        .setCreateTime(new Date())
                        .setUpdateTime(new Date())
                        .setUpdater(HttpUtils.getCurrentUserName())
                        .setCreator(HttpUtils.getCurrentUserName());
                edgeEntity.setId(UUID.randomUUID());
                edges.add(edgeEntity);
            }
        }
        return edges;
    }

    /**
     * 通过schema进行拆分,然后获取表信息.
     *
     * @param tableName 表名
     * @return 返回简单表名
     */
    private String getSimpleTabelName(String tableName) {
        if (StringUtils.isEmpty(tableName)) {
            return null;
        }
        if (tableName.contains(".")) {
            return tableName.split("\\.")[1];
        }
        return tableName;
    }

    /**
     * 通过schema进行拆分,然后获取表信息.
     *
     * @param tableNames 数据源ID
     * @param snapshot   表名称集合
     * @return 表信息, 供注册和以后使用
     */
    private Map<String, List<DataSourceTableColumnDto>> getTableColumnInfo(Set<String> tableNames, EtlSnapshot snapshot) {
        //如果表名是临时表
        //对于携带schema和默认相同的,需要两边都注册,不然可能会解析失败
        final Set<String> schemaDefaultTables = tableNames.stream()
                .filter(table -> table.contains(".")
                        && table.split("\\.")[0].equals(snapshot.getSchemaName()))
                .collect(Collectors.toSet());
        Set<String> noSchemaTables = tableNames.stream().filter(table -> !table.contains(".")).collect(Collectors.toSet());
        log.info("noSchemaTables:{}", noSchemaTables);
        //包含schema的需要获取对应的datasourceId
        //不带schema的,默认schema和snapshot相同
        Map<String, Set<String>> schemaNotDefaultTableMap = new HashMap<>();
        //没有schema的,默认schema和snapshot相同，但是有可能表是tmp表导致解析出错
        Map<String, List<DataSourceTableColumnDto>> noSchemaTableColumn = new HashMap<>();
        try {
            if (!noSchemaTables.isEmpty()) {
                noSchemaTableColumn = getTableColumnInfoByDatasourceId(noSchemaTables, snapshot.getDatasourceId());
                log.info("获取字段成功");
            }
        } catch (Exception e) {
            e.printStackTrace();
            for (String tableName : schemaDefaultTables) {
                if (tableName.startsWith("tmp_")) {
                    schemaDefaultTables.add("tmp." + tableName);
                }

            }
            log.error("获取字段失败", e);
        }
        for (String tableName : tableNames) {
            if (tableName.contains(".") && !tableName.split("\\.")[0].equals(snapshot.getSchemaName())) {
                //schema和默认的schema不相同
                String schemaName = tableName.split("\\.")[0];
                Set<String> tableSet = schemaNotDefaultTableMap.get(schemaName);
                if (tableSet == null) {
                    tableSet = new HashSet<>();
                    tableSet.add(tableName);
                    schemaNotDefaultTableMap.put(schemaName, tableSet);
                } else {
                    tableSet.add(tableName);
                }
            }
        }
        DataSourceInfoDto mainDataSource = dataSourceInfoService.getDataSource(snapshot.getDatasourceId());
        String host = mainDataSource.getHost();
        for (Map.Entry<String, Set<String>> entry : schemaNotDefaultTableMap.entrySet()) {
            //有多少不同的schema,则需要获取对应的datasourceId
            String schemaName = entry.getKey();
            Wrapper<DataSourceInfoEntity> query = Wrappers.<DataSourceInfoEntity>lambdaQuery() //
                    .eq(DataSourceInfoEntity::getHost, host)
                    .eq(DataSourceInfoEntity::getSchemaName, schemaName);
            DataSourceInfoEntity datasource = dataSourceInfoService.getOne(query);
            if (datasource == null) {
                log.error("get dataSource failed,schemaName:{}", schemaName);
                throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "schemaName not invalid,schemaName:" + schemaName);
            }
            Map<String, List<DataSourceTableColumnDto>> datasourceTableColumns = getTableColumnInfoByDatasourceId(entry.getValue(),
                    datasource.getId());
            noSchemaTableColumn.putAll(datasourceTableColumns);
        }
        Map<String, List<DataSourceTableColumnDto>> schemaDefaultTableColumn = getTableColumnInfoByDatasourceId(schemaDefaultTables,
                snapshot.getDatasourceId());
        noSchemaTableColumn.putAll(schemaDefaultTableColumn);
        return noSchemaTableColumn;
    }

    /**
     * 保存外表对应的 MaxCompute 表节点.
     *
     * @param foreignTableMappings 外表映射关系
     * @param etlSnapshot          原始 Hologres 数据源信息（用于获取外表的 schema/database）
     */
    private List<AssetLineageNodeEntity> saveForeignTableMcNodes(Map<String, ForeignTableMapping> foreignTableMappings, EtlSnapshot etlSnapshot) {
        List<AssetLineageNodeEntity> results = new ArrayList<>();

        for (ForeignTableMapping mapping : foreignTableMappings.values()) {
            String mcTableName = mapping.getMcTableName();
            List<DataSourceTableColumnDto> mcColumns = mapping.getMcColumns();

            if (CollectionUtil.isEmpty(mcColumns)) {
                continue;
            }

            DataSourceTableColumnDto firstColumn = mcColumns.get(0);

            // 生成 MaxCompute 表的 URN
            String tableUrn = String.join(SystemConstant.COLON,
                    mapping.getMcDataSource().getDatabaseType(),
                    mapping.getMcDataSource().getName(),
                    mapping.getMcDataSource().getDatabaseName(),
                    mapping.getMcDataSource().getSchemaName(),
                    mcTableName);

            // 创建 MaxCompute 表节点
            AssetLineageNodeEntity tableNodeEntity = new AssetLineageNodeEntity();
            tableNodeEntity.setNodeName(mcTableName)
                    .setNodeUrn(tableUrn)
                    .setNodeType(NodeTypeEnum.DATABASE.getNodeType())
                    .setNodeSubType(NodeSubTypeEnum.TABLE.getNodeSubType())
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date())
                    .setUpdater(HttpUtils.getCurrentUserName())
                    .setCreator(HttpUtils.getCurrentUserName());
            tableNodeEntity.setId(UUID.randomUUID());
            results.add(tableNodeEntity);

            // 创建 MaxCompute 表的列节点
            for (DataSourceTableColumnDto columnDto : mcColumns) {
                AssetLineageNodeEntity columnNodeEntity = new AssetLineageNodeEntity();
                String columnUrn = String.join(SystemConstant.COLON, tableUrn, columnDto.getColumnName());
                columnNodeEntity.setNodeName(columnDto.getColumnName())
                        .setNodeUrn(columnUrn)
                        .setNodeType(NodeTypeEnum.DATABASE.getNodeType())
                        .setNodeSubType(NodeSubTypeEnum.COLUMN.getNodeSubType())
                        .setCreateTime(new Date())
                        .setUpdateTime(new Date())
                        .setUpdater(HttpUtils.getCurrentUserName())
                        .setCreator(HttpUtils.getCurrentUserName());
                columnNodeEntity.setId(UUID.randomUUID());
                results.add(columnNodeEntity);
            }
        }
        return results;
    }

    /**
     * 保存外表到 MaxCompute 表的映射边.
     *
     * @param actualEdges          实际的血缘边（结果表 → 外表）
     * @param foreignTableMappings 外表映射关系
     * @param tableColumns         所有表的列信息
     * @param resourceId           资源ID
     */
    private List<AssetLineageEdgeEntity> getForeignTableMappingEdges(List<EdgeTableColumnDto> actualEdges,
            Map<String, ForeignTableMapping> foreignTableMappings,
            Map<String, List<DataSourceTableColumnDto>> tableColumns,
            UUID resourceId) {
        // 收集所有涉及外表的字段
        Map<String, Set<String>> foreignTableFields = new HashMap<>();

        for (EdgeTableColumnDto edge : actualEdges) {
            List<EdgeColumnInfoDto> sourceColumnInfos = edge.getSourceColumnInfos();
            if (CollectionUtils.isEmpty(sourceColumnInfos)) {
                continue;
            }

            for (EdgeColumnInfoDto sourceColumnInfo : sourceColumnInfos) {
                String sourceTableName = sourceColumnInfo.getTableName();
                if (foreignTableMappings.containsKey(sourceTableName)) {
                    // 这是一个外表的字段
                    String columnName = sourceColumnInfo.getColumnName();
                    foreignTableFields.computeIfAbsent(sourceTableName, k -> new HashSet<>()).add(columnName);
                }
            }
        }

        List<AssetLineageEdgeEntity> edges = new ArrayList<>();

        // 为每个外表字段生成到 MaxCompute 表字段的映射边
        for (Map.Entry<String, Set<String>> entry : foreignTableFields.entrySet()) {
            String foreignTableName = entry.getKey();
            Set<String> columnNames = entry.getValue();

            ForeignTableMapping mapping = foreignTableMappings.get(foreignTableName);
            if (mapping == null) {
                continue;
            }

            List<DataSourceTableColumnDto> holoColumns = tableColumns.get(foreignTableName);
            if (CollectionUtil.isEmpty(holoColumns)) {
                continue;
            }

            DataSourceTableColumnDto holoFirstColumn = holoColumns.get(0);

            for (String columnName : columnNames) {
                // 查找 MaxCompute 表中对应的字段
                DataSourceTableColumnDto mcColumn = mapping.getMcColumns().stream()
                        .filter(c -> c.getColumnName().equals(columnName))
                        .findFirst()
                        .orElse(null);

                if (mcColumn == null) {
                    log.warn("MaxCompute 表 {} 中未找到字段 {}", mapping.getMcTableName(), columnName);
                    continue;
                }

                // 构建外表字段 URN（目标）
                String foreignTableSimpleName = getSimpleTabelName(foreignTableName);
                String foreignColumnUrn = String.join(SystemConstant.COLON,
                        holoFirstColumn.getDatabaseType(),
                        holoFirstColumn.getDatasourceName(),
                        holoFirstColumn.getDatabaseName(),
                        holoFirstColumn.getSchemaName(),
                        foreignTableSimpleName,
                        columnName);

                // 构建 MaxCompute 字段 URN（源）
                String mcColumnUrn = null;
                mcColumnUrn = String.join(SystemConstant.COLON,
                        mapping.getMcDataSource().getDatabaseType(),
                        mapping.getMcDataSource().getName(),
                        mapping.getMcDataSource().getDatabaseName(),
                        mapping.getMcDataSource().getSchemaName(),
                        mapping.getMcTableName(),
                        columnName);

                // 创建映射边（MaxCompute → 外表）
                AssetLineageEdgeEntity edgeEntity = new AssetLineageEdgeEntity();
                edgeEntity.setSourceUrn(mcColumnUrn); // MaxCompute 字段作为源
                edgeEntity.setTargetUrn(foreignColumnUrn); // 外表字段作为目标
                edgeEntity.setResourceId(resourceId);
                edgeEntity.setCreateTime(new Date());
                edgeEntity.setUpdateTime(new Date());
                edgeEntity.setUpdater(HttpUtils.getCurrentUserName());
                edgeEntity.setCreator(HttpUtils.getCurrentUserName());
                edgeEntity.setId(UUID.randomUUID());

                edges.add(edgeEntity);
            }

            // 同时创建表级映射边
            String foreignTableUrn = String.join(SystemConstant.COLON,
                    holoFirstColumn.getDatabaseType(),
                    holoFirstColumn.getDatasourceName(),
                    holoFirstColumn.getDatabaseName(),
                    holoFirstColumn.getSchemaName(),
                    getSimpleTabelName(foreignTableName));

            String mcTableUrn = String.join(SystemConstant.COLON,
                    mapping.getMcDataSource().getDatabaseType(),
                    mapping.getMcDataSource().getName(),
                    mapping.getMcDataSource().getDatabaseName(),
                    mapping.getMcDataSource().getSchemaName(),
                    mapping.getMcTableName());

            AssetLineageEdgeEntity tableEdgeEntity = new AssetLineageEdgeEntity();
            tableEdgeEntity.setSourceUrn(mcTableUrn);
            tableEdgeEntity.setTargetUrn(foreignTableUrn);
            tableEdgeEntity.setResourceId(resourceId);
            tableEdgeEntity.setCreateTime(new Date());
            tableEdgeEntity.setUpdateTime(new Date());
            tableEdgeEntity.setUpdater(HttpUtils.getCurrentUserName());
            tableEdgeEntity.setCreator(HttpUtils.getCurrentUserName());
            tableEdgeEntity.setId(UUID.randomUUID());

            edges.add(tableEdgeEntity);
        }

        return edges;
    }

    /**
     * 清理孤立节点：如果一个节点既不是任何边的起点，也不是任何边的终点，则从结果中移除.
     */
    private void pruneOrphanNodes(LineEdgeNodeVoV2 result) {
        if (result == null || CollectionUtil.isEmpty(result.getNodeVos())) {
            return;
        }

        // 1. 收集当前所有边涉及到的 URN
        Set<String> activeUrns = new HashSet<>();

        // 实体边关联的 URN
        if (CollectionUtil.isNotEmpty(result.getEntityEdgeVos())) {
            for (EntityEdgeVo edge : result.getEntityEdgeVos()) {
                activeUrns.add(edge.getSource());
                activeUrns.add(edge.getTarget());
            }
        }

        // 属性边关联的容器 URN
        if (CollectionUtil.isNotEmpty(result.getAttributeEdgeVos())) {
            for (AttributeEdgeVo edge : result.getAttributeEdgeVos()) {
                activeUrns.add(edge.getSource());
                activeUrns.add(edge.getTarget());
            }
        }

        // 2. 如果边全被过滤了，点也直接清空
        if (activeUrns.isEmpty()) {
            result.setNodeVos(new ArrayList<>());
            return;
        }

        // 3. 只保留在边中出现的节点
        List<EdgeNodeVo> filteredNodes = result.getNodeVos().stream()
                .filter(node -> activeUrns.contains(node.getNodeUrn()))
                .collect(Collectors.toList());

        result.setNodeVos(filteredNodes);
    }

    // --- 基础工具方法 (保持不变) ---
    private String getTableNameFromUrn(String urn) {
        if (StringUtils.isBlank(urn)) {
            return null;
        }
        String[] parts = urn.split(":");
        return parts.length > 4 ? parts[4] : null;
    }

    private boolean isTmpTable(String tableName) {
        return tableName != null && tableName.startsWith("tmp_");
    }

    private void recursiveTrace(String currentUrn, boolean isUp, Set<String> visited, Set<String> results) {
        String tableName = getTableNameFromUrn(currentUrn);
        if (!isTmpTable(tableName)) {
            results.add(currentUrn);
            return;
        }
        if (visited.contains(currentUrn)) {
            return;
        }
        visited.add(currentUrn);
        EdgeNodeRequestVo traceReq = new EdgeNodeRequestVo();
        traceReq.setNodeUrn(currentUrn);
        traceReq.setDepth(1);
        traceReq.setIsUp(isUp);
        traceReq.setIsLeafNode(isColumnUrn(currentUrn));
        List<LineageEdgeDto> nextLinks = edgeMapper.link(traceReq);
        if (CollectionUtil.isEmpty(nextLinks)) {
            results.add(currentUrn);
            return;
        }
        for (LineageEdgeDto link : nextLinks) {
            recursiveTrace(isUp ? link.getSourceUrn() : link.getTargetUrn(), isUp, visited, results);
        }
    }

    private Set<String> findFinalUrns(String urn, boolean isUp) {
        Set<String> finalUrns = new HashSet<>();
        Set<String> visited = new HashSet<>();
        recursiveTrace(urn, isUp, visited, finalUrns);
        return finalUrns;
    }

    /**
     * 实现 linkBusinessV3.
     * 实现 linkBusinessV3.
     * 1. 支持临时表递归溯源。
     * 2. 字段/指标归类到容器的 attributes 属性中（attributeName, attributeUrn）。
     * 3. 严格区分边类型：
     *    - 只要有一端是叶子节点（COLUMN/METRIC），则只生成属性边（attributeEdgeVos）。
     *    - 只有两端都是容器（TABLE/API/MENU）时，才生成实体边（entityEdgeVos），消除冗余。
     * 4. 支持 API 调用边的 tag/dimension 过滤。
     * @param req 查询实体
     * @return 血缘关系数据
     */
    public LineEdgeNodeVoV2 linkBusinessV3(EdgeNodeRequestVo req) {
        LineEdgeNodeVoV2 result = new LineEdgeNodeVoV2();
        if (StringUtils.isBlank(req.getNodeUrn())) {
            return result;
        }

        // 1. 判定是否符合“统一指标”特殊溯源逻辑
        if (shouldTriggerUnifiedMetricLogic(req)) {
            return handleUnifiedMetricRecursive(req);
        }

        // 2. 普通搜索逻辑
        Set<String> seedUrns = new HashSet<>();
        seedUrns.add(req.getNodeUrn());

        List<LineageEdgeDto> rawEdges = edgeMapper.linkV2(req);
        if (CollectionUtil.isEmpty(rawEdges)) {
            handleV3SingleNode(result, seedUrns);
            dealTagInfo(req, result);
            return result;
        }

        resolveV3LineageLogic(result, rawEdges, req, seedUrns);
        dealTagInfo(req, result);
        return result;
    }

    /**
     * 判定是否触发统一指标逻辑：METRIC 类型 & type='un' & isUp 为空.
     */
    private boolean shouldTriggerUnifiedMetricLogic(EdgeNodeRequestVo req) {
        if (req.getIsUp() != null) {
            return false;
        }
        AssetLineageNodeEntity node = nodeService.getOne(new LambdaQueryWrapper<AssetLineageNodeEntity>()
                .eq(AssetLineageNodeEntity::getNodeUrn, req.getNodeUrn()));

        if (node != null && NodeSubTypeEnum.METRIC.getNodeSubType().equals(node.getNodeSubType())) {
            JsonNode prop = node.getNodeProp(); // 此时 nodeProp 已经是 JsonNode
            return prop != null && "un".equals(prop.path("type").asText());
        }
        return false;
    }

    /**
     * 统一指标专用：双中心递归处理.
     */
    private LineEdgeNodeVoV2 handleUnifiedMetricRecursive(EdgeNodeRequestVo req) {
        LineEdgeNodeVoV2 result = new LineEdgeNodeVoV2();
        String currentUrn = req.getNodeUrn();
        // 截断获取父节点 URN: 取最后一个冒号之前的
        String parentUrn = currentUrn.contains(":") ? currentUrn.substring(0, currentUrn.lastIndexOf(":")) : currentUrn;
        String[] tagDimension = currentUrn.substring(currentUrn.lastIndexOf(":") + 1).split(",");

        List<LineageEdgeDto> combinedEdges = new ArrayList<>();
        req.setTag(tagDimension[0]);
        req.setDimension(tagDimension[1]);

        // A. 当前节点递归：向上(深度) + 向下(深度)
        combinedEdges.addAll(fetchEdgesRecursive(currentUrn, true, 1, null, null));
        combinedEdges.addAll(fetchEdgesRecursive(currentUrn, false, 1, null, null));

        // B. 父节点递归：向上(1层) + 向下(深度)，需带 tag/dimension 过滤
        combinedEdges.addAll(fetchEdgesRecursive(parentUrn, true, 1, tagDimension[0], tagDimension[1]));
        combinedEdges.addAll(fetchEdgesRecursive(parentUrn, false, null, tagDimension[0], tagDimension[1]));

        // C. 合并与组装
        Set<String> seedUrns = new HashSet<>(Arrays.asList(currentUrn, parentUrn));
        // 去重边
        List<LineageEdgeDto> distinctEdges = combinedEdges.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(e -> e.getSourceUrn() + "->" + e.getTargetUrn()))),
                        ArrayList::new));

        if (distinctEdges.isEmpty()) {
            handleV3SingleNode(result, seedUrns);
        } else {
            resolveV3LineageLogic(result, distinctEdges, req, seedUrns);
        }

        dealTagInfo(req, result);
        return result;
    }

    /**
     * 通用递归搜索引擎 (BFS 逻辑).
     * @param direction true 为上游(isUp=true), false 为下游(isUp=false)
     * @param maxDepth 深度限制，null 为无限制
     */
    private List<LineageEdgeDto> fetchEdgesRecursive(String startUrn, Boolean direction, Integer maxDepth, String tag, String dimension) {
        List<LineageEdgeDto> resultEdges = new ArrayList<>();
        Set<String> visitedUrns = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startUrn);

        int depth = 0;
        while (!queue.isEmpty()) {
            if (maxDepth != null && depth >= maxDepth) {
                break;
            }

            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String current = queue.poll();
                if (visitedUrns.contains(current)) {
                    continue;
                }
                visitedUrns.add(current);

                // 构造单步请求
                EdgeNodeRequestVo stepReq = new EdgeNodeRequestVo();
                stepReq.setNodeUrn(current);
                stepReq.setIsUp(direction);
                stepReq.setIsLeafNode(current.contains(":")); // 简单判定逻辑，可根据实际调整

                List<LineageEdgeDto> stepEdges = edgeMapper.linkV2(stepReq);
                if (CollectionUtil.isEmpty(stepEdges)) {
                    continue;
                }

                for (LineageEdgeDto edge : stepEdges) {
                    // 如果存在 tag/dimension 过滤需求
                    if (StringUtils.isNotBlank(tag) && StringUtils.isNotBlank(dimension)) {
                        if (!checkEdgePropMatch(edge.getEdgeProp(), tag, dimension)) {
                            continue;
                        }
                    }

                    resultEdges.add(edge);
                    // 决定下一个探索节点
                    String nextUrn = direction ? edge.getSourceUrn() : edge.getTargetUrn();
                    queue.add(nextUrn);
                }
            }
            depth++;
        }
        return resultEdges;
    }

    /**
     * 匹配边属性中的 tag 和 dimension.
     */
    private boolean checkEdgePropMatch(JsonNode edgeProp, String tag, String dimension) {
        if (edgeProp == null) {
            return false;
        }

        try {
            // 假设 TAG_SET = "tagSet"
            JsonNode tagSetArray = JacksonUtils.unwrapTagSet(edgeProp, TAG_SET);
            if (tagSetArray.isArray()) {
                for (JsonNode item : tagSetArray) {
                    if (tag.equals(item.path("tag").asText()) && dimension.equals(item.path("dimension").asText())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void dealTagInfo(EdgeNodeRequestVo req, LineEdgeNodeVoV2 result) {
        if (req.getTag() == null) {
            //先判断当前节点有无指标数据
            if (CollectionUtil.isNotEmpty(result.getNodeVos())
                    && result.getNodeVos().stream()
                            .anyMatch(m -> NodeSubTypeEnum.API.getNodeSubType().equals(m.getNodeSubType())
                                    && m.getNodeUrn().contains("api/biz-data/unified_metrics"))) {
                EdgeNodeVo apiEdgeNodeVo = result.getNodeVos().stream()
                        .filter(m -> NodeSubTypeEnum.API.getNodeSubType().equals(m.getNodeSubType())
                                && m.getNodeUrn().contains("api/biz-data/unified_metrics"))
                        .findFirst().get();
                if (apiEdgeNodeVo != null && CollectionUtil.isNotEmpty(apiEdgeNodeVo.getAttributes())) {
                    AssetNodeAttributesDto assetNodeAttributesDto = apiEdgeNodeVo.getAttributes().stream().findFirst().get();
                    String[] tempArray = StringUtils.substringAfterLast(assetNodeAttributesDto.getAttributeUrn(), ":").split(",");
                    result.setTag(tempArray[0]);
                    result.setDimension(tempArray[1]);
                    result.getNodeVos().stream().forEach(m -> {
                        m.setTag(tempArray[0]);
                        m.setDimension(tempArray[1]);
                    });
                }
            }

        } else {
            result.setTag(req.getTag());
            result.setDimension(req.getDimension());
            if (CollectionUtil.isNotEmpty(result.getNodeVos())) {
                result.getNodeVos().stream().forEach(m -> {
                    m.setTag(req.getTag());
                    m.setDimension(req.getDimension());
                });
            }
        }
    }

    /**
     * 核心逻辑：解析原始边数据，转换为 V2 版本的节点与边结构.
     *
     * @param result        结果容器
     * @param rawEdges      从数据库/递归搜索引擎中获取的原始边列表
     * @param req           原始请求参数（用于 tag/dimension 过滤）
     * @param seedUrns      起始节点集合（包含当前指标及父容器）
     */
    private void resolveV3LineageLogic(LineEdgeNodeVoV2 result, List<LineageEdgeDto> rawEdges, EdgeNodeRequestVo req, Set<String> seedUrns) {
        ObjectMapper mapper = new ObjectMapper();
        // 存储 边(pairKey) -> 属性(ArrayNode) 的映射
        Map<String, ArrayNode> pairToPropMap = new HashMap<>();
        // 存储所有涉及到的 URN（包含种子节点、解析出的中间节点、终点节点）
        Set<String> allInvolvedUrns = new HashSet<>(seedUrns);
        // 存储解析后的 边字符串 (sourceUrn->targetUrn)
        Set<String> resolvedPairs = new HashSet<>();

        // --- 第一步：递归追溯真实 URN 并记录边关系 ---
        if (CollectionUtil.isNotEmpty(rawEdges)) {
            for (LineageEdgeDto edge : rawEdges) {
                // findFinalUrns 用于处理临时表或复杂的列级溯源转换
                Set<String> finalSources = findFinalUrns(edge.getSourceUrn(), true);
                Set<String> finalTargets = findFinalUrns(edge.getTargetUrn(), false);

                for (String sCol : finalSources) {
                    for (String tCol : finalTargets) {
                        if (sCol.equals(tCol)) {
                            continue;
                        }
                        String pairKey = sCol + "->" + tCol;
                        resolvedPairs.add(pairKey);
                        allInvolvedUrns.add(sCol);
                        allInvolvedUrns.add(tCol);

                        // 合并边上的属性（如 tag, dimension 等信息）
                        if (edge.getEdgeProp() != null) {
                            ArrayNode merged = pairToPropMap.computeIfAbsent(pairKey, k -> mapper.createArrayNode());
                            // TAG_SET 通常定义为 "tagSet"
                            JsonNode tagSetArray = JacksonUtils.unwrapTagSet(edge.getEdgeProp(), TAG_SET);
                            if (tagSetArray.isArray()) {
                                merged.addAll((ArrayNode) tagSetArray);
                            } else {
                                merged.add(tagSetArray);
                            }
                        }
                    }
                }
            }
        }

        // --- 第二步：批量获取所有相关节点的元数据（类型、名称等） ---
        Map<String, String> nodeTypeMap = new HashMap<>();
        Map<String, String> nodeNameMap = new HashMap<>();
        if (!allInvolvedUrns.isEmpty()) {
            List<AssetLineageNodeEntity> nodes = nodeService.list(new LambdaQueryWrapper<AssetLineageNodeEntity>()
                    .in(AssetLineageNodeEntity::getNodeUrn, allInvolvedUrns));
            for (AssetLineageNodeEntity n : nodes) {
                nodeTypeMap.put(n.getNodeUrn(), n.getNodeSubType());
                nodeNameMap.put(n.getNodeUrn(), n.getNodeName());
            }
        }

        // --- 第三步：分类构建准备工作 ---
        List<AttributeEdgeVo> finalAttrEdges = new ArrayList<>();
        List<EntityEdgeVo> finalEntityEdges = new ArrayList<>();
        // 容器 URN -> 容器下的属性列表 (如 API -> 包含的 Metrics)
        Map<String, Set<AssetNodeAttributesDto>> containerToAttrs = new HashMap<>();
        // 需要返回给前端的所有容器节点 (TABLE/API/MENU)
        Set<String> containerUrns = new HashSet<>();
        Set<String> processedEntityPairs = new HashSet<>();

        // 【关键修复】：强制将种子节点及其容器加入结果集，确保即便没有边也能显示当前指标
        for (String seed : seedUrns) {
            String type = nodeTypeMap.get(seed);
            if (type == null) {
                continue; // 数据库查不到该节点，跳过
            }

            String container = isLeaf(type) ? getParentUrn(seed) : seed;
            containerUrns.add(container);
            if (isLeaf(type)) {
                // 如果种子是叶子（如指标），将其挂载到父容器的属性列表中
                addAttrV3(containerToAttrs, container, seed, nodeNameMap.get(seed));
            }
        }

        // --- 第四步：分类构建边关系并完善节点收集 ---
        for (String pair : resolvedPairs) {
            String[] parts = pair.split("->");
            String sUrn = parts[0];
            String tUrn = parts[1];
            String sType = nodeTypeMap.get(sUrn);
            String tType = nodeTypeMap.get(tUrn);

            // 如果节点在库中不存在，则忽略该边
            if (sType == null || tType == null) {
                continue;
            }

            // 确定两端节点的容器 URN
            String sContainer = isLeaf(sType) ? getParentUrn(sUrn) : sUrn;
            String tContainer = isLeaf(tType) ? getParentUrn(tUrn) : tUrn;

            // 业务逻辑：API/MENU 之间的调用，需根据请求中的 tag/dimension 再次进行边属性匹配
            if (isContainerType(sType) && isContainerType(tType)) {
                if (StringUtils.isNotBlank(req.getTag()) && StringUtils.isNotBlank(req.getDimension())) {
                    // 如果没有匹配的属性，或者 edgeProp 本身为空，则丢弃
                    if (!checkPropMatch(pairToPropMap.get(pair), req.getTag(), req.getDimension())) {
                        continue;
                    }
                }
            }

            // 核心边分类逻辑
            if (isLeaf(sType) || isLeaf(tType)) {
                // 情况A：只要有一端是叶子（COLUMN/METRIC），生成【属性边】，不生成实体边
                AttributeEdgeVo attrEdge = new AttributeEdgeVo();
                attrEdge.setId(pair);
                attrEdge.setSource(sContainer);
                attrEdge.setTarget(tContainer);
                attrEdge.setSourceHandle(sUrn);
                attrEdge.setTargetHandle(tUrn);
                finalAttrEdges.add(attrEdge);
            } else {
                // 情况B：两端都是容器（API/MENU/TABLE），生成【实体边】
                if (!sContainer.equals(tContainer)) {
                    String entityKey = sContainer + "->" + tContainer;
                    if (processedEntityPairs.add(entityKey)) {
                        EntityEdgeVo ev = new EntityEdgeVo();
                        ev.setId(entityKey);
                        ev.setSource(sContainer);
                        ev.setTarget(tContainer);
                        // 包装边属性回 Json 格式
                        ev.setEdgeProp(JacksonUtils.wrapTagSet(pairToPropMap.get(pair), TAG_SET));
                        finalEntityEdges.add(ev);
                    }
                }
            }

            // 记录所有在边关系中涉及到的容器 URN
            containerUrns.add(sContainer);
            containerUrns.add(tContainer);
            // 如果是叶子节点，归类到各自容器的属性中
            if (isLeaf(sType)) {
                addAttrV3(containerToAttrs, sContainer, sUrn, nodeNameMap.get(sUrn));
            }
            if (isLeaf(tType)) {
                addAttrV3(containerToAttrs, tContainer, tUrn, nodeNameMap.get(tUrn));
            }

        }

        // --- 第五步：组装最终的 NodeVos 列表 ---
        if (!containerUrns.isEmpty()) {
            List<AssetLineageNodeEntity> containers = nodeService.list(new LambdaQueryWrapper<AssetLineageNodeEntity>()
                    .in(AssetLineageNodeEntity::getNodeUrn, containerUrns));

            List<EdgeNodeVo> nodeVos = new ArrayList<>();
            for (AssetLineageNodeEntity c : containers) {
                EdgeNodeVo vo = new EdgeNodeVo();
                vo.setNodeUrn(c.getNodeUrn());
                vo.setNodeName(c.getNodeName());
                vo.setNodeSubType(c.getNodeSubType());
                // 设置当前容器下的所有属性（如 API 下的所有 Metrics）
                vo.setAttributes(containerToAttrs.get(c.getNodeUrn()));
                nodeVos.add(vo);
            }
            result.setNodeVos(nodeVos);
        }

        result.setAttributeEdgeVos(finalAttrEdges);
        result.setEntityEdgeVos(finalEntityEdges);

        // 清理孤立节点（如有必要）
        pruneOrphanNodes(result);
    }

    private boolean isContainerType(String type) {
        return NodeSubTypeEnum.API.getNodeSubType().equals(type)
                || NodeSubTypeEnum.MENU.getNodeSubType().equals(type);
    }

    private boolean isLeaf(String type) {
        return NodeSubTypeEnum.COLUMN.getNodeSubType().equals(type)
                || NodeSubTypeEnum.METRIC.getNodeSubType().equals(type);
    }

    private void addAttrV3(Map<String, Set<AssetNodeAttributesDto>> map, String container, String urn, String name) {
        AssetNodeAttributesDto dto = new AssetNodeAttributesDto();
        dto.setAttributeUrn(urn);
        dto.setAttributeName(name != null ? name : urn.substring(urn.lastIndexOf(":") + 1));
        map.computeIfAbsent(container, k -> new HashSet<>()).add(dto);
    }

    private boolean checkPropMatch(ArrayNode props, String tag, String dimension) {
        if (props == null || props.isEmpty()) {
            return false;
        }
        for (JsonNode item : props) {
            if (tag.equals(item.path("tag").asText()) && dimension.equals(item.path("dimension").asText())) {
                return true;
            }
        }
        return false;
    }

    private void handleV3SingleNode(LineEdgeNodeVoV2 result, Set<String> seedUrns) {
        List<AssetLineageNodeEntity> nodes = nodeService.list(new LambdaQueryWrapper<AssetLineageNodeEntity>()
                .in(AssetLineageNodeEntity::getNodeUrn, seedUrns));
        List<EdgeNodeVo> vos = new ArrayList<>();
        for (AssetLineageNodeEntity node : nodes) {
            EdgeNodeVo vo = new EdgeNodeVo();
            String urn = node.getNodeUrn();
            String type = node.getNodeSubType();
            if (isLeaf(type)) {
                String pUrn = getParentUrn(urn);
                vo.setNodeUrn(pUrn);
                vo.setNodeName(pUrn.substring(pUrn.lastIndexOf(":") + 1));
                AssetNodeAttributesDto ad = new AssetNodeAttributesDto();
                ad.setAttributeUrn(urn);
                ad.setAttributeName(node.getNodeName());
                vo.setAttributes(new HashSet<>(Collections.singletonList(ad)));
                vo.setNodeSubType(NodeSubTypeEnum.METRIC.getNodeSubType().equals(type) ? NodeSubTypeEnum.API.getNodeSubType()
                        : NodeSubTypeEnum.TABLE.getNodeSubType());
            } else {
                vo.setNodeUrn(urn);
                vo.setNodeName(node.getNodeName());
                vo.setNodeSubType(type);
            }
            vos.add(vo);
        }
        result.setNodeVos(vos);
        result.setEntityEdgeVos(new ArrayList<>());
        result.setAttributeEdgeVos(new ArrayList<>());
    }

    /**
     * 外表映射关系.
     */
    @Data
    public static class ForeignTableMapping {
        /**
         * Hologres 外表全名（包含 schema）.
         */
        private String foreignTableName;

        /**
         * MaxCompute 表名.
         */
        private String mcTableName;

        /**
         * MaxCompute 数据源信息.
         */
        private DataSourceInfoDto mcDataSource;

        /**
         * MaxCompute 表的列信息.
         */
        private List<DataSourceTableColumnDto> mcColumns;
    }

    /**
     * 获取 HG_CREATE_TABLE_LIKE 表名映射关系.
     * map.
     *
     * @return 表名映射关系
     */
    private Map<String, Map<String, String>> getTableRenameMap() {
        Map<String, Map<String, String>> tableRenameMap = new HashMap<>();

        // 查询包含 HG_CREATE_TABLE_LIKE 的资源
        LambdaQueryWrapper<AssetLineageResourceEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetLineageResourceEntity::getResourceType, "ETL");
        queryWrapper.apply("UPPER(resource_snapshot->>'sql') LIKE '%CALL HG_CREATE_TABLE_LIKE%'");

        List<AssetLineageResourceEntity> resources = resourceMapper.selectList(queryWrapper);

        if (CollectionUtil.isEmpty(resources)) {
            return tableRenameMap;
        }

        // 解析每个资源的映射关系
        for (AssetLineageResourceEntity resource : resources) {
            JsonNode snapshot = resource.getResourceSnapshot();
            if (snapshot == null) {
                continue;
            }

            String fileNameWithExt = snapshot.has("fileName") ? snapshot.get("fileName").asText() : null;
            String sql = snapshot.has("sql") ? snapshot.get("sql").asText() : null;

            if (fileNameWithExt == null || sql == null) {
                continue;
            }
            sql = SqlVariableResolver.replaceSqlVariables(sql);

            // 文件名去掉 .sql 后缀
            String fileName = fileNameWithExt.replaceAll("\\.sql$", "");

            // 解析 CALL HG_CREATE_TABLE_LIKE('临时表', '源表') 格式
            // 格式: HG_CREATE_TABLE_LIKE('dw.tmp_xxx', 'select * from dw.xxx') 或 HG_CREATE_TABLE_LIKE('dw.tmp_xxx', 'dw.xxx')
            // group(1) = 临时表（写入目标）
            // group(2) = 源表参数（可能是 'select * from dw.xxx' 或 'dw.xxx'）
            Pattern pattern = Pattern.compile(
                    "HG_CREATE_TABLE_LIKE\\s*\\(\\s*'([^']+)',\\s*'([^']+)'\\s*\\)",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sql);

            Map<String, String> tableMap = tableRenameMap.computeIfAbsent(fileName, k -> new HashMap<>());

            while (matcher.find()) {
                String tempTable = matcher.group(1); // 临时表（写入目标）
                String sourceArg = matcher.group(2); // 源表参数（可能是 'select * from dw.xxx' 或 'dw.xxx'）

                // 从源表参数中提取表名（去掉 'select * from ' 前缀）
                String sourceTable = sourceArg.replaceAll("(?i)^select\\s+.*?\\s+from\\s+", "").trim();

                tableMap.put(tempTable, sourceTable);
                log.info("解析到表名映射: {} -> {} (fileName: {})", tempTable, sourceTable, fileName);
            }
        }

        return tableRenameMap;
    }

    /**
     * 替换SQL中的临时表名为源表名.
     *
     * @param sql            SQL语句
     * @param tableRenameMap 表名映射关系 (文件名, (临时表, 源表))
     * @param fileName       当前文件名（带.sql后缀）
     * @return 替换后的SQL语句
     */
    private String replaceTempTableNamesInSql(String sql,
            Map<String, Map<String, String>> tableRenameMap,
            String fileName) {
        if (sql == null || tableRenameMap.isEmpty() || fileName == null) {
            return sql;
        }

        // 文件名去掉 .sql 后缀
        String fileNameKey = fileName.replaceAll("\\.sql$", "");
        Map<String, String> tableMap = tableRenameMap.get(fileNameKey);

        if (tableMap == null || tableMap.isEmpty()) {
            return sql;
        }

        // 遍历映射关系，替换SQL中的表名
        String resultSql = sql;
        for (Map.Entry<String, String> entry : tableMap.entrySet()) {
            String sourceTable = entry.getKey();
            String sourceTable1 = null;
            if (sourceTable.contains(".")) {
                sourceTable1 = sourceTable.substring(sourceTable.indexOf(".") + 1);
            }
            String targetTable = entry.getValue();
            if (resultSql.contains(sourceTable)) {
                log.info("SQL表名替换: {} -> {} (fileName: {})", sourceTable, targetTable, fileNameKey);
                resultSql = resultSql.replace(sourceTable, targetTable);
            } else if (sourceTable1 != null && resultSql.contains(sourceTable1)) {
                log.info("SQL表名替换: {} -> {} (fileName: {})", sourceTable1, targetTable, fileNameKey);
                resultSql = SqlConverter.replaceSql(resultSql, sourceTable1, targetTable);
            }
        }

        return resultSql;
    }

    /**
     * 根据datasourceName、tableName、columnName获取血缘关系.
     *
     * @param req 数据源名称
     * @return 血缘关系
     */
    public LineEdgeNodeVoV3 getLineageByColumn(EdgeRequestVo req) {
        //获取nodUrn
        //根据nodeUrn获取血缘
        String nodeUrn = nodeService.getNodeUrn(req);
        LineEdgeNodeVoV3 edgeNodeRequestVoV3 = new LineEdgeNodeVoV3();
        if (nodeUrn == null) {
            return edgeNodeRequestVoV3;
        } else {
            EdgeNodeRequestVo edgeNodeRequestVo = new EdgeNodeRequestVo();
            edgeNodeRequestVo.setNodeUrn(nodeUrn);
            edgeNodeRequestVo.setIsLeafNode(true);
            return linkTable(edgeNodeRequestVo);
        }
    }

}
