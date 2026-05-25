package com.datafusion.manager.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 属性边关系VO（字段到字段的边，包含连接点信息）.
 *
 * @author xufeng
 * @version 1.0.0, 2026/03/16
 * @since 2026/03/16
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AttributeEdgeVo extends EntityEdgeVo {

    /**
     * 源连接点（对应的属性URN）.
     */
    @Schema(name = "sourceHandle", description = "源连接点（对应的属性URN）")
    private String sourceHandle;

    /**
     * 目标连接点（对应的属性URN）.
     */
    @Schema(name = "targetHandle", description = "目标连接点（对应的属性URN）")
    private String targetHandle;
}