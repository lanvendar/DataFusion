package com.datafusion.manager.scheduler.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.scheduler.po.WorkerRegistryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度 worker 注册表 Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Mapper
public interface WorkerRegistryMapper extends BaseMapper<WorkerRegistryEntity> {
}
