package com.datafusion.agent.runtime.worker.plugin.spark.k8s;

import com.datafusion.agent.runtime.worker.plugin.spark.SparkExecutionParam;

/**
 * Spark Operator 客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/9
 * @since 1.0.0
 */
public interface K8sOperatorClient {

    /**
     * 提交 SparkApplication.
     *
     * @param param 执行参数
     * @return 运行引用
     */
    SparkKubernetesRuntimeRef submit(SparkExecutionParam param);

    /**
     * 停止 SparkApplication.
     *
     * @param runtimeRef 运行引用
     */
    void stop(SparkKubernetesRuntimeRef runtimeRef);

    /**
     * 强杀 SparkApplication.
     *
     * @param runtimeRef 运行引用
     */
    void kill(SparkKubernetesRuntimeRef runtimeRef);

    /**
     * 查询 Operator 状态.
     *
     * @param runtimeRef 运行引用
     * @return Operator 状态快照
     */
    SparkOperatorStatus queryStatus(SparkKubernetesRuntimeRef runtimeRef);

    /**
     * 采集日志.
     *
     * @param runtimeRef 运行引用
     * @return 日志内容
     */
    String collectLogs(SparkKubernetesRuntimeRef runtimeRef);

    /**
     * 清理资源.
     *
     * @param runtimeRef 运行引用
     * @return true 表示清理完成
     */
    boolean cleanup(SparkKubernetesRuntimeRef runtimeRef);
}
