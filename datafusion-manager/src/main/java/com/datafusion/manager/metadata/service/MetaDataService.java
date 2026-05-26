package com.datafusion.manager.metadata.service;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.metadata.dto.BatchCreateTableCheckDto;
import com.datafusion.manager.metadata.dto.BatchCreateTableCheckResultDto;
import com.datafusion.manager.metadata.dto.BatchCreateTableDdlDto;
import com.datafusion.manager.metadata.dto.BatchMetaDataCompareDto;
import com.datafusion.manager.metadata.dto.BatchMetaDataCompareResultDto;
import com.datafusion.manager.metadata.dto.MetadataTableOperateLogQueryDto;
import com.datafusion.manager.metadata.dto.MetadataTableOperateLogViewDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.dto.RunSqlDto;
import com.datafusion.manager.metadata.dto.SourceAndSyncedTableDto;
import com.datafusion.manager.metadata.dto.TableBusMetaDataDto;
import com.datafusion.manager.metadata.dto.TableBusMetaDataQueryDto;
import com.datafusion.manager.metadata.dto.TableBusMetaDto;
import com.datafusion.manager.metadata.dto.TableColumnInfoCompareResultDto;
import com.datafusion.manager.metadata.dto.TableColumnsTreeDto;
import com.datafusion.manager.metadata.dto.TableCompareDetailQueryDto;
import com.datafusion.manager.metadata.dto.TableCompareGenerateDdlRequestDto;
import com.datafusion.manager.metadata.dto.TableLiteQueryDto;
import com.datafusion.manager.metadata.dto.UpdateDatasourceMetaDataDto;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;

import java.util.List;
import java.util.UUID;

/**
 * 元数据服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/30
 * @since 3.6.4, 2024/8/30
 */
public interface MetaDataService {
    /**
     * 源数据表结构一致性检查.
     *
     * @param datasourceIds 数据源ID集合
     * @return 异常集合
     */
    List<Exception> checkTable(List<UUID> datasourceIds);

    /**
     * 根据数据源ID查询数据源下的数据表集合 和 已同步的表.
     *
     * @param tableLiteQueryDto 数据源表查询DTO
     * @return 数据源表和已同步的表
     */
    SourceAndSyncedTableDto getSourceTablesAndSyncedTables(TableLiteQueryDto tableLiteQueryDto);

    /**
     * 元数据采集.
     *
     * @param retrieveDto 参数
     * @return 元数据采集
     */
    String registerTables(RetrieveMetaDataDto retrieveDto);
    
    /**
     * 统计表行数和存储大小.
     *
     * @param tableId 表ID
     * @return 表总数量
     */
    TableBusMetaDto countRowAndSize(UUID tableId);

    /**
     * 元数据预览（表数据）.
     *
     * @param queryDto 查询条件
     * @return 表总数量
     */
    TableBusMetaDataDto getMetaTableData(TableBusMetaDataQueryDto queryDto);

    /**
     * 源数据表结构结构刷新检查.
     *
     * @param datasourceIds 数据源ID集合
     * @return 异常集合
     */
    List<Exception> refreshTables(List<UUID> datasourceIds);

    /**
     * 源数据源和目标数据源批量对比.
     * @param batchCreateTableCheckDto 批量对比请求参数
     * @return 批量对比结果
     */
    List<BatchCreateTableCheckResultDto> batchCreateTableCheck(BatchCreateTableCheckDto batchCreateTableCheckDto);

    /**
     * 根据dataSourceId获取所有表字段.
     * @param dataSourceId 根据dataSourceId获取所有表字段
     * @return dataSource下所有表
     */
    List<TableColumnsTreeDto> getTableInfos(UUID dataSourceId);
    
    /**
     * 源数据源和目标数据源批量对比.
     * @param batchMetaDataCompareDto 批量对比实体
     * @return 批量对比结果
     */
    List<BatchMetaDataCompareResultDto> batchMetaDataCompare(BatchMetaDataCompareDto batchMetaDataCompareDto);
    
    /**
     * 获取源表和目标表的字段详情及差异信息.
     * @param tableCompareDetailQueryDto 对比表信息
     * @return 源表和目标表的字段详情及差异信息
     */
    TableColumnInfoCompareResultDto getTableCompareDetail(TableCompareDetailQueryDto tableCompareDetailQueryDto);
    
    /**
     * 根据对比详情生成 DDL 脚本.
     * @param tableCompareGenerateDdlRequestDto 表对比详情
     * @return DDL 脚本
     */
    String generateTableCompareDdlSql(TableCompareGenerateDdlRequestDto tableCompareGenerateDdlRequestDto);
    
    /**
     * 生成批量建表语句接口.
     * @param batchCreateTableDdlDto 生成批量建表语句请求体
     * @return dataSource下所有表
     */
    String batchCreateTableDdl(BatchCreateTableDdlDto batchCreateTableDdlDto);

    /**
     * 数据集成生成批量建表语句接口.
     * @param batchCreateTableDdlDto 生成批量建表语句请求体
     * @return dataSource下所有表
     */
    String syncBatchCreateTableDdl(BatchCreateTableDdlDto batchCreateTableDdlDto);


    /**
     * 执行最终的sql脚本.
     * @param runSqlDto sql执行脚本实体
     * @return sql 执行结果
     */
    Boolean executeSql(RunSqlDto runSqlDto);

    /**
     * 根据数据源ID和表名，连源库实时查询表的全部字段信息.
     *
     * @param datasourceId 数据源ID
     * @param tableName    表名
     * @return 字段信息列表
     */
    List<TableColumnInfo> getColumnsFromSource(UUID datasourceId, String tableName);

    /**
     * 对比操作日志分页查询接口.
     * @param query 查询实体
     * @return 分页查询结果
     */
    PageResponse<MetadataTableOperateLogViewDto> pageOperateLogs(PageQuery<MetadataTableOperateLogQueryDto> query);
    
    /**
     * 操作日志详情.
     * @param id id
     * @return 操作日志详情
     */
    MetadataTableOperateLogViewDto getOperateLog(UUID id);

    /**
     * 删除数据源元数据.
     *
     * @param dataSourceIds 数据源id列表
     * @return 删除结果
     */
    Boolean deleteDatasourceMetaData(List<UUID> dataSourceIds);

    /**
     * 根据数据源id全量更新数据库元数据.
     *
     * @param updateDatasourceInfo 数据源id列表
     * @return 删除结果
     */
    Boolean updateDatasourceMetaData(UpdateDatasourceMetaDataDto updateDatasourceInfo);
}
