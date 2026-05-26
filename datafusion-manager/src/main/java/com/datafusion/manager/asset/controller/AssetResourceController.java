package com.datafusion.manager.asset.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.asset.dto.AssetEdgeNodeDto;
import com.datafusion.manager.asset.dto.AssetNodeDto;
import com.datafusion.manager.asset.dto.ResourceDto;
import com.datafusion.manager.asset.dto.ResourcePageDto;
import com.datafusion.manager.asset.dto.ResourceVo;
import com.datafusion.manager.asset.dto.request.ApiBatchResourceReq;
import com.datafusion.manager.asset.dto.request.ApiResourceReq;
import com.datafusion.manager.asset.dto.request.BatchOperateReq;
import com.datafusion.manager.asset.dto.request.EtlResourceReq;
import com.datafusion.manager.asset.dto.request.MenuResourceReq;
import com.datafusion.manager.asset.dto.request.MetricsResourceReq;
import com.datafusion.manager.asset.dto.request.MetricsUpdateResourceReq;
import com.datafusion.manager.asset.dto.request.TableResourceReq;
import com.datafusion.manager.asset.dto.response.ApiResourceResp;
import com.datafusion.manager.asset.dto.response.AppResp;
import com.datafusion.manager.asset.dto.response.MenuResourceResp;
import com.datafusion.manager.asset.dto.response.MenuResp;
import com.datafusion.manager.asset.dto.response.MetricsResourceResp;
import com.datafusion.manager.asset.dto.response.TableResourceResp;
import com.datafusion.manager.asset.enums.BusinessDomainEnum;
import com.datafusion.manager.asset.enums.EnvEnum;
import com.datafusion.manager.asset.enums.OrganizationEnum;
import com.datafusion.manager.asset.enums.ResourceStatusEnum;
import com.datafusion.manager.asset.enums.ResourceTypeEnum;
import com.datafusion.manager.asset.enums.ServiceTypeEnum;
import com.datafusion.manager.asset.job.ParseEtlSqlJob;
import com.datafusion.manager.asset.po.AssetLineageResourceEntity;
import com.datafusion.manager.asset.service.AssetResourceApiService;
import com.datafusion.manager.asset.service.AssetResourceEtlService;
import com.datafusion.manager.asset.service.AssetResourceMenuService;
import com.datafusion.manager.asset.service.AssetResourceMetricsService;
import com.datafusion.manager.asset.service.AssetResourceService;
import com.datafusion.manager.asset.service.AssetResourceTableService;
import com.datafusion.manager.metadata.dto.KeyValueDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 血缘资源通用操作 Controller.
 *
 * <p>
 * 包含资源CRUD、录入血缘、资源分页查询、节点/边查询等通用操作。
 * 特定资源类型的操作已拆分到独立的Controller中。
 * </p>
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/28
 * @since 2025/9/28
 */
@Slf4j
@RestController
@RequestMapping("/api/asset/resource")
@RequiredArgsConstructor
@Tag(name = "【血缘资源】")
public class AssetResourceController {

    /**
     * 资源处理service.
     */
    private final AssetResourceService assetResourceService;


    /**
     * 删除资源.
     *
     * @param id 数据源ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "根据ID删除资源")
    public Result<Boolean> deleteAssetResource(@PathVariable("id") UUID id) {
        return Result.success(assetResourceService.deleteAssetResource(id));
    }

    /**
     * 批量删除资源.
     *
     * @param batchOperateReq 批量操作请求体
     * @return 删除结果
     */
    @PostMapping("/delete/batch")
    @Operation(summary = "批量删除资源")
    public Result<Boolean> batchDeleteAssetResource(@RequestBody BatchOperateReq batchOperateReq) {
        return Result.success(assetResourceService.batchDeleteAssetResource(batchOperateReq.getIds()));
    }

    /**
     * 录入血缘.
     *
     * @param id 资源Id
     * @return 录入结果
     */
    @PostMapping("/enter/lineage/{id}")
    @Operation(summary = "【血缘资源】录入血缘")
    public Result<Boolean> enterLineage(@PathVariable("id") UUID id) {
        assetResourceService.importLineage(List.of(id));
        return Result.success(true);
    }

    /**
     * 批量录入血缘.
     *
     * @param batchOperateReq 批量操作请求体
     * @return boolean
     */
    @PostMapping("/enter/lineage/batch")
    @Operation(summary = "【血缘资源】批量录入血缘")
    public Result<Boolean> batchEnterLineage(@RequestBody BatchOperateReq batchOperateReq) {
        assetResourceService.importLineage(batchOperateReq.getIds());
        return Result.success(true);
    }



