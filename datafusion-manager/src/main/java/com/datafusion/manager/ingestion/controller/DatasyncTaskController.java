package com.datafusion.manager.ingestion.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.ingestion.dto.DatasyncTaskDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskQueryDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskSaveDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskUpdateDto;
import com.datafusion.manager.ingestion.dto.DataxJsonVo;
import com.datafusion.manager.ingestion.service.DatasyncTaskService;
import com.datafusion.manager.ingestion.service.DataxJsonService;
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
 * 数据同步任务管理.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@RestController
@RequestMapping("/api/ingestion/datasync-task")
@Tag(name = "【数据集成-数据同步任务】")
@RequiredArgsConstructor
public class DatasyncTaskController {

    /**
     * 数据同步任务服务.
     */
    private final DatasyncTaskService datasyncTaskService;

    /**
     * DataX JSON 生成服务.
     */
    private final DataxJsonService dataxJsonService;

    /**
     * 分页查询数据同步任务.
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询数据同步任务")
    public Result<PageResponse<DatasyncTaskDto>> page(
            @RequestBody PageQuery<DatasyncTaskQueryDto> query) {
        return Result.success(datasyncTaskService.pageDatasyncTask(query));
    }

    /**
     * 查询数据同步任务列表.
     *
     * @param query 查询条件
     * @return 列表结果
     */
    @PostMapping("/list")
    @Operation(summary = "查询数据同步任务列表")
    public Result<List<DatasyncTaskDto>> list(
            @RequestBody DatasyncTaskQueryDto query) {
        return Result.success(datasyncTaskService.listDatasyncTask(query));
    }

    /**
     * 查询数据同步任务详情(含字段映射).
     *
     * @param id 任务id
     * @return 任务详情
     */
    @GetMapping("/detail/{id}")
    @Operation(summary = "查询数据同步任务详情")
    public Result<DatasyncTaskDto> detail(@PathVariable UUID id) {
        return Result.success(datasyncTaskService.getDatasyncTaskById(id));
    }

    /**
     * 生成数据同步任务的 DataX JSON.
     *
     * @param taskId 任务ID
     * @return DataX JSON 响应
     */
    @GetMapping("/{taskId}/datax-json")
    @Operation(summary = "生成数据同步任务的 DataX JSON")
    public Result<DataxJsonVo> dataxJson(@PathVariable UUID taskId) {
        return Result.success(dataxJsonService.buildDataxJson(taskId));
    }

    /**
     * 新增数据同步任务.
     *
     * @param dto 新增参数
     * @return 新记录id
     */
    @PostMapping("/add")
    @Operation(summary = "新增数据同步任务")
    public Result<UUID> add(@RequestBody @Validated DatasyncTaskSaveDto dto) {
        return Result.success(datasyncTaskService.addDatasyncTask(dto));
    }

    /**
     * 修改数据同步任务(字段映射全量替换).
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    @PostMapping("/update")
    @Operation(summary = "修改数据同步任务")
    public Result<Boolean> update(@RequestBody @Validated DatasyncTaskUpdateDto dto) {
        return Result.success(datasyncTaskService.updateDatasyncTask(dto));
    }

    /**
     * 删除数据同步任务(主表+从表).
     *
     * @param id 任务id
     * @return 是否成功
     */
    @PostMapping("/delete/{id}")
    @Operation(summary = "删除数据同步任务")
    public Result<Boolean> delete(@PathVariable UUID id) {
        return Result.success(datasyncTaskService.deleteDatasyncTask(id));
    }

    /**
     * 发布数据同步任务.
     *
     * @param id 任务id
     * @return 是否成功
     */
    @PostMapping("/publish/{id}")
    @Operation(summary = "发布数据同步任务")
    public Result<Boolean> publish(@PathVariable UUID id) {
        return Result.success(datasyncTaskService.publishDatasyncTask(id));
    }

    /**
     * 下线数据同步任务.
     *
     * @param id 任务id
     * @return 是否成功
     */
    @PostMapping("/offline/{id}")
    @Operation(summary = "下线数据同步任务")
    public Result<Boolean> offline(@PathVariable UUID id) {
        return Result.success(datasyncTaskService.offlineDatasyncTask(id));
    }
}