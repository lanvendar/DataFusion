package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.po.AssetLineageMetricsUnTmpEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 统一指标临时表 Mapper.
 *
 * @author feng.xu
 * @version 1.0.0 , 2026/04/16
 * @since 2026/04/16
 */
@Mapper
public interface AssetLineageMetricsUnTmpMapper extends BaseMapper<AssetLineageMetricsUnTmpEntity> {

    /**
     * 查询所有 tag_info 数据.
     *
     * @return tag_info 列表
     */
    List<Map<String, Object>> selectTagInfoList();

    /**
     * 根据 tag 和 physical_level 查询 tag_info.
     *
     * @param tag            标签
     * @param physicalLevel  物理层级
     * @param timeliness     计算时效
     * @return tag_info 列表
     */
    List<Map<String, Object>> selectTagInfoByTagAndLevel(
            @Param("tag") String tag,
            @Param("physicalLevel") String physicalLevel,
            @Param("timeliness") String timeliness);

    /**
     * 批量插入统一指标数据.
     *
     * @param entityList 指标数据列表
     * @return 插入数量
     */
    int batchInsert(List<AssetLineageMetricsUnTmpEntity> entityList);

    /**
     * 清空临时表.
     */
    void truncateTable();

    /**
     * 查询临时表数据（用于后续addMetrics处理）.
     *
     * @return 临时表数据列表
     */
    List<AssetLineageMetricsUnTmpEntity> selectAll();
}
