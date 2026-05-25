package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * 元数据-表结一致性构检查DTO.
 *
 * @author chengtg
 * @version 3.7.4, 2024/11/12
 * @since 3.7.4, 2024/11/12
 */
@Data
@Schema(name = "TableCheckDto", description = "元数据-表结一致性构检查DTO")
public class TableCheckDto {

    /**
     * 数据源ID.
     */
    @Schema(name = "datasourceIds", description = "数据源ID集合")
    @NotEmpty(message = "datasourceIds集合不能为空")
    private List<UUID> datasourceIds;

}
