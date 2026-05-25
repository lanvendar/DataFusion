package com.datafusion.manager.asset.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datafusion.manager.asset.po.MetricSyncRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.UUID;

/**
 * 指标同步记录Mapper.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/01/22
 * @since 2026/01/22
 */
@Mapper
public interface MetricsSyncRecordMapper extends BaseMapper<MetricSyncRecordEntity> {

    /**
     * 更新同步记录状态.
     *
     * @param id           同步记录ID.
     * @param syncStatus   同步状态.
     * @param syncEndTime  同步结束时间.
     * @param totalCount   总数.
     * @param successCount 成功数.
     * @param failCount    失败数.
     * @param failMetricIds 失败的thirdMetricId列表.
     * @return 更新行数.
     */
    int updateSyncStatus(@Param("id") UUID id,
            @Param("syncStatus") Integer syncStatus,
            @Param("syncEndTime") Date syncEndTime,
            @Param("totalCount") Integer totalCount,
            @Param("successCount") Integer successCount,
            @Param("failCount") Integer failCount,
            @Param("failMetricIds") String failMetricIds);

}
