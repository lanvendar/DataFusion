package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
