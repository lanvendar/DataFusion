package com.datafusion.manager.scheduler.service.impl;

import com.datafusion.manager.scheduler.dao.FlowInstanceHisMapper;
import com.datafusion.manager.scheduler.dao.FlowInstanceMapper;
import com.datafusion.manager.scheduler.dao.TaskInstanceHisMapper;
import com.datafusion.manager.scheduler.dao.TaskInstanceMapper;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import com.datafusion.manager.scheduler.po.TaskInstanceEntity;
import com.datafusion.manager.scheduler.service.SchedulerInstanceArchiveService;
import com.datafusion.scheduler.enums.StatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-实例归档Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SchedulerInstanceArchiveServiceImpl implements SchedulerInstanceArchiveService {

    /**
     * 默认归档批次大小.
     */
    private static final int DEFAULT_BATCH_SIZE = 200;

    /**
     * 成功状态存储值.
     */
    private static final List<String> SUCCESS_STATUS_VALUES = List.of(
            StatusEnum.RUN_SUCCESS.getStateType(),
            StatusEnum.ENFORCE_SUCCESS.getStateType()
    );

    /**
     * 流程实例Mapper.
     */
    private final FlowInstanceMapper flowInstanceMapper;

    /**
     * 流程实例历史Mapper.
     */
    private final FlowInstanceHisMapper flowInstanceHisMapper;

    /**
     * 任务实例Mapper.
     */
    private final TaskInstanceMapper taskInstanceMapper;

    /**
     * 任务实例历史Mapper.
     */
    private final TaskInstanceHisMapper taskInstanceHisMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveSuccessInstances(int batchSize) {
        int limit = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        return archiveFlowInstances(limit) + archiveTaskInstances(limit);
    }

    private int archiveFlowInstances(int limit) {
        List<FlowInstanceEntity> instances = flowInstanceMapper.listArchiveCandidates(SUCCESS_STATUS_VALUES, limit);
        if (CollectionUtils.isEmpty(instances)) {
            return 0;
        }

        List<UUID> ids = instances.stream().map(FlowInstanceEntity::getId).collect(Collectors.toList());
        flowInstanceHisMapper.insertIgnoreBatch(ids);
        return flowInstanceMapper.removeByInstanceIds(ids);
    }

    private int archiveTaskInstances(int limit) {
        List<TaskInstanceEntity> instances = taskInstanceMapper.listArchiveCandidates(SUCCESS_STATUS_VALUES, limit);
        if (CollectionUtils.isEmpty(instances)) {
            return 0;
        }

        List<UUID> ids = instances.stream().map(TaskInstanceEntity::getId).collect(Collectors.toList());
        taskInstanceHisMapper.insertIgnoreBatch(ids);
        return taskInstanceMapper.removeByInstanceIds(ids);
    }
}
