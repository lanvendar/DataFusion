package com.datafusion.manager.scheduler.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.scheduler.dto.DagSaveDto;
import com.datafusion.manager.scheduler.dto.FlowDagDto;
import com.datafusion.manager.scheduler.dto.FlowInfoDto;
import com.datafusion.manager.scheduler.dto.FlowInfoQueryDto;
import com.datafusion.manager.scheduler.dto.FlowInfoSaveDto;
import com.datafusion.manager.scheduler.dto.FlowInfoUpdateDto;
import com.datafusion.manager.scheduler.dto.FlowScheduleDto;
import com.datafusion.manager.scheduler.service.FlowInfoService;
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

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/3/31
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/scheduler/flow")
@Tag(name = "【调度流程配置】")
@RequiredArgsConstructor
public class FlowController {

    /**
     * 流程信息Service.
     */
    private final FlowInfoService flowInfoService;

    // --- 基础 CRUD ---

    /**
     * 分页查询流程.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询流程")
    public Result<PageResponse<FlowInfoDto>> page(@RequestBody PageQuery<FlowInfoQueryDto> query) {
        return Result.success(flowInfoService.pageFlowInfo(query));
    }

    /**
     * 查询流程列表.
     *
     * @param query 查询条件
     * @return 流程列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询流程列表")
    public Result<List<FlowInfoDto>> list(@RequestBody FlowInfoQueryDto query) {
        return Result.success(flowInfoService.listFlowInfo(query));
    }

    /**
     * 查询流程详情.
     *
     * @param id 流程ID
     * @return 流程详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "查询流程详情")
    public Result<FlowInfoDto> detail(@PathVariable UUID id) {
        return Result.success(flowInfoService.getFlowInfoById(id));
    }

    /**
     * 新增流程.
     *
     * @param dto 新增参数
     * @return 流程ID
     */
    @PostMapping("/add")
    @Operation(summary = "新增流程")
    public Result<UUID> add(@RequestBody @Validated FlowInfoSaveDto dto) {
        return Result.success(flowInfoService.addFlowInfo(dto));
    }

    /**
     * 修改流程.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改流程")
    public Result<Boolean> update(@RequestBody @Validated FlowInfoUpdateDto dto) {
        return Result.success(flowInfoService.updateFlowInfo(dto));
    }

    /**
     * 删除流程.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除流程")
    public Result<Boolean> delete(@PathVariable UUID id) {
        return Result.success(flowInfoService.deleteFlowInfo(id));
    }

    // --- DAG 编排 ---

    /**
     * 查询流程DAG.
     *
     * @param id 流程ID
     * @return DAG数据
     */
    @GetMapping("/dag/detail/{id}")
    @Operation(summary = "查询流程DAG")
    public Result<FlowDagDto> getDag(@PathVariable UUID id) {
        return Result.success(flowInfoService.getDag(id));
    }

    /**
     * 保存流程DAG.
     *
     * @param dto DAG保存参数
     * @return 是否成功
     */
    @PostMapping("/dag/save")
    @Operation(summary = "保存流程DAG")
    public Result<Boolean> saveDag(@RequestBody @Validated DagSaveDto dto) {
        return Result.success(flowInfoService.saveDag(dto));
    }

    // --- 发布与调度 ---

    /**
     * 发布流程.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    @PostMapping("/publish/{id}")
    @Operation(summary = "发布流程")
    public Result<Boolean> publish(@PathVariable UUID id) {
        return Result.success(flowInfoService.publish(id));
    }

    /**
     * 取消发布.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    @PostMapping("/unpublish/{id}")
    @Operation(summary = "取消发布")
    public Result<Boolean> unpublish(@PathVariable UUID id) {
        return Result.success(flowInfoService.unpublish(id));
    }

    /**
     * 开始调度.
     *
     * @param dto 调度配置
     * @return 是否成功
     */
    @PostMapping("/enable")
    @Operation(summary = "开始调度")
    public Result<Boolean> enable(@RequestBody @Validated FlowScheduleDto dto) {
        return Result.success(flowInfoService.enable(dto));
    }

    /**
     * 取消调度.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    @PostMapping("/disable/{id}")
    @Operation(summary = "取消调度")
    public Result<Boolean> disable(@PathVariable UUID id) {
        return Result.success(flowInfoService.disable(id));
    }
}
