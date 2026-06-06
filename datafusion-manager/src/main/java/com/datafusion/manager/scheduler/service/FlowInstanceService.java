package com.datafusion.manager.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dto.FlowInstanceDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程实例Service.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
public interface FlowInstanceService extends IService<FlowInstanceEntity> {

    /**
     * 分页查询流程实例.
     *
     * @param query 查询条件
     * @return 流程实例分页
     */
    PageResponse<FlowInstanceDto> pageFlowInstance(PageQuery<SchedulerInstanceQueryDto> query);

    /**
     * 查询流程实例详情.
     *
     * @param id 流程实例ID
     * @return 流程实例详情
     */
    FlowInstanceDto getFlowInstanceById(UUID id);

    /**
     * 根据实例ID查询流程实例.
     *
     * @param instanceId 实例ID
     * @return 流程实例
     */
    FlowInstanceEntity getInstanceById(UUID instanceId);

    /**
     * 查询非终态的流程实例.
     *
     * @param flowId 流程ID
     * @return 可用实例列���
     */
    List<FlowInstanceEntity> listAvailable(UUID flowId);

    /**
     * 查询最近一次流程实例.
     *
     * @param flowId  流程ID
     * @param version 发布版本
     * @return 最近一次流程实例
     */
    FlowInstanceEntity getLastInstance(UUID flowId, Long version);

    /**
     * 删除流程实例.
     *
     * @param instanceId 实例ID
     * @return 影响行数
     */
    int removeByInstanceId(UUID instanceId);
}
