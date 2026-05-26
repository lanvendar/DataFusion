package com.datafusion.manager.metadata.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.spring.dto.response.Result;
import com.datafusion.manager.metadata.dto.ColumnInfoDto;
import com.datafusion.manager.metadata.dto.ColumnInfoQueryDto;
import com.datafusion.manager.metadata.dto.ColumnInfoSaveDto;
import com.datafusion.manager.metadata.dto.ColumnInfoUpdateDto;
import com.datafusion.manager.metadata.dto.RetrieveMetaDataDto;
import com.datafusion.manager.metadata.po.ColumnInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-字段信息服务.
 *
 * @author david
 * @version 3.6.4, 2024/8/29
 * @since 3.6.4, 2024/8/29
 */
public interface ColumnInfoService extends IService<ColumnInfoEntity> {

    /**
     * 获取并检查字段非空.
     *
     * @param id 字段ID
     * @return 字段实体
     */
    ColumnInfoEntity getWithCheckNonNull(UUID id);

    /**
     * 分页查询.
     *
     * @param queryDto 查询条件
     * @return 字段信息
     */
    Result<PageResponse<ColumnInfoDto>> pageColumnInfos(PageQuery<ColumnInfoQueryDto> queryDto);

    /**
     * 列表查询.
     *
     * @param queryDto 查询条件
     * @return 字段信息
     */
    List<ColumnInfoDto> listColumnInfos(ColumnInfoQueryDto queryDto);

    /**
     * 添加字段.
     *
     * @param saveDto 参数
     * @return 字段ID
     */
    UUID addColumnInfo(ColumnInfoSaveDto saveDto);

    /**
     * 更新字段信息.
     *
     * @param updateDto 更新参数
     * @return 字段ID
     */
    boolean updateColumnInfo(ColumnInfoUpdateDto updateDto);

    /**
     * 查询字段.
     *
     * @param id 字段ID
     * @return 字段信息
     */
    ColumnInfoDto getColumnInfo(UUID id);

    /**
     * 删除字段.
     *
     * @param id 字段ID
     * @return 删除结果·
     */
    boolean deleteColumnInfo(UUID id);

    /**
     * 根据表ID删除字段.
     *
     * @param tableId 表ID
     * @return 删除结果
     */
    boolean deleteByTableId(UUID tableId);

    /**
     * 根据表ID查询字段.
     *
     * @param tableId 表ID
     * @return 字段列表
     */
    List<ColumnInfoEntity> getByTableId(UUID tableId);

    /**
     * 根据表ID集合查询字段.
     *
     * @param tableIds 表ID集合
     * @return 字段列表
     */
    List<ColumnInfoEntity> getByTableIds(List<UUID> tableIds);

    /**
     * 根据数据源id删除该ID下的所有字段.
     *
     * @param query 数据源ID
     * @return 操作结果
     */
    Boolean deleteColumnsByDataSourceId(RetrieveMetaDataDto query);

}
