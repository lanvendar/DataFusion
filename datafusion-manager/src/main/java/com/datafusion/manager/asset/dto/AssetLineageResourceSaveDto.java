package com.datafusion.manager.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
@Data
public class AssetLineageResourceSaveDto {
    
    /**
     * 资源名称.
     */
    @NotNull(message = "资源名称不能为空")
    @Schema(name = "resourceName", description = "资源名称")
    private String resourceName;
    
    /**
     * 资源标签.
     */
    @NotNull(message = "资源标签不能为空")
    @Schema(name = "resourceTag", description = "资源标签,枚举1节点,2边关系,3节点和边")
    private String resourceTag;
    
    /**
     * 资源类型.
     */
    @NotNull(message = "资源类型不能为空")
    @Schema(name = "resourceType", description = "资源类型,1接口,2菜单,3指标,4 etl任务")
    private String resourceType;
    
    /**
     * 资源快照.
     */
    @NotNull(message = "资源快照不能为空")
    @Schema(name = "resourceSnapshot", description = "存储资源的相关信息,资源类型不同,存储的结构不一样")
    private JsonNode resourceSnapshot;
}
