package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.FlowInstanceMapper;
import com.datafusion.manager.scheduler.dao.FlowInstanceHisMapper;
import com.datafusion.manager.scheduler.dto.FlowInstanceDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceActionDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.model.SchedulerInstanceActionPolicy;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import com.datafusion.manager.scheduler.po.FlowInstanceHisEntity;
import com.datafusion.manager.scheduler.service.FlowInstanceService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.manager.utils.ImplUtil;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterService;
import com.datafusion.scheduler.master.flow.enums.FlowTypeEnum;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.model.ParamData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
@Slf4j
@RequiredArgsConstructor
public class FlowInstanceServiceImpl extends ServiceImpl<FlowInstanceMapper, FlowInstanceEntity>
        implements FlowInstanceService {

    /**
     * 历史流程实例Mapper.
     */
    private final FlowInstanceHisMapper flowInstanceHisMapper;

    /**
     * master 服务.
     */
    private final ObjectProvider<MasterService> masterServiceProvider;

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
    public Boolean actionFlowInstance(SchedulerInstanceActionDto action) {
        if (action == null || action.getFlowInstanceId() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程实例ID不能为空");
        }
        ActionType actionType = parseActionType(action.getActionType());
        FlowInstanceEntity entity = baseMapper.getInstanceById(action.getFlowInstanceId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "仅支持操作实时流程实例");
        }
        if (!SchedulerInstanceActionPolicy.canFlowAction(entity.getStatus(), actionType)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "当前流程实例状态不支持该操作");
        }

        FlowInstance instance = toFlowInstance(entity);
        MasterService masterService = masterServiceProvider.getObject();
        switch (actionType) {
            case SUBMIT:
                masterService.getFlowAction().flowSubmit(instance);
                break;
            case STOP:
                masterService.getFlowAction().flowStop(instance);
                break;
            default:
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "不支持的流程实例操作");
        }
        log.info("操作流程实例,user={},flowInstanceId={},actionType={},result={}",
                HttpUtils.getCurrentUserName(), action.getFlowInstanceId(), actionType, true);
        return true;
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
        dto.setAvailableActions(SchedulerInstanceActionPolicy.flowActions(entity.getStatus()));
        return dto;
    }

    private Long calcDuration(Long startTime, Long endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return endTime - startTime;
    }

    private ActionType parseActionType(String actionType) {
        try {
            return ActionType.valueOf(actionType);
        } catch (Exception e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程实例操作类型不合法");
        }
    }

    private FlowInstance toFlowInstance(FlowInstanceEntity entity) {
        FlowInstance instance = new FlowInstance();
        instance.setInstanceId(ImplUtil.uuidToStr(entity.getId()));
        instance.setFlowId(ImplUtil.uuidToStr(entity.getFlowId()));
        instance.setFlowName(entity.getFlowName());
        instance.setFlowType(entity.getFlowType() != null ? FlowTypeEnum.fromString(entity.getFlowType()) : null);
        instance.setVersion(entity.getPublishVersion() != null ? String.valueOf(entity.getPublishVersion()) : null);
        instance.setState(entity.getStatus() != null ? StatusEnum.fromString(entity.getStatus()) : null);
        instance.setScheduleTime(entity.getScheduleTime());
        instance.setStartTime(entity.getStartTime());
        instance.setEndTime(entity.getEndTime());
        instance.setFlowParam(JacksonUtils.tryObj2Bean(entity.getFlowParam(), ParamData.class));
        instance.setDepEventIds(ImplUtil.parseCommaSet(entity.getDepEventIds()));
        instance.setEventId(ImplUtil.uuidToStr(entity.getEventId()));
        return instance;
    }
}
