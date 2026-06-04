package com.datafusion.manager.metadata.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.type.TypeInfo;
import com.datafusion.common.type.TypeInfoConverter;
import com.datafusion.common.type.TypeInfoManager;
import com.datafusion.common.type.TypeInfoParser;
import com.datafusion.common.utils.AssertUtils;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.uuid.IdGenerator;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.metadata.constant.MetaDataConstant;
import com.datafusion.manager.metadata.constant.TablePropertiesOptions;
import com.datafusion.manager.metadata.dto.BatchCreateTableCheckDto;
import com.datafusion.manager.metadata.dto.BatchCreateTableCheckResultDto;
import com.datafusion.manager.metadata.dto.BatchCreateTableDdlDto;
import com.datafusion.manager.metadata.dto.BatchMetaDataCompareDto;
import com.datafusion.manager.metadata.dto.BatchMetaDataCompareResultDto;
import com.datafusion.manager.metadata.dto.ColumnTreeDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoQueryDto;
import com.datafusion.manager.metadata.dto.MetaDataCompareDto;
import com.datafusion.manager.metadata.dto.MetadataTableOperateLogQueryDto;
import com.datafusion.manager.metadata.dto.MetadataTableOperateLogViewDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.dto.RunSqlDto;
import com.datafusion.manager.metadata.dto.SourceAndSyncedTableDto;
import com.datafusion.manager.metadata.dto.TableBusMetaDataDto;
import com.datafusion.manager.metadata.dto.TableBusMetaDataQueryDto;
import com.datafusion.manager.metadata.dto.TableBusMetaDto;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareInfo;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareResultDto;
import com.datafusion.manager.metadata.dto.TableColumnsTreeDto;
import com.datafusion.manager.metadata.dto.TableCompareDetailQueryDto;
import com.datafusion.manager.metadata.dto.TableCompareGenerateDdlRequestDto;
import com.datafusion.manager.metadata.dto.TableInfoLiteDto;
import com.datafusion.manager.metadata.dto.TableLiteQueryDto;
import com.datafusion.manager.metadata.dto.UpdateDatasourceMetaDataDto;
import com.datafusion.manager.metadata.enums.DefaultColumnEnum;
import com.datafusion.manager.metadata.enums.TableColumnCompareEnum;
import com.datafusion.manager.metadata.enums.TableCompareEnum;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.po.MetadataTableOperateLogEntity;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import com.datafusion.manager.metadata.service.ColumnInfoService;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.service.HistoryDataService;
import com.datafusion.manager.metadata.service.MetaDataService;
import com.datafusion.manager.metadata.service.MetadataTableOperateLogService;
import com.datafusion.manager.metadata.service.TableInfoService;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.MetaDataSupport;
import com.datafusion.manager.metadata.support.TransformSupport;
import com.datafusion.manager.metadata.support.model.DataPreviewQuery;
import com.datafusion.manager.metadata.support.model.MetaDataInfo;
import com.datafusion.manager.metadata.support.model.MetaDataQuery;
import com.datafusion.manager.metadata.support.model.RunSqlParam;
import com.datafusion.manager.metadata.support.model.SelectListColumn;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import com.datafusion.manager.metadata.support.model.TableInfo;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.datafusion.common.constant.SystemConstant.COMMENT_SYMBOL;
import static com.datafusion.common.constant.SystemConstant.LINE_FEED;

