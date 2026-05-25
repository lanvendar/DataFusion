package com.datafusion.manager.metadata.dto;

import com.datafusion.manager.metadata.enums.TableCompareEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.UUID;

/**
 * BatchMetaDataCompareResultDto.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/10
 * @since 2025/9/10
 */
@Data
@Schema(name = "BatchMetaDataCompareResultDto", description = "批量表结构对比结果")
@Accessors(chain = true)
public class BatchMetaDataCompareResultDto {
    
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
     * 匹配上的表名.
     */
    @Schema(name = "mappingTableName", description = "匹配上的表名")
    private String mappingTableName;
    
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
     * 对比结果.
     */
    @Schema(name = "compareResult", description = "对比结果")
    private TableCompareEnum compareResult;
}
