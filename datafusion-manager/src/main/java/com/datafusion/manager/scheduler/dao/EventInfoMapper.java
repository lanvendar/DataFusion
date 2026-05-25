package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.EventInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度-事件信息Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface EventInfoMapper extends BaseMapper<EventInfoEntity> {

}
