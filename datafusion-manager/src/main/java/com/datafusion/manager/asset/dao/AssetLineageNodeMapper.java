package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.manager.asset.dto.AssetLineageNodeResourceDto;
import com.datafusion.manager.asset.dto.TableColumnRequestVo;
import com.datafusion.manager.asset.dto.TableColumnsNodeDto;
import com.datafusion.manager.asset.po.AssetLineageNodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 血缘节点Mapper.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Mapper
public interface AssetLineageNodeMapper extends BaseMapper<AssetLineageNodeEntity> {

    /**
     * 查询节点总数量.
     *
     * @param query 分页请求体
     * @return 返回总数量
     */
    int pageTableColumnsCount(@Param("req") PageQuery<TableColumnRequestVo> query);

    /**
     * 查询节点总数量.
     *
     * @param query 分页请求体
     * @return 返回符合条件的数据
     */
    List<TableColumnsNodeDto> pageTableColumnsList(@Param("req") PageQuery<TableColumnRequestVo> query);

    /**
     * 根据节点URN查询节点.
     *
     * @param nodeUrn 节点URN
     * @return 节点实体
     */
    AssetLineageNodeEntity selectByNodeUrn(@Param("nodeUrn") String nodeUrn);

    /**
     * 根据节点URN列表批量查询节点.
     *
     * @param nodeUrns 节点URN列表
     * @return 节点实体列表
     */
    List<AssetLineageNodeEntity> selectByNodeUrns(@Param("nodeUrns") List<String> nodeUrns);

    /**
     * 根据节点URN查询节点和资源.
     *
     * @param nodeUrn 节点URN
     * @return 节点和资源实体列表
     */
    List<AssetLineageNodeResourceDto> selectNodeResourceByNodeUrn(@Param("nodeUrn") String nodeUrn);

    /**
     * 根据ID列表批量删除节点.
     *
     * @param ids ID列表
     */
    void deleteByIds(@Param("ids") List<UUID> ids);

    /**
     * 根据数据源名称、表名称、列名称获取节点URN.
     *
     * @param datasourceName 数据源名称
     * @param tableNameColumnName      表字段名称
     * @return 节点URN
     */
    String getNodeUrn(@Param("datasourceName") String datasourceName, @Param("tableNameColumnName") String tableNameColumnName);
}
