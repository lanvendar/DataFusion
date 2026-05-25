package com.datafusion.manager.asset.dto.skywalking;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * skywalking服务返回实体.
 * @author xufeng
 * @version 1.0.0, 2025/10/21
 * @since 2025/10/21
 */
@Data
public class SkyWalkingServiceDto implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 实例id.
     */
    private String id;
    
    /**
     * 名称.
     */
    private String name;
    
    /**
     * 简称.
     */
    private String shortName;
    
    /**
     * 服务类型.
     */
    @JsonProperty("__typename")
    private String typeName;
}
