package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.web.dto.request.page.PageQuery;
import com.datafusion.common.web.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.EventInfoDto;
import com.datafusion.manager.scheduler.dto.EventInfoQueryDto;
import com.datafusion.manager.scheduler.dto.EventInfoSaveDto;
import com.datafusion.manager.scheduler.dto.EventInfoUpdateDto;
import com.datafusion.manager.scheduler.po.EventInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-事件信息Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface EventInfoService extends IService<EventInfoEntity> {

    /**
     * 分页查询事件.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<EventInfoDto> pageEventInfo(PageQuery<EventInfoQueryDto> query);

    /**
     * 查询事件列表.
     *
     * @param query 查询条件
     * @return 事件列表
     */
    List<EventInfoDto> listEventInfo(EventInfoQueryDto query);

    /**
     * 根据ID查询事件详情.
     *
     * @param id 事件ID
     * @return 事件详情
     */
    EventInfoDto getEventInfoById(UUID id);

    /**
     * 新增事件.
     *
     * @param dto 新增参数
     * @return 事件ID
     */
    UUID addEventInfo(EventInfoSaveDto dto);

    /**
     * 修改事件.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateEventInfo(EventInfoUpdateDto dto);

    /**
     * 删除事件.
     *
     * @param id 事件ID
     * @return 是否成功
     */
    boolean deleteEventInfo(UUID id);
}