    /**
     * 血缘资源查询List.
     *
     * @param query 查询所有资源
     * @return 资源明细结果
     */
    @PostMapping("/api/list")
    @Operation(summary = "【血缘资源】查询所有资源")
    public Result<List<ResourceDto>> resourceList(@RequestBody ResourceDto query) {
        return Result.success(assetResourceService.resourceList(query));
    }

    /**
     * 所有资源类型.
     *
     * @return 返回所有资源类型
     */
    @GetMapping("/resourceTypeList")
    @Operation(summary = "【血缘资源】查询所有资源类型")
    public Result<List<KeyValueDto>> resourceTypeList() {
        List<KeyValueDto> keyValueDtos = Arrays.stream(ResourceTypeEnum.values()).map(
                x -> new KeyValueDto(x.getResouceType(), x.getResouceTypeDesc())).collect(Collectors.toList());
        return Result.success(keyValueDtos);
    }

    /**
     * 资源状态类型.
     *
     * @return 返回所有状态类型
     */
    @GetMapping("/resourceStatusList")
    @Operation(summary = "【血缘资源】查询资源状态类型")
    public Result<List<KeyValueDto>> resourceStatusList() {
        List<KeyValueDto> keyValueDtos = Arrays.stream(ResourceStatusEnum.values()).map(
                x -> new KeyValueDto(String.valueOf(x.getStatus()), x.getStatusDesc())).collect(Collectors.toList());
        return Result.success(keyValueDtos);
    }

    /**
     * 组织列表.
     *
     * @return 返回所有组织
     */
    @GetMapping("/organizationList")
    @Operation(summary = "【血缘资源】查询组织列表")
    public Result<List<KeyValueDto>> organizationList() {
        List<KeyValueDto> keyValueDtos = Arrays.stream(OrganizationEnum.values()).map(
                x -> new KeyValueDto(x.getName(), x.getDesc())).collect(Collectors.toList());
        return Result.success(keyValueDtos);
    }

    /**
     * 业务域列表.
     *
     * @return 返回所有业务域
     */
    @GetMapping("/businessDomainList")
    @Operation(summary = "【血缘资源】查询业务域列表")
    public Result<List<KeyValueDto>> businessDomainList() {
        List<KeyValueDto> keyValueDtos = Arrays.stream(BusinessDomainEnum.values()).map(
                x -> new KeyValueDto(x.getName(), x.getDesc())).collect(Collectors.toList());
        return Result.success(keyValueDtos);
    }

    /**
     * 环境列表.
     *
     * @return 返回所有环境
     */
    @GetMapping("/envList")
    @Operation(summary = "【血缘资源】查询环境列表")
    public Result<List<KeyValueDto>> envList() {
        List<KeyValueDto> keyValueDtos = Arrays.stream(EnvEnum.values()).map(
                x -> new KeyValueDto(x.getName(), x.getDesc())).collect(Collectors.toList());
        return Result.success(keyValueDtos);
    }

    /**
     * 服务类型列表.
     *
     * @return 返回所有服务类型
     */
    @GetMapping("/serviceTypeList")
    @Operation(summary = "【血缘资源】查询服务类型列表")
    public Result<List<KeyValueDto>> serviceTypeList() {
        List<KeyValueDto> keyValueDtos = Arrays.stream(ServiceTypeEnum.values()).map(
                x -> new KeyValueDto(x.getName(), x.getDesc())).collect(Collectors.toList());
        return Result.success(keyValueDtos);
    }

    /**
     * 资源列表.
     *
     * @param query 查询资源分页列表
     * @return 查询所有资源列表
     */
    @PostMapping("/pageResouces")
    @Operation(summary = "【血缘资源】查询资源分页")
    public Result<PageResponse<ResourceVo>> pageResouces(@RequestBody PageQuery<ResourcePageDto> query) {

        return Result.success(assetResourceService.pageResouces(query));
    }

    /**
     * 根据资源Id查询节点信息.
     *
     * @param query 分页请求体
     * @return 返回所有资源
     */
    @PostMapping("/pageNodesByResourceId")
    @Operation(summary = "【血缘资源】 根据资源Id查询node节点")
    public Result<PageResponse<AssetNodeDto>> pageNodesByResourceId(@RequestBody PageQuery<UUID> query) {
        return Result.success(assetResourceService.pageNodesByResourceId(query));
    }

    /**
     * 根据资源Id查询节点信息.
     *
     * @param query 分页请求体
     * @return 返回分页边关系
     */
    @PostMapping("/pageEdgesByResourceId")
    @Operation(summary = "【血缘资源】 根据资源Id查询node节点")
    public Result<PageResponse<AssetEdgeNodeDto>> pageEdgesByResourceId(@RequestBody PageQuery<UUID> query) {
        return Result.success(assetResourceService.pageEdgesByResourceId(query));
    }

    // ************************************************************************* API ******************************************/

    /**
     * API资源服务.
     */
    private final AssetResourceApiService assetResourceApiService;

