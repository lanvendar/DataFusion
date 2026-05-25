package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务信息Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface TaskInfoMapper extends BaseMapper<TaskInfoEntity> {

    /**
     * 根据任务ID查询任务信息.
     *
     * @param taskId 任务ID
     * @return 任务信息
     */
    TaskInfoEntity getTaskInfo(@Param("taskId") UUID taskId);

    /**
     * 根据流程ID查询所有任务信息.
     *
     * @param flowId 流程ID
     * @return 任务信息列表
     */
    List<TaskInfoEntity> listByFlowId(@Param("flowId") UUID flowId);
}
