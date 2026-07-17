package com.datafusion.scheduler.worker.context;

/**
 * Worker 单次任务动作上下文.
 *
 * <p>上下文聚合当前提交快照、插件可修改的执行状态候选副本，以及重新提交前的历史快照和状态。
 * 对象只在一次 submit、stop、kill 或 finish 调用期间有效，不参与缓存和持久化。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public final class RunningTaskContext {

    /**
     * 当前任务提交快照.
     */
    private final WorkerTaskExecutionSnap snapshot;

    /**
     * 插件可修改的执行状态候选副本.
     */
    private final WorkerTaskExecutionState executionState;

    /**
     * 重新提交前的任务提交快照.
     */
    private final WorkerTaskExecutionSnap previousSnapshot;

    /**
     * 重新提交前的任务执行状态.
     */
    private final WorkerTaskExecutionState previousState;

    /**
     * 当前任务工作目录.
     */
    private final String workDirPath;

    /**
     * 创建单次任务动作上下文.
     *
     * @param snapshot         当前任务提交快照
     * @param executionState   插件可修改的执行状态候选副本
     * @param previousSnapshot 重新提交前的任务提交快照
     * @param previousState    重新提交前的任务执行状态
     * @param workDirPath      当前任务工作目录
     */
    public RunningTaskContext(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState executionState,
            WorkerTaskExecutionSnap previousSnapshot, WorkerTaskExecutionState previousState, String workDirPath) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot不能为空");
        }
        if (executionState == null) {
            throw new IllegalArgumentException("executionState不能为空");
        }
        this.snapshot = snapshot;
        this.executionState = executionState;
        this.previousSnapshot = previousSnapshot;
        this.previousState = previousState;
        this.workDirPath = workDirPath;
    }

    /**
     * 获取当前任务提交快照.
     *
     * @return 当前任务提交快照
     */
    public WorkerTaskExecutionSnap getSnapshot() {
        return snapshot;
    }

    /**
     * 获取插件可修改的执行状态候选副本.
     *
     * @return 执行状态候选副本
     */
    public WorkerTaskExecutionState getExecutionState() {
        return executionState;
    }

    /**
     * 获取重新提交前的任务提交快照.
     *
     * @return 历史任务提交快照
     */
    public WorkerTaskExecutionSnap getPreviousSnapshot() {
        return previousSnapshot;
    }

    /**
     * 获取重新提交前的任务执行状态.
     *
     * @return 历史任务执行状态
     */
    public WorkerTaskExecutionState getPreviousState() {
        return previousState;
    }

    /**
     * 获取当前任务工作目录.
     *
     * @return 当前任务工作目录
     */
    public String getWorkDirPath() {
        return workDirPath;
    }

    /**
     * 获取任务实例 ID.
     *
     * @return 任务实例 ID
     */
    public String getTaskInstanceId() {
        return snapshot.getTaskInstanceId() == null
                ? executionState.getTaskInstanceId() : snapshot.getTaskInstanceId();
    }
}
