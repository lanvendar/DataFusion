package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.manager.scheduler.dao.EventInstanceMapper;
import com.datafusion.manager.scheduler.po.EventInstanceEntity;
import com.datafusion.manager.scheduler.service.EventInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public List<EventInstanceEntity> loadByEventId(String eventId, long eventTime, int type) {
        return baseMapper.loadByEventId(eventId, eventTime, type);
    }

    @Override
    public EventInstanceEntity loadByEventKey(String eventId, Long eventTime) {
        return baseMapper.loadByEventKey(eventId, eventTime);
    }
}
