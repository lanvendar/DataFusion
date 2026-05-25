package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据表业务元数据信息.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/4
 * @since 3.7.2, 2024/11/4
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableBusMetaDto {

    /**
     * 表行记录数.
     */
    @Schema(name = "total", description = "表行记录数")
    private long total;

    /**
     * 表存储量大小.
     */
    @Schema(name = "size", description = "表存储量大小")
    private String size;
}
