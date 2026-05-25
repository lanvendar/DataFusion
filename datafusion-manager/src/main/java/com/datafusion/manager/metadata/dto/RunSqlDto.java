package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 最终sql执行.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/16
 * @since 2025/9/16
 */

@Data
@Schema(name = "RunSqlDto", description = "最终sql执行")
public class RunSqlDto {
    /**
     * 轨迹id.
     */
    @Schema(name = "trackId", description = "轨迹id")
    @NotNull
    private UUID trackId;
    
    /**
     * 执行的sql脚本.
     */
    @Schema(name = "sql", description = "执行的sql脚本")
    @NotNull
    private String sql;
}
