package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.EventInstanceMapper;
import com.datafusion.manager.scheduler.dto.EventInstanceDto;
import com.datafusion.manager.scheduler.dto.EventInstanceQueryDto;
import com.datafusion.manager.scheduler.po.EventInstanceEntity;
import com.datafusion.manager.scheduler.service.EventInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-事件实例Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class EventInstanceServiceImpl extends ServiceImpl<EventInstanceMapper, EventInstanceEntity>
        implements EventInstanceService {

    @Override
    public PageResponse<EventInstanceDto> pageEventInstance(PageQuery<EventInstanceQueryDto> query) {
        PageQuery<EventInstanceQueryDto> pageQuery = normalizePageQuery(query);
        Page<EventInstanceEntity> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        Page<EventInstanceEntity> result = baseMapper.pageEventInstance(page, normalizeOption(pageQuery.getOption()));

        PageResponse<EventInstanceDto> response = new PageResponse<>();
        response.setDataList(result.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) result.getCurrent());
        response.setSize((int) result.getSize());
        response.setTotal((int) result.getTotal());
        return response;
    }

    @Override
    public EventInstanceDto getEventInstanceById(UUID id) {
        EventInstanceEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "事件实例不存在");
        }
        return toDto(entity);
    }

    @Override
    public List<EventInstanceEntity> loadByEventId(String eventId, long eventTime, int type) {
        return baseMapper.loadByEventId(eventId, eventTime, type);
    }

    @Override
    public EventInstanceEntity loadByEventKey(String eventId, Long eventTime) {
        return baseMapper.loadByEventKey(eventId, eventTime);
    }

    private PageQuery<EventInstanceQueryDto> normalizePageQuery(PageQuery<EventInstanceQueryDto> query) {
        if (query != null) {
            return query;
        }
        return new PageQuery<>(new EventInstanceQueryDto());
    }

    private EventInstanceQueryDto normalizeOption(EventInstanceQueryDto option) {
        return option != null ? option : new EventInstanceQueryDto();
    }

    private EventInstanceDto toDto(EventInstanceEntity entity) {
        EventInstanceDto dto = new EventInstanceDto();
        dto.setId(entity.getId());
        dto.setEventId(entity.getEventId());
        dto.setEventName(entity.getEventName());
        dto.setEventType(entity.getEventType());
        dto.setFlowInstanceId(entity.getFlowInstanceId());
        dto.setTaskInstanceId(entity.getTaskInstanceId());
        dto.setEffectTime(entity.getEffectTime());
        dto.setEffectBeginTime(entity.getEffectBeginTime());
        dto.setEffectEndTime(entity.getEffectEndTime());
        return dto;
    }
}
