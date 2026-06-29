package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.TaskInfoCopyDto;
import com.datafusion.manager.scheduler.dto.TaskInfoDto;
import com.datafusion.manager.scheduler.dto.TaskInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TaskInfoUpdateDto;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务信息Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface TaskInfoService extends IService<TaskInfoEntity> {

    /**
     * 根据任务ID查询任务信息.
     *
     * @param taskId 任务ID
     * @return 任务信息
     */
    TaskInfoEntity getTaskInfo(UUID taskId);

    /**
     * 根据流程ID查询所有任务信息.
     *
     * @param flowId 流程ID
     * @return 任务信息列表
     */
    List<TaskInfoEntity> listByFlowId(UUID flowId);

    /**
     * 分页查询任务.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<TaskInfoDto> pageTaskInfo(PageQuery<TaskInfoQueryDto> query);

    /**
     * 查询任务列表.
     *
     * @param query 查询条件
     * @return 任务列表
     */
    List<TaskInfoDto> listTaskInfo(TaskInfoQueryDto query);

    /**
     * 根据ID查询任务详情.
     *
     * @param id 任务ID
     * @return 任务详情
     */
    TaskInfoDto getTaskInfoById(UUID id);

    /**
     * 新增任务.
     *
     * @param dto 新增参数
     * @return 任务ID
     */
    UUID addTaskInfo(TaskInfoSaveDto dto);

    /**
     * 复制任务.
     *
     * @param dto 复制参数
     * @return 新任务ID
     */
    UUID copyTaskInfo(TaskInfoCopyDto dto);

    /**
     * 修改任务.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateTaskInfo(TaskInfoUpdateDto dto);

    /**
     * 删除任务.
     *
     * @param id 任务ID
     * @return 是否成功
     */
    boolean deleteTaskInfo(UUID id);
}
