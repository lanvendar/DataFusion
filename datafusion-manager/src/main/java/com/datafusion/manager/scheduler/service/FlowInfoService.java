package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.DagSaveDto;
import com.datafusion.manager.scheduler.dto.FlowDagDto;
import com.datafusion.manager.scheduler.dto.FlowInfoDto;
import com.datafusion.manager.scheduler.dto.FlowInfoQueryDto;
import com.datafusion.manager.scheduler.dto.FlowInfoSaveDto;
import com.datafusion.manager.scheduler.dto.FlowInfoUpdateDto;
import com.datafusion.manager.scheduler.dto.FlowPublishDto;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程信息Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface FlowInfoService extends IService<FlowInfoEntity> {

    /**
     * 根据流程ID查询流程信息.
     *
     * @param flowId 流程ID
     * @return 流程信息
     */
    FlowInfoEntity getFlowInfo(UUID flowId);

    /**
     * 查询所有已发布且启用的流程信息.
     *
     * @return 流程信息列表
     */
    List<FlowInfoEntity> listAllEnabled();

    /**
     * 分页查询流程.
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<FlowInfoDto> pageFlowInfo(PageQuery<FlowInfoQueryDto> query);

    /**
     * 查询流程列表.
     *
     * @param query 查询条件
     * @return 流程列表
     */
    List<FlowInfoDto> listFlowInfo(FlowInfoQueryDto query);

    /**
     * 根据ID查询流程详情.
     *
     * @param id 流程ID
     * @return 流程详情
     */
    FlowInfoDto getFlowInfoById(UUID id);

    /**
     * 新增流程.
     *
     * @param dto 新增参数
     * @return 流程ID
     */
    UUID addFlowInfo(FlowInfoSaveDto dto);

    /**
     * 修改流程.
     *
     * @param dto 修改参数
     * @return 是否成功
     */
    boolean updateFlowInfo(FlowInfoUpdateDto dto);

    /**
     * 删除流程.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    boolean deleteFlowInfo(UUID id);

    /**
     * 查询流程DAG.
     *
     * @param flowId 流程ID
     * @return DAG数据
     */
    FlowDagDto getDag(UUID flowId);

    /**
     * 保存流程DAG.
     *
     * @param dto DAG保存参数
     * @return 是否成功
     */
    boolean saveDag(DagSaveDto dto);

    /**
     * 发布流程.
     *
     * @param dto 发布请求
     * @return 是否成功
     */
    boolean publish(FlowPublishDto dto);

    /**
     * 取消发布.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    boolean unpublish(UUID id);

    /**
     * 启用调度.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    boolean enable(UUID id);

    /**
     * 停用调度.
     *
     * @param id 流程ID
     * @return 是否成功
     */
    boolean disable(UUID id);
}
