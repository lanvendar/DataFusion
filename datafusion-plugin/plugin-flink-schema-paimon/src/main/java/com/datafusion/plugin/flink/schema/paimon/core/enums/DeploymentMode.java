package com.datafusion.plugin.flink.schema.paimon.core.enums;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

/**
 * Flink 部署模式.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public enum DeploymentMode {

    /**
     * 本地模式.
     */
    LOCAL,

    /**
     * Standalone 模式.
     */
    STANDALONE,

    /**
     * Yarn 模式.
     */
    YARN,

    /**
     * Kubernetes 模式.
     */
    KUBERNETES;

    /**
     * 解析部署模式.
     *
     * @param value 配置值
     * @return 部署模式
     */
    public static DeploymentMode parse(String value) {
        String text = TextUtils.upper(value, LOCAL.name());
        try {
            return DeploymentMode.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new FlinkSchemaPaimonException("Unsupported deploymentMode: " + value, e);
        }
    }
}
