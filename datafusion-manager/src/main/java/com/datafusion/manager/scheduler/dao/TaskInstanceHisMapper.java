package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.po.TaskInstanceHisEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务实例历史Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Mapper
public interface TaskInstanceHisMapper extends BaseMapper<TaskInstanceHisEntity> {

    /**
     * 分页查询历史任务实例.
     *
     * @param page  分页对象
     * @param query 查询条件
     * @return 历史任务实例分页
     */
    Page<TaskInstanceHisEntity> pageTaskInstance(Page<TaskInstanceHisEntity> page,
                                                 @Param("query") SchedulerInstanceQueryDto query);

    /**
     * 根据实例ID查询历史任务实例.
     *
     * @param instanceId 实例ID
     * @return 历史任务实例
     */
    TaskInstanceHisEntity getInstanceById(@Param("instanceId") UUID instanceId);

    /**
     * 查询流程实例下所有历史任务实例.
     *
     * @param flowInstanceId 流程实例ID
     * @return 历史任务实例列表
     */
    List<TaskInstanceHisEntity> listByFlowInsId(@Param("flowInstanceId") UUID flowInstanceId);

    /**
     * 按实例ID从实时表批量复制到历史任务实例.
     *
     * @param instanceIds 实例ID列表
     * @return 影响行数
     */
    int insertIgnoreBatch(@Param("instanceIds") List<UUID> instanceIds);

    /**
     * 按流程实例ID从实时表批量复制到历史任务实例.
     *
     * @param flowInstanceIds 流程实例ID列表
     * @return 影响行数
     */
    int insertIgnoreBatchByFlowInstanceIds(@Param("flowInstanceIds") List<UUID> flowInstanceIds);
}
