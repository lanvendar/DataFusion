package com.datafusion.manager.asset.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/11/6
 * @since 2025/11/6
 */
@Data
@Accessors(chain = true)
public class LineageEdgeDto {

    /**
     * 来源.
     */
    private String sourceUrn;

    /**
     * 目标.
     */
    private String targetUrn;

    /**
     * 深度.
     */
    private Integer depth;

    /**
     * 创建时间.
     */
    private Date createTime;

    /**
     * 更新时间.
     */
    private Date updateTime;

    /**
     * 属性值.
     */
    private JsonNode edgeProp;
}
