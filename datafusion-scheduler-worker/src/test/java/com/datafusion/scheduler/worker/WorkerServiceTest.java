package com.datafusion.scheduler.worker;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.enums.SubmitModeEnum;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.Worker;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.worker.client.WorkerClient;
import com.datafusion.scheduler.worker.client.WorkerIdentityStore;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.datafusion.scheduler.worker.plugin.PluginRunModeStateMapping;
import com.datafusion.scheduler.worker.plugin.PluginTaskExecutor;
import com.datafusion.scheduler.worker.plugin.WorkerPluginRouter;
import com.datafusion.scheduler.worker.reporter.TaskStateListenerRegistry;
import com.datafusion.scheduler.worker.state.WorkerTaskStateCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link WorkerService}.
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
 * @since 1.0.0
 */
class WorkerServiceTest {

    /** 测试使用的心跳调度器. */
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void shutdownScheduler() {
        heartbeatScheduler.shutdownNow();
    }

    @Test
    void shouldCommitSyncSubmitAndReturnPersistedState() {
        TestFixture fixture = new TestFixture(Runnable::run);

        TaskResult result = fixture.service.submitTask(request(SubmitModeEnum.SYNC));

        assertEquals(StatusEnum.SUBMIT_SUCCESS, result.getTaskState());
        assertEquals("app-1", result.getWorkerResult().getAppId());
        assertEquals(StatusEnum.SUBMIT_SUCCESS, fixture.store.readState("task-1").orElseThrow().getStatus());
        assertEquals(List.of(StatusEnum.SUBMIT_SUCCESS), fixture.listener.reportedStatuses);
    }

    @Test
    void shouldReturnSubmittingBeforeAsyncActionRuns() {
        RecordingExecutor executor = new RecordingExecutor();
        TestFixture fixture = new TestFixture(executor);

        TaskResult accepted = fixture.service.submitTask(request(SubmitModeEnum.ASYNC));

        assertEquals(StatusEnum.SUBMITTING, accepted.getTaskState());
        assertEquals(StatusEnum.SUBMITTING, fixture.store.readState("task-1").orElseThrow().getStatus());
        assertEquals(1, executor.tasks.size());

        executor.tasks.get(0).run();
        assertEquals(StatusEnum.SUBMIT_SUCCESS, fixture.store.readState("task-1").orElseThrow().getStatus());
    }

    @Test
    void shouldNotOverwriteTerminalStateCommittedDuringSubmit() {
        TestFixture fixture = new TestFixture(Runnable::run);
        fixture.plugin.submitAction = context -> {
            WorkerTaskExecutionState terminal = context.getExecutionState().copy();
            terminal.setStatus(StatusEnum.RUN_SUCCESS);
            terminal.setAppId("app-fast");
            fixture.store.saveState(terminal, terminal.getRevision());
        };

        TaskResult result = fixture.service.submitTask(request(SubmitModeEnum.SYNC));

        assertEquals(StatusEnum.RUN_SUCCESS, result.getTaskState());
        assertEquals("app-fast", result.getWorkerResult().getAppId());
    }

    @Test
    void shouldKeepFilesAndListenerWhenPluginFinishFails() {
        TestFixture fixture = new TestFixture(Runnable::run);
        fixture.service.submitTask(request(SubmitModeEnum.SYNC));
        fixture.plugin.finishResult = false;

        boolean finished = fixture.service.finishTask(request(SubmitModeEnum.SYNC));

        assertFalse(finished);
        assertTrue(fixture.store.readState("task-1").isPresent());
        assertTrue(fixture.listener.unregistered.isEmpty());
    }

    @Test
    void shouldRestoreOnlyManagerTasksWithLocalExecutionFiles() {
        TestFixture fixture = new TestFixture(Runnable::run);
        fixture.store.restorable.add("task-1");
        fixture.client.unfinishedTasks = Optional.of(List.of(request(SubmitModeEnum.SYNC), request("task-2")));
        Worker worker = new Worker();
        worker.setId("worker-1");
        worker.setWorkerCode("worker-code");
        fixture.identity.worker = worker;

        fixture.service.start(initialWorker());

        assertTrue(fixture.service.isReady());
        assertEquals(List.of("task-1"), fixture.listener.registeredTaskIds);
        fixture.service.stop();
    }

    private Worker initialWorker() {
        Worker worker = new Worker();
        worker.setWorkerCode("worker-code");
        return worker;
    }

    private TaskRequest request(SubmitModeEnum submitMode) {
        return request("task-1", submitMode);
    }

    private TaskRequest request(String taskInstanceId) {
        return request(taskInstanceId, SubmitModeEnum.SYNC);
    }

    private TaskRequest request(String taskInstanceId, SubmitModeEnum submitMode) {
        TaskRequest request = new TaskRequest();
        request.setFlowInstanceId("flow-1");
        request.setTaskInstanceId(taskInstanceId);
        request.setTaskName("task-name");
        request.setPluginType("TEST");
        request.setRunMode("LOCAL");
        request.setSubmitMode(submitMode);
        return request;
    }

    /** WorkerService test fixture. */
    private class TestFixture {

        /** Execution store. */
        private final MemoryExecutionStore store = new MemoryExecutionStore();

        /** Plugin executor. */
        private final TestPluginExecutor plugin = new TestPluginExecutor();

        /** Listener registry. */
        private final RecordingListenerRegistry listener = new RecordingListenerRegistry();

        /** Worker client. */
        private final RecordingWorkerClient client = new RecordingWorkerClient();

        /** Identity store. */
        private final MemoryIdentityStore identity = new MemoryIdentityStore();

        /** Service under test. */
        private final WorkerService service;

