package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/9
 * @since 2025/9/9
 */
@Data
@Schema(name = "BatchCreateTableDdlDto", description = "批量创建建表DDL请求对象")
public class BatchCreateTableDdlDto {
    
    /**
     * 轨迹id.
     */
    @Schema(name = "trackId", description = "轨迹id")
    @NotNull
    private UUID trackId;
    
    /**
     * 源数据库ID.
     */
    @NotNull(message = "sourceDataSourceId不能为空")
    @Schema(name = "sourceDataSourceId", description = "源数据源ID")
    private UUID sourceDataSourceId;
    
    /**
     * 目标数据库ID.
     */
    @NotNull(message = "targetDataSourceId不能为空")
    @Schema(name = "targetDataSourceId", description = "目标数据源ID")
    private UUID targetDataSourceId;
    
    /**
     * 表名映射,源表名,对应目标表名.
     */
    @NotEmpty(message = "tableMapping不能为空")
    @Schema(name = "tableMapping", description = "表名映射")
    private Map<String, String> tableMapping;
    
    /**
     * 默认公共字段.
     */
    @Schema(name = "defaultColumns", description = "公共字段")
    private List<String> defaultColumns;
    
    
}