/**
 * 元数据服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/30
 * @since 3.6.4, 2024/8/30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaDataServiceImpl implements MetaDataService {
    
    /**
     * 过滤注释用的正则匹配.
     */
    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile("--[^\\n]*");

    /**
     * 元数据-数据库服务.
     */
    private final DataSourceInfoService dataSourceInfoService;
    
    /**
     * 元数据-表服务.
     */
    private final TableInfoService tableInfoService;
    
    /**
     * 元数据-字段信息服务.
     */
    private final ColumnInfoService columnInfoService;
    
    /**
     * 元数据-字段信息服务.
     */
    private final HistoryDataService historyDataService;
    
    /**
     * 操作日志记录.
     */
    private final MetadataTableOperateLogService metadataTableOperateLogService;
    
    
    /**
     * 检查并同步多个数据源的表结构. 使用多线程执行，每个数据源在一个单独的线程中处理。 如果某个线程中发生异常，异常信息会被收集并返回。
     *
     * @param datasourceIds 数据源ID列表
     * @return 包含所有异常的列表
     */
    @Override
    public List<Exception> checkTable(List<UUID> datasourceIds) {
        ExecutorService executorService = Executors.newFixedThreadPool(datasourceIds.size()); // 根据实际情况调整线程池大小
        List<Future<Void>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        
        for (UUID datasourceId : datasourceIds) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    processDataSource(datasourceId, Collections.EMPTY_LIST);
                } catch (Exception e) {
                    exceptions.add(e);
                }
                return null;
            });
            futures.add(future);
        }
        
        // 等待所有任务完成
        for (Future<Void> future : futures) {
            try {
                future.get(); // 等待任务完成
            } catch (InterruptedException | ExecutionException e) {
                exceptions.add((Exception) e.getCause());
            }
        }
        
        executorService.shutdown();
        return exceptions;
    }
    
    @Override
    public SourceAndSyncedTableDto getSourceTablesAndSyncedTables(TableLiteQueryDto tableLiteQueryDto) {
        log.info("用户信息：" + HttpUtils.getUsername());
        MutablePair<List<TableInfoLiteDto>, List<TableInfoLiteDto>> mutablePair =
                processDataSource(tableLiteQueryDto.getDatasourceId(), tableLiteQueryDto.getTableNames());
        
        return new SourceAndSyncedTableDto(mutablePair.getLeft(), mutablePair.getRight());
    }
    
    /**
     * 元数据采集.
     *
     * @param retrieveDto 参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String registerTables(RetrieveMetaDataDto retrieveDto) {
        UUID datasourceId = retrieveDto.getDatasourceId();
        DataSourceInfoEntity ds = dataSourceInfoService.getWithCheckNonNull(datasourceId);
        List<TableInfoEntity> tables = tableInfoService.getTables(datasourceId, retrieveDto.getTableNames());

        String warnMsg = null;
        if (CollectionUtils.isNotEmpty(tables)) {
            List<String> tableNames = tables.stream().map(TableInfoEntity::getTableName).collect(Collectors.toList());
            String existsTableNames = String.join(SystemConstant.COMMA, tableNames);
            
            log.warn("数据表[{}], 已存在, 无法同步", existsTableNames);
            retrieveDto.getTableNames().removeAll(tableNames);
            warnMsg = String.format("数据表[%s]已存在, 无法同步", existsTableNames);
        }
        if (CollectionUtils.isNotEmpty(retrieveDto.getTableNames())) {
            MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(ds);
            TransformSupport transformService = DatabaseSupportManager.getTransformSupport(ds.getDatabaseType());
            List<String> tableNames = retrieveDto.getTableNames();
            //如果数据源是maxcompute，把表分批执行,已处理odps返回10000行的问题
            if (DatabaseTypeEnum.MAXCOMPUTE.getTypeName().equalsIgnoreCase(ds.getDatabaseType())) {
                for (int i = 0; i < tableNames.size(); i += MetaDataConstant.MAXCOMPUTE_META_BATCH_SIZE) {
                    List<String> subList = tableNames.subList(i, Math.min(i +  MetaDataConstant.MAXCOMPUTE_META_BATCH_SIZE, tableNames.size()));
                    processMetaData( subList,  ds, databaseService,  transformService);
                    int finishTablesNum = Math.min(i + MetaDataConstant.MAXCOMPUTE_META_BATCH_SIZE, tableNames.size());
                    log.info("MaxCompute元数据同步进度：已处理[{}/{}]张表", finishTablesNum, tableNames.size());
                }
            } else {
                processMetaData( tableNames,  ds, databaseService,  transformService);
            }
        }
        return warnMsg;
    }

    private void processMetaData(List<String> tableNames, DataSourceInfoEntity ds,
                                 MetaDataSupport databaseService, TransformSupport transformService) {
        MetaDataQuery metaDataQuery = new MetaDataQuery();
        metaDataQuery.setTableNames(tableNames);
        MetaDataInfo metaData = databaseService.getMetaData(transformService.transformDataSourceInfo(ds), metaDataQuery);
        if (Objects.isNull(metaData) || Objects.isNull(metaData.getTables())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "获取元数据失败");
        } else {
            saveMetaData(metaData, ds);
        }
    }
    
    @Override
    public TableBusMetaDto countRowAndSize(UUID tableId) {
        TableInfoEntity table = tableInfoService.getWithCheckNonNull(tableId);
        List<ColumnInfoEntity> columns = columnInfoService.getByTableId(tableId);
        //新增表结构查询的时候,就是没有字段信息,而不是报错
        if (CollectionUtils.isEmpty(columns) || table.getIsModify()) {
            return new TableBusMetaDto(0, "0kb");
        }
        DataSourceInfoEntity dsEntity = dataSourceInfoService.getWithCheckNonNull(table.getDatasourceId());
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dsEntity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo ds = databaseTransformService.transformDataSourceInfo(dsEntity);
        return new TableBusMetaDto(databaseService.getTableCount(ds, table.getTableName()), databaseService.getTableSize(ds, table.getTableName()));
    }
    
    @Override
    public TableBusMetaDataDto getMetaTableData(TableBusMetaDataQueryDto queryDto) {
        AssertUtils.notNull(queryDto.getTableId(), "参数tableId不能为空");
        TableInfoEntity tableInfoEntity = tableInfoService.getWithCheckNonNull(queryDto.getTableId());
        List<ColumnInfoEntity> columns = columnInfoService.getByTableId(queryDto.getTableId());
        if (CollectionUtils.isEmpty(columns)) {
            return new TableBusMetaDataDto();
        }
        //元数据新建的表结构,源数据并没有
        if (tableInfoEntity.getIsModify()) {
            return new TableBusMetaDataDto();
        }
        DataSourceInfoEntity dsEntity = dataSourceInfoService.getWithCheckNonNull(tableInfoEntity.getDatasourceId());
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dsEntity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(dsEntity);
        DataSourceInfo ds = databaseTransformService.transformDataSourceInfo(dsEntity);
        
        DataPreviewQuery queryInfo = new DataPreviewQuery();
        queryInfo.setTableName(tableInfoEntity.getTableName());
        queryInfo.setQueryConditions(queryDto.getQueryConditions());
        queryInfo.setOrderConditions(queryDto.getOrderConditions());
        queryInfo.setLimit(queryDto.getLimit());
        List<Map<String, Object>> data =
                databaseService.getDataPreview(ds, queryInfo);
        // 表头
        List<SelectListColumn> tableHeader =
                columns.stream().map(e -> new SelectListColumn(e.getColumnName(), e.getColumnDesc())).collect(Collectors.toList());
        
        return new TableBusMetaDataDto(tableHeader, data);
    }
    
    @Override
    public List<Exception> refreshTables(List<UUID> datasourceIds) {
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        if (CollectionUtil.isEmpty(datasourceIds)) {
            List<DataSourceInfoEntity> dataSourceInfoList = dataSourceInfoService.list();
            if (CollectionUtil.isEmpty(dataSourceInfoList)) {
                return exceptions;
            }
            datasourceIds = dataSourceInfoList.stream().map(DataSourceInfoEntity::getId).collect(Collectors.toList());
        }
        ExecutorService executorService = Executors.newFixedThreadPool(datasourceIds.size()); // 根据实际情况调整线程池大小
        List<Future<Void>> futures = new ArrayList<>();
        for (UUID datasourceId : datasourceIds) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    freshDataSource(datasourceId, Collections.EMPTY_LIST);
                } catch (Exception e) {
                    exceptions.add(e);
                }
                return null;
            });
            futures.add(future);
        }
        
        // 等待所有任务完成
        for (Future<Void> future : futures) {
            try {
                future.get(); // 等待任务完成
            } catch (InterruptedException | ExecutionException e) {
                exceptions.add((Exception) e.getCause());
            }
        }
        
        executorService.shutdown();
        return exceptions;
    }
    
    @Override
    public List<BatchCreateTableCheckResultDto> batchCreateTableCheck(BatchCreateTableCheckDto batchCreateTableCheckDto) {
        //获取源数据中表结构
        DataSourceInfoEntity sourceDs = dataSourceInfoService.getWithCheckNonNull(batchCreateTableCheckDto.getSourceDatasourceId());
        DataSourceInfoEntity targetDs = dataSourceInfoService.getWithCheckNonNull(batchCreateTableCheckDto.getTargetDatasourceId());
        if (DatabaseTypeEnum.BIGDATA_DATABASE.contains(DatabaseTypeEnum.fromString(sourceDs.getDatabaseType()))
                || DatabaseTypeEnum.RELATIONAL_DATABASE.contains(DatabaseTypeEnum.fromString(targetDs.getDatabaseType()))) {
            throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "批量建表暂时支持从关系型数据库到大数据库建表");
        }
        MetaDataSupport sourceDatabaseService = DatabaseSupportManager.getMetaDataSupport(sourceDs);
        TransformSupport sourceDatabaseTransformService = DatabaseSupportManager.getTransformSupport(sourceDs);
        DataSourceInfo sourceDataSourceInfo = sourceDatabaseTransformService.transformDataSourceInfo(sourceDs);
        MetaDataQuery sourceMetaDataQuery = new MetaDataQuery();
        sourceMetaDataQuery.setTableNames(batchCreateTableCheckDto.getTableNames());
        MetaDataInfo sourceMetaData = sourceDatabaseService.getMetaData(sourceDataSourceInfo, sourceMetaDataQuery);
        Map<String, List<TableColumnInfo>> sourceTableMap = sourceMetaData.getColumns();
        
        Set<String> missingSourceTables = batchCreateTableCheckDto.getTableNames().stream()
                .filter(tableName -> !sourceTableMap.containsKey(tableName))
                .collect(Collectors.toSet());
        if (!missingSourceTables.isEmpty()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,
                    String.join(SystemConstant.COMMA, missingSourceTables) + "源数据源并未查到对应表");
        }
        
        // 2. 处理源表和目标表的映射关系
        Map<String, String> tableMapping = batchCreateTableCheckDto.getTableNames().stream()
                .collect(Collectors.toMap(
                        sourceTableName -> sourceTableName,
                        sourceTableName -> transformTableName(sourceTableName, batchCreateTableCheckDto)
                ));
        
        // 3. 获取目标数据源信息及表结构
        
        MetaDataInfo targetMetaData = getMetaDataInfo(targetDs, new HashSet<>(tableMapping.values()));
        Map<String, List<TableColumnInfo>> targetTableMap = targetMetaData.getColumns();
        
        // 4. 比对结果
        TransformSupport targetDatabaseTransformService = DatabaseSupportManager.getTransformSupport(targetDs);
        DataSourceInfo targetDataSourceInfo = targetDatabaseTransformService.transformDataSourceInfo(targetDs);
        List<BatchCreateTableCheckResultDto> batchTableCompareResults = sourceTableMap.entrySet().stream()
                .map(entry -> {
                    String sourceTableName = entry.getKey();
                    List<TableColumnInfo> sourceColumns = entry.getValue();
                    String targetTableName = tableMapping.get(sourceTableName);
                    
                    BatchCreateTableCheckResultDto currentResult = new BatchCreateTableCheckResultDto();
                    currentResult.setSourceDataSourceId(sourceDataSourceInfo.getId())
                            .setTargetDataSourceId(targetDataSourceInfo.getId())
                            .setSourceTableColumnNums(sourceColumns.size())
                            .setSourceTableName(sourceTableName)
                            .setTargetTableName(targetTableName);
                    
                    List<TableColumnInfo> targetColumns = targetTableMap.get(targetTableName);
                    boolean isTargetTableExist = (targetColumns != null);
                    currentResult.setIsTargetTableExist(isTargetTableExist);
                    
                    if (isTargetTableExist) {
                        currentResult.setTargetTableColumnNums(targetColumns.size());
                    }
                    return currentResult;
                })
                .sorted(Comparator.comparing(BatchCreateTableCheckResultDto::getIsTargetTableExist).reversed()) // Ensure existing tables come first
                .collect(Collectors.toList());
        
        // 5. 记录操作日志
        recordOperationLog(batchCreateTableCheckDto);
        return batchTableCompareResults;
    }
    
    /**
     * 处理前后缀源表名和目标表名映射.
     *
     * @param dto 批量创建批量表检查请求
     */
    private String transformTableName(String sourceTableName, BatchCreateTableCheckDto dto) {
        String targetTableName = sourceTableName;
        //处理前缀
        if (Boolean.TRUE.equals(dto.getIsAddPrefix())) {
            targetTableName = dto.getPrefix() + targetTableName;
        } else if (Boolean.FALSE.equals(dto.getIsAddPrefix())) {
            if (sourceTableName.startsWith(dto.getPrefix())) {
                targetTableName = sourceTableName.substring(dto.getPrefix().length());
            }
        }
        //处理后缀
        if (Boolean.TRUE.equals(dto.getIsAddSuffix())) {
            targetTableName = targetTableName + dto.getSuffix();
        } else if (Boolean.FALSE.equals(dto.getIsAddSuffix())) {
            if (sourceTableName.endsWith(dto.getSuffix())) {
                targetTableName = targetTableName.substring(0, targetTableName.length() - dto.getSuffix().length());
            }
        }
        return targetTableName;
    }
    
    /**
     * 记录操作日志.
     *
     * @param dto 批量创建批量表检查请求
     */
    private void recordOperationLog(BatchCreateTableCheckDto dto) {
        MetadataTableOperateLogEntity operateLogEntity = new MetadataTableOperateLogEntity();
        operateLogEntity.setId(dto.getTrackId());
        operateLogEntity.setOperateType(0); // Assuming 0 is the type for this operation
        operateLogEntity.setOperateTime(LocalDateTime.now());
        operateLogEntity.setSourceDatasourceId(dto.getSourceDatasourceId());
        operateLogEntity.setTargetDatasourceId(dto.getTargetDatasourceId());
        
        try {
            JsonNode snapshotStep1 = JacksonUtils.str2JsonNode(JacksonUtils.obj2PrettyStr(dto));
            operateLogEntity.setSnapshotStep1(snapshotStep1);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize BatchCreateTableCheckDto for operation log, trackId: {}", dto.getTrackId(), e);
        }
        
        String currentUserName = HttpUtils.getCurrentUserName();
        Date now = new Date();
        operateLogEntity.setCreator(currentUserName);
        operateLogEntity.setUpdater(currentUserName);
        operateLogEntity.setCreateTime(now);
        operateLogEntity.setUpdateTime(now);
        metadataTableOperateLogService.addLog(operateLogEntity);
    }
    
    @Override
    public List<TableColumnsTreeDto> getTableInfos(UUID dataSourceId) {
        if (dataSourceId == null || StrUtil.isEmpty(dataSourceId.toString())) {
            throw new CommonException(ErrorCodeEnum.USER_ERROR_A0400, "dataSourceId不能为空");
        }
        List<TableColumnsTreeDto> tableColumnsTreeDtos = new ArrayList<>();
        DataSourceInfoEntity sourceDs =
                dataSourceInfoService.getWithCheckNonNull(dataSourceId);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(sourceDs);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(sourceDs);
        DataSourceInfo dataSourceInfo = databaseTransformService.transformDataSourceInfo(sourceDs);
        MetaDataInfo metaData = databaseService.getMetaData(dataSourceInfo, new MetaDataQuery());
        List<TableInfo> tables = metaData.getTables();
        if (CollectionUtil.isEmpty(tables)) {
            return tableColumnsTreeDtos;
        }
        Map<String, List<TableColumnInfo>> columnsMap = metaData.getColumns();
        tables.stream().forEach(x -> {
            TableColumnsTreeDto tableColumnsTreeDto = new TableColumnsTreeDto();
            tableColumnsTreeDto.setTableName(x.getTableName())
                    .setTableDesc(x.getTableDesc());
            List<TableColumnInfo> tableColumnInfos = columnsMap.get(x.getTableName());
            if (CollectionUtil.isNotEmpty(tableColumnInfos)) {
                List<TableColumnInfo> tableColumnInfoList = columnsMap.get(x.getTableName());
                if (CollectionUtil.isNotEmpty(tableColumnInfoList)) {
                    List<ColumnTreeDto> columnsTreeDtos = tableColumnInfoList.stream().map(y -> {
                                return new ColumnTreeDto().setColumnName(y.getColumnName())
                                        .setColumnDesc(y.getColumnDesc())
                                        .setColumnType(y.getColumnType())
                                        .setColumnFullType(y.getFullColumnType());
                            }
                    ).collect(Collectors.toList());
                    tableColumnsTreeDto.setColumns(columnsTreeDtos);
                    tableColumnsTreeDtos.add(tableColumnsTreeDto);
                }
            }
        });
        return tableColumnsTreeDtos;
    }

    @Override
    public List<TableColumnInfo> getColumnsFromSource(UUID datasourceId, String tableName) {
        if (datasourceId == null) {
            throw new CommonException(ErrorCodeEnum.USER_ERROR_A0400, "datasourceId不能为空");
        }
        if (StrUtil.isEmpty(tableName)) {
            throw new CommonException(ErrorCodeEnum.USER_ERROR_A0400, "tableName不能为空");
        }
        DataSourceInfoEntity dsEntity = dataSourceInfoService.getWithCheckNonNull(datasourceId);
        MetaDataInfo metaData = getMetaDataInfo(dsEntity, Collections.singleton(tableName));
        if (Objects.isNull(metaData) || MapUtil.isEmpty(metaData.getColumns())) {
            return Collections.emptyList();
        }
        List<TableColumnInfo> columns = metaData.getColumns().get(tableName);
        return CollectionUtil.isEmpty(columns) ? Collections.emptyList() : columns;
    }

    @Override
    public String batchCreateTableDdl(BatchCreateTableDdlDto batchCreateTableDdlDto) {
        DataSourceInfoEntity sourceDs = dataSourceInfoService.getWithCheckNonNull(batchCreateTableDdlDto.getSourceDataSourceId());
        DataSourceInfoEntity targetDs = dataSourceInfoService.getWithCheckNonNull(batchCreateTableDdlDto.getTargetDataSourceId());
        //获取源数据信息
        MetaDataInfo sourceMetaData = getMetaDataInfo(sourceDs, batchCreateTableDdlDto.getTableMapping().keySet());
        Map<String, List<TableColumnInfo>> sourceColumnsMap = sourceMetaData.getColumns();
        
        //目标源字段转换以及公共字段处理
        Map<String, List<TableColumnInfo>> targetColumnMap =
                transformColumnsAndAddDefaults(sourceColumnsMap, sourceDs, targetDs, batchCreateTableDdlDto);
        
        //源表和目标表信息转换
        List<TableInfo> targetTableInfos =
                transformTableInfos(sourceMetaData.getTables(), batchCreateTableDdlDto, sourceDs, targetDs, targetColumnMap);
        
        //生成构建ddl的元信息
        MetaDataInfo finalMetaData = new MetaDataInfo();
        finalMetaData.setColumns(targetColumnMap);
        finalMetaData.setTables(targetTableInfos);
        
        // 记录批量建表第二步的日志
        recordOperationLogStep2(batchCreateTableDdlDto);
        
        //通过元数据信息,生成目标表生成ddl信息
        TransformSupport targetTransformSupport = DatabaseSupportManager.getTransformSupport(targetDs);
        DataSourceInfo targetDataSourceInfo = targetTransformSupport.transformDataSourceInfo(targetDs);
        MetaDataSupport targetSupport = DatabaseSupportManager.getMetaDataSupport(targetDs);
        return targetSupport.batchCreateTableDdl(targetDataSourceInfo, finalMetaData);
    }

    @Override
    public String syncBatchCreateTableDdl(BatchCreateTableDdlDto batchCreateTableDdlDto) {
        DataSourceInfoEntity sourceDs = dataSourceInfoService.getWithCheckNonNull(batchCreateTableDdlDto.getSourceDataSourceId());
        DataSourceInfoEntity targetDs = dataSourceInfoService.getWithCheckNonNull(batchCreateTableDdlDto.getTargetDataSourceId());
        //获取源数据信息
        MetaDataInfo sourceMetaData = getMetaDataInfo(sourceDs, batchCreateTableDdlDto.getTableMapping().keySet());
        Map<String, List<TableColumnInfo>> sourceColumnsMap = sourceMetaData.getColumns();

        //目标源字段转换以及公共字段处理
        Map<String, List<TableColumnInfo>> targetColumnMap =
                transformColumnsAndAddDefaults(sourceColumnsMap, sourceDs, targetDs, batchCreateTableDdlDto);

        //源表和目标表信息转换
        List<TableInfo> targetTableInfos =
                transformTableInfos(sourceMetaData.getTables(), batchCreateTableDdlDto, sourceDs, targetDs, targetColumnMap);

        //生成构建ddl的元信息
        MetaDataInfo finalMetaData = new MetaDataInfo();
        finalMetaData.setColumns(targetColumnMap);
        finalMetaData.setTables(targetTableInfos);

        //通过元数据信息,生成目标表生成ddl信息
        TransformSupport targetTransformSupport = DatabaseSupportManager.getTransformSupport(targetDs);
        DataSourceInfo targetDataSourceInfo = targetTransformSupport.transformDataSourceInfo(targetDs);
        MetaDataSupport targetSupport = DatabaseSupportManager.getMetaDataSupport(targetDs);
        return targetSupport.batchCreateTableDdl(targetDataSourceInfo, finalMetaData);
    }

    /**
     * 转换字段信息,以及增加公共字段.
     *
     * @param sourceColumnsMap 源表字段信息集合
     * @param sourceDs         源数据源信息
     * @param targetDs         目标数据源信息
     * @param dto              批量创建批量创建表ddl请求
     */
    private Map<String, List<TableColumnInfo>> transformColumnsAndAddDefaults(
            Map<String, List<TableColumnInfo>> sourceColumnsMap,
            DataSourceInfoEntity sourceDs,
            DataSourceInfoEntity targetDs,
            BatchCreateTableDdlDto dto) {
        
        Map<String, List<TableColumnInfo>> targetColumnMap = new HashMap<>();
        //源表和目标转换的解析器
        TypeInfoParser sourceParser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(sourceDs.getDatabaseType()));
        TypeInfoParser targetParser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(targetDs.getDatabaseType()));
        TypeInfoConverter converter = TypeInfoManager.getConverter(
                DatabaseTypeEnum.fromString(sourceDs.getDatabaseType()),
                DatabaseTypeEnum.fromString(targetDs.getDatabaseType())
        );
        for (Map.Entry<String, List<TableColumnInfo>> entry : sourceColumnsMap.entrySet()) {
            String sourceTableName = entry.getKey();
            List<TableColumnInfo> originalColumns = entry.getValue();
            String targetTableName = dto.getTableMapping().get(sourceTableName);
            
            List<TableColumnInfo> transformedColumns = originalColumns.stream()
                    .map(col -> {
                        TableColumnInfo newCol = new TableColumnInfo();
                        BeanUtils.copyProperties(col, newCol); // Copy properties
                        TypeInfo sourceTypeInfo = sourceParser.parse(
                                newCol.getColumnType(), newCol.getColumnLength(), newCol.getColumnPrecision(), newCol.getScale());
                        TypeInfo targetTypeInfo = converter.convertTypeInfo(sourceTypeInfo);
                        newCol.setFullColumnType(targetTypeInfo.getFullFieldType());
                        newCol.setColumnType(targetTypeInfo.getFieldType());
                        newCol.setColumnLength(targetTypeInfo.getLength());
                        newCol.setColumnPrecision(targetTypeInfo.getPrecision());
                        newCol.setScale(targetTypeInfo.getScale());
                        return newCol;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));
            
            // Add default columns
            if (CollectionUtil.isNotEmpty(dto.getDefaultColumns())) {
                for (String defaultColumnKey : dto.getDefaultColumns()) {
                    ColumnInfoEntity defaultColumn = DefaultColumnEnum.fromKeyForColumn(defaultColumnKey);
                    if (defaultColumn != null) {
                        TableColumnInfo tableColumnInfo = new TableColumnInfo();
                        BeanUtils.copyProperties(defaultColumn, tableColumnInfo);
                        tableColumnInfo.setFullColumnType(
                                targetParser.parse(tableColumnInfo.getColumnType(), tableColumnInfo.getColumnLength(),
                                        tableColumnInfo.getColumnPrecision(), tableColumnInfo.getScale()).getFullFieldType());
                        transformedColumns.add(tableColumnInfo);
                    }
                }
            }
            targetColumnMap.put(targetTableName, transformedColumns);
        }
        return targetColumnMap;
    }
    
    /**
     * 处理starrocks特殊逻辑.
     *
     * @param targetColumnMap  字段信息集合
     * @param sourceTableInfos 源表信息
     * @param dto              批量创建批量创建表ddl请求
     */
    private List<TableInfo> transformTableInfos(
            List<TableInfo> sourceTableInfos,
            BatchCreateTableDdlDto dto,
            DataSourceInfoEntity sourceDs,
            DataSourceInfoEntity targetDs,
            Map<String, List<TableColumnInfo>> targetColumnMap) {
        
        return sourceTableInfos.stream()
                .map(sourceTableInfo -> {
                    TableInfo newTableInfo = new TableInfo();
                    BeanUtils.copyProperties(sourceTableInfo, newTableInfo); // Copy base properties
                    
                    String originalSourceTableName = sourceTableInfo.getTableName();
                    String targetTableName = dto.getTableMapping().get(originalSourceTableName);
                    newTableInfo.setTableName(targetTableName);
                    
                    Properties tableProperties = Optional.ofNullable(newTableInfo.getTableProperties())
                            .orElseGet(Properties::new);
                    
                    // Add default columns to partition properties if applicable
                    if (CollectionUtil.isNotEmpty(dto.getDefaultColumns())) {
                        List<String> partitionKeysFromDefaults = dto.getDefaultColumns().stream()
                                .map(DefaultColumnEnum::fromKey)
                                .filter(dc -> dc != null && dc.isPartition())
                                .map(dc -> dc.getColumnInfoEntitySupplier().get().getColumnName())
                                .collect(Collectors.toList());
                        if (CollectionUtil.isNotEmpty(partitionKeysFromDefaults)) {
                            // Combine with existing partition keys if any
                            String existingPartitionKeys = tableProperties.getProperty(TablePropertiesOptions.PARTITION_KEYS.key());
                            Set<String> allPartitionKeys = new HashSet<>();
                            if (StrUtil.isNotEmpty(existingPartitionKeys)) {
                                Arrays.stream(existingPartitionKeys.split(SystemConstant.COMMA)).forEach(allPartitionKeys::add);
                            }
                            allPartitionKeys.addAll(partitionKeysFromDefaults);
                            tableProperties.setProperty(TablePropertiesOptions.PARTITION_KEYS.key(),
                                    String.join(SystemConstant.COMMA, allPartitionKeys));
                        }
                    }
                    if (newTableInfo.getPrimaryKeys() != null) {
                        if (StrUtil.isEmpty(newTableInfo.getPrimaryKeys())) {
                            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "表:" + newTableInfo.getTableName() + "主键为空");
                        }
                        //hologres 主键的名字不一样
                        if (!sourceDs.getDatabaseType().equals(targetDs.getDatabaseType()) && DatabaseTypeEnum.HOLOGRES
                                .getTypeName().equalsIgnoreCase(targetDs.getDatabaseType())) {
                            tableProperties.setProperty(TablePropertiesOptions.HOLOGRE_PRIMARY_KEYS.key(), newTableInfo.getPrimaryKeys());
                        } else {
                            tableProperties.setProperty(TablePropertiesOptions.PRIMARY_KEYS.key(), newTableInfo.getPrimaryKeys());
                        }
                    }
                    newTableInfo.setTableProperties(tableProperties);
                    
                    if (!sourceDs.getDatabaseType().equals(targetDs.getDatabaseType()) && DatabaseTypeEnum.STARROCKS
                            .getTypeName().equalsIgnoreCase(targetDs.getDatabaseType())) {
                        handleStarRocksColumnOrdering(newTableInfo, targetColumnMap);
                    }
                    return newTableInfo;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 处理starrocks特殊逻辑.
     *
     * @param targetColumnMap 字段信息集合
     * @param tableInfo       表信息
     */
    private void handleStarRocksColumnOrdering(TableInfo tableInfo, Map<String, List<TableColumnInfo>> targetColumnMap) {
        String targetTableName = tableInfo.getTableName();
        List<TableColumnInfo> columnsForStarRocks = new ArrayList<>();
        Map<String, TableColumnInfo> columnMapByName =
                targetColumnMap.getOrDefault(targetTableName, Collections.emptyList()).stream()
                        .collect(Collectors.toMap(TableColumnInfo::getColumnName, p -> p));
        
        List<String> primaryKeysList = StrUtil.split(tableInfo.getPrimaryKeys(), SystemConstant.COMMA, true, true);
        List<String> partitionKeysList = StrUtil.split(tableInfo.getPartitionKeys(), SystemConstant.COMMA, true, true);
        
        primaryKeysList.stream()
                .map(columnMapByName::get)
                .filter(Objects::nonNull)
                .forEach(columnsForStarRocks::add);
        
        String tableModel = tableInfo.getTableProperties().getProperty(TablePropertiesOptions.TABLE_MODEL.key());
        if (StrUtil.isEmpty(tableModel)) {
            List<String> partitionNotPrimaryKey = partitionKeysList.stream()
                    .filter(pk -> !primaryKeysList.contains(pk))
                    .collect(Collectors.toList());
            
            partitionNotPrimaryKey.stream()
                    .map(columnMapByName::get)
                    .filter(Objects::nonNull)
                    .forEach(columnsForStarRocks::add);
            
            // Update primary keys to include these partition keys
            Set<String> combinedPrimaryKeys = new LinkedHashSet<>(primaryKeysList); // Use LinkedHashSet to maintain order and uniqueness
            combinedPrimaryKeys.addAll(partitionNotPrimaryKey);
            tableInfo.getTableProperties().setProperty(TablePropertiesOptions.PRIMARY_KEYS.key(),
                    String.join(SystemConstant.COMMA, combinedPrimaryKeys));
        }
        
        // Add remaining columns (not primary or partition)
        targetColumnMap.getOrDefault(targetTableName, Collections.emptyList()).stream()
                .filter(col -> !primaryKeysList.contains(col.getColumnName()) && !partitionKeysList.contains(col.getColumnName()))
                .forEach(columnsForStarRocks::add);
        
        targetColumnMap.put(targetTableName, columnsForStarRocks);
    }
    
    /**
     * 记录操作日志.
     *
     * @param dto 批量创建批量创建表ddl请求
     */
    private void recordOperationLogStep2(BatchCreateTableDdlDto dto) {
        MetadataTableOperateLogEntity operateLogEntity = metadataTableOperateLogService.getWithCheckNonNull(dto.getTrackId());
        operateLogEntity.setOperateTime(LocalDateTime.now());
        try {
            operateLogEntity.setSnapshotStep2(JacksonUtils.str2JsonNode(JacksonUtils.obj2PrettyStr(dto)));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize BatchCreateTableDdlDto for operation log (step 2), trackId: {}", dto.getTrackId(), e);
        }
        String currentUserName = HttpUtils.getCurrentUserName();
        Date now = new Date();
        operateLogEntity.setUpdater(currentUserName);
        operateLogEntity.setUpdateTime(now);
        metadataTableOperateLogService.updateLog(operateLogEntity);
    }
    
    /**
     * 删除注释.
     *
     * @param sql sql脚本
     * @return 纯净版sql
     */
    private String cleanSqlStatement(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        // 2. 移除单行注释
        Matcher singleLineMatcher = SINGLE_LINE_COMMENT_PATTERN.matcher(sql);
        return singleLineMatcher.replaceAll("");
    }

    @Override
    public Boolean executeSql(RunSqlDto runSqlDto) {
        
        //step1:验证脚本中是否存在非法字符，比如 drop、truncate 之类的关键字，大小写都得匹配
        String lowerCaseSql = runSqlDto.getSql().toLowerCase();
        
        // **核心修改：非法关键字设置**
        // 阻止所有非 CREATE TABLE 和 ALTER TABLE 的 DDL/DML 操作
        List<String> illegalKeywords = Arrays.asList(
                "drop table", "truncate table", "delete from", "insert into", "update ",
                "drop database", "alter database", "create database",
                "drop index", "create index", "alter index",
                "drop view", "create view", "alter view",
                "drop procedure", "create procedure", "alter procedure",
                "drop function", "create function", "alter function",
                "drop trigger", "create trigger", "alter trigger",
                "drop sequence", "create sequence", "alter sequence",
                "drop schema", "create schema", "alter schema",
                "grant", "revoke", // 权限管理
                "commit", "rollback", // 事务控制，这里我们自己管理事务
                "declare", // 存储过程/函数块，如果不需要执行复杂逻辑
                "select" // 通常不允许执行查询，除非有特定需求
        );
        
        for (String keyword : illegalKeywords) {
            if (lowerCaseSql.contains(keyword)) {
                throw new CommonException(ErrorCodeEnum.USER_INVALID_PARAM_A0154, "非法关键词：" + keyword);
            }
        }
        
        //step2:将脚本按分号隔开，拆分
        String[] sqlStatements = runSqlDto.getSql().split("(?<!\\\\);");
        
        //step3:根据正则匹配，将脚本分成 create table类和alter table 类
        List<String> createTableStatements = new ArrayList<>();
        List<String> alterTableStatements = new ArrayList<>();
        List<String> disallowedStatements = new ArrayList<>(); // 用于存储不被允许的语句
        Pattern createTablePattern = Pattern.compile("^\\s*CREATE\\s+TABLE\\s+", Pattern.CASE_INSENSITIVE);
        // ALTER TABLE 的语句有很多类型，例如 ADD COLUMN, DROP COLUMN, MODIFY COLUMN, RENAME COLUMN 等
        // 更准确地匹配 ALTER TABLE
        Pattern alterTablePattern = Pattern.compile(
                "^\\s*ALTER\\s+TABLE\\s+[a-zA-Z0-9_.\"`]+\\s+(ADD|DROP|MODIFY|CHANGE|RENAME)\\s+(COLUMN|TO|CONSTRAINT)\\s*",
                Pattern.CASE_INSENSITIVE);
        for (String statement : sqlStatements) {
            String trimmedStatement = cleanSqlStatement(statement.trim());
            if (trimmedStatement.isEmpty()) {
                continue;
            }
            
            if (createTablePattern.matcher(trimmedStatement).find()) {
                createTableStatements.add(trimmedStatement);
            } else if (alterTablePattern.matcher(trimmedStatement).find()) {
                alterTableStatements.add(trimmedStatement);
            } else {
                // 任何不属于 CREATE TABLE 或 ALTER TABLE (且是字段修改类) 的语句都被视为不允许
                disallowedStatements.add(trimmedStatement);
            }
        }
        //step4:分批调用，返回执行结果
        // create table 语句直接运行 原生支持 if not exits
        MetadataTableOperateLogEntity trackLogModel = metadataTableOperateLogService.getWithCheckNonNull(runSqlDto.getTrackId());
        DataSourceInfoEntity dataSourceInfoEntity =
                dataSourceInfoService.getWithCheckNonNull(trackLogModel.getTargetDatasourceId());
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(dataSourceInfoEntity);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(dataSourceInfoEntity);
        DataSourceInfo dataSourceInfo = databaseTransformService.transformDataSourceInfo(dataSourceInfoEntity);
        StringBuilder sb = new StringBuilder();
        if (CollectionUtil.isNotEmpty(createTableStatements)) {
            List<RunSqlParam> params = createTableStatements.stream().map(sql -> {
                return new RunSqlParam(null, sql);
            }).collect(Collectors.toList());
            int[] executeResult = databaseService.runSql(dataSourceInfo, params);
            for (int i = 0; i < executeResult.length; i++) {
                sb.append(params.get(i).getRunSql()).append(LINE_FEED);
                sb.append(COMMENT_SYMBOL).append(executeResult[i] == 0 ? "执行成功" : "执行失败").append(LINE_FEED);
            }
        }
        
        // alter table 不能直接执行,目前只有postgres 原生支持  add cloumn if not exits
        // 所以需要从日志里面拿到选择的表（目标表）的源数据，检验是脚本是否需要执行
        // 现在简单点，表有数据就不执行更新语句;下面的
        // case1:新增 字段，如果目标元数据中已经包含该字段直接返回成功，否则新增字段
        // case2:修改 字段，可以直接运行，数据修改字段应该可以重复执行
        // case3:删除 字段，如果目标元数据中已经没有这个字段，返回成功，否则新增字段
        if (CollectionUtil.isNotEmpty(alterTableStatements)) {
            List<String> tableNameList = alterTableStatements.stream().map(m -> {
                return getAlterTableName(m);
            }).collect(Collectors.toList());
            List<String> distinctTableNameList = tableNameList.stream().distinct()
                    .collect(Collectors.toList());
            
            List<List<Map<String, Object>>> tableDataList = distinctTableNameList.stream().map(m -> {
                DataPreviewQuery queryInfo = new DataPreviewQuery();
                queryInfo.setTableName(m);
                queryInfo.setLimit(1);
                return databaseService.getDataPreview(dataSourceInfo, queryInfo);
            }).collect(Collectors.toList());
            Map<String, Boolean> tableDataMap = new HashMap<>();
            for (int i = 0; i < tableDataList.size(); i++) {
                tableDataMap.put(distinctTableNameList.get(i), CollectionUtil.isNotEmpty(tableDataList.get(i)));
            }
            //有数据就直接返回不实际执行
            List<String> withDataAlterSql = alterTableStatements.stream()
                    .filter(m -> tableDataMap.get(getAlterTableName(m))).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(withDataAlterSql)) {
                withDataAlterSql.stream().forEach(m -> {
                    sb.append(m).append(LINE_FEED);
                    sb.append(COMMENT_SYMBOL).append("非空表,不执行,只展示");
                });
            }
            List<String> withNoDataAlterSql = alterTableStatements.stream()
                    .filter(m -> !tableDataMap.get(getAlterTableName(m))).collect(Collectors.toList());
            if (CollectionUtil.isNotEmpty(withNoDataAlterSql)) {
                List<RunSqlParam> params = withNoDataAlterSql.stream().map(sql -> {
                    return new RunSqlParam(null, sql);
                }).collect(Collectors.toList());
                int[] executeResult = databaseService.runSql(dataSourceInfo, params);
                for (int i = 0; i < executeResult.length; i++) {
                    sb.append(params.get(i).getRunSql()).append(LINE_FEED);
                    sb.append(COMMENT_SYMBOL).append(executeResult[i] == 0 ? "执行成功" : "执行失败").append(LINE_FEED);
                }
            }
            //没有数据就到下一步执行
        }
        //step5:重新组装返回结果，把脚本和执行结果返回给前端
        
        //todo建表语句和更新字段语句拆开
        if (CollectionUtil.isNotEmpty(disallowedStatements)) {
            List<RunSqlParam> params = disallowedStatements.stream().map(sql -> {
                return new RunSqlParam(null, sql);
            }).collect(Collectors.toList());
            int[] executeResult = databaseService.runSql(dataSourceInfo, params);
            for (int i = 0; i < executeResult.length; i++) {
                sb.append(params.get(i).getRunSql()).append(LINE_FEED);
                sb.append(COMMENT_SYMBOL).append(executeResult[i] == 0 ? "执行成功" : "执行失败").append(LINE_FEED);
            }
        }
        MetadataTableOperateLogEntity operateLogEntity = metadataTableOperateLogService.getWithCheckNonNull(runSqlDto.getTrackId());
        operateLogEntity.setOperateTime(LocalDateTime.now());
        try {
            operateLogEntity.setSnapshotStep3(JacksonUtils.str2JsonNode(
                    JacksonUtils.obj2PrettyStr(runSqlDto)));
        } catch (JsonProcessingException e) {
            log.error("序列化出现异常", e);
        }
        operateLogEntity.setUpdater(HttpUtils.getCurrentUserName());
        operateLogEntity.setUpdateTime(new Date());
        metadataTableOperateLogService.updateLog(operateLogEntity);
        return true;
    }

    @Override
    public PageResponse<MetadataTableOperateLogViewDto> pageOperateLogs(PageQuery<MetadataTableOperateLogQueryDto> query) {
        IPage<MetadataTableOperateLogEntity> page = new Page<>(query.getCurrent(), query.getSize());
        
        List<DataSourceInfoDto> dataSourceList = dataSourceInfoService.listDataSource(new DataSourceInfoQueryDto());
        Wrapper<MetadataTableOperateLogEntity> queryWrapper = buildQueryWrapper(query, dataSourceList);
        page = metadataTableOperateLogService.selectPage(page, queryWrapper);
        PageResponse<MetadataTableOperateLogViewDto> pageResponse = new PageResponse<>();
        
        // 注意：IPage 的字段是 long 类型，PageResponse 是 Integer，这里进行类型转换
        pageResponse.setTotal((int) page.getTotal());
        pageResponse.setCurrent((int) page.getCurrent());
        pageResponse.setSize((int) page.getSize());
        
        // 4. 映射数据记录列表，并处理 null 的情况
        Map<UUID, DataSourceInfoDto> dataSourceMap = dataSourceList.stream()
                .collect(Collectors.toMap(
                        DataSourceInfoDto::getId, // 假设DataSourceInfoDto有getKeyField()方法
                        Function.identity()
                ));
        List<MetadataTableOperateLogEntity> records = page.getRecords();
        pageResponse.setDataList(CollectionUtil.isNotEmpty(records) ? records.stream().map(dto -> {
            MetadataTableOperateLogViewDto vo = new MetadataTableOperateLogViewDto();
            BeanUtils.copyProperties(dto, vo);
            DataSourceInfoDto sourceEntity = dataSourceMap.get(vo.getSourceDatasourceId());
            DataSourceInfoDto targetEntity = dataSourceMap.get(vo.getTargetDatasourceId());
            if (sourceEntity != null) {
                vo.setSourceDatabaseName(sourceEntity.getDatabaseName());
                vo.setSourceDataSourceName(sourceEntity.getName());
                vo.setSourceSchemaName(sourceEntity.getSchemaName());
            }
            if (targetEntity != null) {
                vo.setTargetDatabaseName(targetEntity.getDatabaseName());
                vo.setTargetDataSourceName(targetEntity.getName());
                vo.setTargetSchemaName(targetEntity.getSchemaName());
            }
            return vo;
        }).collect(Collectors.toList()) : Collections.emptyList());
        return pageResponse;
    }
    
    /**
     * buildQueryWrapper.
     *
     * @param query          查询实体
     * @param dataSourceList 数据源
     * @return 查询条件
     */
    private Wrapper<MetadataTableOperateLogEntity> buildQueryWrapper(PageQuery<MetadataTableOperateLogQueryDto> query,
                                                                     List<DataSourceInfoDto> dataSourceList) {
        // 对应 <mapper> 中的 select t.*, 我们在 Wrapper 中主要关注 where 和 order by
        // 创建 QueryWrapper 实例，并指定泛型为对应的实体类，以获得更好的类型安全和提示
        QueryWrapper<MetadataTableOperateLogEntity> queryWrapper = new QueryWrapper<>();
        
        if (query != null && query.getOption() != null) {
            
            queryWrapper.eq(query.getOption().getOperateType() != null, "operate_type", query.getOption().getOperateType());
            
            queryWrapper.ge(query.getOption().getOperateTime() != null, "operate_time", query.getOption().getOperateTime());
            if (query.getOption().getOperateTime() != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(query.getOption().getOperateTime());
                calendar.add(Calendar.DATE, 1);
                Date newDate = calendar.getTime();
                queryWrapper.lt("operate_time", newDate);
            }
            if (StrUtil.isNotEmpty(query.getOption().getSourceDatabaseName())) {
                List<UUID> sourceDataSourceIds = dataSourceList.stream()
                        .filter(m -> m.getName().contains(query.getOption().getSourceDatabaseName()))
                        .map(m -> {
                            return m.getId();
                        }).collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(sourceDataSourceIds)) {
                    queryWrapper.in("source_datasource_id", sourceDataSourceIds);
                }
            }
            
            if (StrUtil.isNotEmpty(query.getOption().getTargetDatabaseName())) {
                List<UUID> targetDataSourceIds = dataSourceList.stream()
                        .filter(m -> m.getName().contains(query.getOption().getTargetDatabaseName()))
                        .map(m -> {
                            return m.getId();
                        }).collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(targetDataSourceIds)) {
                    queryWrapper.in("target_datasource_id", targetDataSourceIds);
                }
            }
        }
        
        // 对应 order by t.column_serial asc
        // 这个排序条件在 <where> 之外，所以无论 query 是否为 null 都应该应用
        queryWrapper.orderByDesc("operate_time");
        
        return queryWrapper;
    }
    
    @Override
    public MetadataTableOperateLogViewDto getOperateLog(UUID id) {
        MetadataTableOperateLogViewDto result = new MetadataTableOperateLogViewDto();
        MetadataTableOperateLogEntity entity = metadataTableOperateLogService.getWithCheckNonNull(id);
        BeanUtils.copyProperties(entity, result);
        DataSourceInfoDto sourceEntity = dataSourceInfoService.getDataSource(entity.getSourceDatasourceId());
        DataSourceInfoDto targetEntity = dataSourceInfoService.getDataSource(entity.getTargetDatasourceId());
        if (sourceEntity != null) {
            result.setSourceDatabaseName(sourceEntity.getDatabaseName());
            result.setSourceDataSourceName(sourceEntity.getName());
            result.setSourceSchemaName(sourceEntity.getSchemaName());
        }
        if (targetEntity != null) {
            result.setTargetDatabaseName(targetEntity.getDatabaseName());
            result.setTargetDataSourceName(targetEntity.getName());
            result.setTargetSchemaName(targetEntity.getSchemaName());
        }
        return result;
    }
    
    /**
     * 获取sql脚本中的表名.
     *
     * @param sql sql脚本
     * @return 表名
     */
    private String getAlterTableName(String sql) {
        // 定义正则表达式来匹配 "ALTER TABLE" 后面的表名
        // \s+ 匹配一个或多个空格
        // `? 匹配可选的反引号
        // (\w+) 是捕获组，匹配一个或多个单词字符（字母、数字、下划线），这是我们想要的表名
        Pattern pattern = Pattern.compile("ALTER TABLE\\s+`?(\\w+)`?");
        Matcher matcher = pattern.matcher(sql);
        
        if (matcher.find()) {
            // 如果找到匹配，则返回第一个捕获组（即表名）
            return matcher.group(1);
        }
        return null; // 如果没有找到表名，则返回null
    }

    @Override
    public List<BatchMetaDataCompareResultDto> batchMetaDataCompare(BatchMetaDataCompareDto batchMetaDataCompareDto) {
        if (Objects.isNull(batchMetaDataCompareDto)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "批量表结构对比请求参数不能为空");
        }
        
        MetaDataCompareDto sourceCompareDto = batchMetaDataCompareDto.getSource();
        MetaDataCompareDto targetCompareDto = batchMetaDataCompareDto.getTarget();
        
        // 1. 获取源端和目标端的所有元数据信息
        // 注意：这里需要确保 getMetaDataInfo 能够根据传入的 tableNames 过滤结果
        // 或者在 service 层获取所有表名，然后在这里进行过滤
        MetaDataInfo sourceMetaData = getMetaDataInfo(sourceCompareDto);
        MetaDataInfo targetMetaData = getMetaDataInfo(targetCompareDto);
        
        List<BatchMetaDataCompareResultDto> results = new ArrayList<>();
        
        // 2. 将目标表的名称集合转换为 Map，方便查找
        // 键是目标表名，值是 TableInfo 对象，便于获取表ID等信息
        Map<String, TableInfo> targetTableNameToInfoMap = targetMetaData.getTables().stream()
                .collect(Collectors.toMap(
                        targetTableInfo -> getActualTargetTableName(targetTableInfo.getTableName(), targetCompareDto),
                        info -> info,
                        (oldValue, newValue) -> oldValue // 解决键冲突：如果逆向处理后产生相同的源表名，保留第一个
                ));
        
        // 3. 遍历源端需要对比的表名
        for (String sourceTableName : sourceCompareDto.getTableNames()) {
            BatchMetaDataCompareResultDto resultDto = new BatchMetaDataCompareResultDto();
            resultDto.setSourceTableName(sourceTableName);
            resultDto.setSourceDataSourceId(sourceCompareDto.getDatasourceId()); // 假设获取源表ID的方法
            
            List<TableColumnInfo> sourceColumns = sourceMetaData.getColumns().get(sourceTableName);
            
            Integer sourceColumnNums = (sourceColumns != null) ? sourceColumns.size() : 0;
            resultDto.setSourceTableColumnNums(sourceColumnNums);
            
            // 3.2 根据前缀/后缀规则确定目标表名
            String actualTargetTableName = getActualTargetTableName(sourceTableName, sourceCompareDto);
            resultDto.setMappingTableName(actualTargetTableName);
            
            // 3.3 查找目标表
            TableInfo targetTableInfo = targetTableNameToInfoMap.get(actualTargetTableName);
            
            if (targetTableInfo == null) {
                // 目标表不存在
                resultDto.setCompareResult(TableCompareEnum.TARGET_NOT_EXIST);
                resultDto.setTargetTableColumnNums(0);
                resultDto.setTargetDataSourceId(targetCompareDto.getDatasourceId());
                results.add(resultDto);
            } else {
                resultDto.setTargetTableName(targetTableInfo.getTableName());
                resultDto.setTargetDataSourceId(targetCompareDto.getDatasourceId());
                // 假设 targetMetaData.getColumns() 返回的是 Map<String, List<ColumnInfoEntity>>
                // 或者需要一个转换方法将 TableColumnInfo 转换为 ColumnInfoEntity
                List<TableColumnInfo> targetColumns = targetMetaData.getColumns().get(targetTableInfo.getTableName());
                
                Integer targetColumnNums = (targetColumns != null) ? targetColumns.size() : 0;
                resultDto.setTargetTableColumnNums(targetColumnNums);
                
                // 3.4 比较列结构
                if (sourceColumns == null || sourceColumns.isEmpty() || targetColumns == null || targetColumns.isEmpty()) {
                    // 如果任一端列为空，但表存在，也认为是差异，除非两者都为空且表也为空
                    // 这里简化处理：如果两边列的数量都为0，认为是完全一致（如果表也匹配的话），否则认为是差异
                    if (sourceColumnNums == 0 && targetColumnNums == 0) {
                        resultDto.setCompareResult(TableCompareEnum.IDENTICAL);
                    } else {
                        resultDto.setCompareResult(TableCompareEnum.DIFFERENT);
                    }
                } else {
                    boolean columnsIdentical = compareTableInfo(sourceColumns, targetColumns);
                    if (columnsIdentical) {
                        resultDto.setCompareResult(TableCompareEnum.IDENTICAL);
                    } else {
                        resultDto.setCompareResult(TableCompareEnum.DIFFERENT);
                    }
                }
                results.add(resultDto);
            }
        }
        results.sort(Comparator.comparing(BatchMetaDataCompareResultDto::getCompareResult));
        MetadataTableOperateLogEntity operateLogEntity = new MetadataTableOperateLogEntity();
        operateLogEntity.setId(batchMetaDataCompareDto.getTrackId());
        operateLogEntity.setOperateType(1);
        operateLogEntity.setOperateTime(LocalDateTime.now());
        operateLogEntity.setSourceDatasourceId(batchMetaDataCompareDto.getSource().getDatasourceId());
        operateLogEntity.setTargetDatasourceId(batchMetaDataCompareDto.getTarget().getDatasourceId());
        try {
            operateLogEntity.setSnapshotStep1(JacksonUtils.str2JsonNode(
                    JacksonUtils.obj2PrettyStr(batchMetaDataCompareDto)));
        } catch (JsonProcessingException e) {
            log.error("序列化出现异常", e);
        }
        operateLogEntity.setCreator(HttpUtils.getCurrentUserName());
        operateLogEntity.setUpdater(HttpUtils.getCurrentUserName());
        operateLogEntity.setCreateTime(new Date());
        operateLogEntity.setUpdateTime(new Date());
        metadataTableOperateLogService.addLog(operateLogEntity);
        return results;
    }
    
    @Override
    public TableColumnInfoCompareResultDto getTableCompareDetail(TableCompareDetailQueryDto tableCompareDetailQueryDto) {
        MetaDataCompareDto sourceQuery = new MetaDataCompareDto();
        sourceQuery.setDatasourceId(tableCompareDetailQueryDto.getSourceDatasourceId());
        sourceQuery.setTableNames(new ArrayList<>(List.of(tableCompareDetailQueryDto.getSourceTableName())));
        MetaDataInfo sourceMetaDataInfo = getMetaDataInfo(sourceQuery);
        
        MetaDataInfo targetMetaDataInfo = null;
        if (StrUtil.isNotEmpty(tableCompareDetailQueryDto.getTargetTableName())) {
            MetaDataCompareDto targetQuery = new MetaDataCompareDto();
            targetQuery.setDatasourceId(tableCompareDetailQueryDto.getTargetDatasourceId());
            targetQuery.setTableNames(new ArrayList<>(List.of(tableCompareDetailQueryDto.getTargetTableName())));
            targetMetaDataInfo = getMetaDataInfo(targetQuery);
        }
        
        List<TableColumnInfo> sourceColumns = sourceMetaDataInfo != null && MapUtil.isNotEmpty(sourceMetaDataInfo.getColumns())
                && sourceMetaDataInfo.getColumns().containsKey(tableCompareDetailQueryDto.getSourceTableName())
                ? sourceMetaDataInfo.getColumns().get(tableCompareDetailQueryDto.getSourceTableName()) : new ArrayList<>();
        List<TableColumnInfo> targetColumns = targetMetaDataInfo != null && MapUtil.isNotEmpty(targetMetaDataInfo.getColumns())
                && targetMetaDataInfo.getColumns().containsKey(tableCompareDetailQueryDto.getTargetTableName())
                ? targetMetaDataInfo.getColumns().get(tableCompareDetailQueryDto.getTargetTableName()) : new ArrayList<>();
        
        Map<String, TableColumnInfo> sourceColumnMap = sourceColumns.stream()
                .collect(Collectors.toMap(TableColumnInfo::getColumnName, Function.identity()));
        Map<String, TableColumnInfo> targetColumnMap = targetColumns.stream()
                .collect(Collectors.toMap(TableColumnInfo::getColumnName, Function.identity()));
        
        Set<String> allColumnNames = new HashSet<>();
        allColumnNames.addAll(sourceColumnMap.keySet());
        allColumnNames.addAll(targetColumnMap.keySet());
        
        List<TableColumnInfoCompareInfo> resultSourceColumns = new ArrayList<>();
        List<TableColumnInfoCompareInfo> resultTargetColumns = new ArrayList<>();
        
        for (String columnName : allColumnNames) {
            TableColumnInfo sourceColumn = sourceColumnMap.get(columnName);
            TableColumnInfo targetColumn = targetColumnMap.get(columnName);
            
            TableColumnInfoCompareInfo sourceCompareInfo = new TableColumnInfoCompareInfo();
            TableColumnInfoCompareInfo targetCompareInfo = new TableColumnInfoCompareInfo();
            
            // Populate sourceCompareInfo and targetCompareInfo with data
            if (sourceColumn != null) {
                BeanUtils.copyProperties(sourceColumn, sourceCompareInfo);
            } else {
                // If source column is missing, create a placeholder
                BeanUtils.copyProperties(targetColumn, sourceCompareInfo);
                // You might want to set other properties as null or default values
            }
            
            if (targetColumn != null) {
                BeanUtils.copyProperties(targetColumn, targetCompareInfo);
            } else {
                // If target column is missing, create a placeholder
                BeanUtils.copyProperties(sourceColumn, targetCompareInfo);
                // You might want to set other properties as null or default values
            }
            
            TableColumnCompareEnum compareResult = compareTableColumns(sourceColumn, targetColumn);
            sourceCompareInfo.setCompareResult(compareResult);
            targetCompareInfo.setCompareResult(compareResult); // Both sides should show the same comparison result for the field
            
            resultSourceColumns.add(sourceCompareInfo);
            resultTargetColumns.add(targetCompareInfo);
        }
        
        // Sort the columns by name for consistent order
        resultSourceColumns
                .sort(Comparator.comparing(TableColumnInfoCompareInfo::getCompareResult, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TableColumnInfoCompareInfo::getColumnName, Comparator.nullsLast(Comparator.naturalOrder())));
        resultTargetColumns
                .sort(Comparator.comparing(TableColumnInfoCompareInfo::getCompareResult, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TableColumnInfoCompareInfo::getColumnName, Comparator.nullsLast(Comparator.naturalOrder())));
        return new TableColumnInfoCompareResultDto(resultSourceColumns, resultTargetColumns);
    }
    
    @Override
    public String generateTableCompareDdlSql(TableCompareGenerateDdlRequestDto tableCompareGenerateDdlRequestDto) {
        if (tableCompareGenerateDdlRequestDto == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "请求体不能为空");
        }
        List<BatchMetaDataCompareResultDto> tableCompareResultList = tableCompareGenerateDdlRequestDto.getTableCompareResultList();
        if (CollectionUtil.isEmpty(tableCompareResultList)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "请选择对比表信息");
        }
        StringBuilder sb = new StringBuilder();
        //过滤掉对比一样的信息
        tableCompareResultList = tableCompareResultList.stream().filter(m -> !m.getCompareResult().equals(TableCompareEnum.IDENTICAL)).collect(
                Collectors.toList());
        if (CollectionUtil.isNotEmpty(tableCompareResultList)) {
            DataSourceInfoEntity ds = dataSourceInfoService.getWithCheckNonNull(tableCompareResultList.get(0).getSourceDataSourceId());
            MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(ds);
            TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(ds);
            tableCompareResultList.forEach(m -> {
                if (m.getCompareResult().equals(TableCompareEnum.TARGET_NOT_EXIST)) {
                    //走创建表的信息
                    MetaDataQuery metaDataQuery = new MetaDataQuery();
                    metaDataQuery.setTableNames(new ArrayList<>(List.of(m.getSourceTableName())));
                    MetaDataInfo metaData = databaseService.getMetaData(databaseTransformService.transformDataSourceInfo(ds), metaDataQuery);
                    sb.append(databaseService.batchCreateTableDdl(databaseTransformService.transformDataSourceInfo(ds), metaData));
                    
                } else if (m.getCompareResult().equals(TableCompareEnum.DIFFERENT)) {
                    //走修改表字段的信息
                    TableCompareDetailQueryDto queryDto = new TableCompareDetailQueryDto();
                    queryDto.setSourceDatasourceId(m.getSourceDataSourceId());
                    queryDto.setSourceTableName(m.getSourceTableName());
                    queryDto.setTargetDatasourceId(m.getTargetDataSourceId());
                    queryDto.setTargetTableName(m.getTargetTableName());
                    TableColumnInfoCompareResultDto compareDetailDto = getTableCompareDetail(queryDto);
                    sb.append(databaseService.getAlterTableSql(databaseTransformService.transformDataSourceInfo(ds), compareDetailDto));
                }
            });
        }
        MetadataTableOperateLogEntity operateLogEntity = metadataTableOperateLogService.getWithCheckNonNull(
                tableCompareGenerateDdlRequestDto.getTrackId());
        operateLogEntity.setOperateTime(LocalDateTime.now());
        try {
            operateLogEntity.setSnapshotStep2(JacksonUtils.str2JsonNode(
                    JacksonUtils.obj2PrettyStr(tableCompareGenerateDdlRequestDto)));
        } catch (JsonProcessingException e) {
            log.error("序列化出现异常", e);
        }
        operateLogEntity.setUpdater(HttpUtils.getCurrentUserName());
        operateLogEntity.setUpdateTime(new Date());
        metadataTableOperateLogService.updateLog(operateLogEntity);
        return sb.toString();
    }
    
    /**
     * Compares two TableColumnInfo objects and returns the comparison result.
     * This method can be extended to compare more attributes.
     */
    private TableColumnCompareEnum compareTableColumns(TableColumnInfo sourceColumn, TableColumnInfo targetColumn) {
        if (sourceColumn == null && targetColumn == null) {
            // This case should ideally not happen if iterating through allColumnNames
            return TableColumnCompareEnum.IDENTICAL; // Or throw an error
        } else if (sourceColumn == null) {
            return TableColumnCompareEnum.DELETE; // Field exists in target but not in source
        } else if (targetColumn == null) {
            return TableColumnCompareEnum.NEW; // Field exists in source but not in target
        } else {
            // Perform detailed comparison of properties
            boolean isDifferent = !Objects.equals(sourceColumn.getColumnType(), targetColumn.getColumnType())
                    || !Objects.equals(sourceColumn.getJavaType(), targetColumn.getJavaType())
                    || !Objects.equals(sourceColumn.getIsNullable(), targetColumn.getIsNullable())
                    || !Objects.equals(sourceColumn.getIsPrimary(), targetColumn.getIsPrimary())
                    || !Objects.equals(sourceColumn.getColumnLength(), targetColumn.getColumnLength())
                    || !Objects.equals(sourceColumn.getColumnPrecision(), targetColumn.getColumnPrecision())
                    || !Objects.equals(sourceColumn.getScale(), targetColumn.getScale())
                    || !Objects.equals(sourceColumn.getColumnDesc(), targetColumn.getColumnDesc())
                    || !Objects.equals(sourceColumn.getDefaultValue(), targetColumn.getDefaultValue());
            // Add more fields to compare as needed
            
            if (isDifferent) {
                return TableColumnCompareEnum.DIFFERENT;
            } else {
                return TableColumnCompareEnum.IDENTICAL;
            }
        }
    }
    
    /**
     * 辅助方法：根据前缀/后缀规则获取目标表名.
     *
     * @param sourceTableName 源表表名
     * @param compareDto      目标表名
     * @return 目标表
     */
    private String getActualTargetTableName(String sourceTableName, MetaDataCompareDto compareDto) {
        String targetTableName = sourceTableName;
        
        // 处理前缀
        if (Boolean.TRUE.equals(compareDto.getIsAddPrefix()) && compareDto.getPrefix() != null) {
            targetTableName = compareDto.getPrefix() + targetTableName;
        } else if (Boolean.FALSE.equals(compareDto.getIsAddPrefix()) && compareDto.getPrefix() != null) {
            if (targetTableName.startsWith(compareDto.getPrefix())) {
                targetTableName = targetTableName.substring(compareDto.getPrefix().length());
            }
        }
        
        // 处理后缀
        if (Boolean.TRUE.equals(compareDto.getIsAddSuffix()) && compareDto.getSuffix() != null) {
            targetTableName = targetTableName + compareDto.getSuffix();
        } else if (Boolean.FALSE.equals(compareDto.getIsAddSuffix()) && compareDto.getSuffix() != null) {
            if (targetTableName.endsWith(compareDto.getSuffix())) {
                targetTableName = targetTableName.substring(0, targetTableName.length() - compareDto.getSuffix().length());
            }
        }
        return targetTableName;
    }
    
    /**
     * 比较两个表的列信息是否一致 此方法用于检查源表和目标表的列信息是否有差异，以确保数据迁移或同步时列结构一致.
     *
     * @param sourceColumns 源表的列信息列表，包含各列的属性
     * @param targetColumns 目标表的列信息列表，结构与源表列信息列表相似
     * @return 如果两个表的列信息完全匹配则返回true，否则返回false
     */
    private boolean compareTableInfo(List<TableColumnInfo> sourceColumns, List<TableColumnInfo> targetColumns) {
        // 如果两个列表大小不同，直接返回 false
        if (null == sourceColumns || null == targetColumns || sourceColumns.size() != targetColumns.size()) {
            return false;
        }
        sourceColumns.sort(Comparator.comparing(TableColumnInfo::getColumnName));
        targetColumns.sort(Comparator.comparing(TableColumnInfo::getColumnName));
        
        // 遍历 sourceColumns 和 targetColumns 进行比较
        for (int i = 0; i < sourceColumns.size(); i++) {
            TableColumnInfo sourceColumn = sourceColumns.get(i);
            TableColumnInfo targetColumn = targetColumns.get(i);
            
            // 比较字段名称
            if (!compareValues(sourceColumn.getColumnName(), targetColumn.getColumnName())) {
                return false;
            }
            
            // 比较字段类型
            if (!compareValues(sourceColumn.getColumnType(), targetColumn.getColumnType())) {
                return false;
            }
            
            // 比较字段注释
            if (!compareValues(sourceColumn.getColumnDesc(), targetColumn.getColumnDesc())) {
                return false;
            }
            
            // 比较字段的 Java 类型
            if (!compareValues(sourceColumn.getJavaType(), targetColumn.getJavaType())) {
                return false;
            }
            
            // 比较是否主键
            if (!compareValues(sourceColumn.getIsPrimary(), targetColumn.getIsPrimary())) {
                return false;
            }
            
            // 比较是否非空
            if (!compareValues(sourceColumn.getIsNullable(), targetColumn.getIsNullable())) {
                return false;
            }
            
            // 比较字段长度
            if (!compareValues(sourceColumn.getColumnLength(), targetColumn.getColumnLength())) {
                return false;
            }
            
            // 比较字段精度
            if (!compareValues(sourceColumn.getColumnPrecision(), targetColumn.getColumnPrecision())) {
                return false;
            }
            
            // 比较默认值
            if (!compareValues(sourceColumn.getDefaultValue(), targetColumn.getDefaultValue())) {
                return false;
            }
        }
        
        // 如果所有比较项都一致，返回 true
        return true;
    }
    
    /**
     * 获取元数据信息.
     *
     * @param metaDataCompareDto 对比请求实体
     * @return 元数据信息
     */
    private MetaDataInfo getMetaDataInfo(MetaDataCompareDto metaDataCompareDto) {
        return getMetaDataInfo(dataSourceInfoService.getWithCheckNonNull(metaDataCompareDto.getDatasourceId()),
                metaDataCompareDto.getTableNames().stream().collect(Collectors.toSet()));
    }
    
    /**
     * 获取源数据信息.
     *
     * @param sourceDs         数据源信息
     * @param sourceTableNames 查询表名
     */
    private MetaDataInfo getMetaDataInfo(DataSourceInfoEntity sourceDs, Set<String> sourceTableNames) {
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(sourceDs);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(sourceDs);
        DataSourceInfo dataSourceInfo = databaseTransformService.transformDataSourceInfo(sourceDs);
        
        MetaDataQuery metaDataQuery = new MetaDataQuery();
        metaDataQuery.setTableNames(new ArrayList<>(sourceTableNames));
        return databaseService.getMetaData(dataSourceInfo, metaDataQuery);
    }
    
    /**
     * 处理单个数据源的表结构同步.
     *
     * @param datasourceId 数据源ID
     * @param tables       表名
     * @return 数据源的已同步表和源表
     */
    private MutablePair<List<TableInfoLiteDto>, List<TableInfoLiteDto>> processDataSource(UUID datasourceId, List<String> tables) {
        DataSourceInfoEntity ds = dataSourceInfoService.getWithCheckNonNull(datasourceId);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(ds);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(ds);
        MetaDataQuery metaDataQuery = new MetaDataQuery();
        metaDataQuery.setTableNames(tables);
        MetaDataInfo metaData = databaseService.getMetaData(databaseTransformService.transformDataSourceInfo(ds), metaDataQuery);
        if (Objects.isNull(metaData)) {
            log.warn("获取元数据信息失败, ds:{}", ds);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "获取元数据信息失败或不存在");
        }
        List<TableInfoEntity> syncedTables = tableInfoService.getTables(datasourceId, Collections.emptyList());
        List<TableInfoLiteDto> syncedLiteTables = getSyncedLiteTables(syncedTables, metaData);
        List<TableInfoLiteDto> sourceTables = getSourceTables(metaData, syncedTables);
        return MutablePair.of(sourceTables, syncedLiteTables);
    }
    
    /**
     * 保存元数据.
     *
     * @param metaData 元数据
     * @param schema   数据源信息
     */
    private void saveMetaData(MetaDataInfo metaData, DataSourceInfoEntity schema) {
        // 1. 预提取：将循环外的常量和对象提取出来，避免重复计算
        Map<String, List<TableColumnInfo>> columnMap = Optional.ofNullable(metaData.getColumns()).orElse(Collections.emptyMap());
        List<TableInfo> tables = Optional.ofNullable(metaData.getTables()).orElse(Collections.emptyList());

        if (CollectionUtils.isEmpty(tables)) return;

        String currentUserName = HttpUtils.getCurrentUserName();
        Date now = new Date();
        LocalDateTime localNow = LocalDateTime.now();
        TypeInfoParser parser = TypeInfoManager.getParser(DatabaseTypeEnum.fromString(schema.getDatabaseType()));

        List<TableInfoEntity> tableEntities = new ArrayList<>();
        List<ColumnInfoEntity> allColumnEntities = new ArrayList<>(); // 汇总所有列

        for (TableInfo t : tables) {
            TableInfoEntity table = new TableInfoEntity();
            BeanUtils.copyProperties(t, table);

            UUID tableId = IdGenerator.createTableId(schema.getId(), table.getTableName());
            table.setId(tableId);
            table.setDatasourceId(schema.getId());
            table.setIsModify(false);
            table.setIsEqual(true);
            table.setCheckTime(localNow);
            table.setUpdater(currentUserName);
            table.setCreator(currentUserName);
            table.setUpdateTime(now);
            table.setCreateTime(now);

            // 2. 收集列信息，但不立即入库
            List<TableColumnInfo> columns = columnMap.get(table.getTableName());
            if (CollectionUtils.isNotEmpty(columns)) {
                for (TableColumnInfo c : columns) {
                    ColumnInfoEntity column = new ColumnInfoEntity();
                    BeanUtils.copyProperties(c, column);
                    column.setId(IdGenerator.createColumnId(tableId, column.getColumnName()));
                    column.setTableId(tableId);
                    column.setUpdater(currentUserName);
                    column.setCreator(currentUserName);
                    column.setUpdateTime(now);
                    column.setCreateTime(now);

                    if (column.getColumnLength() != null && column.getColumnLength() <= 0) {
                        column.setColumnLength(null);
                    }

                    String simpleJavaType = parser.parse(c.getColumnType()).getSimpleJavaType();
                    column.setJavaType(simpleJavaType);
                    column.setViewType(simpleJavaType);

                    allColumnEntities.add(column);
                }
            }
            tableEntities.add(table);
        }

        // 3. 两次批量操作完成入库（利用事务保证原子性）
        if (CollectionUtils.isNotEmpty(allColumnEntities)) {
            // 将 5 万条数据按每 1000 条一份进行切分
            List<List<ColumnInfoEntity>> partitions = ListUtils.partition(allColumnEntities, MetaDataConstant.META_BATCH_SIZE);

            for (List<ColumnInfoEntity> batch : partitions) {
                // 每一批次执行一次批量插入
                columnInfoService.saveBatch(batch);
            }
        }
        tableInfoService.saveBatch(tableEntities);
    }
    
    /**
     * 获取同步的轻量级表信息列表.
     *
     * @param syncedTables 同步的表信息实体列表，不能为空
     * @param metaData     元数据信息，用于处理表信息时的参考和配置
     * @return 返回处理后的轻量级表信息列表
     */
    private List<TableInfoLiteDto> getSyncedLiteTables(List<TableInfoEntity> syncedTables, MetaDataInfo metaData) {
        if (CollectionUtils.isEmpty(syncedTables)) {
            return Collections.emptyList();
        }
        List<UUID> tableIds = syncedTables.stream().map(TableInfoEntity::getId).collect(Collectors.toList());
        Map<String, List<ColumnInfoEntity>> columnMap =
                columnInfoService.getByTableIds(tableIds).stream().collect(Collectors.groupingBy(ColumnInfoEntity::getTableName));
        
        return syncedTables.stream().map(e -> {
            TableInfoLiteDto table = new TableInfoLiteDto();
            table.setTableName(e.getTableName());
            table.setTableDesc(e.getTableDesc());
            table.setIsView(e.getIsView());
            //            table.setTableType(e.getTableType());
            table.setSynced(true);
            table.setIsEqual(
                    Objects.isNull(metaData) ? false :
                            compareTable(metaData.getColumns().get(e.getTableName()), columnMap.get(e.getTableName())));
            e.setIsEqual(table.getIsEqual());
            return table;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取源数据库表信息 该方法根据元数据信息和已同步的表信息，筛选出需要的源数据库表信息.
     *
     * @param metaData     元数据信息，包含数据库的基本信息和结构
     * @param syncedTables 已同步的表列表，用于与源数据库表进行对比和筛选
     * @return 返回一个TableInfoLiteDto对象的列表，包含筛选后的源数据库表信息
     */
    private List<TableInfoLiteDto> getSourceTables(MetaDataInfo metaData, List<TableInfoEntity> syncedTables) {
        if (Objects.isNull(metaData)) {
            return Collections.emptyList();
        }
        Map<String, TableInfoEntity> tableNameMap =
                syncedTables.stream().collect(Collectors.toMap(TableInfoEntity::getTableName, Function.identity()));
        
        return metaData.getTables().stream().map(e -> {
            TableInfoLiteDto table = new TableInfoLiteDto();
            table.setTableName(e.getTableName());
            table.setTableDesc(e.getTableDesc());
            table.setIsView(e.getIsView());
            table.setTableType(e.getTableType());
            table.setSynced(tableNameMap.containsKey(table.getTableName()));
            return table;
        }).collect(Collectors.toList());
    }
    
    /**
     * 比较两个表的列信息是否一致 此方法用于检查源表和目标表的列信息是否有差异，以确保数据迁移或同步时列结构一致.
     *
     * @param sourceColumns 源表的列信息列表，包含各列的属性
     * @param targetColumns 目标表的列信息列表，结构与源表列信息列表相似
     * @return 如果两个表的列信息完全匹配则返回true，否则返回false
     */
    private boolean compareTable(List<TableColumnInfo> sourceColumns, List<ColumnInfoEntity> targetColumns) {
        // 如果两个列表大小不同，直接返回 false
        if (null == sourceColumns || null == targetColumns || sourceColumns.size() != targetColumns.size()) {
            return false;
        }
        sourceColumns.sort(Comparator.comparing(TableColumnInfo::getColumnName));
        targetColumns.sort(Comparator.comparing(ColumnInfoEntity::getColumnName));
        
        // 遍历 sourceColumns 和 targetColumns 进行比较
        for (int i = 0; i < sourceColumns.size(); i++) {
            TableColumnInfo sourceColumn = sourceColumns.get(i);
            ColumnInfoEntity targetColumn = targetColumns.get(i);
            
            // 比较字段名称
            if (!compareValues(sourceColumn.getColumnName(), targetColumn.getColumnName())) {
                return false;
            }
            
            // 比较字段类型
            if (!compareValues(sourceColumn.getColumnType(), targetColumn.getColumnType())) {
                return false;
            }
            
            // 比较字段注释
            if (!compareValues(sourceColumn.getColumnDesc(), targetColumn.getColumnDesc())) {
                return false;
            }
            
            // 比较字段的 Java 类型
            // if (!compareValues(sourceColumn.getJavaType(), targetColumn.getJavaType())) {
            //     return false;
            // }
            
            // 比较是否主键
            if (!compareValues(sourceColumn.getIsPrimary(), targetColumn.getIsPrimary())) {
                return false;
            }
            
            // 比较是否非空
            if (!compareValues(sourceColumn.getIsNullable(), targetColumn.getIsNullable())) {
                return false;
            }
            
            // 比较字段长度
            if (!compareValues(sourceColumn.getColumnLength(), targetColumn.getColumnLength())) {
                return false;
            }
            
            // 比较字段精度
            if (!compareValues(sourceColumn.getColumnPrecision(), targetColumn.getColumnPrecision())) {
                return false;
            }
            
            // 比较默认值
            if (!compareValues(sourceColumn.getDefaultValue(), targetColumn.getDefaultValue())) {
                return false;
            }
        }
        
        // 如果所有比较项都一致，返回 true
        return true;
    }
    
    /**
     * 比较两个值是否相等.
     *
     * @param sourceValue 第一个值，用于比较的源值
     * @param targetValue 第二个值，与源值进行比较的目标值
     * @return 如果两个值相等，则返回 true；否则返回 false
     */
    private <T> boolean compareValues(T sourceValue, T targetValue) {
        if (sourceValue == null) {
            return targetValue == null;
        }
        return sourceValue.equals(targetValue);
    }
    
    /**
     * freshDataSource.
     *
     * @param datasourceId datasourceId
     * @param tables       tables
     * @return MutablePair MutablePair
     */
    private MutablePair<List<TableInfoLiteDto>, List<TableInfoLiteDto>> freshDataSource(UUID
                                                                                                datasourceId, List<String> tables) {
        DataSourceInfoEntity ds = dataSourceInfoService.getWithCheckNonNull(datasourceId);
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(ds);
        TransformSupport databaseTransformService = DatabaseSupportManager.getTransformSupport(ds);
        MetaDataQuery metaDataQuery = new MetaDataQuery();
        metaDataQuery.setTableNames(tables);
        MetaDataInfo metaData = databaseService.getMetaData(databaseTransformService.transformDataSourceInfo(ds), metaDataQuery);
        if (Objects.isNull(metaData)) {
            log.warn("获取元数据信息失败, ds:{}", ds);
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "获取元数据信息失败或不存在");
        } else {
            ds.setTableCount(metaData.getTableCount());
            dataSourceInfoService.updateById(ds);
        }
        List<TableInfoEntity> syncedTables = tableInfoService.getTables(datasourceId, Collections.emptyList());
        if (CollectionUtil.isEmpty(syncedTables)) {
            log.info("数据源" + datasourceId + "不存在同步的表，不需要更新!");
            return null;
        }
        List<TableInfoLiteDto> syncedLiteTables = getSyncedLiteTables(syncedTables, metaData);
        List<TableInfoLiteDto> sourceTables = getSourceTables(metaData, syncedTables);
        Set<String> needUpdateTables = syncedLiteTables.stream()
                .filter(m -> !m.getIsEqual()).map(TableInfoLiteDto::getTableName).collect(Collectors.toSet());
        if (CollectionUtil.isEmpty(needUpdateTables)) {
            log.info("数据源" + datasourceId + "没有表需要更新!");
            return MutablePair.of(sourceTables, syncedLiteTables);
        }
        metaData = filterMetaData(metaData, needUpdateTables);
        for (String item : needUpdateTables) {
            historyDataService.saveSnapshot(getUuidByName(syncedTables, item));
        }
        //更新数据
        saveMetaData(metaData, ds);
        
        return MutablePair.of(sourceTables, syncedLiteTables);
    }
    
    /**
     * 获取表主键.
     *
     * @param syncedTables syncedTables
     * @param tableName    tableName
     * @return UUID
     */
    private UUID getUuidByName(List<TableInfoEntity> syncedTables, String tableName) {
        return syncedTables.stream().filter(m -> tableName.equals(m.getTableName())).collect(Collectors.toList()).get(0).getId();
    }
    
    /**
     * 过滤不需要更新表数据信息.
     *
     * @param originalMetaData originalMetaData
     * @param tableNamesToKeep tableNamesToKeep
     * @return MetaDataInfo
     */
    private MetaDataInfo filterMetaData(MetaDataInfo originalMetaData, Set<String> tableNamesToKeep) {
        // 安全检查
        if (originalMetaData == null || tableNamesToKeep == null || tableNamesToKeep.isEmpty()) {
            // 返回一个空的 MetaDataInfo 对象，而不是 null
            return new MetaDataInfo();
        }
        
        // 1. 过滤 tables 列表
        // 使用 Stream API 筛选出 tableName 在 tableNamesToKeep 集合中的 TableInfo 对象
        List<TableInfo> filteredTables = originalMetaData.getTables().stream()
                .filter(table -> tableNamesToKeep.contains(table.getTableName()))
                .collect(Collectors.toList());
        
        // 2. 过滤 columns Map
        // 筛选出 key (tableName) 在 tableNamesToKeep 集合中的条目
        Map<String, List<TableColumnInfo>> filteredColumns = originalMetaData.getColumns().entrySet().stream()
                .filter(entry -> tableNamesToKeep.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        // 3. 创建并填充新的 MetaDataInfo 对象
        MetaDataInfo filteredMetaData = new MetaDataInfo();
        filteredMetaData.setTables(filteredTables);
        filteredMetaData.setColumns(filteredColumns);
        
        // tableCount 字段可以由 getTableCount() 自动计算，无需手动设置。
        // 如果 MetaDataInfo 中的 tableCount 是一个需要持久化的字段而非计算属性，则需要设置它：
        // filteredMetaData.setTableCount(filteredTables.size());
        
        return filteredMetaData;
    }
    
    /**
     * 实体转换.
     *
     * @param tables  tables
     * @param columns columns
     * @return MetaDataInfo
     */
    private MetaDataInfo convert(List<TableInfoEntity> tables, List<ColumnInfoEntity> columns) {
        MetaDataInfo metaDataInfo = new MetaDataInfo();
        
        if (tables != null && !tables.isEmpty()) {
            // Convert TableInfoEntity to TableInfo
            List<TableInfo> tableInfos = tables.stream().map(dto -> {
                TableInfo vo = new TableInfo();
                BeanUtils.copyProperties(dto, vo);
                return vo;
            }).collect(Collectors.toList());
            metaDataInfo.setTables(tableInfos);
        }
        
        if (columns != null && !columns.isEmpty()) {
            // Group ColumnInfoEntity by tableId
            Map<UUID, List<ColumnInfoEntity>> columnsByTableId = columns.stream()
                    .collect(Collectors.groupingBy(ColumnInfoEntity::getTableId));
            
            // Convert grouped ColumnInfoEntity to Map<String, List<TableColumnInfo>>
            Map<String, List<TableColumnInfo>> tableColumnsMap = new HashMap<>();
            for (Map.Entry<UUID, List<ColumnInfoEntity>> entry : columnsByTableId.entrySet()) {
                UUID tableId = entry.getKey();
                List<ColumnInfoEntity> columnEntities = entry.getValue();
                
                // Find the corresponding TableInfoEntity to get the table name
                // Assuming tableId in ColumnInfoEntity always corresponds to an existing TableInfoEntity
                String tableName = tables.stream()
                        .filter(t -> t.getId().equals(tableId))
                        .map(TableInfoEntity::getTableName)
                        .findFirst()
                        .orElse(null); // Or handle error if table not found
                
                if (tableName != null) {
                    List<TableColumnInfo> tableColumnInfos = columnEntities.stream()
                            .map(dto -> {
                                TableColumnInfo vo = new TableColumnInfo();
                                BeanUtils.copyProperties(dto, vo);
                                return vo;
                            })
                            .collect(Collectors.toList());
                    tableColumnsMap.put(tableName, tableColumnInfos);
                }
            }
            metaDataInfo.setColumns(tableColumnsMap);
        }
        
        // The getTableCount method in MetaDataInfo will automatically calculate tableCount
        return metaDataInfo;
    }

    /**
     * 删除数据源元数据.
     *
     * @param dataSourceIds 数据源id列表
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteDatasourceMetaData(List<UUID> dataSourceIds){
        if (CollectionUtils.isEmpty(dataSourceIds)) {
            log.warn("数据源ID列表为空，无需删除");
            return true;
        }
        for (UUID dataSourceId : dataSourceIds) {
            if (dataSourceId == null) {
                log.warn("跳过空的数据源ID");
                continue;
            }
            RetrieveMetaDataDto query = new RetrieveMetaDataDto();
            query.setDatasourceId(dataSourceId);
            // 删除字段信息
            Boolean columnsRes = columnInfoService.deleteColumnsByDataSourceId(query);
            // 删除表信息
            Boolean tablesRes = tableInfoService.deleteTablesByDataSourceId(query);
        }
        return true;
    }

    /**
     * 根据数据源id全量更新数据库元数据.
     *
     * @param updateDatasourceInfo 数据源id列表
     * @return 删除结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateDatasourceMetaData(UpdateDatasourceMetaDataDto updateDatasourceInfo) {
        List<UUID> dataSourceIds = updateDatasourceInfo.getDataSourceIds();
        Boolean isDelete = updateDatasourceInfo.getIsDelete();
        if (CollectionUtils.isEmpty(dataSourceIds)) {
            log.warn("数据源ID列表为空，无需更新");
            return true;
        }
        int successCount = 0;
        int failCount = 0;
        List<String> warnings = new ArrayList<>();
        for (UUID dataSourceId : dataSourceIds) {
            if (dataSourceId == null) {
                continue;
            }
            try {
                RetrieveMetaDataDto query = new RetrieveMetaDataDto();
                query.setDatasourceId(dataSourceId);
                if (isDelete) {
                    Boolean deleteRes = deleteDatasourceMetaData(Collections.singletonList(dataSourceId));
                    if (!Boolean.TRUE.equals(deleteRes)) {
                        log.error("数据源[{}]元数据删除失败", dataSourceId);
                        throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505,
                                String.format("数据源[%s]元数据删除失败", dataSourceId));
                    }
                }
                List<TableColumnsTreeDto> tables = getTableInfos(dataSourceId);
                if (CollectionUtils.isNotEmpty(tables)) {
                    query.setTableNames(tables.stream()
                            .map(TableColumnsTreeDto::getTableName)
                            .collect(Collectors.toList()));
                    String warnMsg = registerTables(query);
                    if (warnMsg != null) {
                        warnings.add(warnMsg);
                        log.warn("数据源[{}]元数据注册存在警告: {}", dataSourceId, warnMsg);
                    }
                }
                successCount++;
                log.info("数据源[{}]元数据更新成功", dataSourceId);
            } catch (Exception e) {
                failCount++;
                log.error("更新数据源[{}]的元数据失败: {}", dataSourceId, e.getMessage(), e);
                throw e;
            }
        }
        if (!warnings.isEmpty()) {
            log.warn("批量更新元数据完成，总数:{}, 成功:{}, 存在{}条警告",
                    dataSourceIds.size(), successCount, warnings.size());
        } else {
            log.info("批量更新元数据完成，总数:{}, 全部成功", dataSourceIds.size());
        }
        return true;
    }
}