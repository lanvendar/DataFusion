package com.datafusion.manager.metadata.controller;

import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.common.web.dto.response.Result;
import com.datafusion.manager.metadata.dto.ColumnInfoDto;
import com.datafusion.manager.metadata.dto.ColumnInfoQueryDto;
import com.datafusion.manager.metadata.dto.ColumnInfoSaveDto;
import com.datafusion.manager.metadata.dto.ColumnInfoUpdateDto;
import com.datafusion.manager.metadata.service.ColumnInfoService;
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
 * 元数据-数据表字段相关.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@RestController
@RequestMapping("/api/metadata/column")
@RequiredArgsConstructor
@Tag(name = "【表字段】")
public class ColumnInfoController {
    
    /**
     * 元数据-字段信息服务.
     */
    private final ColumnInfoService columnInfoService;
    
    /**
     * 分页查询表信息.
     *
     * @param query 查询参数
     * @return 表信息
     */
    @PostMapping("/page")
    @Operation(summary = "【表字段】分页查询表字段")
    public Result<PageResponse<ColumnInfoDto>> pageColumnInfos(@RequestBody PageQuery<ColumnInfoQueryDto> query) {
        return columnInfoService.pageColumnInfos(query);
    }

    /**
     * 查询表信息列表.
     *
     * @param query 查询条件
     * @return 表信息列表
     */
    @PostMapping("/list")
    @Operation(summary = "【表字段】查询表字段列表")
    public Result<List<ColumnInfoDto>> listColumnInfos(@RequestBody ColumnInfoQueryDto query) {
        List<ColumnInfoDto> list = columnInfoService.listColumnInfos(query);
        return Result.success(list);
    }
    
    /**
     * 添加表信息.
     *
     * @param dto 表信息
     * @return 添加结果
     */
    @PostMapping("/add")
    @Operation(summary = "【表字段】添加表字段")
    //@AuditLog(source = AuditSourceType.DATACENTER, operationEvent = "添加表字段", resourceType = "元数据", operType = AuditOperationType.ADD)
    public Result<UUID> addColumnInfo(@RequestBody @Validated ColumnInfoSaveDto dto) {
        return Result.success(columnInfoService.addColumnInfo(dto));
    }
    
    /**
     * 修改表信息.
     *
     * @param dto 表信息
     * @return 修改结果
     */
    @PostMapping("/update")
    @Operation(summary = "【表字段】修改表字段")
    //@AuditLog(source = AuditSourceType.DATACENTER, operationEvent = "修改表字段", resourceType = "元数据", operType = AuditOperationType.UPDATE)
    public Result<Boolean> updateColumnInfo(@RequestBody @Validated ColumnInfoUpdateDto dto) {
        boolean updated = columnInfoService.updateColumnInfo(dto);
        return updated ? Result.success(true) : Result.failed(ErrorCodeEnum.SYSTEM_ERROR_B0001, "更新失败");
    }
    
    /**
     * 根据ID查询.
     *
     * @param id 表ID
     * @return 表信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "【表字段】根据ID查询表字段")
    public Result<ColumnInfoDto> getColumnInfo(@PathVariable("id") UUID id) {
        return Result.success(columnInfoService.getColumnInfo(id));
    }
    
    /**
     * 根据ID删除.
     *
     * @param id 表ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "【表字段】根据ID删除表字段")
    //@AuditLog(source = AuditSourceType.DATACENTER, operationEvent = "删除表字段", resourceType = "元数据", operType = AuditOperationType.DELETE)
    public Result<Boolean> deleteColumnInfo(@PathVariable("id") UUID id) {
        boolean deleted = columnInfoService.deleteColumnInfo(id);
        return deleted ? Result.success(true) : Result.failed(ErrorCodeEnum.SYSTEM_ERROR_B0001, "删除失败");
    }
}
