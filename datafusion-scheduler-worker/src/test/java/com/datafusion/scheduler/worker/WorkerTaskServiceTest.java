package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link WorkerTaskService}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/22
 * @since 1.0.0
 */
class WorkerTaskServiceTest {

    @Test
    void shouldRouteSamePluginTypeByRunMode() {
        SuccessPluginTaskExecutor local = new SuccessPluginTaskExecutor("LOCAL");
        SuccessPluginTaskExecutor k8s = new SuccessPluginTaskExecutor("K8S");
        WorkerTaskOperatorRouter router = new WorkerTaskOperatorRouter(List.of(local, k8s));

        assertSame(local, router.route("TEST", "LOCAL"));
        assertSame(local, router.route(" test ", "local"));
        assertSame(k8s, router.route("TEST", "K8S"));
        assertEquals(List.of("TEST"), new ArrayList<>(router.pluginTypes()));
        assertThrows(IllegalArgumentException.class, () -> new WorkerTaskOperatorRouter(
                List.of(local, new SuccessPluginTaskExecutor("local"))));
    }

    @Test
    void shouldRemoveContextWhenFinishContextMissing() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        WorkerTaskService taskService = taskService(contextStorage, new SuccessPluginTaskExecutor());

        boolean result = taskService.finishTask(finishRequest());

