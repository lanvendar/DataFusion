package com.datafusion.manager.asset.dao;

import com.datafusion.manager.asset.po.MetricInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 指标信息Mapper.
 * 用于从dw_tag_info和tag_info表查询指标数据.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/21
 * @since 2026/01/21
 */
@Mapper
public interface MetricsTmpMapper {

    /**
     * 清空临时表.
     */
    void truncateMetricTmpTable();

    /**
     * 将原始查询结果写入临时表（不过滤条件）.
     */
    void insertMetricTmpFromOriginal();

    /**
     * 从临时表查询指标数据列表.
     *
     * @param afterDate 起始日期
     * @param metricCode 指标编码
     * @param thirdMetricIds 第三方指标ID列表
     * @return 指标数据列表.
     */
    List<MetricInfoEntity> selectMetricTmpList(@Param("afterDate") LocalDate afterDate,
                                               @Param("metricCode") String metricCode, @Param("thirdMetricIds") List<String> thirdMetricIds);

}