        TestFixture(Executor actionExecutor) {
            WorkerPluginRouter router = new WorkerPluginRouter(List.of(plugin), List.of(new TestStateMapping()));
            WorkerTaskStateCoordinator coordinator = new WorkerTaskStateCoordinator(store);
            service = new WorkerService(client, identity, router, store, coordinator, listener,
                    actionExecutor, heartbeatScheduler, SubmitModeEnum.SYNC, 60000L, false);
        }
    }

    /** In-memory execution store. */
    private static class MemoryExecutionStore implements WorkerTaskExecutionStore {

        /** Snapshots. */
        private final Map<String, WorkerTaskExecutionSnap> snapshots = new HashMap<>();

        /** States. */
        private final Map<String, WorkerTaskExecutionState> states = new HashMap<>();

        /** Restorable task IDs. */
        private final Set<String> restorable = new HashSet<>();

        @Override
        public String saveSnapshot(WorkerTaskExecutionSnap snapshot) {
            snapshots.put(snapshot.getTaskInstanceId(), snapshot.copy());
            return "/runtime/" + snapshot.getTaskInstanceId();
        }

        @Override
        public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
            return Optional.ofNullable(snapshots.get(taskInstanceId)).map(WorkerTaskExecutionSnap::copy);
        }

        @Override
        public synchronized boolean saveState(WorkerTaskExecutionState state, long expectedRevision) {
            WorkerTaskExecutionState current = states.get(state.getTaskInstanceId());
            long currentRevision = current == null ? 0L : current.getRevision();
            if (currentRevision != expectedRevision) {
                return false;
            }
            WorkerTaskExecutionState persisted = state.copy();
            persisted.setRevision(currentRevision + 1L);
            states.put(state.getTaskInstanceId(), persisted);
            state.setRevision(persisted.getRevision());
            return true;
        }

        @Override
        public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
            return Optional.ofNullable(states.get(taskInstanceId)).map(WorkerTaskExecutionState::copy);
        }

        @Override
        public Set<String> restoreExecutions(Collection<String> taskInstanceIds) {
            Set<String> restored = new HashSet<>(taskInstanceIds);
            restored.retainAll(restorable);
            return restored;
        }

        @Override
        public void deleteExecution(String taskInstanceId) {
            snapshots.remove(taskInstanceId);
            states.remove(taskInstanceId);
        }
    }

    /** Test plugin executor. */
    private static class TestPluginExecutor implements PluginTaskExecutor {

        /** Optional submit side effect. */
        private java.util.function.Consumer<RunningTaskContext> submitAction = context -> {
        };

        /** Finish result. */
        private boolean finishResult = true;

        @Override
        public String pluginType() {
            return "TEST";
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public WorkerResult submit(RunningTaskContext context) {
            submitAction.accept(context);
            context.getExecutionState().setStatus(StatusEnum.SUBMIT_SUCCESS);
            return WorkerResult.builder().appId("app-1").workDirPath(context.getWorkDirPath()).build();
        }

        @Override
        public WorkerResult stop(RunningTaskContext context) {
            context.getExecutionState().setStatus(StatusEnum.STOP_SUCCESS);
            return new WorkerResult();
        }

        @Override
        public WorkerResult kill(RunningTaskContext context) {
            context.getExecutionState().setStatus(StatusEnum.KILLED);
            return new WorkerResult();
        }

        @Override
        public boolean finish(RunningTaskContext context) {
            return finishResult;
        }
    }

    /** Test state mapping. */
    private static class TestStateMapping implements PluginRunModeStateMapping {

        @Override
        public String pluginType() {
            return "TEST";
        }

        @Override
        public String runMode() {
            return "LOCAL";
        }

        @Override
        public StatusEnum mapState(WorkerTaskExecutionSnap snapshot, WorkerTaskExecutionState state) {
            return state.getStatus();
        }
    }

    /** Recording task listener. */
    private static class RecordingListenerRegistry implements TaskStateListenerRegistry {

        /** Registered task IDs. */
        private final List<String> registeredTaskIds = new ArrayList<>();

        /** Reported states. */
        private final List<StatusEnum> reportedStatuses = new ArrayList<>();

        /** Unregistered task IDs. */
        private final List<String> unregistered = new ArrayList<>();

        @Override
        public void register(String taskInstanceId, StatusEnum reportedStatus) {
            registeredTaskIds.add(taskInstanceId);
            if (reportedStatus != null) {
                reportedStatuses.add(reportedStatus);
            }
        }

        @Override
        public void unregister(String taskInstanceId) {
            unregistered.add(taskInstanceId);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public boolean report(TaskResult result) {
            return true;
        }
    }

    /** Recording worker client. */
    private static class RecordingWorkerClient implements WorkerClient {

        /** Unfinished tasks response. */
        private Optional<List<TaskRequest>> unfinishedTasks = Optional.of(List.of());

        @Override
        public Worker register(Worker worker) {
            worker.setId("worker-1");
            return worker;
        }

        @Override
        public Worker heartbeat(Worker worker) {
            return worker;
        }

        @Override
        public Worker offline(Worker worker) {
            return worker;
        }

        @Override
        public Optional<List<TaskRequest>> findUnfinishedTasks(Worker worker) {
            return unfinishedTasks;
        }
    }

    /** In-memory worker identity store. */
    private static class MemoryIdentityStore implements WorkerIdentityStore {

        /** Stored worker. */
        private Worker worker;

        @Override
        public Optional<Worker> load() {
            return Optional.ofNullable(worker);
        }

        @Override
        public void save(Worker savedWorker) {
            worker = savedWorker;
        }
    }

    /** Recording executor. */
    private static class RecordingExecutor implements Executor {

        /** Queued tasks. */
        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }
}
