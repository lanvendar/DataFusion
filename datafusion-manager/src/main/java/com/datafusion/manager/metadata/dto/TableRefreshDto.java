package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 元数据-表结刷新DTO.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/22
 * @since 2025/8/22
 */
@Data
@Schema(name = "TableRefreshDto", description = "元数据-表结刷新DTO")
public class TableRefreshDto {
    
    /**
     * 数据源ID.
     */
    @Schema(name = "datasourceIds", description = "数据源ID集合")
    private List<UUID> datasourceIds;
}
