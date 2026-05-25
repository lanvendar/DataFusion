package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.manager.scheduler.dao.TaskInstanceMapper;
import com.datafusion.manager.scheduler.po.TaskInstanceEntity;
import com.datafusion.manager.scheduler.service.TaskInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 调度-任务实例Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TaskInstanceServiceImpl extends ServiceImpl<TaskInstanceMapper, TaskInstanceEntity>
        implements TaskInstanceService {

    @Override
    public TaskInstanceEntity getInstanceById(UUID instanceId) {
        return baseMapper.getInstanceById(instanceId);
    }

    @Override
    public List<UUID> listInsIdsByFlowInsId(UUID flowInstanceId) {
        return baseMapper.listInsIdsByFlowInsId(flowInstanceId);
    }

    @Override
    public List<TaskInstanceEntity> listByFlowInsId(UUID flowInstanceId) {
        return baseMapper.listByFlowInsId(flowInstanceId);
    }

    @Override
    public int removeByInstanceId(UUID instanceId) {
        return baseMapper.removeByInstanceId(instanceId);
    }

    @Override
    public int removeByFlowInsId(UUID flowInstanceId) {
        return baseMapper.removeByFlowInsId(flowInstanceId);
    }
}
