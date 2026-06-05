package com.datafusion.manager.system.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.system.dto.PluginConfigDto;
import com.datafusion.manager.system.dto.PluginConfigQueryDto;
import com.datafusion.manager.system.dto.PluginConfigSaveDto;
import com.datafusion.manager.system.dto.PluginConfigUpdateDto;
import com.datafusion.manager.system.service.PluginConfigService;
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
 * 系统-插件配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/system/plugin")
@Tag(name = "【系统插件配置】")
@RequiredArgsConstructor
public class PluginConfigController {

    /**
     * 插件配置Service.
     */
    private final PluginConfigService pluginConfigService;

    /**
     * 分页查询插件配置.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询插件配置")
    public Result<PageResponse<PluginConfigDto>> page(@RequestBody PageQuery<PluginConfigQueryDto> query) {
        return Result.success(pluginConfigService.pagePluginConfig(query));
    }

    /**
     * 查询插件配置列表.
     *
     * @param query 查询条件
     * @return 插件配置列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询插件配置列表")
    public Result<List<PluginConfigDto>> list(@RequestBody PluginConfigQueryDto query) {
        return Result.success(pluginConfigService.listPluginConfig(query));
    }

    /**
     * 新增插件配置.
     *
     * @param dto 新增参数
     * @return 插件配置ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增插件配置")
    public Result<UUID> add(@RequestBody @Validated PluginConfigSaveDto dto) {
        return Result.success(pluginConfigService.addPluginConfig(dto));
    }

    /**
     * 复制插件配置.
     *
     * @param dto 复制参数
     * @return 新插件配置ID
     */
    @PostMapping("/copy")
    @Operation(summary = "复制插件配置")
    public Result<UUID> copy(@RequestBody @Validated PluginConfigSaveDto dto) {
        return Result.success(pluginConfigService.copyPluginConfig(dto));
    }

    /**
     * 修改插件配置.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改插件配置")
    public Result<Boolean> update(@RequestBody @Validated PluginConfigUpdateDto dto) {
        return Result.success(pluginConfigService.updatePluginConfig(dto));
    }

    /**
     * 根据ID查询插件配置.
     *
     * @param id 插件配置ID
     * @return 插件配置详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询插件配置")
    public Result<PluginConfigDto> getById(@PathVariable("id") UUID id) {
        return Result.success(pluginConfigService.getPluginConfigById(id));
    }

    /**
     * 删除插件配置.
     *
     * @param id 插件配置ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除插件配置")
    public Result<Boolean> delete(@PathVariable("id") UUID id) {
        return Result.success(pluginConfigService.deletePluginConfig(id));
    }
}
