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
        assertEquals(StatusEnum.RUNNING, result.getTaskState());
        assertNull(result.getWorkerResult().getMessage());
    }

    @Test
    void shouldNotExposeDuplicateSubmitMessageAsWorkerResult() {
        RecordingContextStorage contextStorage = new RecordingContextStorage();
        contextStorage.context = RunningTaskContext.fromRequest(finishRequest());
        contextStorage.context.markSubmitted();
        SuccessPluginTaskExecutor executor = new SuccessPluginTaskExecutor();
        WorkerTaskService taskService = taskService(contextStorage, executor);

        TaskResult result = taskService.submitTask(finishRequest());

        assertEquals(0, executor.submitCount);
        assertEquals(StatusEnum.RUNNING, result.getTaskState());
        assertNull(result.getWorkerResult().getMessage());
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
     * Success plugin task executor.
     */
    private static class SuccessPluginTaskExecutor implements PluginTaskExecutor {

        /**
         * Submit count.
         */
        private int submitCount;

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
            return null;
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
