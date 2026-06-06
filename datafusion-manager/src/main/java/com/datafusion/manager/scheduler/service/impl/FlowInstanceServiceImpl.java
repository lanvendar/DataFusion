package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.FlowInstanceMapper;
import com.datafusion.manager.scheduler.dao.FlowInstanceHisMapper;
import com.datafusion.manager.scheduler.dto.FlowInstanceDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import com.datafusion.manager.scheduler.po.FlowInstanceHisEntity;
import com.datafusion.manager.scheduler.service.FlowInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * 历史流程实例Mapper.
     */
    private final FlowInstanceHisMapper flowInstanceHisMapper;

    /**
     * 历史视图类型.
     */
    private static final String VIEW_TYPE_HISTORY = "HISTORY";

    @Override
    public PageResponse<FlowInstanceDto> pageFlowInstance(PageQuery<SchedulerInstanceQueryDto> query) {
        PageQuery<SchedulerInstanceQueryDto> pageQuery = normalizePageQuery(query);
        SchedulerInstanceQueryDto option = normalizeOption(pageQuery.getOption());

        if (isHistory(option)) {
            Page<FlowInstanceHisEntity> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
            Page<FlowInstanceHisEntity> result = flowInstanceHisMapper.pageFlowInstance(page, option);
            return toPageResponse(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
        }

        Page<FlowInstanceEntity> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        Page<FlowInstanceEntity> result = baseMapper.pageFlowInstance(page, option);
        return toPageResponse(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public FlowInstanceDto getFlowInstanceById(UUID id) {
        FlowInstanceEntity entity = baseMapper.getInstanceById(id);
        if (entity == null) {
            entity = flowInstanceHisMapper.getInstanceById(id);
        }
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程实例不存在");
        }
        return toDto(entity);
    }

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

    private PageQuery<SchedulerInstanceQueryDto> normalizePageQuery(PageQuery<SchedulerInstanceQueryDto> query) {
        if (query != null) {
            return query;
        }
        return new PageQuery<>(new SchedulerInstanceQueryDto());
    }

    private SchedulerInstanceQueryDto normalizeOption(SchedulerInstanceQueryDto option) {
        return option != null ? option : new SchedulerInstanceQueryDto();
    }

    private boolean isHistory(SchedulerInstanceQueryDto query) {
        return VIEW_TYPE_HISTORY.equalsIgnoreCase(query.getViewType());
    }

    private PageResponse<FlowInstanceDto> toPageResponse(List<? extends FlowInstanceEntity> records,
                                                         long current,
                                                         long size,
                                                         long total) {
        PageResponse<FlowInstanceDto> response = new PageResponse<>();
        response.setDataList(records.stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) current);
        response.setSize((int) size);
        response.setTotal((int) total);
        return response;
    }

    private FlowInstanceDto toDto(FlowInstanceEntity entity) {
        FlowInstanceDto dto = new FlowInstanceDto();
        dto.setId(entity.getId());
        dto.setFlowId(entity.getFlowId());
        dto.setFlowName(entity.getFlowName());
        dto.setFlowCode(entity.getFlowCode());
        dto.setFlowType(entity.getFlowType());
        dto.setStatus(entity.getStatus());
        dto.setTriggerId(entity.getTriggerId());
        dto.setPublishVersion(entity.getPublishVersion());
        dto.setScheduleTime(entity.getScheduleTime());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setDuration(calcDuration(entity.getStartTime(), entity.getEndTime()));
        dto.setFlowDagSnapshot(entity.getFlowDagSnapshot());
        return dto;
    }

    private Long calcDuration(Long startTime, Long endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return endTime - startTime;
    }
}
