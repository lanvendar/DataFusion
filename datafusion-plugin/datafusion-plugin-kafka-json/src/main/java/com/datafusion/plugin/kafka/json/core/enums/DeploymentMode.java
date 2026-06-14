package com.datafusion.plugin.kafka.json.core.enums;

import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.util.TextUtils;

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
            throw new KafkaJsonPaimonException("Unsupported deploymentMode: " + value, e);
        }
    }
}
