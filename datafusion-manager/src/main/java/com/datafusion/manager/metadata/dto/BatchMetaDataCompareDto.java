package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * MetaDataCompareDto.
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BatchMetaDataCompareDto", description = "批量表结构对比实体")
public class BatchMetaDataCompareDto {
    
    /**
     * 轨迹id.
     */
    @Schema(name = "trackId", description = "轨迹id")
    @NotNull
    private UUID trackId;
    
    /**
     * 源端.
     */
    @Schema(name = "source", description = "源端")
    @NotNull
    private MetaDataCompareDto source;
    
    /**
     * 目标端.
     */
    @Schema(name = "target", description = "目标端")
    @NotNull
    private MetaDataCompareDto target;
    
}
