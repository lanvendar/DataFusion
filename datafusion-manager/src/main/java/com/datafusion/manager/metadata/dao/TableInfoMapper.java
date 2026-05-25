package com.datafusion.manager.metadata.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.manager.metadata.dto.DataSourceTableColumnDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.dto.TableInfoDto;
import com.datafusion.manager.metadata.dto.TableInfoQueryDto;
import com.datafusion.manager.metadata.po.TableInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-表Mapper.
 *
 * @author david
 * @version 3.6.4, 2024/8/22
 * @since 3.6.4, 2024/8/22
 */
@Mapper
public interface TableInfoMapper extends BaseMapper<TableInfoEntity> {
    
    /**
     * 分页查询.
     *
     * @param query 查询条件
     * @return 表信息
     */
    List<TableInfoDto> getPageTableList(@Param("query") PageQuery<TableInfoQueryDto> query);
    
    /**
     * 列表查询.
     *
     * @param query 查询条件
     * @return 表信息
     */
    List<TableInfoDto> getTableList(@Param("query") TableInfoQueryDto query);
    
    /**
     * 列表查询.
     * @param tables 查询条件
     * @param datasourceId 查询条件
     * @return 表信息
     */
    List<DataSourceTableColumnDto> getDataSourceTableColumns(@Param("datasourceId") UUID datasourceId, @Param("tables") List<String> tables);
    
    /**
     * 根据ID查询.
     *
     * @param id 表ID
     * @return 表信息
     */
    TableInfoDto getTableInfo(@Param("id") UUID id);
    
    /**
     * 根据数据源ID查询表数量.
     *
     * @param datasourceId 数据源ID
     * @return 数据源表数量
     */
    Long getTableCount(@Param("datasourceId") UUID datasourceId);
    
    /**
     * 根据数据源ID查询表数量.
     *
     * @param query 分页查询对象
     * @return 数据源表数量
     */
    int getPageTotalCount(@Param("query") PageQuery<TableInfoQueryDto> query);

    /**
     * 根据数据源id删除该ID下的所有表.
     *
     * @param query 数据源ID
     * @return 操作结果
     */
    Boolean deleteTablesByDataSourceId(@Param("query") RetrieveMetaDataDto query);
}
