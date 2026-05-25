package com.datafusion.manager.asset.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.web.dto.response.Result;
import com.datafusion.manager.asset.dto.EtlReImportRequestVo;
import com.datafusion.manager.asset.dto.EtlSqlUpAndDownVo;
import com.datafusion.manager.asset.job.EtlGitLabJob;
import com.datafusion.manager.asset.job.ParseEtlSqlJob;
import com.datafusion.manager.asset.service.AssetLineageMetricsUnSyncService;
import com.datafusion.manager.asset.service.AssetResourceEtlService;
import com.datafusion.manager.asset.service.AssetResourceMenuService;
import com.datafusion.manager.asset.service.AssetResourceMetricsSyncService;
import com.datafusion.manager.asset.service.AssetResourceService;
import com.datafusion.manager.asset.service.SkywalkingTraceProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jodd.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 异步任务，操作相关.
 * @author xufeng
 * @version 1.0.0, 2026/4/8
 * @since 2026/4/8
 */
@RestController
@RequestMapping("/api/asset/system/operation")
@RequiredArgsConstructor
@Tag(name = "【任务操作相关】")
public class AssetOperationController {

    /**
     * API链路处理服务.
     */
    private final SkywalkingTraceProcessingService skywalkingTraceProcessingService;

    /**
     * 指标同步服务.
     */
    private final AssetResourceMetricsSyncService assetResourceMetricsSyncService;

