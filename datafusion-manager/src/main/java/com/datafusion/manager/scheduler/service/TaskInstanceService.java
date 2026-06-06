package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.FlowInstanceTaskQueryDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceActionDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogQueryDto;
import com.datafusion.manager.scheduler.po.TaskInstanceEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务实例Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface TaskInstanceService extends IService<TaskInstanceEntity> {

    /**
     * 分页查询任务实例.
     *
     * @param query 查询条件
     * @return 任务实例分页
     */
    PageResponse<TaskInstanceDto> pageTaskInstance(PageQuery<SchedulerInstanceQueryDto> query);

    /**
     * 查询任务实例详情.
     *
     * @param id 任务实例ID
     * @return 任务实例详情
     */
    TaskInstanceDto getTaskInstanceById(UUID id);

    /**
     * 查询流程实例下所有任务实例.
     *
     * @param query 查询条件
     * @return 任务实例列表
     */
    List<TaskInstanceDto> listByFlowInstance(FlowInstanceTaskQueryDto query);

    /**
     * 读取任务实例日志.
     *
     * @param query 查询条件
     * @return 任务实例日志
     */
    TaskInstanceLogDto readTaskInstanceLog(TaskInstanceLogQueryDto query);

    /**
     * 操作任务实例.
     *
     * @param action 操作请求
     * @return 是否提交成功
     */
    Boolean actionTaskInstance(SchedulerInstanceActionDto action);

    /**
     * 根据实例ID查询任务实例.
     *
     * @param instanceId 实例ID
     * @return 任务实例
     */
    TaskInstanceEntity getInstanceById(UUID instanceId);

    /**
     * 查询流程实例下所有任务实例ID.
     *
     * @param flowInstanceId 流程实例ID
     * @return 任务实例ID列表
     */
    List<UUID> listInsIdsByFlowInsId(UUID flowInstanceId);

    /**
     * 查询流程实例下所有任务实例.
     *
     * @param flowInstanceId 流程实例ID
     * @return 任务实例列表
     */
    List<TaskInstanceEntity> listByFlowInsId(UUID flowInstanceId);

    /**
     * 删除任务实例.
     *
     * @param instanceId 实例ID
     * @return 影响行数
     */
    int removeByInstanceId(UUID instanceId);

    /**
     * 删除流程实例下所有任务实例.
     *
     * @param flowInstanceId 流程实例ID
     * @return 影响行数
     */
    int removeByFlowInsId(UUID flowInstanceId);
}
