package com.datafusion.manager.metadata.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoQueryDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-数据库Mapper..
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
@Mapper
public interface DataSourceInfoMapper extends BaseMapper<DataSourceInfoEntity> {
    
    /**
     * 分页查询数据库.
     *
     * @param query 查询参数
     * @return 查询结果
     */
    List<DataSourceInfoDto> pageList(@Param("query") PageQuery<DataSourceInfoQueryDto> query);
    
    /**
     * 分页查询数据库.
     *
     * @param query 查询参数
     * @return 返回总数量
     */
    Integer pageCount(@Param("query") PageQuery<DataSourceInfoQueryDto> query);
    
    /**
     * 返回所有数据源.
     *
     * @param query 查询参数
     * @return 查询结果
     */
    List<DataSourceInfoDto> list(@Param("query") DataSourceInfoQueryDto query);
    
    /**
     * 根据jdbcUrl查询数据库.
     *
     * @param id           数据源id
     * @param jdbcUrl      jdbcUrl
     * @param databaseName databaseName
     * @param schemaName   schemaName
     * @return jdbcUrl查询数据库数量
     */
    int countByJdbcUrl(@Param("id") UUID id, @Param("jdbcUrl") String jdbcUrl, @Param("databaseName") String databaseName,
                       @Param("schemaName") String schemaName);
    
    /**
     * 根据表id查询数据源.
     *
     * @param tableId 表id
     * @return 数据源信息
     */
    DataSourceInfoDto getDataSourceByTableId(UUID tableId);
    
    /**
     * 根据数据表ID集合查询数据源.
     *
     * @param tableIds 数据表ID集合
     * @return 数据源列表
     */
    List<DataSourceInfoEntity> listByTableIds(@Param("tableIds") List<UUID> tableIds);
}
