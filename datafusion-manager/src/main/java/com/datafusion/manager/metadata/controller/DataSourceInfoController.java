package com.datafusion.manager.metadata.controller;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.metadata.dto.ColumnViewConfigDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoCopyDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoQueryDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoSaveDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoUpdateDto;
import com.datafusion.manager.metadata.dto.DatabaseTypeDto;
import com.datafusion.manager.metadata.dto.TableCheckDto;
import com.datafusion.manager.metadata.dto.TableRefreshDto;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.service.MetaDataService;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.MetaDataSupport;
import com.datafusion.manager.metadata.support.model.DataSourceExtendParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 元数据-数据源接口.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@RestController
@RequestMapping("/api/metadata/datasource")
@Tag(name = "【数据源管理】")
@RequiredArgsConstructor
public class DataSourceInfoController {
    /**
     * 元数据-数据库服务.
     */
    private final DataSourceInfoService dataSourceInfoService;
    
    /**
     * 元数据服务.
     */
    private final MetaDataService metaDataService;
    
    /**
     * 支持.
     */
    private static final String[] DEFAULT_DATA_SYNC_TYPE = {"maxcompute", "hologres",
            "starrocks", "postgres", "dm"};
    
    
    /**
     * 数据源类型.
     *
     * @return 数据源列表
     */
    @GetMapping("/databaseType")
    @Operation(summary = "数据源类型")
    public Result<List<DatabaseTypeDto>> getDatabaseType() {
        List<DatabaseTypeDto> dataTypes = Arrays.asList(DEFAULT_DATA_SYNC_TYPE).stream().map(e ->
                new DatabaseTypeDto(e, e)).collect(Collectors.toList());
        return Result.success(dataTypes);
    }
    
    /**
     * 数据源类型-拓展参数.
     *
     * @param databaseType 数据库类型
     * @return 数据源列表
     */
    @GetMapping("/{databaseType}/extendParams")
    @Operation(summary = "拓展参数")
    public Result<List<DataSourceExtendParam>> getExtendParams(@PathVariable("databaseType") String databaseType) {
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(databaseType);
        return Result.success(databaseService.getDefaultExtendParams());
    }
    
    /**
     * 数据源类型-字段类型.
     *
     * @param databaseType 数据库类型
     * @return 数据源列表
     */
    @GetMapping("/{databaseType}/columnDataTypes")
    @Operation(summary = "字段类型")
    public Result<Set<String>> getColoumnDataTypes(@PathVariable("databaseType") String databaseType) {
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(databaseType);
        return Result.success(databaseService.getColumnDataTypes());
    }
    
    /**
     * 数据源类型-字段类型对应的页面配置.
     *
     * @param databaseType 数据库类型
     * @param dataType     字段类型
     * @return 数据源列表
     */
    @GetMapping("/{databaseType}/{dataType}/viewConifg")
    @Operation(summary = "字段类型页面配置")
    public Result<ColumnViewConfigDto> getDataTypeViewConfig(@PathVariable("databaseType") String databaseType,
                                                             @PathVariable("dataType") String dataType) {
        MetaDataSupport databaseService = DatabaseSupportManager.getMetaDataSupport(databaseType);
        return Result.success(databaseService.getDataTypeViewConfig(dataType));
    }
    
    /**
     * 分页查询.
     *
     * @param query 查询参数
     * @return 数据源列表
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询数据源列表")
    public Result<PageResponse<DataSourceInfoDto>> pageDataSource(@RequestBody PageQuery<DataSourceInfoQueryDto> query) {
        return Result.success(dataSourceInfoService.pageDataSource(query));
    }
    
    /**
     * 查询列表.
     *
     * @param query 查询参数
     * @return 数据源列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询数据源列表")
    public Result<List<DataSourceInfoDto>> listDataSource(@RequestBody DataSourceInfoQueryDto query) {
        return Result.success(dataSourceInfoService.listDataSource(query));
    }
    
    /**
     * 新增数据源.
     *
     * @param dto 新增参数
     * @return 新增结果
     */
    @PostMapping("/add")
    @Operation(summary = "添加数据源")
    public Result<UUID> addDataSource(@RequestBody @Validated DataSourceInfoSaveDto dto) {
        return Result.success(dataSourceInfoService.addDataSource(dto));
    }
    
