package com.datafusion.scheduler.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 组件参数.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/7/26
 * @since 2022/7/26
 */
@Data
public class PluginData {

    /**
     * 组件类型.
     */
    private String pluginType;

    /**
     * 组件名称.
     */
    private String pluginName;

    /**
     * 运行模式.
     */
    private String runMode;

    /**
     * 组件参数.
     */
    private JsonNode pluginParam;
}
