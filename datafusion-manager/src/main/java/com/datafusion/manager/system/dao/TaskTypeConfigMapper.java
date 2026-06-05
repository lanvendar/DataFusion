package com.datafusion.manager.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.system.po.TaskTypeConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统-任务类型配置Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Mapper
public interface TaskTypeConfigMapper extends BaseMapper<TaskTypeConfigEntity> {

}
