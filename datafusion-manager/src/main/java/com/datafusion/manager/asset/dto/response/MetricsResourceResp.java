package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * 编辑指标实体.
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class MetricsResourceResp {
    
    /**
     * 指标列表.
     */
    @Schema(name = "metricsResp", description = "指标")
    private MetricsResp metricsResp;
    
    /**
     * 父级资源id.
     */
    @Schema(name = "parentResourceId", description = "父级资源id，不存在则是数据库同步过来")
    private UUID parentResourceId;

    /**
     * url.
     */
    @Schema(name = "requestUrl", description = "父级资源id为空时，这个值不为空")
    private String requestUrl;
}
