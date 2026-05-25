package com.datafusion.manager.asset.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * app响应体.
 *
 * @author zhengjiexiang
 * @version 1.0.0 , 2026/2/13
 * @since 2026/2/13
 */
@Data
public class AppResp {

    /**
     * 资源ID.
     */
    @Schema(name = "appCode", description = "应用Code")
    private String appCode;

    /**
     * 应用名称.
     */
    @Schema(name = "appName", description = "应用名称")
    private String appName;

}
