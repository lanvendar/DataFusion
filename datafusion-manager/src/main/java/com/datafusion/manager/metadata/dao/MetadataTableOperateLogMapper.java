package com.datafusion.manager.metadata.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.metadata.po.MetadataTableOperateLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 表结构同步记录表Mapper..
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/15
 * @since 2025/9/15
 */
@Mapper
public interface MetadataTableOperateLogMapper extends BaseMapper<MetadataTableOperateLogEntity> {

}
