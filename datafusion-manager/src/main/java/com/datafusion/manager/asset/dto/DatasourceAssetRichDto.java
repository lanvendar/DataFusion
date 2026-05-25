package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 血缘搜索返回实体.
 * @author xufeng
 * @version 1.0.0, 2026/3/16
 * @since 2026/3/16
 */
@Data
@Accessors(chain = true)
@Schema(name = "DatasourceAssetRichDto", description = "返回数据源表字段树形结构")
public class DatasourceAssetRichDto {

    /**
     * 数据源id.
     */
    @Schema(name = "datasourceId", description = "数据源od")
    private String datasourceId;

    /**
     * 数据源名称.
     */
    @Schema(name = "datasourceName", description = "数据源名称")
    private String datasourceName;

    /**
     * 表列表.
     */
    @Schema(name = "tableList", description = "表字段")
    private List<TableColumnsNodeDto> tableList;
}
