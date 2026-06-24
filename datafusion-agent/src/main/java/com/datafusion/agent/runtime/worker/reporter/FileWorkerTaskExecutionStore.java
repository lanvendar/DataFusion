package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.TaskRequest;
import com.datafusion.scheduler.model.TaskRuntimeFiles;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

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
public class FileWorkerTaskExecutionStore implements WorkerTaskExecutionStore {

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
    private final LoadingCache<String, WorkerTaskExecutionEntry> executionCache;

    /**
     * 构造函数.
     *
     * @param properties agent 配置
     */
    public FileWorkerTaskExecutionStore(AgentProperties properties) {
        this.properties = properties;
        this.executionCache = Caffeine.newBuilder().build(this::loadExecutionEntry);
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
            registerExecution(snapshot.getTaskInstanceId(), snapshotFile.getParent());
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
                .map(entry -> snapshotFile(entry.getExecutionDir(), taskInstanceId))
                .filter(Files::exists)
                .flatMap(this::readSnapshotFile)
                .or(() -> findSnapshotFile(taskInstanceId).flatMap(path -> {
                    registerExecution(taskInstanceId, path.getParent());
                    return readSnapshotFile(path);
                }));
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
            registerExecution(state.getTaskInstanceId(), stateFile.getParent());
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
                .map(entry -> stateFile(entry.getExecutionDir(), taskInstanceId))
                .filter(Files::exists)
                .flatMap(this::readStateFile)
                .or(() -> findStateFile(taskInstanceId).flatMap(path -> readStateFile(path)
                        .map(state -> {
                            registerExecution(taskInstanceId, path.getParent());
                            return state;
                        })));
    }

    @Override
    public List<WorkerTaskExecutionState> listListeningStates() {
        return executionCache.asMap()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().getExecutionDir() != null)
                .map(entry -> stateFile(entry.getValue().getExecutionDir(), entry.getKey()))
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
            workDir(request).ifPresent(path -> registerExecution(request.getTaskInstanceId(), path));
        }
    }

    @Override
    public void remove(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        Optional<Path> executionDir = executionEntry(taskInstanceId)
                .map(WorkerTaskExecutionEntry::getExecutionDir)
                .or(() -> findStateFile(taskInstanceId).map(Path::getParent))
                .or(() -> findSnapshotFile(taskInstanceId).map(Path::getParent));
        executionDir.ifPresent(path -> {
            try {
                Files.deleteIfExists(stateFile(path, taskInstanceId));
                Files.deleteIfExists(snapshotFile(path, taskInstanceId));
            } catch (Exception e) {
                log.warn("删除任务执行状态文件失败, taskInstanceId={}", taskInstanceId, e);
            } finally {
                executionCache.invalidate(taskInstanceId);
            }
        });
    }

    private Path snapshotFile(WorkerTaskExecutionSnap snapshot) {
        return executionEntry(snapshot.getTaskInstanceId())
                .map(WorkerTaskExecutionEntry::getExecutionDir)
                .map(path -> snapshotFile(path, snapshot.getTaskInstanceId()))
                .orElseGet(() -> executionDir(snapshot.getFlowInstanceId(), snapshot.getTaskInstanceId())
                        .resolve(snapshotFileName(snapshot.getTaskInstanceId())));
    }

    private Path stateFile(WorkerTaskExecutionState state) {
        return executionEntry(state.getTaskInstanceId())
                .map(WorkerTaskExecutionEntry::getExecutionDir)
                .map(path -> stateFile(path, state.getTaskInstanceId()))
                .orElseGet(() -> findSnapshotFile(state.getTaskInstanceId()).flatMap(this::readSnapshotFile)
                        .map(snapshot -> stateFile(executionDir(snapshot.getFlowInstanceId(), state.getTaskInstanceId()),
                                state.getTaskInstanceId()))
                        .orElseGet(() -> stateFile(executionDir(null, state.getTaskInstanceId()),
                                state.getTaskInstanceId())));
    }

    private Path snapshotFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(snapshotFileName(taskInstanceId));
    }

    private Path stateFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(stateFileName(taskInstanceId));
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

    private boolean isStateFile(Path path) {
        return fileName(path).endsWith(STATE_FILE_SUFFIX);
    }

    private boolean isSnapshotFile(Path path, String taskInstanceId) {
        return fileName(path).equals(snapshotFileName(taskInstanceId));
    }

    private boolean isStateFile(Path path, String taskInstanceId) {
        return fileName(path).equals(stateFileName(taskInstanceId));
    }

    private String fileName(Path path) {
        return path.getFileName().toString();
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
        WorkerTaskExecutionEntry entry = executionCache.get(taskInstanceId);
        return entry == null || entry.getExecutionDir() == null ? Optional.empty() : Optional.of(entry);
    }

    private WorkerTaskExecutionEntry loadExecutionEntry(String taskInstanceId) {
        Optional<Path> stateFile = findStateFile(taskInstanceId);
        if (stateFile.isPresent()) {
            return new WorkerTaskExecutionEntry(stateFile.get().getParent());
        }
        Optional<Path> snapshotFile = findSnapshotFile(taskInstanceId);
        if (snapshotFile.isPresent()) {
            return new WorkerTaskExecutionEntry(snapshotFile.get().getParent());
        }
        return new WorkerTaskExecutionEntry(null);
    }

    private void registerExecution(String taskInstanceId, Path executionDir) {
        if (isBlank(taskInstanceId) || executionDir == null) {
            return;
        }
        executionCache.put(taskInstanceId, new WorkerTaskExecutionEntry(executionDir));
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
         * Execution directory.
         */
        private final Path executionDir;

        /**
         * Constructor.
         *
         * @param executionDir Execution directory
         */
        private WorkerTaskExecutionEntry(Path executionDir) {
            this.executionDir = executionDir;
        }

        private Path getExecutionDir() {
            return executionDir;
        }
    }
}
