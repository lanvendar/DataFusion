package com.datafusion.manager.asset.dto;

import lombok.Data;

import java.util.List;

/**
 * 指标访问统计数据 DTO.
 *
 * @author DataFusion
 * @version 1.0.0
 */
@Data
public class MetricAccessStatsDto {

    /**
     * 统一指标ID (tag_info.id).
     */
    private Long tagInfoId;

    /**
     * 统一指标编码 (tag_code).
     */
    private String tagCode;

    /**
     * 统一指标名称 (tag_name).
     */
    private String tagName;

    /**
     * 时间维度 (转换后).
     */
    private String dimension;

    /**
     * 时间维度原始值.
     */
    private String originalDimension;

    /**
     * 物理层级 (从 uri 解析).
     */
    private String physicalLevel;

    /**
     * 计算时效 t0/t1 (从 uri 解析).
     */
    private String timeliness;

    /**
     * API 地址 (从 tag_access_stats.uri 规范化).
     */
    private String apiUrl;

    /**
     * 数仓指标信息 (来自 dw_tag_info).
     */
    private List<DwTagInfoDto> dwTagInfoList;

    /**
     * 数仓指标 DTO.
     *
     * @author DataFusion
     * @version 1.0.0
     */
    @Data
    public static class DwTagInfoDto {

        /** 数仓指标ID. */
        private Long id;

        /** 时间维度. */
        private String timeDimension;

        /** 数仓编码. */
        private String warehouseCode;

        /** API 地址. */
        private String apiUrl;

        /** 指标表名. */
        private String tagTable;
    }
}
