package com.datafusion.agent.runtime.worker.context;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskRuntimeFiles;
import com.datafusion.scheduler.worker.context.RunningTaskContext;
import com.datafusion.scheduler.worker.context.WorkerTaskContextStorage;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 基于文件的 Worker 任务执行状态存储.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Slf4j
public class WorkerTaskExecutionContext implements WorkerTaskExecutionStore, WorkerTaskContextStorage {

    /**
     * 日期格式.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Snapshot file suffix.
     */
    private static final String SNAPSHOT_FILE_SUFFIX = ".snap";

    /**
     * State file suffix.
     */
    private static final String STATE_FILE_SUFFIX = ".state";

    /**
     * JSON file suffix.
     */
    private static final String JSON_FILE_SUFFIX = ".json";

    /**
     * JSON 序列化器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * agent 配置.
     */
    private final AgentProperties properties;

    /**
     * 任务执行索引缓存.
     */
    private final Cache<String, WorkerTaskExecutionEntry> executionCache;

    /**
     * 构造函数.
     *
     * @param properties agent 配置
     */
    public WorkerTaskExecutionContext(AgentProperties properties) {
        this.properties = properties;
        this.executionCache = Caffeine.newBuilder().build();
    }

    @Override
    public void saveSnapshot(WorkerTaskExecutionSnap snapshot) {
        if (snapshot == null || isBlank(snapshot.getTaskInstanceId())) {
            return;
        }
        try {
            Path snapshotFile = snapshotFile(snapshot);
            Files.createDirectories(snapshotFile.getParent());
            Files.writeString(snapshotFile, json(snapshot), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            updateContext(snapshot.getTaskInstanceId(),
                    context -> context.mergeSnapshot(snapshot, pathText(snapshotFile.getParent())));
        } catch (Exception e) {
            log.warn("记录任务提交快照失败, taskInstanceId={}", snapshot.getTaskInstanceId(), e);
        }
    }

    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return executionEntry(taskInstanceId)
                .flatMap(entry -> workDir(entry.getContext()))
                .map(path -> snapshotFile(path, taskInstanceId))
                .filter(Files::exists)
                .flatMap(this::readSnapshotFile)
                .or(() -> findSnapshotFile(taskInstanceId).flatMap(this::readSnapshotFile));
    }

