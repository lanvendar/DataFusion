package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.TaskInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务实例Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface TaskInstanceMapper extends BaseMapper<TaskInstanceEntity> {

    /**
     * 根据实例ID查询任务实例.
     *
     * @param instanceId 实例ID
     * @return 任务实例
     */
    TaskInstanceEntity getInstanceById(@Param("instanceId") UUID instanceId);

    /**
     * 查询流程实例下所有任务实例ID.
     *
     * @param flowInstanceId 流程实例ID
     * @return 任务实例ID列表
     */
    List<UUID> listInsIdsByFlowInsId(@Param("flowInstanceId") UUID flowInstanceId);

    /**
     * 查询流程实例下所有任务实例.
     *
     * @param flowInstanceId 流程实例ID
     * @return 任务实例列表
     */
    List<TaskInstanceEntity> listByFlowInsId(@Param("flowInstanceId") UUID flowInstanceId);

    /**
     * 删除任务实例.
     *
     * @param instanceId 实例ID
     * @return 影响行数
     */
    int removeByInstanceId(@Param("instanceId") UUID instanceId);

    /**
     * 删除流程实例下所有任务实例.
     *
     * @param flowInstanceId 流程实例ID
     * @return 影响行数
     */
    int removeByFlowInsId(@Param("flowInstanceId") UUID flowInstanceId);
}
