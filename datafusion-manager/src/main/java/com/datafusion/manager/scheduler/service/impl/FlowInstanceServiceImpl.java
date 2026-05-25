package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.manager.scheduler.dao.FlowInstanceMapper;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import com.datafusion.manager.scheduler.service.FlowInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程实例Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class FlowInstanceServiceImpl extends ServiceImpl<FlowInstanceMapper, FlowInstanceEntity>
        implements FlowInstanceService {

    @Override
    public FlowInstanceEntity getInstanceById(UUID instanceId) {
        return baseMapper.getInstanceById(instanceId);
    }

    @Override
    public List<FlowInstanceEntity> listAvailable(UUID flowId) {
        return baseMapper.listAvailable(flowId);
    }

    @Override
    public FlowInstanceEntity getLastInstance(UUID flowId, Long version) {
        return baseMapper.getLastInstance(flowId, version);
    }

    @Override
    public int removeByInstanceId(UUID instanceId) {
        return baseMapper.removeByInstanceId(instanceId);
    }
}
