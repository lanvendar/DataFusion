package com.datafusion.manager.system.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.system.dto.VariableInfoDto;
import com.datafusion.manager.system.dto.VariableInfoQueryDto;
import com.datafusion.manager.system.dto.VariableInfoSaveDto;
import com.datafusion.manager.system.dto.VariableInfoUpdateDto;
import com.datafusion.manager.system.service.VariableInfoService;
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
 * 系统-变量配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/30
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/system/variable")
@Tag(name = "【系统变量配置】")
@RequiredArgsConstructor
public class VariableController {

    /**
     * 变量信息Service.
     */
    private final VariableInfoService variableInfoService;

    /**
     * 分页查询变量.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询变量")
    public Result<PageResponse<VariableInfoDto>> page(@RequestBody PageQuery<VariableInfoQueryDto> query) {
        return Result.success(variableInfoService.pageVariableInfo(query));
    }

    /**
     * 查询变量列表.
     *
     * @param query 查询条件
     * @return 变量列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询变量列表")
    public Result<List<VariableInfoDto>> list(@RequestBody VariableInfoQueryDto query) {
        return Result.success(variableInfoService.listVariableInfo(query));
    }

    /**
     * 新增变量(仅CUSTOM类型).
     *
     * @param dto 新增参数
     * @return 变量ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增变量")
    public Result<UUID> add(@RequestBody @Validated VariableInfoSaveDto dto) {
        return Result.success(variableInfoService.addVariableInfo(dto));
    }

    /**
     * 修改变量(SYSTEM类型仅可改value).
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改变量")
    public Result<Boolean> update(@RequestBody @Validated VariableInfoUpdateDto dto) {
        return Result.success(variableInfoService.updateVariableInfo(dto));
    }

    /**
     * 根据ID查询变量.
     *
     * @param id 变量ID
     * @return 变量详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询变量")
    public Result<VariableInfoDto> getById(@PathVariable("id") UUID id) {
        return Result.success(variableInfoService.getVariableInfoById(id));
    }

    /**
     * 删除变量(仅CUSTOM类型).
     *
     * @param id 变量ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除变量")
    public Result<Boolean> delete(@PathVariable("id") UUID id) {
        return Result.success(variableInfoService.deleteVariableInfo(id));
    }
}
