package com.datafusion.manager.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 数据同步任务查询条件DTO.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Data
@Accessors(chain = true)
@Schema(name = "DatasyncTaskQueryDto", description = "数据同步任务查询条件DTO")
public class DatasyncTaskQueryDto {

    /**
     * 任务名称(模糊查询).
     */
    @Schema(name = "name", description = "任务名称(模糊查询)")
    private String name;

    /**
     * 任务编码(精确查询).
     */
    @Schema(name = "code", description = "任务编码(精确查询)")
    private String code;

    /**
     * 来源数据源类型(精确查询).
     */
    @Schema(name = "sourceDsType", description = "来源数据源类型(精确查询)")
    private String sourceDsType;

    /**
     * 目标数据源类型(精确查询).
     */
    @Schema(name = "targetDsType", description = "目标数据源类型(精确查询)")
    private String targetDsType;

    /**
     * 发布状态(精确查询).
     */
    @Schema(name = "publishStatus", description = "发布状态(精确查询)")
    private Boolean publishStatus;

    /**
     * 来源数据源ID(精确查询).
     */
    @Schema(name = "sourceDsId", description = "来源数据源ID(精确查询)")
    private UUID sourceDsId;

    /**
     * 目标数据源ID(精确查询).
     */
    @Schema(name = "targetDsId", description = "目标数据源ID(精确查询)")
    private UUID targetDsId;
}