    /**
     * 处理API资源的链路分析任务.
     *
     * @param serviceEnName 服务英文名称（可选，为空则处理所有服务）
     * @param startDate     资源创建开始日期（可选，默认为前一天）
     * @param endDate       资源创建结束日期（可选，默认为前一天）
     * @param sampleCount   每个耗时区间采样数量（可选，默认为5）
     * @param hourSplit     一天按小时切分的份数（可选，默认4，即每份6小时）
     * @param resetStatus   是否重置状态（可选，默认为false，为true时先重置指定serviceEnName的资源状态为IMPORT_SUCCESS）
     */
    @PostMapping("/api/sync")
    @Operation(summary = "【API资源】处理API资源的链路分析任务-定时任务")
    public void processApiResources(
            @RequestParam(required = false) String serviceEnName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "5") Integer sampleCount,
            @RequestParam(required = false, defaultValue = "4") Integer hourSplit,
            @RequestParam(required = false, defaultValue = "false") Boolean resetStatus) {
        skywalkingTraceProcessingService.processApiResources(serviceEnName, startDate, endDate, sampleCount, hourSplit, resetStatus);
    }

    /**
     * 从数据库同步指标数据到血缘数据库.
     *
     * @param afterDate      同步指定日期后的指标，默认为当天.
     * @param metricCode     指标编码（可选），为空则同步所有指标.
     * @param thirdMetricIds 外部指标ID列表（可选），用于批量同步.
     * @return boolean
     */
    @PostMapping("/metrics/sync")
    @Operation(summary = "【Metrics资源】从数据库同步指标数据到血缘数据库-定时任务")
    public Result<Boolean> syncMetricsFromDb(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate afterDate,
            @RequestParam(required = false) String metricCode,
            @RequestParam(required = false) List<String> thirdMetricIds) {
        if (afterDate == null && StringUtil.isBlank(metricCode) && CollectionUtil.isEmpty(thirdMetricIds)) {
            afterDate = LocalDate.now();
        }
        assetResourceMetricsSyncService.syncMetricsFromDb(afterDate, metricCode, thirdMetricIds);
        return Result.success(true);
    }

    /**
     * 菜单处理service.
     */
    private final AssetResourceMenuService assetResourceMenuService;

    /**
     * 从源数据库同步菜单数据到临时表.
     *
     * @return boolean
     */
    @PostMapping("/menu/tmp")
    @Operation(summary = "【Menu资源】从源数据库同步菜单数据到临时表-定时任务")
    public Result<Boolean> syncMenuToTmpDb() {
        assetResourceMenuService.syncMenuToTmpDb();
        return Result.success(true);
    }

    /**
     * 从源数据库同步菜单数据到临时表.
     *
     * @return boolean
     */
    @PostMapping("/menu/sync")
    @Operation(summary = "【Menu资源】解析-定时任务")
    public Result<Boolean> syncMenu() {
        assetResourceMenuService.syncMenu();
        return Result.success(true);
    }

    /**
     * ETL资源服务.
     */
    private final AssetResourceEtlService assetResourceEtlService;

    /**
     * 从gitlab 拉取etl资源并解析资源到resource库.
     */
    private final EtlGitLabJob etlGitLabJob;

    /**
     * gitlab etl资源.
     */
    private final ParseEtlSqlJob parseEtlSqlJob;

    /**
     * 重新录入gitlab资源.
     *
     * @param reImportVo 分页请求体
     * @return 返回分页边关系
     */
    @PostMapping("/etl/gitlab/reImportEtl")
    @Operation(summary = "【ETL资源】 重新录入gitlab资源,后端内部调试使用")
    public Result<Boolean> reImportEtl(@RequestBody EtlReImportRequestVo reImportVo) {
        return Result.success(assetResourceEtlService.reImportEtl(reImportVo));
    }

    /**
     * 从数据库中直接解析status=3的ETL资源.
     *
     * @param resourceName 资源名称，可选
     * @return 状态码
     */
    @GetMapping("/etl/parseEtl")
    @Operation(summary = "测试使用【ETL资源】 从数据库中直接解析status=3的ETL资源")
    public Result<Boolean> parseEtl(@RequestParam(required = false) String resourceName) {
        parseEtlSqlJob.parseEtl(resourceName);
        return Result.success(true);
    }

    /**
     * 从gitlab拉取ETL资源.
     *
     * @return 状态码
     */
    @PostMapping("/etl/pullEtlGitLab")
    @Operation(summary = "测试使用【ETL资源】 从gitlab拉取ETL资源到本地")
    public Result<Boolean> pullEtlGitLab(@RequestBody EtlSqlUpAndDownVo req) {
        etlGitLabJob.etlPull(req);
        return Result.success(true);
    }


    @PostMapping("/etl/initEtlSql")
    @Operation(summary = "【ETL资源】 初始化ETL资源")
    public Result<Boolean> initEtlSql(@RequestBody EtlSqlUpAndDownVo req) {
        etlGitLabJob.initEtlSql(req);
        return Result.success(true);
    }

    /**
     * 资源处理service.
     */
    private final AssetResourceService assetResourceService;

    /**
     * 自动导入 ParseSuccess 状态资源的血缘.
     *
     * @param resourceType 资源类型（可选，不传则查所有类型）
     * @return boolean
     */
    @PostMapping("/lineage/autoImport")
    @Operation(summary = "【血缘资源】自动导入ParseSuccess状态资源的血缘")
    public Result<Boolean> importLineageFromParseSuccess(
            @RequestParam(required = false) String resourceType) {
        assetResourceService.autoImportLingage(resourceType);
        return Result.success(true);
    }

    /**
     * 统一指标同步服务.
     */
    private final AssetLineageMetricsUnSyncService assetLineageMetricsUnSyncService;

    /**
     * 同步统一指标数据到资源表.
     * 从 tag_access_stats 获取 API 信息，通过 uri 解析 physical_level 和 timeliness，
     * 与 tag_info 匹配后同步到 asset_lineage_metrics_un_tmp 表，再写入 asset_lineage_resource 表.
     *
     * @return 是否成功
     */
    @PostMapping("/metrics/un/sync")
    @Operation(summary = "【统一指标】同步统一指标数据到资源表")
    public Result<Boolean> syncUnifiedMetrics() {
        assetLineageMetricsUnSyncService.syncUnifiedMetrics();
        return Result.success(true);
    }

}
