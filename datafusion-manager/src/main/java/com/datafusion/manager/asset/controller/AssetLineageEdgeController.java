package com.datafusion.manager.asset.controller;

import com.datafusion.common.web.dto.response.Result;
import com.datafusion.manager.asset.dto.EdgeNodeRequestVo;
import com.datafusion.manager.asset.dto.EdgeRequestVo;
import com.datafusion.manager.asset.dto.LineEdgeNodeVoV2;
import com.datafusion.manager.asset.dto.LineEdgeNodeVoV3;
import com.datafusion.manager.asset.service.AssetLineageEdgeService;
import com.datafusion.manager.asset.service.AssetLineageFilterTmpEdgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 血缘边关系controller.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/21
 * @since 2025/10/21
 */
@RestController
@RequestMapping("/api/asset/edge")
@RequiredArgsConstructor
@Tag(name = "【血缘关系】")
public class AssetLineageEdgeController {

    /**
     * 血缘关系处理service.
     */
    private final AssetLineageEdgeService edgeService;

    private final AssetLineageFilterTmpEdgeService assetLineageFilterTmpEdgeService;


    /**
     * 表级血缘关系查询（新版，单对象格式）.
     * @param req 血缘查询请求体
     * @return 资源明细结果
     */
    @PostMapping("/linkTable")
    @Operation(summary = "【血缘资源】查询血缘关系（表级血缘新版）")
    public Result<LineEdgeNodeVoV3> linkTable(@RequestBody EdgeNodeRequestVo req) {
        return Result.success(edgeService.linkTable(req));
    }

    /**
     * 业务血缘关系查询（COLUMN和METRIC特殊处理）.
     * - COLUMN节点: 不显示自身，显示父TABLE节点，字段信息冗余到subNodeDetails
     * - METRIC节点: 分裂为METRIC和API两个节点，产生METRIC→API的边
     * @param req 血缘查询请求体
     * @return 资源明细结果
     */
    @PostMapping("/linkBusiness")
    @Operation(summary = "【血缘资源】查询业务血缘关系（COLUMN/METRIC特殊处理）")
    public Result<LineEdgeNodeVoV2> linkBusiness(@RequestBody EdgeNodeRequestVo req) {
        return Result.success(edgeService.linkBusinessV3(req));
    }

    /**
     * 通过数据源名称、表名、字段名查询血缘关系.
     * @param req 请求实体
     * @return 血缘关系
     */
    @PostMapping("/getLineageByColumn")
    @Operation(summary = "【血缘资源】通过数据源名称、表名、字段名查询血缘关系")
    public Result<LineEdgeNodeVoV3> getLineageByColumn(@RequestBody EdgeRequestVo req) {
        return Result.success(edgeService.getLineageByColumn(req));
    }

    /**
     * 通过数据源名称、表名、字段名查询血缘关系.
     * @param req 请求实体
     * @return 血缘关系
     */
    @PostMapping("/linkTableFilterTmp")
    @Operation(summary = "【血缘资源】查询血缘关系去除tmp表（表级血缘新版）")
    public Result<LineEdgeNodeVoV3> linkTableTmpRecursive(@RequestBody EdgeNodeRequestVo req) {
        return Result.success(assetLineageFilterTmpEdgeService.linkTableTmpRecursive(req));
    }
}
