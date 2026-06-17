package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.enums.StatusEnum;
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
        loadExistingStates();
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
            registerExecution(snapshot.getTaskInstanceId(), snapshotFile.getParent(), null);
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
                    registerExecution(taskInstanceId, path.getParent(), null);
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
            StatusEnum oldStatus = Optional.ofNullable(executionCache.asMap().get(state.getTaskInstanceId()))
                    .map(WorkerTaskExecutionEntry::getState)
                    .map(WorkerTaskExecutionState::getStatus)
                    .orElse(null);
            Files.writeString(stateFile, json(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            registerExecution(state.getTaskInstanceId(), stateFile.getParent(), state);
            if (oldStatus != state.getStatus()) {
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
                .map(WorkerTaskExecutionEntry::getState)
                .or(() -> findStateFile(taskInstanceId).flatMap(path -> readStateFile(path)
                        .map(state -> {
                            registerExecution(taskInstanceId, path.getParent(), state);
                            return state;
                        })));
    }

    @Override
    public List<WorkerTaskExecutionState> listListeningStates() {
        return executionCache.asMap()
                .values()
                .stream()
                .map(WorkerTaskExecutionEntry::getState)
                .filter(state -> state != null && !isBlank(state.getTaskInstanceId()))
                .toList();
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
                        .resolve(snapshot.getTaskInstanceId() + ".snap"));
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
        return executionDir.resolve(taskInstanceId + ".snap");
    }

    private Path stateFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(taskInstanceId + ".state");
    }

    private Path statusLogFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(taskInstanceId + ".log");
    }

    private Optional<Path> findSnapshotFile(String taskInstanceId) {
        return findSnapshotFiles()
                .stream()
                .filter(path -> path.getFileName().toString().equals(taskInstanceId + ".snap"))
                .findFirst();
    }

    private Optional<Path> findStateFile(String taskInstanceId) {
        return findStateFiles()
                .stream()
                .filter(path -> path.getFileName().toString().equals(taskInstanceId + ".state"))
                .findFirst();
    }

    private List<Path> findSnapshotFiles() {
        Path root = Path.of(properties.getModules(), properties.getStorage().getTaskRuntimeDir());
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".snap")).toList();
        } catch (Exception e) {
            log.warn("读取任务提交快照文件列表失败, root={}", root, e);
            return Collections.emptyList();
        }
    }

    private List<Path> findStateFiles() {
        Path root = Path.of(properties.getModules(), properties.getStorage().getTaskRuntimeDir());
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".state")).toList();
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

    private void loadExistingStates() {
        for (Path stateFile : findStateFiles()) {
            readStateFile(stateFile).ifPresent(state -> {
                if (!isBlank(state.getTaskInstanceId())) {
                    registerExecution(state.getTaskInstanceId(), stateFile.getParent(), state);
                }
            });
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
            WorkerTaskExecutionState state = readStateFile(stateFile.get()).orElse(null);
            return new WorkerTaskExecutionEntry(stateFile.get().getParent(), state);
        }
        Optional<Path> snapshotFile = findSnapshotFile(taskInstanceId);
        if (snapshotFile.isPresent()) {
            return new WorkerTaskExecutionEntry(snapshotFile.get().getParent(), null);
        }
        return new WorkerTaskExecutionEntry(null, null);
    }

    private void registerExecution(String taskInstanceId, Path executionDir, WorkerTaskExecutionState state) {
        if (isBlank(taskInstanceId) || executionDir == null) {
            return;
        }
        executionCache.asMap().compute(taskInstanceId, (key, existing) -> {
            WorkerTaskExecutionState nextState = state == null && existing != null ? existing.getState() : state;
            return new WorkerTaskExecutionEntry(executionDir, nextState);
        });
    }

    private Path executionDir(String flowInstanceId, String taskInstanceId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getModules(), properties.getStorage().getTaskRuntimeDir(), date,
                safePath(flowInstanceId), safePath(taskInstanceId));
    }

    private String json(Object value) throws Exception {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private void appendStatusLog(Path executionDir, WorkerTaskExecutionState state) throws Exception {
        Files.writeString(statusLogFile(executionDir, state.getTaskInstanceId()),
                statusLine(state) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String statusLine(WorkerTaskExecutionState state) {
        return "time:" + state.getUpdateTime()
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
         * Worker task execution state.
         */
        private final WorkerTaskExecutionState state;

        private WorkerTaskExecutionEntry(Path executionDir, WorkerTaskExecutionState state) {
            this.executionDir = executionDir;
            this.state = state;
        }

        private Path getExecutionDir() {
            return executionDir;
        }

        private WorkerTaskExecutionState getState() {
            return state;
        }
    }
}