    /**
     * api资源导入.
     *
     * @param apiResourceReq apiResourceReq
     * @return boolean
     */
    @PostMapping("/api/import")
    @Operation(summary = "【API资源】接口资源单个添加")
    public Result<Boolean> addApiResource(@RequestBody @Validated ApiResourceReq apiResourceReq) {
        return Result.success(assetResourceApiService.saveOrUpdateApiResource(null, apiResourceReq));
    }

    /**
     * api资源导入.
     *
     * @param apiResourceReq apiResourceReq
     * @param resourceId     资源Id
     * @return boolean
     */
    @PutMapping("/api/{resourceId}")
    @Operation(summary = "【API资源】接口资源单个更新")
    public Result<Boolean> updateApiResource(@PathVariable("resourceId") UUID resourceId, @RequestBody @Validated ApiResourceReq apiResourceReq) {
        return Result.success(assetResourceApiService.saveOrUpdateApiResource(resourceId, apiResourceReq));
    }

    /**
     * api资源批量导入.
     *
     * @param file                上传的openapi json 文件
     * @param apiBatchResourceReq apiBatchResourceReq
     * @return boolean
     */
    @PostMapping("/api/import/batch")
    @Operation(summary = "【API资源】接口资源批量导入")
    public Result<Boolean> apiResourceImportBatch(@RequestPart("jsonFile") MultipartFile file,
            @RequestPart(value = "metadata", required = false) ApiBatchResourceReq apiBatchResourceReq) {
        return Result.success(assetResourceApiService.apiResourceBatchImport(file, apiBatchResourceReq));
    }

    /**
     * api资源查询.
     *
     * @param resourceId resourceId
     * @return 分页查询结果
     */
    @GetMapping("/api/{resourceId}")
    @Operation(summary = "【API资源】api资源查询")
    public Result<ApiResourceResp> apiResourceQuery(@PathVariable("resourceId") UUID resourceId) {
        return Result.success(assetResourceApiService.apiResourceQuery(resourceId));
    }

    // ************************************************************************* Metrics ******************************************/

    /**
     * 指标服务.
     */
    private final AssetResourceMetricsService assetResourceMetricsService;

    /**
     * api资源添加指标.
     *
     * @param metricsResourceReq metricsResourceReq
     * @return boolean
     */
    @PostMapping("/metrics/import")
    @Operation(summary = "【Metrics资源】新增指标")
    public Result<Boolean> importApiResourceMetrics(@RequestBody @Validated MetricsResourceReq metricsResourceReq) {
        assetResourceMetricsService.addMetrics(metricsResourceReq);
        return Result.success(true);
    }

    /**
     * 查询指标资源.
     *
     * @param resourceId 资源ID
     * @return boolean
     */
    @GetMapping("/metrics/{resourceId}")
    @Operation(summary = "【Metrics资源】查询指标")
    public Result<MetricsResourceResp> getApiResourceMetrics(@PathVariable("resourceId") UUID resourceId) {
        return Result.success(assetResourceMetricsService.getMetrics(resourceId));
    }

    /**
     * 更新资源.
     *
     * @param resourceId         资源ID
     * @param metricsResourceReq metricsResourceReq
     * @return boolean
     */
    @PutMapping("/{resourceId}")
    @Operation(summary = "【Metrics资源】更新指标")
    public Result<Boolean> updateApiResourceMetrics(@PathVariable("resourceId") UUID resourceId,
            @RequestBody @Validated MetricsUpdateResourceReq metricsResourceReq) {
        assetResourceMetricsService.updateMetrics(resourceId, metricsResourceReq);
        return Result.success(true);
    }

    // ************************************************************************* Menu ******************************************/

    /**
     * 菜单处理service.
     */
    private final AssetResourceMenuService assetResourceMenuService;

    /**
     * 查询应用名称列表.
     *
     * @return 应用名称列表
     */
    @GetMapping("/menu/apps")
    @Operation(summary = "【Menu资源】查询应用名称列表")
    public Result<List<AppResp>> appList() {
        return Result.success(assetResourceMenuService.getAppNameList());
    }

    /**
     * 根据应用名称查询菜单树.
     *
     * @param appCode 应用code
     * @return 菜单树列表
     */
    @GetMapping("/menu/tree")
    @Operation(summary = "【Menu资源】根据应用名称查询菜单树")
    public Result<List<MenuResp>> menuTree(@RequestParam("appCode") String appCode) {
        return Result.success(assetResourceMenuService.getMenuTreeByAppCode(appCode));
    }

    /**
     * 菜单资源导入.
     *
     * @param menuResourceReq 菜单资源导入请求对象
     * @return boolean
     */
    @PostMapping("/menu/import")
    @Operation(summary = "【Menu资源】菜单资源导入")
    public Result<Boolean> menuResourceImport(@RequestBody @Validated MenuResourceReq menuResourceReq) {
        assetResourceMenuService.saveOrUpdateMenu(null, menuResourceReq);
        return Result.success(true);
    }

