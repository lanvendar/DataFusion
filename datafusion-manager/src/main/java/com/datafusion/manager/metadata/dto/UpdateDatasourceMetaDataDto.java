package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 根据数据源id更新数据源元数据.
 *
 * @author wei.bowen
 * @version 1.0.0, 2026/4/27
 * @since 2026/4/27
 */
@Data
@Schema(name = "UpdateDatasourceMetaDataDto", description = "根据数据源id更新数据源元数据")
public class UpdateDatasourceMetaDataDto {
    /**
     * 数据源ID.
     */
    @Schema(name = "dataSourceIds", description = "数据源ID")
    @NotNull(message = "dataSourceIds不能为空")
    private List<UUID> dataSourceIds;

    /**
     * 数据表名称集合.
     */
    @Schema(name = "isDelete", description = "是否先删除元数据")
    private Boolean isDelete;
}
