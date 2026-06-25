package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.TriggerCronPreviewDto;
import com.datafusion.manager.scheduler.dto.TriggerCronPreviewResultDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TriggerInfoUpdateDto;
import com.datafusion.manager.scheduler.po.TriggerInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-触发器信息Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface TriggerInfoService extends IService<TriggerInfoEntity> {

    /**
     * 根据触发器ID查询触发器信息.
     *
     * @param triggerId 触发器ID
     * @return 触发器信息
     */
    TriggerInfoEntity getByTriggerId(UUID triggerId);

    /**
     * 分页查询触发器信息.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<TriggerInfoDto> pageTriggerInfo(PageQuery<TriggerInfoQueryDto> query);

    /**
     * 查询触发器信息列表.
     *
     * @param query 查询条件
     * @return 触发器列表
     */
    List<TriggerInfoDto> listTriggerInfo(TriggerInfoQueryDto query);

    /**
     * 根据ID查询触发器详情.
     *
     * @param id 触发器ID
     * @return 触发器详情
     */
    TriggerInfoDto getTriggerInfoById(UUID id);

    /**
     * 新增触发器.
     *
     * @param dto 新增参数
     * @return 触发器ID
     */
    UUID addTriggerInfo(TriggerInfoSaveDto dto);

    /**
     * 修改触发器.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateTriggerInfo(TriggerInfoUpdateDto dto);

    /**
     * 删除触发器.
     *
     * @param id 触发器ID
     * @return 是否成功
     */
    boolean deleteTriggerInfo(UUID id);

    /**
     * 预览cron后续运行时间.
     *
     * @param dto cron预览参数
     * @return cron预览结果
     */
    TriggerCronPreviewResultDto previewCron(TriggerCronPreviewDto dto);
}
