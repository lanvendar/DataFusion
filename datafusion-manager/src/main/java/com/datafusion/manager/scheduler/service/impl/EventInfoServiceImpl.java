package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.EventInfoMapper;
import com.datafusion.manager.scheduler.dto.EventInfoDto;
import com.datafusion.manager.scheduler.dto.EventInfoQueryDto;
import com.datafusion.manager.scheduler.dto.EventInfoSaveDto;
import com.datafusion.manager.scheduler.dto.EventInfoUpdateDto;
import com.datafusion.manager.scheduler.po.EventInfoEntity;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.service.EventInfoService;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-事件信息Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class EventInfoServiceImpl extends ServiceImpl<EventInfoMapper, EventInfoEntity>
        implements EventInfoService {

    /**
     * 事件类型: TASK.
     */
    private static final String EVENT_TYPE_TASK = "1";

    /**
     * 事件类型: FLOW.
     */
    private static final String EVENT_TYPE_FLOW = "2";

    /**
     * 流程信息Service.
     */
    private final FlowInfoService flowInfoService;

    /**
     * 任务信息Service.
     */
    private final TaskInfoService taskInfoService;

    @Override
    public PageResponse<EventInfoDto> pageEventInfo(PageQuery<EventInfoQueryDto> query) {
        LambdaQueryWrapper<EventInfoEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<EventInfoEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        PageResponse<EventInfoDto> response = new PageResponse<>();
        response.setDataList(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<EventInfoDto> listEventInfo(EventInfoQueryDto query) {
        LambdaQueryWrapper<EventInfoEntity> wrapper = buildQueryWrapper(query);
        return baseMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public EventInfoDto getEventInfoById(UUID id) {
        EventInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件不存在");
        }
        return toDto(entity);
    }

    @Override
    public UUID addEventInfo(EventInfoSaveDto dto) {
        validateEventTypeRelation(dto.getEventType(), dto.getFlowId(), dto.getTaskId());

        EventInfoEntity entity = new EventInfoEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventName(dto.getEventName());
        entity.setEventType(dto.getEventType());
        entity.setFlowId(dto.getFlowId());
        entity.setTaskId(dto.getTaskId());

        Date now = new Date();
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateEventInfo(EventInfoUpdateDto dto) {
        EventInfoEntity entity = getById(dto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件不存在");
        }

        if (StringUtils.isNotBlank(dto.getEventName())) {
            entity.setEventName(dto.getEventName());
        }
        if (StringUtils.isNotBlank(dto.getEventType())) {
            entity.setEventType(dto.getEventType());
        }
        if (dto.getFlowId() != null) {
            entity.setFlowId(dto.getFlowId());
        }
        if (dto.getTaskId() != null) {
            entity.setTaskId(dto.getTaskId());
        }

        // 校验修改后的关联一致性
        validateEventTypeRelation(entity.getEventType(), entity.getFlowId(), entity.getTaskId());

        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    @Override
    public boolean deleteEventInfo(UUID id) {
        EventInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件不存在");
        }

        checkEventNotReferenced(entity);

        return removeById(id);
    }

    // region 私有方法

    /**
     * 构建查询条件.
     *
     * @param query 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<EventInfoEntity> buildQueryWrapper(EventInfoQueryDto query) {
        LambdaQueryWrapper<EventInfoEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getEventName())) {
                wrapper.like(EventInfoEntity::getEventName, query.getEventName());
            }
            if (StringUtils.isNotBlank(query.getEventType())) {
                wrapper.eq(EventInfoEntity::getEventType, query.getEventType());
            }
            if (query.getFlowId() != null) {
                wrapper.eq(EventInfoEntity::getFlowId, query.getFlowId());
            }
            if (query.getTaskId() != null) {
                wrapper.eq(EventInfoEntity::getTaskId, query.getTaskId());
            }
        }
        wrapper.orderByDesc(EventInfoEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 校验事件类型与关联对象的一致性.
     *
     * @param eventType 事件类型
     * @param flowId    关联流程ID
     * @param taskId    关联任务ID
     */
    private void validateEventTypeRelation(String eventType, UUID flowId, UUID taskId) {
        if (EVENT_TYPE_FLOW.equals(eventType) && flowId == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "FLOW类型事件必须关联流程");
        }
        if (EVENT_TYPE_TASK.equals(eventType) && taskId == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "TASK类型事件必须关联任务");
        }
    }

    /**
     * 检查事件是否被流程或任务引用.
     *
     * @param event 事件实体
     */
    private void checkEventNotReferenced(EventInfoEntity event) {
        UUID eventId = event.getId();

        // 检查 flow_info.event_id
        LambdaQueryWrapper<FlowInfoEntity> flowEventWrapper = new LambdaQueryWrapper<>();
        flowEventWrapper.eq(FlowInfoEntity::getEventId, eventId);
        if (event.getFlowId() != null) {
            flowEventWrapper.ne(FlowInfoEntity::getId, event.getFlowId());
        }
        if (flowInfoService.count(flowEventWrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件已被流程引用, 无法删除");
        }

        // 检查 task_info.event_id
        LambdaQueryWrapper<TaskInfoEntity> taskEventWrapper = new LambdaQueryWrapper<>();
        taskEventWrapper.eq(TaskInfoEntity::getEventId, eventId);
        if (event.getTaskId() != null) {
            taskEventWrapper.ne(TaskInfoEntity::getId, event.getTaskId());
        }
        if (taskInfoService.count(taskEventWrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件已被任务引用, 无法删除");
        }

        String eventIdStr = eventId.toString();

        // 检查 flow_info.dep_event_ids (逗号分割字符串, LIKE 模糊匹配后应用层精确确认)
        LambdaQueryWrapper<FlowInfoEntity> flowDepWrapper = new LambdaQueryWrapper<>();
        flowDepWrapper.like(FlowInfoEntity::getDepEventIds, eventIdStr);
        List<FlowInfoEntity> flowsWithDep = flowInfoService.list(flowDepWrapper);
        for (FlowInfoEntity flow : flowsWithDep) {
            if (containsEventId(flow.getDepEventIds(), eventIdStr)) {
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件已被流程依赖引用, 无法删除");
            }
        }

        // 检查 task_info.dep_event_ids
        LambdaQueryWrapper<TaskInfoEntity> taskDepWrapper = new LambdaQueryWrapper<>();
        taskDepWrapper.like(TaskInfoEntity::getDepEventIds, eventIdStr);
        List<TaskInfoEntity> tasksWithDep = taskInfoService.list(taskDepWrapper);
        for (TaskInfoEntity task : tasksWithDep) {
            if (containsEventId(task.getDepEventIds(), eventIdStr)) {
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件已被任务依赖引用, 无法删除");
            }
        }
    }

    /**
     * 检查逗号分割字符串中是否精确包含指定ID.
     *
     * @param depEventIds 逗号分割的事件ID字符串
     * @param eventId     目标事件ID
     * @return 是否包含
     */
    private boolean containsEventId(String depEventIds, String eventId) {
        if (StringUtils.isBlank(depEventIds)) {
            return false;
        }
        for (String id : depEventIds.split(",")) {
            if (id.trim().equals(eventId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Entity转Dto.
     *
     * @param entity 事件实体
     * @return 事件Dto
     */
    private EventInfoDto toDto(EventInfoEntity entity) {
        EventInfoDto dto = new EventInfoDto();
        dto.setId(entity.getId());
        dto.setEventName(entity.getEventName());
        dto.setEventType(entity.getEventType());
        dto.setFlowId(entity.getFlowId());
        dto.setTaskId(entity.getTaskId());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
    // endregion
}
