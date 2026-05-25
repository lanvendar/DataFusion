package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程实例Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface FlowInstanceMapper extends BaseMapper<FlowInstanceEntity> {

    /**
     * 根据实例ID查询流程实例.
     *
     * @param instanceId 实例ID
     * @return 流程实例
     */
    FlowInstanceEntity getInstanceById(@Param("instanceId") UUID instanceId);

    /**
     * 查询非终态的流程实例.
     *
     * @param flowId 流程ID
     * @return 可用实例列表
     */
    List<FlowInstanceEntity> listAvailable(@Param("flowId") UUID flowId);

    /**
     * 查询最近一次流程实例.
     *
     * @param flowId  流程ID
     * @param version 发布版本
     * @return 最近一次流程实例
     */
    FlowInstanceEntity getLastInstance(@Param("flowId") UUID flowId,
                                       @Param("version") Long version);

    /**
     * 删除流程实例.
     *
     * @param instanceId 实例ID
     * @return 影响行数
     */
    int removeByInstanceId(@Param("instanceId") UUID instanceId);
}
