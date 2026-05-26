package com.datafusion.manager.ingestion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.ingestion.dto.DatasyncTaskDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskQueryDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskSaveDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskUpdateDto;
import com.datafusion.manager.ingestion.po.IngestionDatasyncTaskEntity;

import java.util.List;
import java.util.UUID;

/**
 * 数据同步任务服务接口.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
public interface DatasyncTaskService extends IService<IngestionDatasyncTaskEntity> {

    /**
     * 分页查询数据同步任务.
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResponse<DatasyncTaskDto> pageDatasyncTask(PageQuery<DatasyncTaskQueryDto> query);

    /**
     * 查询数据同步任务列表.
     *
     * @param query 查询条件
     * @return 列表结果
     */
    List<DatasyncTaskDto> listDatasyncTask(DatasyncTaskQueryDto query);

    /**
     * 根据id查询数据同步任务详情(含字段映射).
     *
     * @param id 任务id
     * @return 任务详情
     */
    DatasyncTaskDto getDatasyncTaskById(UUID id);

    /**
     * 新增数据同步任务(含字段映射).
     *
     * @param dto 新增参数
     * @return 新记录id
     */
    UUID addDatasyncTask(DatasyncTaskSaveDto dto);

    /**
     * 修改数据同步任务(字段映射全量替换).
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateDatasyncTask(DatasyncTaskUpdateDto dto);

    /**
     * 删除数据同步任务(主表+从表).
     *
     * @param id 任务id
     * @return 是否成功
     */
    boolean deleteDatasyncTask(UUID id);

    /**
     * 发布数据同步任务.
     *
     * @param id 任务id
     * @return 是否成功
     */
    boolean publishDatasyncTask(UUID id);

    /**
     * 下线数据同步任务.
     *
     * @param id 任务id
     * @return 是否成功
     */
    boolean offlineDatasyncTask(UUID id);
}