    /**
     * 查询菜单资源.
     *
     * @param resourceId 资源ID
     * @return 菜单资源
     */
    @GetMapping("/menu/{resourceId}")
    @Operation(summary = "【Menu资源】查询菜单资源")
    public Result<MenuResourceResp> getMenu(@PathVariable("resourceId") UUID resourceId) {
        return Result.success(assetResourceMenuService.getMenu(resourceId));
    }

    /**
     * 更新资源.
     *
     * @param resourceId        资源ID
     * @param updateResourceReq 更新请求体
     * @return boolean
     */
    @PutMapping("/menu/{resourceId}")
    @Operation(summary = "【Menu资源】更新菜单资源")
    public Result<Boolean> updateMenu(@PathVariable("resourceId") UUID resourceId,
            @RequestBody @Validated MenuResourceReq updateResourceReq) {
        assetResourceMenuService.saveOrUpdateMenu(resourceId, updateResourceReq);
        return Result.success(true);
    }

    // ************************************************************************* ETL ******************************************/

    /**
     * ETL资源服务.
     */
    private final AssetResourceEtlService assetResourceEtlService;

    /**
     * gitlab etl资源.
     */
    private final ParseEtlSqlJob parseEtlSqlJob;

    /**
     * etl资源导入.
     *
     * @param etlResourceReq etl资源导入请求体
     * @return 返回是否导入成功
     */
    @PostMapping("/etl/import")
    @Operation(summary = "【ETL资源】etl资源导入")
    public Result<Boolean> etlResourceImport(@RequestBody @Validated EtlResourceReq etlResourceReq) {
        return Result.success(assetResourceEtlService.etlResourceImport(etlResourceReq));
    }

    /**
     * etl资源血缘解析.
     *
     * @param id etl资源id
     * @return 返回是否导入成功
     */
    @PostMapping("/etl/parse/{id}")
    @Operation(summary = "【ETL资源】etl资源血缘解析")
    public Result<Boolean> etlResourceParse(@PathVariable("id") UUID id) {
        return Result.success(parseEtlSqlJob.parseEtlById(id));
    }

    /**
     * 查询ETL资源.
     *
     * @param resourceId 资源ID
     * @return ETL资源
     */
    @GetMapping("/etl/{resourceId}")
    @Operation(summary = "【ETL资源】查询ETL资源")
    public Result<AssetLineageResourceEntity> getEtl(@PathVariable("resourceId") UUID resourceId) {
        return Result.success(assetResourceEtlService.getById(resourceId));
    }

    /**
     * 更新ETL资源.
     *
     * @param resourceId     资源ID
     * @param etlResourceReq 更新请求体
     * @return boolean
     */
    @PutMapping("/etl/{resourceId}")
    @Operation(summary = "【ETL资源】更新ETL资源")
    public Result<Boolean> updateEtl(@PathVariable("resourceId") UUID resourceId,
            @RequestBody @Validated EtlResourceReq etlResourceReq) {
        assetResourceEtlService.saveOrUpdateEtl(resourceId, etlResourceReq);
        return Result.success(true);
    }

    // ************************************************************************* Table ******************************************/

    /**
     * 库表资源服务.
     */
    private final AssetResourceTableService databaseTableResourceService;

    /**
     * 库表资源导入.
     *
     * @param tableResourceReq 库表资源导入请求体
     * @return 返回是否导入成功
     */
    @PostMapping("/table/import")
    @Operation(summary = "【Table资源】库表资源导入")
    public Result<Boolean> dbTableImport(@RequestBody @Validated TableResourceReq tableResourceReq) {
        return Result.success(databaseTableResourceService.tableImport(tableResourceReq));
    }

    /**
     * 查询库表资源.
     *
     * @param resourceId 资源ID
     * @return 库表资源
     */
    @GetMapping("/table/{resourceId}")
    @Operation(summary = "【Table资源】查询库表资源")
    public Result<TableResourceResp> getTable(@PathVariable("resourceId") UUID resourceId) {
        return Result.success(databaseTableResourceService.getTable(resourceId));
    }

    /**
     * 更新库表资源.
     *
     * @param resourceId        资源ID
     * @param tableResourceReq 更新请求体
     * @return boolean
     */
    @PutMapping("/table/{resourceId}")
    @Operation(summary = "【Table资源】更新库表资源")
    public Result<Boolean> updateTable(@PathVariable("resourceId") UUID resourceId,
            @RequestBody @Validated TableResourceReq tableResourceReq) {
        databaseTableResourceService.saveOrUpdateTable(resourceId, tableResourceReq);
        return Result.success(true);
    }

}
