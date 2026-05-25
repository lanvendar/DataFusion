package com.datafusion.manager.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.manager.asset.dto.AssetLineageNodeResourceVo;
import com.datafusion.manager.asset.dto.AssetNodeRequestVo;
import com.datafusion.manager.asset.dto.AssetNodeResourceDto;
import com.datafusion.manager.asset.dto.AssetNodeRichDto;
import com.datafusion.manager.asset.dto.DatasourceAssetRichDto;
import com.datafusion.manager.asset.dto.DatasourceAssetRichRequestVo;
import com.datafusion.manager.asset.dto.EdgeRequestVo;
import com.datafusion.manager.asset.dto.TableColumnRequestVo;
import com.datafusion.manager.asset.dto.TableColumnsNodeDto;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;

import java.util.List;
import java.util.Map;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
public interface AssetLineageNodeService extends IService<AssetLineageNodeEntity> {

    /**
     * 查询节点并分类展示.
     *
     * @param query 分页请求体
     * @return 返回分类节点列表
     */
    PageResponse<TableColumnsNodeDto> pageTableColumns(PageQuery<TableColumnRequestVo> query);

    /**
     * 根据 NodeUrn 批量保存或更新资源.
     * 如果 NodeUrn 已存在则更新，否则插入
     *
     * @param nodeList 资源列表
     * @return 是否保存成功
     */
    boolean batchSaveOrUpdateByNodeUrn(List<AssetLineageNodeEntity> nodeList);

    /**
     * 血缘资源列表查询（钻取查询）.
     *
     * @param request 查询请求
     * @return 数据源列表（包含表和字段信息）
     */
    List<DatasourceAssetRichDto> queryDatasourceAssetRich(DatasourceAssetRichRequestVo request);

    /**
     * 血缘节点查询.
     *
     * @param request 查询请求
     * @return 节点列表
     */
    List<AssetNodeRichDto> queryAssetNode(AssetNodeRequestVo request);

    /**
     * 根据node_urn血缘节点查询.
     *
     * @param request 查询请求
     * @return 节点列表
     */
    List<AssetNodeRichDto> queryAssetNodeByNodeUrn(AssetNodeRequestVo request);

    /**
     * 根据node_urn血缘资源查询.
     *
     * @param request 节点查询请求
     * @return 节点资源列表
     */
    List<AssetLineageNodeResourceVo> selectNodeResourceByNodeUrn(AssetNodeResourceDto request);

    /**
     * 获取节点子类型枚举下拉选项.
     *
     * @param searchType searchType=1时过滤COLUMN
     * @return 枚举选项列表
     */
    List<Map<String, String>> getNodeSubTypeSelect(Integer searchType);

    /**
     * 根据数据源名称、表名称、字段名称获取节点urn.
     *
     * @param request 数据源名称表名称字段名称
     * @return 节点urn
     */
    String getNodeUrn(EdgeRequestVo request);
}
