package com.datafusion.manager.asset.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * 批量操作请求体.
 *
 * @author xufeng
 * @version 1.0.0, 2026/03/11
 * @since 2026/03/11
 */
@Data
public class BatchOperateReq {

    /**
     * 资源ID列表.
     */
    @NotEmpty(message = "ids不能为空")
    @Schema(name = "ids", description = "资源ID列表")
    private List<UUID> ids;
}