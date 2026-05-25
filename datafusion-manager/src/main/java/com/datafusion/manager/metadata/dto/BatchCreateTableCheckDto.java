package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/8
 * @since 2025/9/8
 */
@Data
@Schema(name = "BatchTableCompareDto", description = "批量表结构对比")
public class BatchCreateTableCheckDto {
    
    /**
     * 轨迹id.
     */
    @Schema(name = "trackId", description = "轨迹id")
    @NotNull(message = "trackId不能为空")
    private UUID trackId;

    /**
     * 源数据源ID.
     */
    @Schema(name = "sourceDatasourceId", description = "源数据库ID")
    @NotNull(message = "sourceDatasourceId不能为空")
    private UUID sourceDatasourceId;

    /**
     * 目标数据源ID.
     */
    @NotNull(message = "targetDatasourceId不能为空")
    @Schema(name = "targetDatasourceId", description = "目标数据库ID")
    private UUID targetDatasourceId;

    /**
     * 源需要对比的表名.
     */
    @NotEmpty(message = "tableNames不能为空")
    @Schema(name = "tableNames", description = "源需要对比的表信息")
    private List<String> tableNames;

    /**
     * 源表到目标表是否有前缀关系.
     */
    @Schema(name = "isAddPrefix", description = "源表到目标表是否有前缀关系,null表示无前缀关系,true,源表到目标表位增加,false源表到目标是删除")
    private Boolean isAddPrefix;

    /**
     * 前缀.
     */
    @Schema(name = "prefix", description = "前缀")
    private String prefix;

    /**
     * 源表到目标表是否有前缀关系.
     */
    @Schema(name = "isAddSuffix", description = "源表到目标表是否有后缀关系,null表示无后缀关系,true,源表到目标表增加后缀,false源表到目标是删除后缀")
    private Boolean isAddSuffix;

    /**
     * 前缀.
     */
    @Schema(name = "suffix", description = "后缀")
    private String suffix;

    /**
     * 默认公共字段.
     */
    @Schema(name = "defaultColumns", description = "公共字段")
    private List<String> defaultColumns;

}
