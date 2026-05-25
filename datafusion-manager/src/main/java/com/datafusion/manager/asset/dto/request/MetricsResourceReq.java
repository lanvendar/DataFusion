package com.datafusion.manager.asset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 新增指标实体.
 * @author xufeng
 * @version 1.0.0, 2025/10/13
 * @since 2025/10/13
 */
@Data
public class MetricsResourceReq {
    
    /**
     * 指标列表.
     */
    @Schema(name = "metricsReqList", description = "指标列表")
    private List<MetricsReq> metricsReqList;
    
    /**
     * 父级资源id.
     */
    @NotNull(message = "父级资源id不能为空")
    @Schema(name = "parentResourceId", description = "父级资源id")
    private UUID parentResourceId;
}
