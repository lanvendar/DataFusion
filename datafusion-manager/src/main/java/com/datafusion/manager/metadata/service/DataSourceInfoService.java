package com.datafusion.manager.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.manager.metadata.dto.DataSourceInfoCopyDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoQueryDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoSaveDto;
import com.datafusion.manager.metadata.dto.DataSourceInfoUpdateDto;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-数据库服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
public interface DataSourceInfoService extends IService<DataSourceInfoEntity> {


    /**
     * 检查并获取数据源.
     *
     * @param id 数据源ID
     * @return 数据源信息
     */
    DataSourceInfoEntity getWithCheckNonNull(UUID id);

    /**
     * 分页查询数据库信息.
     *
     * @param query 查询条件
     * @return 数据库信息
     */
    PageResponse<DataSourceInfoDto> pageDataSource(PageQuery<DataSourceInfoQueryDto> query);

    /**
     * 查询数据库.
     *
     * @param query 查询参数
     * @return 查询结果
     */
    List<DataSourceInfoDto> listDataSource(DataSourceInfoQueryDto query);

    /**
     * 根据数据表ID集合查询数据源.
     *
     * @param tableIds 数据表ID集合
     * @return 数据源列表
     */
    List<DataSourceInfoEntity> listByTableIds(List<UUID> tableIds);

    /**
     * 添加数据源.
     *
     * @param copyDto 复制数据源
     * @return 复制数据源结果
     */
    UUID copyDataSource(DataSourceInfoCopyDto copyDto);

    /**
     * 添加数据源.
     *
     * @param saveDto 数据源参数
     * @return 添加结果
     */
    UUID addDataSource(DataSourceInfoSaveDto saveDto);

    /**
     * 修改数据源.
     *
     * @param updateDto 数据源参数
     * @return 修改结果
     */
    boolean updateDataSource(DataSourceInfoUpdateDto updateDto);

    /**
     * 根据ID查询数据源.
     *
     * @param id 数据源ID
     * @return 数据源信息
     */
    DataSourceInfoDto getDataSource(UUID id);

    /**
     * 根据表id查询数据源.
     *
     * @param tableId 表id
     * @return 数据源信息
     */
    DataSourceInfoDto getDataSourceByTableId(UUID tableId);

    /**
     * 根据ID删除数据源.
     *
     * @param id 数据源ID
     * @return 删除结果
     */
    boolean deleteDataSource(UUID id);

    /**
     * 测试数据源连接.
     *
     * @param dto 数据源信息
     * @return 测试连接结果
     */
    boolean testConnect(DataSourceInfoSaveDto dto);
}
