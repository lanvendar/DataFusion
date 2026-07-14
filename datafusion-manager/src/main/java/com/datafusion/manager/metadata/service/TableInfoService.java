package com.datafusion.manager.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.metadata.dto.DataSourceTableColumnDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.dto.TableInfoDto;
import com.datafusion.manager.metadata.dto.TableInfoQueryDto;
import com.datafusion.manager.metadata.dto.TableInfoSaveDto;
import com.datafusion.manager.metadata.dto.TableInfoUpdateDto;
import com.datafusion.manager.metadata.po.TableInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-表服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
public interface TableInfoService extends IService<TableInfoEntity> {

    /**
     * 获取并检查表非空.
     *
     * @param id 数据表ID
     * @return 数据表实体
     */
    TableInfoEntity getWithCheckNonNull(UUID id) throws CommonException;

    /**
     * 分页查询.
     *
     * @param query 查询条件
     * @return 表信息
     */
    Result<PageResponse<TableInfoDto>> pageTableInfos(PageQuery<TableInfoQueryDto> query);

    /**
     * 列表查询.
     *
     * @param query 查询条件
     * @return 表信息
     */
    List<TableInfoDto> listTableInfos(TableInfoQueryDto query);

    /**
     * 添加表信息.
     *
     * @param saveDto 表参数
     * @return 添加结果
     */
    UUID addTableInfo(TableInfoSaveDto saveDto) throws CommonException;

    /**
     * 更新表数据.
     *
     * @param updateDto 更新表数据
     * @return 更新结果
     */
    boolean updateTableInfo(TableInfoUpdateDto updateDto);

    /**
     * 根据ID查询表信息.
     *
     * @param id 表ID
     * @return 表信息
     */
    TableInfoDto getTableInfo(UUID id) throws CommonException;

    /**
     * 根据ID删除表.
     *
     * @param id 表ID
     * @return 删除结果
     */
    boolean deleteTableInfo(UUID id);

    /**
     * 查询表.
     *
     * @param datasourceId 数据源ID
     * @return 表实体信息
     */
    List<TableInfoEntity> getTables(UUID datasourceId);

    /**
     * 查询表.
     *
     * @param datasourceId 数据源ID
     * @param tables   表名称集合
     * @return 表实体信息
     */
    List<TableInfoEntity> getTables(UUID datasourceId, List<String> tables);
    
    /**
     * 查询表.
     *
     * @param datasourceId 数据源ID
     * @param tables   表名称集合
     * @return 表实体信息
     */
    List<DataSourceTableColumnDto> getDataSourceTableColumns(UUID datasourceId, List<String> tables);

    /**
     * 根据数据源ID查询表数量.
     *
     * @param datasourceId 数据源ID
     * @return 数据源表数量
     */
    Long getTableInfoCount(UUID datasourceId);

    /**
     * 根据数据源id删除该ID下的所有表.
     *
     * @param query 数据源ID
     * @return 操作结果
     */
    Boolean deleteTablesByDataSourceId(RetrieveMetaDataDto query);
}
