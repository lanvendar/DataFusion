package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.manager.scheduler.dao.TaskLinkMapper;
import com.datafusion.manager.scheduler.po.TaskLinkEntity;
import com.datafusion.manager.scheduler.service.TaskLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务编排关系Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TaskLinkServiceImpl extends ServiceImpl<TaskLinkMapper, TaskLinkEntity>
        implements TaskLinkService {

    @Override
    public List<TaskLinkEntity> listByFlowId(UUID flowId) {
        return baseMapper.listByFlowId(flowId);
    }
}
