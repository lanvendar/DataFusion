package com.datafusion.manager.asset.dto;

import lombok.Data;

/**
 * 节点实体.
 * @author xufeng
 * @version 1.0.0, 2026/3/17
 * @since 2026/3/17
 */
@Data
public class AssetNodeRichDto extends AssetNodeDto {

    /**
     * 节点子类型描述.
     */
    private String nodeSubTypeDesc;
}
