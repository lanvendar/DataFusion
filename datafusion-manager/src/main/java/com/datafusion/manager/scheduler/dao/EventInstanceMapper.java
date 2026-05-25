package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.EventInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调度-事件实例Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface EventInstanceMapper extends BaseMapper<EventInstanceEntity> {

    /**
     * 按事件ID和类型查询事件实例列表(带时间范围过滤).
     *
     * @param eventId   事件ID
     * @param eventTime 事件时间
     * @param type      事件类型
     * @return 事件实例列表
     */
    List<EventInstanceEntity> loadByEventId(@Param("eventId") String eventId,
                                            @Param("eventTime") long eventTime,
                                            @Param("type") int type);

    /**
     * 按事件ID和时间精确查询事件实例.
     *
     * @param eventId   事件ID
     * @param eventTime 事件时间
     * @return 事件实例
     */
    EventInstanceEntity loadByEventKey(@Param("eventId") String eventId,
                                       @Param("eventTime") Long eventTime);
}
