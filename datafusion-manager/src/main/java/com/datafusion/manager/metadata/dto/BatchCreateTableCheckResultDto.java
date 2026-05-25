package com.datafusion.manager.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * 批量表结构对比结果对象.
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/8
 * @since 2025/9/8
 */
@Data
@Schema(name = "BatchCreateTableCheckResultDto", description = "批量建表检查结果")
@Accessors(chain = true)
public class BatchCreateTableCheckResultDto {

    /**
     * 源数据源ID.
     */
    @Schema(name = "sourceDataSourceId", description = "源数据源ID")
    private UUID sourceDataSourceId;

    /**
     * 源数据源表ID.
     */
    @Schema(name = "sourceTableName", description = "源数据表名")
    private String sourceTableName;

    /**
     * 目标数据源ID.
     */
    @Schema(name = "targetDataSourceId", description = "目标数据源ID")
    private UUID targetDataSourceId;

    /**
     * 目标数据源表名.
     */
    @Schema(name = "targetTableName", description = "目标数据源表名")
    private String targetTableName;

    /**
     * 源数据源表字段数量.
     */
    @Schema(name = "sourceTableColumnNums", description = "源数据源表字段数量")
    private Integer sourceTableColumnNums;

    /**
     * 目标源表字段数量.
     */
    @Schema(name = "targetTableColumnNums", description = "目标源表字段数量")
    private Integer targetTableColumnNums;

    /**
     * 目标数据源表是否存在.
     */
    @Schema(name = "isTargetTableExist", description = "目标数据源表是否存在")
    private Boolean isTargetTableExist;

}
