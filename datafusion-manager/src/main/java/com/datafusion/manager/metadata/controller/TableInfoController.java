package com.datafusion.manager.metadata.controller;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.metadata.dto.TableInfoDto;
import com.datafusion.manager.metadata.dto.TableInfoQueryDto;
import com.datafusion.manager.metadata.dto.TableInfoSaveDto;
import com.datafusion.manager.metadata.dto.TableInfoUpdateDto;
import com.datafusion.manager.metadata.service.TableInfoService;
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

import java.util.List;
import java.util.UUID;

/**
 * 元数据-表信息.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@RestController
@RequestMapping("/api/metadata/table")
@Tag(name = "【表结构管理】")
@RequiredArgsConstructor
public class TableInfoController {
    
    /**
     * 元数据-表服务.
     */
    private final TableInfoService tableInfoService;
    
    /**
     * 分页查询表信息.
     *
     * @param query 查询参数
     * @return 表信息
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询数据表")
    public Result<PageResponse<TableInfoDto>> pageTableInfos(@RequestBody PageQuery<TableInfoQueryDto> query) {
        return tableInfoService.pageTableInfos(query);
    }
    
    /**
     * 查询表信息列表.
     *
     * @param query 查询条件
     * @return 表信息列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询数据表列表")
    public Result<List<TableInfoDto>> listTablesInfos(@RequestBody TableInfoQueryDto query) {
        return Result.success(tableInfoService.listTableInfos(query));
    }
    
    /**
     * 添加表信息.
     *
     * @param dto 表信息
     * @return 添加结果
     */
    @PostMapping("/add")
    @Operation(summary = "添加数据表")
    public Result<UUID> addTableInfo(@RequestBody @Validated TableInfoSaveDto dto) {
        return Result.success(tableInfoService.addTableInfo(dto));
    }
    
    /**
     * 修改表信息.
     *
     * @param dto 表信息
     * @return 修改结果
     */
    @PostMapping("/update")
    @Operation(summary = "修改数据表")
    public Result<Boolean> updateTableInfo(@RequestBody @Validated TableInfoUpdateDto dto) {
        boolean updated = tableInfoService.updateTableInfo(dto);
        return updated ? Result.success(true) : Result.failed(ErrorCodeEnum.SYSTEM_ERROR_B0001, "更新失败");
    }
    
    /**
     * 根据ID查询.
     *
     * @param id 表ID
     * @return 表信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询数据表")
    public Result<TableInfoDto> getTableInfo(@PathVariable("id") UUID id) {
        return Result.success(tableInfoService.getTableInfo(id));
    }
    
    /**
     * 根据ID删除.
     *
     * @param id 表ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "根据ID删除数据表")
    //@AuditLog(source = AuditSourceType.DATACENTER, operationEvent = "删除数据表", resourceType = "元数据", operType = AuditOperationType.DELETE)
    public Result<Boolean> deleteTableInfo(@PathVariable("id") UUID id) {
        return Result.success(tableInfoService.deleteTableInfo(id));
    }
}
