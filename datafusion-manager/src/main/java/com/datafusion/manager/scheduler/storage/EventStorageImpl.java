package com.datafusion.manager.scheduler.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.manager.scheduler.po.EventInstanceEntity;
import com.datafusion.manager.scheduler.service.EventInstanceService;
import com.datafusion.scheduler.master.event.enmus.EventTypeEnum;
import com.datafusion.scheduler.master.event.model.GlobalEvent;
import com.datafusion.scheduler.master.event.storage.EventStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.datafusion.manager.utils.ImplUtil.strToUuid;
import static com.datafusion.manager.utils.ImplUtil.uuidToStr;

/**
 * 事件存储实现, 适配scheduler EventStorage接口.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventStorageImpl implements EventStorage {

    /**
     * 事件实例Service.
     */
    private final EventInstanceService eventInstanceService;

    @Override
    public void save(GlobalEvent globalEvent) {
        EventInstanceEntity entity = toEventInstanceEntity(globalEvent);
        eventInstanceService.save(entity);
    }

    @Override
    public List<GlobalEvent> loadByEventId(String eventId, long retainTime, int retainNum) {
        LambdaQueryWrapper<EventInstanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EventInstanceEntity::getEventId, UUID.fromString(eventId))
                .ge(EventInstanceEntity::getEffectTime, retainTime)
                .orderByDesc(EventInstanceEntity::getEffectTime)
                .last("LIMIT " + retainNum);
        return eventInstanceService.list(wrapper).stream()
                .map(this::toGlobalEvent)
                .collect(Collectors.toList());
    }

    @Override
    public GlobalEvent loadByEventKey(String eventId, Long eventTime) {
        EventInstanceEntity entity = eventInstanceService.loadByEventKey(eventId, eventTime);
        return toGlobalEvent(entity);
    }

    // region 转换方法

    private GlobalEvent toGlobalEvent(EventInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        GlobalEvent event = new GlobalEvent();
        event.setId(uuidToStr(entity.getEventId()));
        event.setType(parseEventType(entity.getEventType()));
        event.setFlowInstanceId(uuidToStr(entity.getFlowInstanceId()));
        event.setTaskInstanceId(uuidToStr(entity.getTaskInstanceId()));
        event.setEventTime(entity.getEffectTime());
        event.setBeginTime(entity.getEffectBeginTime());
        event.setEndTime(entity.getEffectEndTime());
        return event;
    }

    private EventInstanceEntity toEventInstanceEntity(GlobalEvent event) {
        EventInstanceEntity entity = new EventInstanceEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventId(strToUuid(event.getId()));
        entity.setEventType(event.getType() != null ? String.valueOf(event.getType().getType()) : null);
        entity.setFlowInstanceId(strToUuid(event.getFlowInstanceId()));
        entity.setTaskInstanceId(strToUuid(event.getTaskInstanceId()));
        entity.setEffectTime(event.getEventTime());
        entity.setEffectBeginTime(event.getBeginTime());
        entity.setEffectEndTime(event.getEndTime());
        return entity;
    }

    private EventTypeEnum parseEventType(String type) {
        if (type == null) {
            return null;
        }
        try {
            int typeVal = Integer.parseInt(type);
            for (EventTypeEnum e : EventTypeEnum.values()) {
                if (e.getType() == typeVal) {
                    return e;
                }
            }
        } catch (NumberFormatException e) {
            log.warn("无法解析事件类型: {}", type);
        }
        return null;
    }
    // endregion
}
