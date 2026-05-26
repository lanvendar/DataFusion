package com.datafusion.manager.metadata.controller;

import com.datafusion.common.enums.DatabaseTypeEnum;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.uuid.IdGenerator;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.metadata.dto.BatchCreateTableCheckDto;
import com.datafusion.manager.metadata.dto.BatchCreateTableCheckResultDto;
import com.datafusion.manager.metadata.dto.BatchCreateTableDdlDto;
import com.datafusion.manager.metadata.dto.BatchMetaDataCompareDto;
import com.datafusion.manager.metadata.dto.BatchMetaDataCompareResultDto;
import com.datafusion.manager.metadata.dto.KeyValueDto;
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
import com.datafusion.manager.metadata.enums.DefaultColumnEnum;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;
import com.datafusion.manager.metadata.service.MetaDataExportService;
import com.datafusion.manager.metadata.service.MetaDataService;
import com.datafusion.manager.metadata.support.model.TableColumnInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 元数据修改接口.
 *
 * @author david
 * @version 3.6.4, 2024/9/4
 * @since 3.6.4, 2024/9/4
 */
@RestController
@RequestMapping("/api/metadata")
@Tag(name = "【元数据采集、预览、操作】")
@RequiredArgsConstructor
public class MetaDataController {
    
    /**
     * 元数据服务.
     */
    private final MetaDataService metaDataService;
    
    /**
     * 元数据excel导出service.
     */
    private final MetaDataExportService exportService;
    
    /**
     * 获取数据源数据表和已同步的表.
     *
     * @param tableLiteQueryDto 数据源ID
     * @return 采集结果
     */
    @PostMapping("/getSourceAndSyncedTables")
    @Operation(summary = "获取数据源数据表和已同步的表")
    public Result<SourceAndSyncedTableDto> getTables(@RequestBody TableLiteQueryDto tableLiteQueryDto) {
        return Result.success(metaDataService.getSourceTablesAndSyncedTables(tableLiteQueryDto));
    }
    
    /**
     * 元数据采集后登记表.
     *
     * @param retrieveDto 参数
     * @return 采集结果
     */
    @PostMapping("/registerTables")
    @Operation(summary = "数据库表登记注册")
    public Result<Boolean> registerTables(@RequestBody @Validated RetrieveMetaDataDto retrieveDto) {
        String warnMsg = metaDataService.registerTables(retrieveDto);
        if (warnMsg != null) {
            return Result.failed(ErrorCodeEnum.SYSTEM_ERROR_B0001, warnMsg);
        }
        return Result.success(true);
    }
    
    /**
     * 查询数据库表的业务信息：数据条数与存储大小.
     *
     * @param tableId 数据库表ID
     * @return 据库表的业务信息
     */
    @PostMapping("/getRowCountAndSize/{tableId}")
    @Operation(summary = "查询数据库表的业务信息", description = "查询数据库表的业务信息：数据条数与存储大小")
    public Result<TableBusMetaDto> countRowAndSize(@PathVariable("tableId") UUID tableId) {
        return Result.success(metaDataService.countRowAndSize(tableId));
    }
    
    /**
     * 元数据预览.
     *
     * @param queryDto 元数据分页查询条件
     * @return 据库表的业务信息
     */
    @PostMapping("/getMetaTableData")
    @Operation(summary = "元数据预览", description = "元数据预览")
    public Result<TableBusMetaDataDto> getMetaTableData(
            @RequestBody @Validated TableBusMetaDataQueryDto queryDto) {
        return Result.success(metaDataService.getMetaTableData(queryDto));
    }
    
    /**
     * 导出表结构.
     *
     * @param tableId  表ID
     * @param request  Http请求
     * @param response Http应答
     */
    @Operation(summary = "表结构导出")
    @PostMapping("/{tableId}/exportTable")
    public void exportDevice(@PathVariable("tableId") UUID tableId, HttpServletRequest request, HttpServletResponse response) {
        exportService.exportTableColumn(tableId, request, response);
    }
    
