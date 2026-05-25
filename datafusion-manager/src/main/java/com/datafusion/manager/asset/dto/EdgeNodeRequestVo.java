package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/27
 * @since 2025/10/27
 */
@Data
public class EdgeNodeRequestVo implements Serializable {

    private static final long serialVersionUID = -8681721174695831934L;
    /**
     * 节点urn.
     */
    @Schema(name = "nodeUrn", description = "节点urn,支持表级,即表节点,菜单节点,接口节点,不支持子集,字段,指标")
    @NotNull(message = "查询节点urn不能为空")
    private String nodeUrn;

    /**
     * 节点名称.
     */
    @Schema(name = "nodeName", description = "节点名称")
    private String nodeName;

    /**
     * 查询深度,如果是字段级,只能为1,即上下游.
     */
    @Schema(name = "depth", description = "子节点名称")
    private Integer depth = 1;

    /**
     * 是否展示叶子节点,比如表字段,不展示,只显示表级血缘关系.
     */
    @Schema(name = "isLeafNode", description = "是否展示叶子节点")
    private Boolean isLeafNode = false;

    /**
     * 是否展示叶子节点,比如表字段,不展示,只显示表级血缘关系.
     */
    @Schema(name = "isUp", description = "展示上游，下游血缘")
    private Boolean isUp;

    /**
     * 测点信息.
     */
    @Schema(name = "tag", description = "测点信息")
    private String tag;

    /**
     * 维度信息.
     */
    @Schema(name = "dimension", description = "维度信息")
    private String dimension;

}
