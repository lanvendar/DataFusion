package com.datafusion.manager.auth.dto;

import lombok.Data;

/**
 * FeignAuthentication.
 * @author xufeng
 * @version 1.0.0, 2025/9/8
 * @since 2025/9/8
 */
@Data
public class FeignAuthentication {

    /**
     * token.
     */
    private String token;

    /**
     * uuid.
     */
    private String uuid;

    /**
     * appVersion.
     */
    private String appVersion;

    /**
     * pageKey.
     */
    private String pageKey;

    /**
     * projectName.
     */
    private String projectName;
}
