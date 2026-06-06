package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.po.FlowInstanceHisEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程实例历史Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Mapper
public interface FlowInstanceHisMapper extends BaseMapper<FlowInstanceHisEntity> {

    /**
     * 分页查询历史流程实例.
     *
     * @param page  分页对象
     * @param query 查询条件
     * @return 历史流程实例分页
     */
    Page<FlowInstanceHisEntity> pageFlowInstance(Page<FlowInstanceHisEntity> page,
                                                 @Param("query") SchedulerInstanceQueryDto query);

    /**
     * 根据实例ID查询历史流程实例.
     *
     * @param instanceId 实例ID
     * @return 历史流程实例
     */
    FlowInstanceHisEntity getInstanceById(@Param("instanceId") UUID instanceId);

    /**
     * 按实例ID从实时表批量复制到历史流程实例.
     *
     * @param instanceIds 实例ID列表
     * @return 影响行数
     */
    int insertIgnoreBatch(@Param("instanceIds") List<UUID> instanceIds);
}
