package com.datafusion.manager.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.system.po.VariableInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统-变量信息Mapper.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Mapper
public interface VariableInfoMapper extends BaseMapper<VariableInfoEntity> {

}
