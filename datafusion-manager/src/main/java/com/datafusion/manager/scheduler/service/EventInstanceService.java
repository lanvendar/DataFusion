package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.EventInstanceDto;
import com.datafusion.manager.scheduler.dto.EventInstanceQueryDto;
import com.datafusion.manager.scheduler.po.EventInstanceEntity;

import java.util.List;

/**
 * 调度-事件实例Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface EventInstanceService extends IService<EventInstanceEntity> {

    /**
     * 分页查询事件实例.
     *
     * @param query 查询条件
     * @return 事件实例分页
     */
    PageResponse<EventInstanceDto> pageEventInstance(PageQuery<EventInstanceQueryDto> query);

    /**
     * 查询事件实例详情.
     *
     * @param id 事件实例ID
     * @return 事件实例详情
     */
    EventInstanceDto getEventInstanceById(java.util.UUID id);

    /**
     * 按事件ID和类型查询事件实例列表(带时间范围过滤).
     *
     * @param eventId   事件ID
     * @param eventTime 事件时间
     * @param type      事件类型
     * @return 事件实例列表
     */
    List<EventInstanceEntity> loadByEventId(String eventId, long eventTime, int type);

    /**
     * 按事件ID和时间精确查询事件实例.
     *
     * @param eventId   事件ID
     * @param eventTime 事件时间
     * @return 事件实例
     */
    EventInstanceEntity loadByEventKey(String eventId, Long eventTime);
}
