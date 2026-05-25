package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.TaskLinkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务编排关系Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface TaskLinkMapper extends BaseMapper<TaskLinkEntity> {

    /**
     * 根据流程ID查询所有任务编排关系.
     *
     * @param flowId 流程ID
     * @return 任务编排关系列表
     */
    List<TaskLinkEntity> listByFlowId(@Param("flowId") UUID flowId);
}
