package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import lombok.Builder;
import lombok.Data;

/**
 * Spark Kubernetes 状态快照.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
@Data
@Builder
public class SparkOperatorStatus {

    /**
     * SparkApplication 状态.
     */
    private State state;

    /**
     * SparkApplication 是否存在.
     */
    private boolean applicationExists;

    /**
     * Pod 是否存在.
     */
    private boolean podExists;

    /**
     * Pod 是否仍在运行.
     */
    private boolean podRunning;

    /**
     * Service 是否存在.
     */
    private boolean serviceExists;

    /**
     * Spark Operator 应用状态.
     */
    public enum State {

        /**
         * 尚未返回状态.
         */
        NONE,

        /**
         * 已提交.
         */
        SUBMITTED,

        /**
         * 运行中.
         */
        RUNNING,

        /**
         * 已完成.
         */
        COMPLETED,

        /**
         * 运行失败.
         */
        FAILED,

        /**
         * 提交失败.
         */
        SUBMISSION_FAILED,

        /**
         * 等待重跑.
         */
        PENDING_RERUN,

        /**
         * 失效处理中.
         */
        INVALIDATING,

        /**
         * 成功收尾中.
         */
        SUCCEEDING,

        /**
         * 失败收尾中.
         */
        FAILING,

        /**
         * 暂停中.
         */
        SUSPENDING,

        /**
         * 已暂停.
         */
        SUSPENDED,

        /**
         * 恢复中.
         */
        RESUMING,

        /**
         * 未识别状态.
         */
        UNKNOWN;

        /**
         * 解析 Spark Operator 原始状态.
         *
         * @param value 原始状态
         * @return 状态枚举
         */
        public static State from(String value) {
            if (value == null || value.trim().isEmpty()) {
                return NONE;
            }
            try {
                return State.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }
}
