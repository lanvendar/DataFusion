package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerTaskOperatorRouter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link WorkerTaskService}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/22
 * @since 1.0.0
 */
class WorkerTaskServiceTest {

    @Test
    void shouldPersistFinalStateBeforeRemovingContextWhenFinishContextMissing() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        WorkerTaskService taskService = taskService(contextStorage, new SuccessPluginTaskExecutor());

        TaskResult result = taskService.finishTask(finishRequest());

        assertEquals(StatusEnum.RUN_SUCCESS, result.getTaskState());
        assertEquals(List.of("get:task-1", "create:task-1", "save:RUN_SUCCESS", "remove:task-1"),
                contextStorage.getOperations());
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
        WorkerTaskService taskService = new WorkerTaskService(router, contextStorage, null, asyncExecutor,
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
        return new WorkerTaskService(router, contextStorage, null, sameThreadExecutor, SubmitModeEnum.SYNC);
    }

    private TaskRequest finishRequest() {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId("task-1");
        request.setTaskName("task-name");
        request.setPluginType("TEST");
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
         * Submit count.
         */
        private int submitCount;

        /**
         * Kill count.
         */
        private int killCount;

        /**
         * Kill state.
         */
        private StatusEnum killState;

        @Override
        public String pluginType() {
            return "TEST";
        }

        @Override
        public TaskResult submitTask(TaskRequest request) {
            submitCount++;
            return null;
        }

        @Override
        public TaskResult stopTask(TaskRequest request) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.STOP_SUCCESS)
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
        public TaskResult finishTask(TaskRequest request) {
            return TaskResult.builder()
                    .taskInstanceId(request.getTaskInstanceId())
                    .flowInstanceId(request.getFlowInstanceId())
                    .taskName(request.getTaskName())
                    .taskState(StatusEnum.RUN_SUCCESS)
                    .build();
        }
    }
}