        assertEquals(true, result);
        assertEquals(List.of("get:task-1", "remove:task-1"), contextStorage.getOperations());
    }

    @Test
    void shouldRemoveContextWhenStopReturnsFinalState() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        WorkerTaskService taskService = taskService(contextStorage, new SuccessPluginTaskExecutor());

        TaskResult result = taskService.stopTask(finishRequest());

        assertEquals(StatusEnum.STOP_SUCCESS, result.getTaskState());
        assertEquals(List.of("get:task-1", "save:STOPPING", "save:STOP_SUCCESS", "remove:task-1"),
                contextStorage.getOperations());
    }

    @Test
    void shouldRetryPluginActionForRecoveryStop() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.setTaskState(StatusEnum.STOPPING);
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.stopTask(finishRequest());

        assertEquals(StatusEnum.STOP_SUCCESS, result.getTaskState());
        assertEquals(1, executor.stopCount);
    }

    @Test
    void shouldSubmitFirstRequestWhenRequestStateIsSubmitting() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);
        TaskRequest request = finishRequest();
        request.setTaskState(StatusEnum.SUBMITTING);

        TaskResult result = taskService.submitTask(request);

        assertEquals(1, executor.submitCount);
        assertEquals(StatusEnum.SUBMIT_SUCCESS, result.getTaskState());
        assertNull(result.getWorkerResult().getMessage());
    }

    @Test
    void shouldReturnDuplicateMessageWhenContextStateIsSubmitting() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.setTaskState(StatusEnum.SUBMITTING);
        contextStorage.context.setWorkDirPath("/opt/datafusion/task-runtime/20260624/flow-1/task-1");
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.submitTask(finishRequest());

        assertEquals(0, executor.submitCount);
        assertEquals(StatusEnum.SUBMITTING, result.getTaskState());
        assertEquals("当前状态不允许进入SUBMITTING", result.getWorkerResult().getMessage());
    }

    @Test
    void shouldReserveSubmittingContextBeforeSyncExecutorStarts() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);
        TaskRequest request = finishRequest();
        request.setTaskState(StatusEnum.SUBMITTING);

        taskService.submitTask(request);

        assertEquals(1, executor.submitCount);
        assertEquals(List.of("create:task-1", "save:SUBMITTING", "save:SUBMIT_SUCCESS"),
                contextStorage.getOperations());
    }

    @Test
    void shouldReturnDuplicateMessageWhenContextStateIsSubmitSuccess() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.setTaskState(StatusEnum.SUBMIT_SUCCESS);
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.submitTask(finishRequest());

        assertEquals(0, executor.submitCount);
        assertEquals(StatusEnum.SUBMIT_SUCCESS, result.getTaskState());
        assertEquals("当前状态不允许进入SUBMITTING", result.getWorkerResult().getMessage());
    }

    @Test
    void shouldNotExecuteSubmitForExistingRuntimeState() {
        for (StatusEnum status : List.of(StatusEnum.RUNNING, StatusEnum.STOPPING, StatusEnum.KILLING,
                StatusEnum.RUN_SUCCESS)) {
            RecordingContextStorage contextStorage = new RecordingContextStorage();
            contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
            contextStorage.context.setTaskState(status);
            SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
            WorkerTaskService taskService = taskService(contextStorage, executor);

            TaskResult result = taskService.submitTask(finishRequest());

            assertEquals(0, executor.submitCount);
            assertEquals(status, result.getTaskState());
            assertEquals("当前状态不允许进入SUBMITTING", result.getWorkerResult().getMessage());
        }
    }

    @Test
    void shouldNotExecuteSubmitWhenOnlyPersistedStateExists() {
        LockingContextStorage contextStorage = new LockingContextStorage();
        contextStorage.persistedState = WorkerTaskExecutionState.builder()
                .taskInstanceId("task-1")
                .status(StatusEnum.RUNNING)
                .build();
        contextStorage.persistedSnapshot = WorkerTaskExecutionSnap.builder()
                .flowInstanceId("flow-1")
                .taskInstanceId("task-1")
                .taskName("task-name")
                .pluginType("TEST")
                .runMode("LOCAL")
                .build();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.submitTask(finishRequest());

        assertEquals(0, executor.submitCount);
        assertEquals(StatusEnum.RUNNING, result.getTaskState());
        assertEquals("当前状态不允许进入SUBMITTING", result.getWorkerResult().getMessage());
    }

    @Test
    void shouldReturnSubmittingAndQueueExecutorWhenSubmitModeIsAsync() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        RecordingExecutor asyncExecutor = new RecordingExecutor();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskOperatorRouter router = new WorkerTaskOperatorRouter(List.of(executor));
        WorkerTaskService taskService = new WorkerTaskService(router, contextStorage, asyncExecutor,
                SubmitModeEnum.SYNC);
        TaskRequest request = finishRequest();
        request.setSubmitMode(SubmitModeEnum.ASYNC);

        TaskResult result = taskService.submitTask(request);

        assertEquals(StatusEnum.SUBMITTING, result.getTaskState());
        assertNull(result.getWorkerResult().getMessage());
        assertEquals(0, executor.submitCount);
        assertEquals(1, asyncExecutor.tasks.size());
        assertEquals(List.of("create:task-1", "save:SUBMITTING"),
                contextStorage.getOperations());
    }

    @Test
    void shouldRejectStopWhileAsyncSubmitIsStillSubmitting() {
        LockingContextStorage contextStorage = new LockingContextStorage();
        RecordingExecutor asyncExecutor = new RecordingExecutor();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor(contextStorage);
        executor.stopState = StatusEnum.STOPPING;
        WorkerTaskService taskService = new WorkerTaskService(new WorkerTaskOperatorRouter(List.of(executor)),
                contextStorage, asyncExecutor, SubmitModeEnum.SYNC);
        TaskRequest request = finishRequest();
        request.setSubmitMode(SubmitModeEnum.ASYNC);

        taskService.submitTask(request);
        taskService.stopTask(finishRequest());
        asyncExecutor.tasks.get(0).run();

        assertEquals(StatusEnum.SUBMIT_SUCCESS, ((RecordingContextStorage) contextStorage).context.getTaskState());
        assertEquals(1, executor.submitCount);
        assertEquals(0, executor.stopCount);
    }

    @Test
    void shouldReturnCanonicalTerminalStateAfterSubmit() {
        LockingContextStorage contextStorage = new LockingContextStorage();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor(contextStorage);
        executor.persistedSubmitState = StatusEnum.RUN_SUCCESS;
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.submitTask(finishRequest());

        assertEquals(StatusEnum.RUN_SUCCESS, result.getTaskState());
        assertEquals(StatusEnum.RUN_SUCCESS, contextStorage.persistedState.getStatus());
        assertEquals(1, contextStorage.snapshotSaveCount);
        assertEquals(SubmitModeEnum.SYNC, contextStorage.persistedSnapshot.getSubmitMode());
    }

    @Test
    void shouldNotOverwriteNewerTerminalStateWhenPluginThrows() {
        LockingContextStorage contextStorage = new LockingContextStorage();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor(contextStorage);
        executor.persistedSubmitState = StatusEnum.RUN_SUCCESS;
        executor.submitException = new IllegalStateException("submit response failed");
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.submitTask(finishRequest());

        assertEquals(StatusEnum.RUN_SUCCESS, result.getTaskState());
        assertEquals(StatusEnum.RUN_SUCCESS, contextStorage.persistedState.getStatus());
    }

    @Test
    void shouldInvokeKillExecutorWhenContextStateIsUnknown() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.setTaskState(StatusEnum.UNKNOWN);
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        executor.killState = StatusEnum.KILLED;
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.killTask(finishRequest());

        assertEquals(1, executor.killCount);
        assertEquals(StatusEnum.KILLED, result.getTaskState());
        assertEquals(List.of("get:task-1", "save:KILLING", "save:KILLED", "remove:task-1"),
                contextStorage.getOperations());
    }

    @Test
    void shouldRejectStopAndKillAfterRunSuccess() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.setTaskState(StatusEnum.RUN_SUCCESS);
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult stopResult = taskService.stopTask(finishRequest());
        TaskResult killResult = taskService.killTask(finishRequest());

        assertEquals(StatusEnum.RUN_SUCCESS, stopResult.getTaskState());
        assertEquals(StatusEnum.RUN_SUCCESS, killResult.getTaskState());
        assertEquals(0, executor.stopCount);
        assertEquals(0, executor.killCount);
    }

    private WorkerTaskService taskService(RecordingContextStorage contextStorage, PluginTaskExecutor executor) {
        WorkerTaskOperatorRouter router = new WorkerTaskOperatorRouter(List.of(executor));
        Executor sameThreadExecutor = Runnable::run;
        return new WorkerTaskService(router, contextStorage, sameThreadExecutor, SubmitModeEnum.SYNC);
    }

    private TaskRequest finishRequest() {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("task-name");
        request.setPluginType("TEST");
        request.setRunMode("LOCAL");
        return request;
    }

    /**
     * Recording context storage.
     */
    private static class RecordingContextStorage implements WorkerTaskContextStorage {

        /**
         * Operations.
         */
        private final List<String> operations = new ArrayList<>();

        /**
         * Context.
         */
        private RunningTaskContext context;

        @Override
        public RunningTaskContext get(String taskInstanceId) {
            operations.add("get:" + taskInstanceId);
            return context;
        }

        @Override
        public RunningTaskContext getOrCreate(TaskRequest request) {
            operations.add("create:" + request.getTaskInstanceId());
            if (context == null) {
                context = RunningTaskContext.fromRequest(request);
            }
            return context;
        }

        @Override
        public void save(RunningTaskContext context) {
            operations.add("save:" + context.getTaskState());
            this.context = context;
        }

        @Override
        public void removeContext(String taskInstanceId) {
            operations.add("remove:" + taskInstanceId);
            context = null;
        }

        List<String> getOperations() {
            return operations;
        }
    }

    /**
     * Recording context storage with revision CAS.
     */
    private static class LockingContextStorage extends RecordingContextStorage implements WorkerTaskExecutionStore {

        /**
         * Persisted snapshot.
         */
        private WorkerTaskExecutionSnap persistedSnapshot;

        /**
         * Persisted state.
         */
        private WorkerTaskExecutionState persistedState;

        /**
         * Snapshot save count.
         */
        private int snapshotSaveCount;

        @Override
        public void saveSnapshot(WorkerTaskExecutionSnap snapshot) {
            snapshotSaveCount++;
            persistedSnapshot = snapshot;
        }

        @Override
        public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
            return Optional.ofNullable(persistedSnapshot);
        }

        @Override
        public boolean saveState(WorkerTaskExecutionState state, long expectedRevision) {
            long currentRevision = persistedState == null ? 0L : persistedState.getRevision();
            if (currentRevision != expectedRevision) {
                return false;
            }
            state.setRevision(currentRevision + 1L);
            persistedState = copyState(state);
            if (super.context != null) {
                super.context.setExecutionState(copyState(state));
            }
            return true;
        }

        @Override
        public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
            return Optional.ofNullable(persistedState).map(LockingContextStorage::copyState);
        }

        @Override
        public void deleteExecution(String taskInstanceId) {
            removeContext(taskInstanceId);
        }

        private static WorkerTaskExecutionState copyState(WorkerTaskExecutionState state) {
            return WorkerTaskExecutionState.builder()
                    .taskInstanceId(state.getTaskInstanceId())
                    .workerId(state.getWorkerId())
                    .appId(state.getAppId())
                    .workDirPath(state.getWorkDirPath())
                    .status(state.getStatus())
                    .revision(state.getRevision())
                    .exitCode(state.getExitCode())
                    .updateTime(state.getUpdateTime())
                    .result(state.getResult())
                    .outputVars(state.getOutputVars())
                    .build();
        }

    }

    /**
     * Recording executor.
     */
    private static class RecordingExecutor implements Executor {

        /**
         * Tasks.
         */
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }

    /**
     * Success plugin task executor.
     */
    private static class SuccessPluginTaskExecutor implements PluginTaskExecutor {

        /**
         * Run mode.
         */
        private final String runMode;

        /**
         * Execution state store.
         */
        private final WorkerTaskExecutionStore stateStore;

        SuccessPluginTaskExecutor() {
            this("LOCAL", null);
        }

        SuccessPluginTaskExecutor(String runMode) {
            this(runMode, null);
        }

        SuccessPluginTaskExecutor(WorkerTaskExecutionStore stateStore) {
            this("LOCAL", stateStore);
        }

        SuccessPluginTaskExecutor(String runMode, WorkerTaskExecutionStore stateStore) {
            this.runMode = runMode;
            this.stateStore = stateStore;
        }

        /**
         * Submit count.
         */
        private int submitCount;

        /**
         * Stop count.
         */
        private int stopCount;

        /**
         * Kill count.
         */
        private int killCount;

        /**
         * Kill state.
         */
        private StatusEnum killState;

        /**
         * Stop state.
         */
        private StatusEnum stopState = StatusEnum.STOP_SUCCESS;

        /**
         * State persisted by submit.
         */
        private StatusEnum persistedSubmitState = StatusEnum.SUBMIT_SUCCESS;

        /**
         * Exception thrown after submit state persistence.
         */
        private RuntimeException submitException;

        @Override
        public String pluginType() {
            return "TEST";
        }

        @Override
        public String runMode() {
            return runMode;
        }

        @Override
        public TaskResult submitTask(TaskRequest request) {
            submitCount++;
            persist(request, persistedSubmitState);
            if (submitException != null) {
                throw submitException;
            }
            return null;
        }

        @Override
        public TaskResult stopTask(TaskRequest request) {
            stopCount++;
            persist(request, stopState);
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(stopState)
                    .build();
        }

        @Override
        public TaskResult killTask(TaskRequest request) {
            killCount++;
            persist(request, killState);
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(killState)
                    .build();
        }

        @Override
        public boolean finishTask(TaskRequest request) {
            return true;
        }

        private void persist(TaskRequest request, StatusEnum status) {
            if (stateStore == null) {
                return;
            }
            WorkerTaskExecutionState state = stateStore.readState(request.getTaskInstanceId()).orElseThrow();
            state.setStatus(status);
            stateStore.saveState(state, state.getRevision());
        }
    }
}
