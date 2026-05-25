package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * TableCompareGenerateDdlRequestDto.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/11
 * @since 2025/9/11
 */
@Data
public class TableCompareGenerateDdlRequestDto {
    
    /**
     * 轨迹id.
     */
    @Schema(name = "trackId", description = "轨迹id")
    @NotNull
    private UUID trackId;
    
    /**
     * 表对比详情列表.
     */
    @Schema(name = "tableCompareResultList", description = "表对比详情列表")
    @NotEmpty
    List<BatchMetaDataCompareResultDto> tableCompareResultList;
}
