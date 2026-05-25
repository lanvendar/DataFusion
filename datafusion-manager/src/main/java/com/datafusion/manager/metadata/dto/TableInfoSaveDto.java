package com.datafusion.manager.metadata.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 元数据-表新增Dto.
 *
 * @author david
 * @version 3.6.4, 2024/8/26
 * @since 3.6.4, 2024/8/26
 */
@Data
@Schema(name = "TableInfoSaveDto", description = "元数据-表新增Dto")
public class TableInfoSaveDto {
    
    /**
     * 数据源ID.
     */
    @NotNull(message = "datasourceId不能为空")
    @Schema(name = "datasourceId不能为空", description = "数据源ID")
    private UUID datasourceId;
    
    /**
     * 表名称.
     */
    @Schema(name = "tableName", description = "表名称")
    @NotEmpty(message = "tableName不能为空")
    private String tableName;
    
    /**
     * 表注释.
     */
    @Schema(name = "tableDesc", description = "表注释")
    private String tableDesc;
    
    /**
     * 表所属目录.
     */
    @Schema(name = "catalogName", description = "表注释")
    private String catalogName;
    
    /**
     * 是否视图.
     */
    @Schema(name = "isView", description = "是否视图")
    private Boolean isView;
    
    /**
     * 视图定义.
     */
    @Schema(name = "viewDef", description = "视图定义")
    private String viewDef;
    
    /**
     * 表属性,存放表的相关属性,主键,分区键等信息.
     */
    @Schema(name = "tableProperties", description = "表属性")
    @JsonSetter(nulls = Nulls.SKIP)
    private JsonNode tableProperties;
    
}
