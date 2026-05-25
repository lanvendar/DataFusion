package com.datafusion.manager.metadata.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.metadata.po.ColumnInfoHisIdEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 元数据-表字段历史Mapper.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/21
 * @since 2025/8/21
 */
@Mapper
public interface ColumnInfoHisMapper extends BaseMapper<ColumnInfoHisIdEntity> {

}
