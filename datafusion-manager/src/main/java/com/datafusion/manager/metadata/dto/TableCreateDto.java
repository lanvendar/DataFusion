package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 表创建请求DTO.
 *
 * @author david
 * @version 3.6.4, 2024/9/11
 * @since 3.6.4, 2024/9/11
 */
@Data
@Schema(name = "TableCreateDto", description = "表创建请求DTO")
public class TableCreateDto {

    /**
     * 源数据源ID.
     */
    @Schema(name = "sourceDatasourceId", description = "源数据源ID")
    @NotNull(message = "源数据源不能为空")
    private UUID sourceDatasourceId;

    /**
     * 目标数据源ID.
     */
    @Schema(name = "targetDatasourceId", description = "目标数据源ID")
    @NotNull(message = "目标数据源不能为空")
    private UUID targetDatasourceId;

    /**
     * 表/目标表映射关系.
     */
    @Schema(name = "mappings", description = "表/目标表映射关系")
    @NotEmpty(message = "数据源表不能为空")
    @Valid
    private List<TableCreateMappingDto> mappings;
}