    @Override
    public void saveState(WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getTaskInstanceId())) {
            return;
        }
        try {
            state.setUpdateTime(System.currentTimeMillis());
            Path stateFile = stateFile(state);
            Files.createDirectories(stateFile.getParent());
            WorkerTaskExecutionState oldState = Files.exists(stateFile) ? readStateFile(stateFile).orElse(null) : null;
            Files.writeString(stateFile, json(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            updateContext(state.getTaskInstanceId(),
                    context -> context.mergeState(state, pathText(stateFile.getParent())));
            if (shouldAppendStatusLog(oldState, state)) {
                appendStatusLog(stateFile.getParent(), state);
            }
        } catch (Exception e) {
            log.warn("记录任务运行态失败, taskInstanceId={}", state.getTaskInstanceId(), e);
        }
    }

    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return executionEntry(taskInstanceId)
                .flatMap(entry -> workDir(entry.getContext()))
                .map(path -> stateFile(path, taskInstanceId))
                .filter(Files::exists)
                .flatMap(this::readStateFile)
                .or(() -> findStateFile(taskInstanceId).flatMap(this::readStateFile));
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        return context(taskInstanceId).orElse(null);
    }

    @Override
    public RunningTaskContext getOrCreate(TaskRequest request) {
        if (request == null || isBlank(request.getTaskInstanceId())) {
            return RunningTaskContext.fromRequest(request);
        }
        return context(request.getTaskInstanceId())
                .orElseGet(() -> {
                    RunningTaskContext context = RunningTaskContext.fromRequest(request);
                    executionCache.put(request.getTaskInstanceId(), new WorkerTaskExecutionEntry(context));
                    return context;
                });
    }

    @Override
    public void save(RunningTaskContext context) {
        if (context == null || isBlank(context.getTaskInstanceId())) {
            return;
        }
        executionCache.put(context.getTaskInstanceId(), new WorkerTaskExecutionEntry(context));
        WorkerTaskExecutionSnap snapshot = copySnapshot(context.getSnapshot());
        if (readSnapshot(context.getTaskInstanceId()).isEmpty()) {
            saveSnapshot(snapshot);
        } else {
            updateContext(context.getTaskInstanceId(), current -> current.mergeSnapshot(snapshot,
                    workDir(context).map(Path::toString).orElse(null)));
        }
        WorkerTaskExecutionState state = copyState(context.getExecutionState());
        mergeExistingState(state);
        saveState(state);
    }

    @Override
    public List<WorkerTaskExecutionState> listListeningStates() {
        return executionCache.asMap()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> stateFile(entry.getValue().getContext(), entry.getKey()))
                .flatMap(Optional::stream)
                .filter(Files::exists)
                .map(this::readStateFile)
                .flatMap(Optional::stream)
                .filter(state -> state != null && !isBlank(state.getTaskInstanceId()))
                .toList();
    }

    @Override
    public void restoreListeningTasks(List<TaskRequest> requests) {
        if (requests == null) {
            return;
        }
        for (TaskRequest request : requests) {
            if (request == null || isBlank(request.getTaskInstanceId())) {
                continue;
            }
            restoreContext(request).ifPresent(context -> executionCache.put(request.getTaskInstanceId(),
                    new WorkerTaskExecutionEntry(context)));
        }
    }

    @Override
    public void deleteExecution(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        Optional<Path> executionDir = executionEntry(taskInstanceId)
                .flatMap(entry -> workDir(entry.getContext()))
                .or(() -> findStateFile(taskInstanceId).map(Path::getParent))
                .or(() -> findSnapshotFile(taskInstanceId).map(Path::getParent));
        executionDir.ifPresent(path -> {
            try {
                Files.deleteIfExists(stateFile(path, taskInstanceId));
                Files.deleteIfExists(snapshotFile(path, taskInstanceId));
                deleteRuntimeJsonFiles(path);
            } catch (Exception e) {
                log.warn("删除任务执行状态文件失败, taskInstanceId={}", taskInstanceId, e);
            } finally {
                executionCache.invalidate(taskInstanceId);
            }
        });
    }

    @Override
    public void stopListening(String taskInstanceId) {
        removeContext(taskInstanceId);
    }

    @Override
    public void removeContext(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        executionCache.invalidate(taskInstanceId);
    }

    private Path snapshotFile(WorkerTaskExecutionSnap snapshot) {
        return executionEntry(snapshot.getTaskInstanceId())
                .flatMap(entry -> workDir(entry.getContext()))
                .map(path -> snapshotFile(path, snapshot.getTaskInstanceId()))
                .orElseGet(() -> executionDir(snapshot.getFlowInstanceId(), snapshot.getTaskInstanceId())
                        .resolve(snapshotFileName(snapshot.getTaskInstanceId())));
    }

    private Path snapshotFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(snapshotFileName(taskInstanceId));
    }

    private Path stateFile(WorkerTaskExecutionState state) {
        return executionEntry(state.getTaskInstanceId())
                .flatMap(entry -> workDir(entry.getContext()))
                .map(path -> stateFile(path, state.getTaskInstanceId()))
                .orElseGet(() -> findSnapshotFile(state.getTaskInstanceId()).flatMap(this::readSnapshotFile)
                        .map(snapshot -> stateFile(executionDir(snapshot.getFlowInstanceId(), state.getTaskInstanceId()),
                                state.getTaskInstanceId()))
                        .orElseGet(() -> stateFile(executionDir(null, state.getTaskInstanceId()),
                                state.getTaskInstanceId())));
    }

    private Path stateFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(stateFileName(taskInstanceId));
    }

    private Optional<Path> stateFile(RunningTaskContext context, String taskInstanceId) {
        return workDir(context).map(path -> stateFile(path, taskInstanceId));
    }

    private Path statusLogFile(Path executionDir) {
        return TaskRuntimeFiles.stateLog(executionDir);
    }

    private String snapshotFileName(String taskInstanceId) {
        return taskInstanceId + SNAPSHOT_FILE_SUFFIX;
    }

    private String stateFileName(String taskInstanceId) {
        return taskInstanceId + STATE_FILE_SUFFIX;
    }

    private boolean isSnapshotFile(Path path) {
        return fileName(path).endsWith(SNAPSHOT_FILE_SUFFIX);
    }

    private boolean isSnapshotFile(Path path, String taskInstanceId) {
        return fileName(path).equals(snapshotFileName(taskInstanceId));
    }

    private boolean isStateFile(Path path) {
        return fileName(path).endsWith(STATE_FILE_SUFFIX);
    }

    private boolean isStateFile(Path path, String taskInstanceId) {
        return fileName(path).equals(stateFileName(taskInstanceId));
    }

    private boolean isJsonFile(Path path) {
        return fileName(path).endsWith(JSON_FILE_SUFFIX);
    }

    private String fileName(Path path) {
        return path.getFileName().toString();
    }

    private void deleteRuntimeJsonFiles(Path executionDir) throws IOException {
        if (executionDir == null || !Files.exists(executionDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(executionDir)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(this::isJsonFile).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Optional<Path> findSnapshotFile(String taskInstanceId) {
        return findSnapshotFiles()
                .stream()
                .filter(path -> isSnapshotFile(path, taskInstanceId))
                .findFirst();
    }

    private Optional<Path> findStateFile(String taskInstanceId) {
        return findStateFiles()
                .stream()
                .filter(path -> isStateFile(path, taskInstanceId))
                .findFirst();
    }

    private List<Path> findSnapshotFiles() {
        Path root = taskRuntimeRoot();
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(this::isSnapshotFile).toList();
        } catch (Exception e) {
            log.warn("读取任务提交快照文件列表失败, root={}", root, e);
            return Collections.emptyList();
        }
    }

    private List<Path> findStateFiles() {
        Path root = taskRuntimeRoot();
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(this::isStateFile).toList();
        } catch (Exception e) {
            log.warn("读取任务运行态文件列表失败, root={}", root, e);
            return Collections.emptyList();
        }
    }

    private Optional<WorkerTaskExecutionSnap> readSnapshotFile(Path path) {
        try {
            return Optional.of(OBJECT_MAPPER.readValue(path.toFile(), WorkerTaskExecutionSnap.class));
        } catch (Exception e) {
            log.warn("读取任务提交快照失败, path={}", path, e);
            return Optional.empty();
        }
    }

    private Optional<WorkerTaskExecutionState> readStateFile(Path path) {
        try {
            return Optional.of(OBJECT_MAPPER.readValue(path.toFile(), WorkerTaskExecutionState.class));
        } catch (Exception e) {
            log.warn("读取任务运行态失败, path={}", path, e);
            return Optional.empty();
        }
    }

    private Optional<WorkerTaskExecutionEntry> executionEntry(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(executionCache.getIfPresent(taskInstanceId));
    }

    private boolean shouldAppendStatusLog(WorkerTaskExecutionState oldState, WorkerTaskExecutionState newState) {
        if (newState == null) {
            return false;
        }
        if (oldState == null) {
            return true;
        }
        return oldState.getStatus() != newState.getStatus()
                || !Objects.equals(oldState.getAppId(), newState.getAppId())
                || !Objects.equals(oldState.getExitCode(), newState.getExitCode());
    }

    private Path executionDir(String flowInstanceId, String taskInstanceId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return taskRuntimeRoot().resolve(date).resolve(safePath(flowInstanceId)).resolve(safePath(taskInstanceId));
    }

    private Path taskRuntimeRoot() {
        return Path.of(properties.getStorage().getTaskRuntimeDir());
    }

    private Optional<Path> workDir(TaskRequest request) {
        String workDirPath = request == null || request.getWorkerResult() == null ? null
                : request.getWorkerResult().getWorkDirPath();
        if (isBlank(workDirPath)) {
            return Optional.empty();
        }
        Path workDir = Path.of(workDirPath);
        if (Files.exists(stateFile(workDir, request.getTaskInstanceId()))
                || Files.exists(snapshotFile(workDir, request.getTaskInstanceId()))) {
            return Optional.of(workDir);
        }
        return Optional.empty();
    }

    private Optional<Path> workDir(RunningTaskContext context) {
        if (context == null || isBlank(context.getWorkDirPath())) {
            return Optional.empty();
        }
        return Optional.of(Path.of(context.getWorkDirPath()));
    }

    private Optional<RunningTaskContext> restoreContext(TaskRequest request) {
        return workDir(request).map(path -> {
            WorkerTaskExecutionSnap snapshot = readSnapshotFile(snapshotFile(path, request.getTaskInstanceId()))
                    .orElse(null);
            WorkerTaskExecutionState state = readStateFile(stateFile(path, request.getTaskInstanceId()))
                    .orElse(null);
            RunningTaskContext context = state == null ? RunningTaskContext.fromRequest(request)
                    : RunningTaskContext.fromSnapshotAndState(snapshot, state);
            context.updateRequest(request);
            if (context.getWorkDirPath() == null) {
                context.setWorkDirPath(path.toString());
            }
            return context;
        });
    }

    private void updateContext(String taskInstanceId, ContextUpdater updater) {
        if (isBlank(taskInstanceId) || updater == null) {
            return;
        }
        RunningTaskContext context = context(taskInstanceId).orElseGet(RunningTaskContext::new);
        context.setTaskInstanceId(taskInstanceId);
        updater.update(context);
        executionCache.put(taskInstanceId, new WorkerTaskExecutionEntry(context));
    }

    private Optional<RunningTaskContext> context(String taskInstanceId) {
        return executionEntry(taskInstanceId)
                .map(WorkerTaskExecutionEntry::getContext);
    }

    private void mergeExistingState(WorkerTaskExecutionState state) {
        findStateFile(state.getTaskInstanceId())
                .flatMap(this::readStateFile)
                .ifPresent(existing -> {
                    if (state.getAppId() == null) {
                        state.setAppId(existing.getAppId());
                    }
                    if (state.getWorkerId() == null) {
                        state.setWorkerId(existing.getWorkerId());
                    }
                    if (state.getWorkDirPath() == null) {
                        state.setWorkDirPath(existing.getWorkDirPath());
                    }
                    if (state.getExitCode() == null) {
                        state.setExitCode(existing.getExitCode());
                    }
                });
    }

    private WorkerTaskExecutionSnap copySnapshot(WorkerTaskExecutionSnap snapshot) {
        return WorkerTaskExecutionSnap.builder()
                .flowInstanceId(snapshot.getFlowInstanceId())
                .taskInstanceId(snapshot.getTaskInstanceId())
                .taskName(snapshot.getTaskName())
                .workerId(snapshot.getWorkerId())
                .pluginType(snapshot.getPluginType())
                .runMode(snapshot.getRunMode())
                .taskData(snapshot.getTaskData())
                .pluginParam(snapshot.getPluginParam())
                .submitMode(snapshot.getSubmitMode())
                .build();
    }

    private WorkerTaskExecutionState copyState(WorkerTaskExecutionState state) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(state.getTaskInstanceId())
                .workerId(state.getWorkerId())
                .appId(state.getAppId())
                .workDirPath(state.getWorkDirPath())
                .status(state.getStatus())
                .exitCode(state.getExitCode())
                .updateTime(state.getUpdateTime())
                .result(state.getResult())
                .outputVars(state.getOutputVars())
                .build();
    }

    private String pathText(Path path) {
        return path == null ? null : path.toString();
    }

    private String json(Object value) throws Exception {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private void appendStatusLog(Path executionDir, WorkerTaskExecutionState state) throws Exception {
        Files.writeString(statusLogFile(executionDir),
                statusLine(state) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String statusLine(WorkerTaskExecutionState state) {
        return "time:" + state.getUpdateTime()
                + "|workerId:" + safeText(state.getWorkerId())
                + "|appId:" + safeText(state.getAppId())
                + "|status:" + (state.getStatus() == null ? "" : state.getStatus().name())
                + "|exitCode:" + (state.getExitCode() == null ? "" : state.getExitCode());
    }

    private String safePath(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Worker task execution cache entry.
     */
    private static final class WorkerTaskExecutionEntry {

        /**
         * Running task context.
         */
        private final RunningTaskContext context;

        /**
         * Constructor.
         *
         * @param context Running task context
         */
        private WorkerTaskExecutionEntry(RunningTaskContext context) {
            this.context = context;
        }

        private RunningTaskContext getContext() {
            return context;
        }
    }

    /**
     * Context updater.
     */
    @FunctionalInterface
    private interface ContextUpdater {

        /**
         * Update context.
         *
         * @param context Running task context
         */
        void update(RunningTaskContext context);
    }
}
