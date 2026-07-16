package com.datafusion.agent.runtime.worker.plugin.flink.k8s;

import com.datafusion.agent.runtime.worker.plugin.flink.FlinkExecutionParam;

/**
 * Flink Kubernetes Operator 客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/3
 * @since 1.0.0
 */
public interface K8sOperatorClient {

    /**
     * 提交 FlinkDeployment.
     *
     * @param param 执行参数
     * @return 运行引用
     */
    FlinkKubernetesRuntimeRef submit(FlinkExecutionParam param);

    /**
     * 停止 FlinkDeployment.
     *
     * @param runtimeRef 运行引用
     */
    void stop(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * 强制清理 FlinkDeployment.
     *
     * @param runtimeRef 运行引用
     */
    void kill(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * 查询 Operator 状态.
     *
     * @param runtimeRef 运行引用
     * @return Operator 状态快照
     */
    FlinkOperatorStatus queryStatus(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * 查询 JobManager 或 TaskManager Pod 是否存在.
     *
     * @param runtimeRef 运行引用
     * @return true 表示仍有运行 Pod
     */
    boolean runtimePodsExist(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * 采集日志.
     *
     * @param runtimeRef 运行引用
     * @return 日志内容
     */
    String collectLogs(FlinkKubernetesRuntimeRef runtimeRef);

    /**
     * 清理运行资源.
     *
     * @param runtimeRef 运行引用
     * @return true 表示清理完成
     */
    boolean cleanup(FlinkKubernetesRuntimeRef runtimeRef);
}
