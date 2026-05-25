package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 调度-流程信息Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface FlowInfoMapper extends BaseMapper<FlowInfoEntity> {

    /**
     * 根据流程ID查询流程信息.
     *
     * @param flowId 流程ID
     * @return 流程信息
     */
    FlowInfoEntity getFlowInfo(@Param("flowId") UUID flowId);

    /**
     * 查询所有已发布且启用的流程信息.
     *
     * @return 流程信息列表
     */
    List<FlowInfoEntity> listAllEnabled();
}
