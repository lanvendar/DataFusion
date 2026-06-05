package com.datafusion.manager.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.system.po.PluginConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统-插件配置Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/4
 * @since 1.0.0
 */
@Mapper
public interface PluginConfigMapper extends BaseMapper<PluginConfigEntity> {

}
