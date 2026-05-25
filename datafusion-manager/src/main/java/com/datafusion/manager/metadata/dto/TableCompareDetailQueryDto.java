package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * TableCompareDetailQueryDto.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
@Data
public class TableCompareDetailQueryDto {
    
    /**
     * 轨迹id.
     */
    @Schema(name = "trackId", description = "轨迹id")
    @NotNull
    private UUID trackId;

    /**
     * 源数据源ID.
     */
    @Schema(name = "sourceDatasourceId", description = "源数据库ID")
    @NotNull
    private UUID sourceDatasourceId;

    /**
     * 需要对比的源表名.
     */
    @Schema(name = "sourceTableName", description = "需要对比的源表名")
    @NotNull(message = "sourceTableName不能为空")
    private String sourceTableName;

    /**
     * 目标数据源ID.
     */
    @Schema(name = "targetDatasourceId", description = "目标数据源ID")
    @NotNull
    private UUID targetDatasourceId;

    /**
     * 需要对比目标表名.
     */
    @Schema(name = "targetTableName", description = "需要对比目标表名")
    private String targetTableName;
}
