package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
/**
 * 节点和资源实体.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/3/20
 * @since 2026/3/20
 */

@Accessors(chain = true)
@Data
public class AssetLineageNodeResourceDto extends AssetNodeDto {

    /**
     * 资源名称.
     */
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;

    /**
     * 资源类型.
     */
    @Schema(name = "resourceType", description = "资源类型,1接口,2菜单,3指标,4 etl任务")
    private String resourceType;

    /**
     * 资源标签.
     */
    @Schema(name = "resourceTag", description = "资源标签,枚举1节点,2边关系,3节点和边")
    private String resourceTag;

}
