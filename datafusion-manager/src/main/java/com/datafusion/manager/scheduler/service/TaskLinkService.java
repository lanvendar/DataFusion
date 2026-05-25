package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.manager.scheduler.po.TaskLinkEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务编排关系Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface TaskLinkService extends IService<TaskLinkEntity> {

    /**
     * 根据流程ID查询所有任务编排关系.
     *
     * @param flowId 流程ID
     * @return 任务编排关系列表
     */
    List<TaskLinkEntity> listByFlowId(UUID flowId);
}
