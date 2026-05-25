package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.po.TagAccessStatsEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * TagAccessStats Mapper.
 */
@Mapper
public interface TagAccessStatsMapper extends BaseMapper<TagAccessStatsEntity> {

    /**
     * 查询所有 tag_access_stats 数据.
     *
     * @return tag_access_stats 列表
     */
    List<TagAccessStatsEntity> selectAll();
}
