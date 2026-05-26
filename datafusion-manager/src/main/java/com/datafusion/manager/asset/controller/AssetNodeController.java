package com.datafusion.manager.asset.controller;

import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.asset.dto.AssetLineageNodeResourceVo;
import com.datafusion.manager.asset.dto.AssetNodeRequestVo;
import com.datafusion.manager.asset.dto.AssetNodeResourceDto;
import com.datafusion.manager.asset.dto.AssetNodeRichDto;
import com.datafusion.manager.asset.dto.DatasourceAssetRichDto;
import com.datafusion.manager.asset.dto.DatasourceAssetRichRequestVo;
import com.datafusion.manager.asset.dto.TableColumnRequestVo;
import com.datafusion.manager.asset.dto.TableColumnsNodeDto;
import com.datafusion.manager.asset.service.AssetLineageNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@RestController
@RequestMapping("/api/asset/node")
@RequiredArgsConstructor
@Tag(name = "【节点】")
public class AssetNodeController {

    /**
     * 资源处理service.
     */
    private final AssetLineageNodeService nodeService;

    /**
     * 表级血缘,左侧库表节点查询.
     *
     * @param query 分页请求体
     * @return 返回分类节点列表
     */
    @PostMapping("/pageTableColumns")
    @Operation(summary = "【节点】获取表字段节点信息")
    public Result<PageResponse<TableColumnsNodeDto>> pageTableColumns(@RequestBody PageQuery<TableColumnRequestVo> query) {
        return Result.success(nodeService.pageTableColumns(query));
    }

    /**
     * 血缘资源列表查询（钻取查询）.
     *
     * @param request 查询请求
     * @return 数据源列表（包含表和字段信息）
     */
    @PostMapping("/datasourceAssetRich")
    @Operation(summary = "【节点】血缘资源列表查询（钻取查询）")
    public Result<List<DatasourceAssetRichDto>> datasourceAssetRich(@RequestBody DatasourceAssetRichRequestVo request) {
        return Result.success(nodeService.queryDatasourceAssetRich(request));
    }

    /**
     * 血缘节点查询.
     *
     * @param request 查询请求
     * @return 节点列表
     */
    @PostMapping("/assetNode")
    @Operation(summary = "【节点】血缘节点查询")
    public Result<List<AssetNodeRichDto>> assetNode(@RequestBody AssetNodeRequestVo request) {
        return Result.success(nodeService.queryAssetNode(request));
    }

    /**
     * 根据node_urn进行血缘节点查询.
     *
     * @param request 查询请求
     * @return 节点列表
     */
    @PostMapping("/assetNodeByNodeUrn")
    @Operation(summary = "【节点】根据node_urn进行血缘节点查询")
    public Result<List<AssetNodeRichDto>> assetNodeByNodeUrn(@RequestBody AssetNodeRequestVo request) {
        return Result.success(nodeService.queryAssetNodeByNodeUrn(request));
    }

    /**
     * 根据node_urn进行血缘节点资源查询.
     *
     * @param request 节点信息
     * @return 资源列表
     */
    @PostMapping("/NodeResourceByNodeUrn")
    @Operation(summary = "【节点】根据node_urn进行节点资源查询")
    public Result<List<AssetLineageNodeResourceVo>> selectNodeResourceByNodeUrn(@RequestBody AssetNodeResourceDto request) {
        return Result.success(nodeService.selectNodeResourceByNodeUrn(request));
    }

    /**
     * 获取节点子类型枚举下拉选项.
     *
     * @param searchType 请求参数，searchType=1时过滤COLUMN
     * @return 枚举选项列表
     */
    @GetMapping("/nodeSubTypeSelect")
    @Operation(summary = "【节点】获取节点子类型枚举下拉选项")
    public Result<List<Map<String, String>>> nodeSubTypeSelect(@RequestParam(name = "searchType", required = false) Integer searchType) {

        return Result.success(nodeService.getNodeSubTypeSelect(searchType));
    }
}
