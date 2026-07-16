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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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
        assertEquals(List.of("get:task-1", "save:STOP_SUCCESS", "remove:task-1"),
                contextStorage.getOperations());
    }

    @Test
    void shouldReturnCurrentStateForRepeatedStop() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.setTaskState(StatusEnum.STOPPING);
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.stopTask(finishRequest());

        assertEquals(StatusEnum.STOPPING, result.getTaskState());
        assertEquals(0, executor.stopCount);
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
        assertEquals("重复提交", result.getWorkerResult().getMessage());
    }

    @Test
    void shouldNotSaveSubmittingContextBeforeSyncExecutorStarts() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);
        TaskRequest request = finishRequest();
        request.setTaskState(StatusEnum.SUBMITTING);

        taskService.submitTask(request);

        assertEquals(1, executor.submitCount);
        assertEquals(List.of("get:task-1", "create:task-1", "save:SUBMIT_SUCCESS"),
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
        assertEquals("重复提交", result.getWorkerResult().getMessage());
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
        assertEquals(List.of("get:task-1", "create:task-1", "save:SUBMITTING"),
                contextStorage.getOperations());
    }

    @Test
    void shouldKeepStoppingWhenAsyncSubmitCompletesLater() {
        LockingContextStorage contextStorage = new LockingContextStorage();
        RecordingExecutor asyncExecutor = new RecordingExecutor();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        executor.stopState = StatusEnum.STOPPING;
        WorkerTaskService taskService = new WorkerTaskService(new WorkerTaskOperatorRouter(List.of(executor)),
                contextStorage, asyncExecutor, SubmitModeEnum.SYNC);
        TaskRequest request = finishRequest();
        request.setSubmitMode(SubmitModeEnum.ASYNC);

        taskService.submitTask(request);
        taskService.stopTask(finishRequest());
        asyncExecutor.tasks.get(0).run();

        assertEquals(StatusEnum.STOPPING, ((RecordingContextStorage) contextStorage).context.getTaskState());
        assertEquals(0, executor.submitCount);
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
        assertEquals(List.of("get:task-1", "save:KILLED", "remove:task-1"),
                contextStorage.getOperations());
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
     * Recording context storage with a task lock.
     */
    private static class LockingContextStorage extends RecordingContextStorage implements WorkerTaskExecutionStore {

        /**
         * Task lock.
         */
        private final ReentrantLock taskLock = new ReentrantLock();

        @Override
        public void saveSnapshot(WorkerTaskExecutionSnap snapshot) {
        }

        @Override
        public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
            return Optional.empty();
        }

        @Override
        public void saveState(WorkerTaskExecutionState state) {
        }

        @Override
        public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
            return Optional.empty();
        }

        @Override
        public void deleteExecution(String taskInstanceId) {
            removeContext(taskInstanceId);
        }

        @Override
        public <T> T withTaskLock(String taskInstanceId, Supplier<T> action) {
            taskLock.lock();
            try {
                return action.get();
            } finally {
                taskLock.unlock();
            }
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

        SuccessPluginTaskExecutor() {
            this("LOCAL");
        }

        SuccessPluginTaskExecutor(String runMode) {
            this.runMode = runMode;
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
            return null;
        }

        @Override
        public TaskResult stopTask(TaskRequest request) {
            stopCount++;
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
    }
}