    /**
     * 源数据源和目标数据源批量建表检查,目标表是否存在,以及字段数量.
     *
     * @param batchCreateTableCheckDto 源数据源和目标数据源批量建表检查请求
     * @return List&ltBatchCreateTableCheckResultDto> 返回选中表从比对结果
     */
    @Operation(summary = "批量建表检查")
    @PostMapping("/batchCreateTableCheck")
    public Result<List<BatchCreateTableCheckResultDto>> batchCreateTableCheck(@RequestBody @Validated
                                                                              BatchCreateTableCheckDto batchCreateTableCheckDto) {
        return Result.success(metaDataService.batchCreateTableCheck(batchCreateTableCheckDto));
    }
    
    /**
     * 根据数据源ID获取所有表字段结构.
     *
     * @param dataSourceId 数据源ID
     * @return List&ltTableColumnsTreeDto> 返回表字段表结构
     */
    @Operation(summary = "获取源数据源表树形结构")
    @GetMapping("/getTableInfos/{dataSourceId}")
    public Result<List<TableColumnsTreeDto>> getTableInfos(@PathVariable("dataSourceId") String dataSourceId) {
        UUID uuid = UUID.fromString(dataSourceId);
        return Result.success(metaDataService.getTableInfos(uuid));
    }

    /**
     * 根据数据源ID和表名，连源库实时查询表的全部字段信息.
     *
     * @param datasourceId 数据源ID
     * @param tableName    表名
     * @return 字段信息列表
     */
    @Operation(summary = "连源库查询表字段信息")
    @GetMapping("/getColumnsFromSource/{datasourceId}/{tableName}")
    public Result<List<TableColumnInfo>> getColumnsFromSource(
            @PathVariable("datasourceId") UUID datasourceId,
            @PathVariable("tableName") String tableName) {
        return Result.success(metaDataService.getColumnsFromSource(datasourceId, tableName));
    }

    /**
     * 源数据源和目标数据源批量对比.
     *
     * @param batchMetaDataCompareDto 源数据源和目标数据源批量比对请求
     * @return 返回源数据源和目标数据源批量比对结果
     */
    @Operation(summary = "数据源表结构对比")
    @PostMapping("/batchMetaCompare")
    public Result<List<BatchMetaDataCompareResultDto>> batchMetaCompare(@RequestBody @Validated BatchMetaDataCompareDto batchMetaDataCompareDto) {
        return Result.success(metaDataService.batchMetaDataCompare(batchMetaDataCompareDto));
    }
    
    /**
     * 源数据源和目标数据源批量对比.
     *
     * @param tableCompareDetailQueryDto 源数据源和目标数据源批量比对请求
     * @return 源数据源和目标数据源批量比对
     */
    @Operation(summary = "表结构对比明细")
    @PostMapping("/getTableCompareDetail")
    public Result<TableColumnInfoCompareResultDto> getTableCompareDetail(
            @RequestBody @Validated TableCompareDetailQueryDto tableCompareDetailQueryDto) {
        return Result.success(metaDataService.getTableCompareDetail(tableCompareDetailQueryDto));
    }
    
    /**
     * 表结构对比SQL生成.
     *
     * @param tableCompareGenerateDdlRequestDto 对比详情结果
     * @return DDL脚本
     */
    @Operation(summary = "表结构对比SQL生成")
    @PostMapping("/generateTableCompareDdlSql")
    public Result<String> generateTableCompareDdlSql(@RequestBody @Validated TableCompareGenerateDdlRequestDto tableCompareGenerateDdlRequestDto) {
        return Result.success(metaDataService.generateTableCompareDdlSql(tableCompareGenerateDdlRequestDto));
    }
    
    /**
     * 批量生成建表语句ddl.
     *
     * @param batchCreateTableDdlDto 批量创建表请求对象
     * @return List&ltTableColumnsTreeDto> 返回表字段表结构
     */
    @Operation(summary = "生成批量建表语句")
    @PostMapping("/batchCreateTableDdl")
    public Result<String> batchCreateTableDdl(@RequestBody @Validated BatchCreateTableDdlDto batchCreateTableDdlDto) {
        return Result.success(metaDataService.batchCreateTableDdl(batchCreateTableDdlDto));
    }

