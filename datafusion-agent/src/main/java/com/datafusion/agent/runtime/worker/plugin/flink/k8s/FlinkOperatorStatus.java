package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import lombok.Builder;
import lombok.Data;

/**
 * Flink Operator 状态快照.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
@Data
@Builder
public class FlinkOperatorStatus {

    /**
     * Operator 作业状态.
     */
    private State state;

    /**
     * Operator 期望作业状态.
     */
    private State desiredState;

    /**
     * JobManager 部署状态.
     */
    private JobManagerState jobManagerState;

    /**
     * FlinkDeployment 是否存在.
     */
    private boolean deploymentExists;

    /**
     * FlinkDeployment spec generation.
     */
    private Long generation;

    /**
     * Operator 已观察的 generation.
     */
    private Long observedGeneration;

    /**
     * JobManager 部署状态.
     */
    public enum JobManagerState {

        /**
         * Operator 尚未返回状态.
         */
        NONE,

        /**
         * JobManager 已就绪.
         */
        READY,

        /**
         * JobManager 已部署但尚未就绪.
         */
        DEPLOYED_NOT_READY,

        /**
         * JobManager 部署中.
         */
        DEPLOYING,

        /**
         * JobManager 不存在.
         */
        MISSING,

        /**
         * JobManager 部署错误.
         */
        ERROR,

        /**
         * 未识别状态.
         */
        UNKNOWN;

        /**
         * 解析 Operator JobManager 状态.
         *
         * @param value 原始状态
         * @return JobManager 状态
         */
        public static JobManagerState from(String value) {
            if (value == null || value.trim().isEmpty()) {
                return NONE;
            }
            try {
                return JobManagerState.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    /**
     * Flink Operator 作业状态.
     */
    public enum State {

        /**
         * Operator 尚未返回状态.
         */
        NONE("none"),

        /**
         * 已创建.
         */
        CREATED("created"),

        /**
         * 初始化中.
         */
        INITIALIZING("initializing"),

        /**
         * 调谐中.
         */
        RECONCILING("reconciling"),

        /**
         * 运行中.
         */
        RUNNING("running"),

        /**
         * 重启中.
         */
        RESTARTING("restarting"),

        /**
         * 失败处理中.
         */
        FAILING("failing"),

        /**
         * 失败.
         */
        FAILED("failed"),

        /**
         * 已完成.
         */
        FINISHED("finished"),

        /**
         * 取消中.
         */
        CANCELLING("cancelling"),

        /**
         * 已取消.
         */
        CANCELED("canceled"),

        /**
         * 已挂起.
         */
        SUSPENDED("suspended"),

        /**
         * 未识别状态.
         */
        UNKNOWN("unknown");

        /**
         * Operator 原始状态值.
         */
        private final String value;

        State(String value) {
            this.value = value;
        }

        /**
         * 获取 Operator 原始状态值.
         *
         * @return 原始状态值
         */
        public String getValue() {
            return value;
        }

        /**
         * 解析 Operator 原始状态.
         *
         * @param value 原始状态
         * @return Operator 状态
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
