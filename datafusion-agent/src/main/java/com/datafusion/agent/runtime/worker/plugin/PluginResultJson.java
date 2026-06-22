package com.datafusion.agent.runtime.worker.plugin;

import com.datafusion.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Plugin result JSON helper.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/9
 * @since 1.0.0
 */
public final class PluginResultJson {

    private PluginResultJson() {
    }

    /**
     * Build result JSON.
     *
     * @param message        message
     * @param pluginType     plugin type
     * @param runMode        run mode
     * @param pluginLogUri   plugin log URI
     * @param exitCode       exit code
     * @return result JSON
     */
    public static ObjectNode build(String message, String pluginType, String runMode, String pluginLogUri,
            Integer exitCode) {
        ObjectNode result = JacksonUtils.createObjectNode();
        putIfNotBlank(result, "message", message);
        putIfNotBlank(result, "pluginType", pluginType);
        putIfNotBlank(result, "runMode", runMode);
        putIfNotBlank(result, "pluginLogUri", pluginLogUri);
        if (exitCode != null) {
            result.put("exitCode", exitCode);
        }
        return result;
    }

    private static void putIfNotBlank(ObjectNode node, String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            node.put(field, value);
        }
    }
}
