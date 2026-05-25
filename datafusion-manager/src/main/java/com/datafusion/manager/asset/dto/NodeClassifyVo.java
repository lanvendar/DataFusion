package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 节点分类结果对象.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Data
public class NodeClassifyVo {
    
    /**
     * 数据源名称.
     */
    @Schema(name = "dataSourceName", description = "数据源名称")
    private String dataSourceName;
    
    /**
     * 数据源名称.
     */
    @Schema(name = "dataSourceType", description = "数据源类型")
    private String dataSourceType;
    
    /**
     * 类型.
     */
    @Schema(name = "tableNodeName", description = "表级节点名称")
    private String tableNodeName;
    
    /**
     * 上一级名称.
     */
    @Schema(name = "tableNodeUrn", description = "表级节点urn")
    private String tableNodeUrn;
    
    /**
     * 子节点.
     */
    @Schema(name = "columnsNode", description = "字段级节点")
    private List<AssetNodeDto> columnsNode;
    
}
