package com.datafusion.manager.metadata.dto;

import com.datafusion.manager.metadata.support.model.SelectListColumn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据预览.
 *
 * @author chengtg
 * @version 3.7.2, 2024/11/5
 * @since 3.7.2, 2024/11/5
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableBusMetaDataDto {
    /**
     * 表头.
     */
    @Schema(name = "header", description = "表头")
    private List<SelectListColumn> header;

    /**
     * 表头.
     */
    @Schema(name = "data", description = "表数据")
    private List<Map<String, Object>> data;
}
