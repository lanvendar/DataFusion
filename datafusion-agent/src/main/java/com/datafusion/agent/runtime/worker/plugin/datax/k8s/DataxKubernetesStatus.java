package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import lombok.Builder;
import lombok.Data;

/**
 * DataX Kubernetes 状态快照.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Data
@Builder
public class DataxKubernetesStatus {

    /**
     * Job 状态.
     */
    private State state;

    /**
     * Job 是否存在.
     */
    private boolean jobExists;

    /**
     * Job status 是否存在.
     */
    private boolean jobStatusExists;

    /**
     * Pod 是否存在.
     */
    private boolean podExists;

    /**
     * Pod 是否仍在运行.
     */
    private boolean podRunning;

    /**
     * DataX Kubernetes Job 状态.
     */
    public enum State {

        /**
         * 暂无有效状态.
         */
        NONE,

        /**
         * 运行中.
         */
        ACTIVE,

        /**
         * 已完成.
         */
        COMPLETE,

        /**
         * 已失败.
         */
        FAILED
    }
}