    /**
     * 新增数据源.
     *
     * @param dto 新增参数
     * @return 新增结果
     */
    @PostMapping("/copyDataSource")
    @Operation(summary = "复制数据源")
    public Result<UUID> copyDataSource(@RequestBody @Validated DataSourceInfoCopyDto dto) {
        return Result.success(dataSourceInfoService.copyDataSource(dto));
    }
    
    /**
     * 修改数据源.
     *
     * @param dto 修改参数
     * @return 修改结果
     */
    @PostMapping("/update")
    @Operation(summary = "修改数据源")
    public Result<Boolean> updateDataSource(@RequestBody @Validated DataSourceInfoUpdateDto dto) {
        boolean updated = dataSourceInfoService.updateDataSource(dto);
        return updated ? Result.success(true) : Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "数据库更新失败");
    }
    
    /**
     * 根据ID查询.
     *
     * @param id 数据源ID
     * @return 数据源信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询数据源")
    public Result<DataSourceInfoDto> getDataSource(@PathVariable("id") UUID id) {
        return Result.success(dataSourceInfoService.getDataSource(id));
    }
    
    /**
     * 根据ID删除.
     *
     * @param id 数据源ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "根据ID删除数据源")
    //@AuditLog(source = AuditSourceType.DATACENTER, operationEvent = "删除数据源", resourceType = "元数据", operType = AuditOperationType.DELETE)
    public Result<Boolean> deleteDataSource(@PathVariable("id") UUID id) {
        boolean deleted = dataSourceInfoService.deleteDataSource(id);
        return deleted ? Result.success(true) : Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, "数据库删除失败");
    }
    
    /**
     * 测试数据源连接.
     *
     * @param dto 数据源信息
     * @return 测试连接结果
     */
    @PostMapping("/testConnect")
    @Operation(summary = "测试数据源连接")
    public Result<Boolean> testConnect(@RequestBody @Validated DataSourceInfoSaveDto dto) {
        boolean connected = dataSourceInfoService.testConnect(dto);
        return connected ? Result.success(true) : Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300,
                "数据库连接超时,请确认网络或者是否有此数据库");
    }
    
    /**
     * 检查表结构是否一致.
     *
     * @param checkTable 元数据-表结一致性构检查DTO
     * @return 表结一致性构检查结果
     */
    @PostMapping("/table/check")
    @Operation(summary = "检查表结构是否一致")
    public Result<String> checkTable(@RequestBody @Validated TableCheckDto checkTable) {
        List<Exception> exceptions = metaDataService.checkTable(checkTable.getDatasourceIds());
        if (exceptions.isEmpty()) {
            return Result.success("表结构检查成功");
        } else {
            StringBuilder errorMessage = new StringBuilder("表结构检查失败，原因如下：\n");
            for (Exception e : exceptions) {
                errorMessage.append(e.getMessage()).append("\n");
            }
            return Result.failed(ErrorCodeEnum.SERVICE_ERROR_C0300, errorMessage.toString());
        }
    }
    
    /**
     * 元数据刷新.
     *
     * @param tableRefreshDto 参数
     * @return 采集结果
     */
    @PostMapping("/metaDataRefresh")
    @Operation(summary = "元数据刷新")
    public Result<String> metaDataRefresh(@RequestBody TableRefreshDto tableRefreshDto) {
        List<Exception> exceptions = metaDataService.refreshTables(tableRefreshDto.getDatasourceIds());
        if (exceptions.isEmpty()) {
            return Result.success("表结构刷新成功");
        } else {
            StringBuilder errorMessage = new StringBuilder("表结构刷新失败，原因如下：\n");
            for (Exception e : exceptions) {
                e.printStackTrace();
                errorMessage.append(e.getMessage()).append("\n");
            }
            return Result.failed(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, errorMessage.toString());
        }
    }
    
}
