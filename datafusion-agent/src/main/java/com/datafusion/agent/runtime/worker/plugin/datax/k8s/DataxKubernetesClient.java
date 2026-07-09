package com.datafusion.agent.runtime.worker.plugin.datax.k8s;

import com.datafusion.agent.runtime.worker.plugin.datax.DataxExecutionParam;

/**
 * DataX Kubernetes 客户端.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/8
 * @since 1.0.0
 */
public interface DataxKubernetesClient {

    /**
     * 提交 Kubernetes Job.
     *
     * @param param 执行参数
     * @return 运行时引用
     */
    DataxKubernetesRuntimeRef submit(DataxExecutionParam param);

    /**
     * 停止 Kubernetes Job.
     *
     * @param runtimeRef 运行时引用
     * @param forcibly   是否强制删除
     */
    void stop(DataxKubernetesRuntimeRef runtimeRef, boolean forcibly);

    /**
     * 查询 Kubernetes 状态.
     *
     * @param runtimeRef 运行时引用
     * @return Kubernetes 状态快照
     */
    DataxKubernetesStatus queryStatus(DataxKubernetesRuntimeRef runtimeRef);

    /**
     * 采集日志.
     *
     * @param runtimeRef 运行时引用
     * @return 日志内容
     */
    String collectLogs(DataxKubernetesRuntimeRef runtimeRef);

    /**
     * 清理资源.
     *
     * @param runtimeRef 运行时引用
     * @param mode       清理模式
     * @return true 表示清理完成
     */
    boolean cleanup(DataxKubernetesRuntimeRef runtimeRef, DataxKubernetesCleanupMode mode);
}
