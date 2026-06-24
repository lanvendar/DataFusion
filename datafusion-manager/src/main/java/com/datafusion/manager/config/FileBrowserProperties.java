package com.datafusion.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * File Browser 跳转配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/24
 * @since 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "filebrowser")
public class FileBrowserProperties {

    /**
     * 是否启用 File Browser 跳转.
     */
    private boolean enabled;

    /**
     * File Browser 文件页 URL 前缀.
     */
    private String baseUrl;

    /**
     * Manager/Agent 侧任务运行目录根路径.
     */
    private String rootPath;

    /**
     * File Browser 用户名.
     */
    private String username;

    /**
     * File Browser 密码.
     */
    private String password;
}
