package com.datafusion.manager.asset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 编辑指标实体.
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class MetricsUpdateResourceReq {
    
    /**
     * 指标列表.
     */
    @Schema(name = "metricsReq", description = "指标")
    private MetricsReq metricsReq;
    
    /**
     * 父级资源id.
     */
    @NotNull(message = "父级资源id不能为空")
    @Schema(name = "parentResourceId", description = "父级资源id")
    private UUID parentResourceId;
}
