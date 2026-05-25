package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.TriggerInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/**
 * 调度-触发器信息Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface TriggerInfoMapper extends BaseMapper<TriggerInfoEntity> {

    /**
     * 根据触发器ID查询触发器信息.
     *
     * @param triggerId 触发器ID
     * @return 触发器信息
     */
    TriggerInfoEntity getByTriggerId(@Param("triggerId") UUID triggerId);
}
