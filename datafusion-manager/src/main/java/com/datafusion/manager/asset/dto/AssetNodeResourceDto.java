package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 新增指标实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/3/20
 * @since 2026/3/20
 */
@Data
public class AssetNodeResourceDto {
    /**
     * nodeUrn  节点urn.
     */
    @NotNull(message = "节点urn不能为空")
    @Schema(name = "nodeUrn", description = "节点urn")
    private String nodeUrn;

    /**
     * 指标code.
     */
    @Schema(name = "tag", description = "指标code")
    private String tag;

    /**
     * 时间维度.
     */
    @Schema(name = "dimension", description = "时间维度")
    private String dimension;

}
