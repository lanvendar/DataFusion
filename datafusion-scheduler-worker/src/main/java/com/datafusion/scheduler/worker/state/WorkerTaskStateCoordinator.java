package com.datafusion.scheduler.worker.state;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * Worker 任务执行状态协调器.
 *
 * <p>协调器集中处理动作准入、状态迁移和 revision CAS。任务执行存储只保证单次状态写入原子性，
 * 插件执行器和状态监听器不得绕过协调器直接写入状态。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
public final class WorkerTaskStateCoordinator {

    /**
     * 任务执行存储.
     */
    private final WorkerTaskExecutionStore stateStore;

    /**
     * 创建任务执行状态协调器.
     *
     * @param stateStore 任务执行存储
     */
    public WorkerTaskStateCoordinator(WorkerTaskExecutionStore stateStore) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore不能为空");
    }

    /**
     * 预留任务动作中间态.
     *
     * <p>首次动作通过 revision CAS 写入中间态。重复的 STOPPING 和 KILLING 视为恢复调用并继续执行，
     * 重复 SUBMITTING 则拒绝，避免重复创建第三方资源。
     *
     * @param snapshot     当前任务提交快照
     * @param workDirPath  当前任务工作目录
     * @param actionStatus 动作中间态
     * @return 动作预留结果及真实落盘状态
     */
    public ActionReservation reserveAction(WorkerTaskExecutionSnap snapshot, String workDirPath,
            StatusEnum actionStatus) {
        if (snapshot == null || isBlank(snapshot.getTaskInstanceId())) {
            throw new IllegalArgumentException("snapshot.taskInstanceId不能为空");
        }
        if (!isActionStatus(actionStatus)) {
            throw new IllegalArgumentException("不支持的动作状态: " + actionStatus);
        }
        String taskInstanceId = snapshot.getTaskInstanceId();
        WorkerTaskExecutionState current = stateStore.readState(taskInstanceId).orElse(null);
        if (current != null && current.getStatus() == actionStatus
                && (actionStatus == StatusEnum.STOPPING || actionStatus == StatusEnum.KILLING)) {
            return new ActionReservation(true, current.copy());
        }
        if (!canStartAction(current == null ? null : current.getStatus(), actionStatus)) {
            return new ActionReservation(false, current == null ? null : current.copy());
        }

        WorkerTaskExecutionState candidate = current == null
                ? WorkerTaskExecutionState.builder().taskInstanceId(taskInstanceId).build() : current.copy();
        candidate.setTaskInstanceId(taskInstanceId);
        candidate.setWorkerId(firstText(snapshot.getWorkerId(), candidate.getWorkerId()));
        candidate.setWorkDirPath(firstText(workDirPath, candidate.getWorkDirPath()));
        candidate.setStatus(actionStatus);
        if (actionStatus == StatusEnum.SUBMITTING) {
            candidate.setAppId(null);
            candidate.setExitCode(null);
            candidate.setResult(null);
            candidate.setOutputVars(null);
        }
        long expectedRevision = current == null ? 0L : current.getRevision();
        if (stateStore.saveState(candidate, expectedRevision)) {
            return new ActionReservation(true, readRequired(taskInstanceId));
        }

        WorkerTaskExecutionState latest = readRequired(taskInstanceId);
        boolean recoverable = latest.getStatus() == actionStatus
                && (actionStatus == StatusEnum.STOPPING || actionStatus == StatusEnum.KILLING);
        return new ActionReservation(recoverable, latest);
    }

    /**
     * 提交插件同步动作结果.
     *
     * <p>目标状态取自 {@link RunningTaskContext#getExecutionState()}。写入使用该候选状态携带的 revision，
     * CAS 失败时返回当前真实状态，避免覆盖监听器已经提交的更新。
     *
     * @param context      任务动作上下文
     * @param workerResult 插件执行结果
     * @return 提交后的真实任务执行状态
     */
    public WorkerTaskExecutionState commitActionResult(RunningTaskContext context, WorkerResult workerResult) {
        if (context == null || context.getExecutionState() == null) {
            throw new IllegalArgumentException("context.executionState不能为空");
        }
        WorkerTaskExecutionState candidate = context.getExecutionState().copy();
        String taskInstanceId = candidate.getTaskInstanceId();
        if (isBlank(taskInstanceId)) {
            throw new IllegalArgumentException("executionState.taskInstanceId不能为空");
        }
        WorkerTaskExecutionState current = readRequired(taskInstanceId);
        if (current.getRevision() != candidate.getRevision()) {
            return current;
        }
        if (!canApplyActionResult(current.getStatus(), candidate.getStatus())) {
            return current;
        }
        mergeWorkerResult(context.getSnapshot(), candidate, workerResult);
        if (candidate.equals(current)) {
            return current;
        }
        stateStore.saveState(candidate, current.getRevision());
        return readRequired(taskInstanceId);
    }

    /**
     * 提交第三方状态映射结果.
     *
     * <p>映射结果严格基于查询基线 revision 提交；查询期间状态变化时直接返回最新状态，
     * 调用方应丢弃本次映射并在下一周期重新查询。
     *
     * @param baseline  查询状态基线
     * @param candidate 映射后的状态候选副本
     * @return 提交后或冲突后的真实任务执行状态
     */
    public WorkerTaskExecutionState commitMappedState(WorkerTaskExecutionState baseline,
            WorkerTaskExecutionState candidate) {
        validateCandidatePair(baseline, candidate);
        String taskInstanceId = baseline.getTaskInstanceId();
        WorkerTaskExecutionState current = readRequired(taskInstanceId);
        if (current.getRevision() != baseline.getRevision()) {
            return current;
        }
        WorkerTaskExecutionState mappedState = candidate.copy();
        mappedState.setRevision(baseline.getRevision());
        if (current.getStatus() == mappedState.getStatus()) {
            return current;
        }
        if (!canApplyMappedState(current.getStatus(), mappedState.getStatus())) {
            return current;
        }
        if (mappedState.equals(current)) {
            return current;
        }
        stateStore.saveState(mappedState, baseline.getRevision());
        return readRequired(taskInstanceId);
    }

    /**
     * 提交终态上报前补充的结果信息.
     *
     * <p>该操作不改变任务状态，只允许在同一终态和同一 revision 上补充日志入口等最终结果。
     * 周期映射状态相同时仍不调用本方法，避免稳定状态无意义增加 revision。
     *
     * @param baseline  终态基线
     * @param candidate 补充结果后的终态候选副本
     * @return 提交后或冲突后的真实任务执行状态
     */
    public WorkerTaskExecutionState commitFinalReport(WorkerTaskExecutionState baseline,
            WorkerTaskExecutionState candidate) {
        validateCandidatePair(baseline, candidate);
        if (baseline.getStatus() == null || !baseline.getStatus().isFinalState()
                || candidate.getStatus() != baseline.getStatus()) {
            throw new IllegalArgumentException("只允许补充相同终态的上报结果");
        }
        String taskInstanceId = baseline.getTaskInstanceId();
        WorkerTaskExecutionState current = readRequired(taskInstanceId);
        if (current.getRevision() != baseline.getRevision() || current.getStatus() != baseline.getStatus()) {
            return current;
        }
        WorkerTaskExecutionState finalState = candidate.copy();
        finalState.setRevision(current.getRevision());
        if (finalState.equals(current)) {
            return current;
        }
        stateStore.saveState(finalState, current.getRevision());
        return readRequired(taskInstanceId);
    }

    /**
     * 提交 LOCAL 进程退出结果.
     *
     * <p>进程退出是不可重复查询的一次性事实。原提交 revision 尚未推进时允许退出终态先于 SUBMIT_SUCCESS 落盘；
     * revision 已推进时仅在 appId 仍指向同一进程的情况下重试。当前为 STOPPING 或 KILLING 时，控制意图优先于退出码。
     *
     * @param actionState  进程启动动作状态
     * @param exitCandidate 进程退出状态候选副本
     * @return 提交后或丢弃过期事件后的真实任务执行状态
     */
    public WorkerTaskExecutionState commitLocalProcessExit(WorkerTaskExecutionState actionState,
            WorkerTaskExecutionState exitCandidate) {
        validateCandidatePair(actionState, exitCandidate);
        String taskInstanceId = actionState.getTaskInstanceId();
        while (true) {
            WorkerTaskExecutionState current = readRequired(taskInstanceId);
            if (current.getStatus() != null && current.getStatus().isFinalState()) {
                return current;
            }
            boolean originalAction = current.getRevision() == actionState.getRevision()
                    && current.getStatus() == actionState.getStatus();
            boolean sameRuntime = !isBlank(current.getAppId())
                    && current.getAppId().equals(exitCandidate.getAppId());
            if (!originalAction && !sameRuntime) {
                return current;
            }

            WorkerTaskExecutionState candidate = current.copy();
            mergeExitCandidate(candidate, exitCandidate);
            if (current.getStatus() == StatusEnum.STOPPING) {
                candidate.setStatus(StatusEnum.STOP_SUCCESS);
            } else if (current.getStatus() == StatusEnum.KILLING) {
                candidate.setStatus(StatusEnum.KILLED);
            }
            candidate.setRevision(current.getRevision());
            if (!canApplyLocalExit(current.getStatus(), candidate.getStatus())) {
                return current;
            }
            if (stateStore.saveState(candidate, current.getRevision())) {
                return readRequired(taskInstanceId);
            }
        }
    }

    private void mergeWorkerResult(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState candidate,
            WorkerResult workerResult) {
        if (workerResult == null) {
            return;
        }
        if (workerResult.getWorkerId() != null) {
            candidate.setWorkerId(workerResult.getWorkerId());
        }
        if (workerResult.getAppId() != null) {
            candidate.setAppId(workerResult.getAppId());
        }
        if (workerResult.getWorkDirPath() != null) {
            candidate.setWorkDirPath(workerResult.getWorkDirPath());
        }
        if (workerResult.getOutputVars() != null) {
            candidate.setOutputVars(workerResult.getOutputVars());
        }
        ObjectNode result = resultObject(candidate.getResult());
        putIfNotBlank(result, "message", workerResult.getMessage());
        putIfNotBlank(result, "pluginLogUri", workerResult.getPluginLogUri());
        if (snapshot != null) {
            putIfNotBlank(result, "pluginType", snapshot.getPluginType());
            putIfNotBlank(result, "runMode", snapshot.getRunMode());
        }
        if (!result.isEmpty()) {
            candidate.setResult(result);
        }
    }

    private void mergeExitCandidate(WorkerTaskExecutionState target, WorkerTaskExecutionState source) {
        target.setStatus(source.getStatus());
        if (source.getWorkerId() != null) {
            target.setWorkerId(source.getWorkerId());
        }
        if (source.getAppId() != null) {
            target.setAppId(source.getAppId());
        }
        if (source.getWorkDirPath() != null) {
            target.setWorkDirPath(source.getWorkDirPath());
        }
        if (source.getExitCode() != null) {
            target.setExitCode(source.getExitCode());
        }
        if (source.getResult() != null) {
            target.setResult(source.getResult().deepCopy());
        }
        if (source.getOutputVars() != null) {
            target.setOutputVars(source.copy().getOutputVars());
        }
    }

    private ObjectNode resultObject(JsonNode source) {
        return source != null && source.isObject()
                ? (ObjectNode) source.deepCopy() : JacksonUtils.createObjectNode();
    }

    private void putIfNotBlank(ObjectNode node, String field, String value) {
        if (!isBlank(value)) {
            node.put(field, value);
        }
    }

    private void validateCandidatePair(WorkerTaskExecutionState baseline, WorkerTaskExecutionState candidate) {
        if (baseline == null || candidate == null || isBlank(baseline.getTaskInstanceId())
                || !baseline.getTaskInstanceId().equals(candidate.getTaskInstanceId())) {
            throw new IllegalArgumentException("状态基线与候选状态的 taskInstanceId 不一致");
        }
    }

    private WorkerTaskExecutionState readRequired(String taskInstanceId) {
        return stateStore.readState(taskInstanceId)
                .orElseThrow(() -> new IllegalStateException("任务执行状态不存在: " + taskInstanceId));
    }

    private boolean canStartAction(StatusEnum currentStatus, StatusEnum actionStatus) {
        if (currentStatus == null) {
            return actionStatus == StatusEnum.SUBMITTING;
        }
        return switch (actionStatus) {
            case SUBMITTING -> currentStatus == StatusEnum.SUBMIT_FAILURE
                    || currentStatus == StatusEnum.RUN_FAILURE || currentStatus == StatusEnum.STOP_SUCCESS
                    || currentStatus == StatusEnum.STOP_FAILURE || currentStatus == StatusEnum.KILLED;
            case STOPPING -> currentStatus == StatusEnum.SUBMIT_SUCCESS
                    || currentStatus == StatusEnum.SUBMIT_FAILURE || currentStatus == StatusEnum.RUNNING;
            case KILLING -> currentStatus == StatusEnum.STOP_FAILURE || currentStatus == StatusEnum.UNKNOWN;
            default -> false;
        };
    }

    private boolean canApplyActionResult(StatusEnum currentStatus, StatusEnum nextStatus) {
        if (currentStatus == null || nextStatus == null) {
            return false;
        }
        return switch (currentStatus) {
            case SUBMITTING -> nextStatus == StatusEnum.SUBMIT_SUCCESS || nextStatus == StatusEnum.SUBMIT_FAILURE;
            case STOPPING -> nextStatus == StatusEnum.STOPPING || nextStatus == StatusEnum.STOP_SUCCESS
                    || nextStatus == StatusEnum.STOP_FAILURE;
            case KILLING -> nextStatus == StatusEnum.KILLING || nextStatus == StatusEnum.KILLED
                    || nextStatus == StatusEnum.UNKNOWN;
            default -> false;
        };
    }

    private boolean canApplyMappedState(StatusEnum currentStatus, StatusEnum nextStatus) {
        if (currentStatus == null || nextStatus == null || currentStatus.isFinalState()) {
            return false;
        }
        if (currentStatus == nextStatus) {
            return true;
        }
        return switch (currentStatus) {
            case SUBMITTING, SUBMIT_SUCCESS -> nextStatus == StatusEnum.RUNNING
                    || nextStatus == StatusEnum.RUN_SUCCESS || nextStatus == StatusEnum.SUBMIT_FAILURE
                    || nextStatus == StatusEnum.RUN_FAILURE || nextStatus == StatusEnum.UNKNOWN;
            case RUNNING -> nextStatus == StatusEnum.RUN_SUCCESS || nextStatus == StatusEnum.RUN_FAILURE
                    || nextStatus == StatusEnum.UNKNOWN;
            case STOPPING -> nextStatus == StatusEnum.STOP_SUCCESS || nextStatus == StatusEnum.STOP_FAILURE
                    || nextStatus == StatusEnum.UNKNOWN;
            case KILLING -> nextStatus == StatusEnum.KILLED || nextStatus == StatusEnum.UNKNOWN;
            default -> false;
        };
    }

    private boolean canApplyLocalExit(StatusEnum currentStatus, StatusEnum nextStatus) {
        if (currentStatus == StatusEnum.STOPPING) {
            return nextStatus == StatusEnum.STOP_SUCCESS;
        }
        if (currentStatus == StatusEnum.KILLING) {
            return nextStatus == StatusEnum.KILLED;
        }
        return currentStatus == StatusEnum.SUBMITTING || currentStatus == StatusEnum.SUBMIT_SUCCESS
                || currentStatus == StatusEnum.RUNNING;
    }

    private boolean isActionStatus(StatusEnum status) {
        return status == StatusEnum.SUBMITTING || status == StatusEnum.STOPPING || status == StatusEnum.KILLING;
    }

    private String firstText(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 任务动作中间态预留结果.
     *
     * @param accepted       是否允许执行插件动作
     * @param executionState 预留后或拒绝时的真实任务执行状态
     * @author datafusion
     * @version 1.0.0, 2026/7/18
     * @since 1.0.0
     */
    public record ActionReservation(boolean accepted, WorkerTaskExecutionState executionState) {
    }
}