    /**
     * 数据集成批量生成建表语句ddl.
     *
     * @param batchCreateTableDdlDto 批量创建表请求对象
     * @return List&ltTableColumnsTreeDto> 返回表字段表结构
     */
    @Operation(summary = "数据集成生成批量建表语句")
    @PostMapping("/syncBatchCreateTableDdl")
    public Result<String> syncBatchCreateTableDdl(@RequestBody @Validated BatchCreateTableDdlDto batchCreateTableDdlDto) {
        return Result.success(metaDataService.syncBatchCreateTableDdl(batchCreateTableDdlDto));
    }


    /**
     * 根据数据库类型返回数据库默认字段.
     *
     * @param databaseType 根据数据库类型,返回公共字段
     * @return List&ltTableColumnsTreeDto> 返回表字段表结构
     */
    @Operation(summary = "根据数据库返回默认字段")
    @GetMapping("/defaultColumns/{databaseType}")
    public Result<List<KeyValueDto>> defaultColumns(@PathVariable("databaseType") String databaseType) {

        //先判断databaseType是否在枚举中
        if (!DatabaseTypeEnum.contains(databaseType)) {
            List<KeyValueDto> resultList = new ArrayList<>();
            return Result.success(resultList);
        }

        // 1. 将字符串转换为枚举类型
        DatabaseTypeEnum dbType = DatabaseTypeEnum.fromString(databaseType); // 假设您的 DatabaseTypeEnum 有 fromString 方法
        
        // 2. 调用枚举的静态方法，获取该数据库类型对应的所有默认字段
        Map<String, ColumnInfoEntity> defaultColumnsMap = DefaultColumnEnum.getDefaultColumnByDatabaseType(dbType);
        
        // 3. 将 Map 转换为前端需要的 List<KeyValueDto> 格式
        List<KeyValueDto> resultList = new ArrayList<>();
        for (String key : defaultColumnsMap.keySet()) {
            resultList.add(new KeyValueDto(key, defaultColumnsMap.get(key).getColumnDesc()));
        }
        return Result.success(resultList);
    }
    
    /**
     * 生成批次id.
     *
     * @param operateType 0:批量创建|1:批量对比
     * @return UUID 轨迹id
     */
    @Operation(summary = "生成批次id")
    @PostMapping("/generateTrackId/{operateType}")
    public Result<UUID> generateTrackId(@PathVariable("operateType") String operateType) {
        return Result.success(IdGenerator.createTrackId(operateType));
    }
    
    /**
     * 执行最终的sql脚本.
     *
     * @param runSqlDto 0:批量创建|1:批量对比
     * @return UUID 轨迹id
     */
    @Operation(summary = "执行最终的sql脚本")
    @PostMapping("/executeSql")
    public Result<Boolean> executeSql(@RequestBody @Validated RunSqlDto runSqlDto) {
        return Result.success(metaDataService.executeSql(runSqlDto));
    }

    /**
     * 对比操作日志分页查询.
     *
     * @param query 查询参数
     * @return 表信息
     */
    @PostMapping("/page/operateLog")
    @Operation(summary = "对比操作日志分页查询")
    public Result<PageResponse<MetadataTableOperateLogViewDto>> pageOperateLogs(@RequestBody PageQuery<MetadataTableOperateLogQueryDto> query) {
        return Result.success(metaDataService.pageOperateLogs(query));
    }
    
    /**
     * 对比操作日志分页详情.
     *
     * @param id 表ID
     * @return 表信息
     */
    @GetMapping("/operateLog/{id}")
    @Operation(summary = "根据ID查询对比操作日志记录")
    public Result<MetadataTableOperateLogViewDto> getOperateLog(@PathVariable("id") UUID id) {
        return Result.success(metaDataService.getOperateLog(id));
    }

    /**
     * 根据数据源id全库更新元数据.
     *
     * @param updateDatasourceInfo 数据源ID列表
     * @return 更新结果
     */
    @PostMapping("/updateDatasourceMetaData")
    @Operation(summary = "根据数据源id全库更新元数据")
    public Result<Boolean> updateDatasourceMetaData(@RequestBody @Validated UpdateDatasourceMetaDataDto updateDatasourceInfo) {
        Boolean result = metaDataService.updateDatasourceMetaData(updateDatasourceInfo);
        return Result.success(result);
    }
    
}
