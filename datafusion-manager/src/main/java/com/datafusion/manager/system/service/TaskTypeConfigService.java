package com.datafusion.manager.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.system.dto.TaskTypeConfigDto;
import com.datafusion.manager.system.dto.TaskTypeConfigQueryDto;
import com.datafusion.manager.system.dto.TaskTypeConfigSaveDto;
import com.datafusion.manager.system.dto.TaskTypeConfigUpdateDto;
import com.datafusion.manager.system.po.TaskTypeConfigEntity;

import java.util.List;
import java.util.UUID;

/**
 * 系统-任务类型配置Service.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
public interface TaskTypeConfigService extends IService<TaskTypeConfigEntity> {

    /**
     * 分页查询任务类型配置.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<TaskTypeConfigDto> pageTaskTypeConfig(PageQuery<TaskTypeConfigQueryDto> query);

    /**
     * 查询任务类型配置列表.
     *
     * @param query 查询条件
     * @return 任务类型配置列表
     */
    List<TaskTypeConfigDto> listTaskTypeConfig(TaskTypeConfigQueryDto query);

    /**
     * 根据ID查询任务类型配置.
     *
     * @param id 任务类型配置ID
     * @return 任务类型配置详情
     */
    TaskTypeConfigDto getTaskTypeConfigById(UUID id);

    /**
     * 新增任务类型配置.
     *
     * @param dto 新增参数
     * @return 任务类型配置ID
     */
    UUID addTaskTypeConfig(TaskTypeConfigSaveDto dto);

    /**
     * 修改任务类型配置.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateTaskTypeConfig(TaskTypeConfigUpdateDto dto);

    /**
     * 删除任务类型配置.
     *
     * @param id 任务类型配置ID
     * @return 是否成功
     */
    boolean deleteTaskTypeConfig(UUID id);

    /**
     * 根据任务类型查询默认插件ID.
     *
     * @param taskType 任务类型
     * @return 默认插件ID
     */
    UUID getDefaultPluginIdByTaskType(String taskType);
}
