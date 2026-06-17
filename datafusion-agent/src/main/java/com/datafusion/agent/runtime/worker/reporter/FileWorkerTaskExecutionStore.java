package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStore;
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
     * 构造函数.
     *
     * @param properties agent 配置
     */
    public FileWorkerTaskExecutionStore(AgentProperties properties) {
        this.properties = properties;
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
        } catch (Exception e) {
            log.warn("记录任务提交快照失败, taskInstanceId={}", snapshot.getTaskInstanceId(), e);
        }
    }

    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return findSnapshotFile(taskInstanceId).flatMap(this::readSnapshotFile);
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
            Files.writeString(stateFile, json(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(stateFile.getParent().resolve(state.getTaskInstanceId() + ".log"),
                    statusLine(state) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("记录任务运行态失败, taskInstanceId={}", state.getTaskInstanceId(), e);
        }
    }

    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return findStateFile(taskInstanceId).flatMap(this::readStateFile);
    }

    @Override
    public List<WorkerTaskExecutionState> listListeningStates() {
        return findStateFiles()
                .stream()
                .map(this::readStateFile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public void remove(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        findStateFile(taskInstanceId).ifPresent(path -> {
            try {
                Path executionDir = path.getParent();
                Files.deleteIfExists(executionDir.resolve(taskInstanceId + ".state"));
                Files.deleteIfExists(executionDir.resolve(taskInstanceId + ".snap"));
                Files.deleteIfExists(executionDir.resolve(taskInstanceId + ".log"));
            } catch (Exception e) {
                log.warn("删除任务执行状态文件失败, taskInstanceId={}", taskInstanceId, e);
            }
        });
    }

    private Path snapshotFile(WorkerTaskExecutionSnap snapshot) {
        return findStateFile(snapshot.getTaskInstanceId())
                .map(path -> path.getParent().resolve(snapshot.getTaskInstanceId() + ".snap"))
                .orElseGet(() -> executionDir(snapshot.getFlowInstanceId(), snapshot.getTaskInstanceId())
                        .resolve(snapshot.getTaskInstanceId() + ".snap"));
    }

    private Path stateFile(WorkerTaskExecutionState state) {
        return findStateFile(state.getTaskInstanceId())
                .orElseGet(() -> findSnapshotFile(state.getTaskInstanceId()).flatMap(this::readSnapshotFile)
                        .map(snapshot -> executionDir(snapshot.getFlowInstanceId(), state.getTaskInstanceId())
                                .resolve(state.getTaskInstanceId() + ".state"))
                        .orElseGet(() -> executionDir(null, state.getTaskInstanceId())
                                .resolve(state.getTaskInstanceId() + ".state")));
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

    private Path executionDir(String flowInstanceId, String taskInstanceId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getModules(), properties.getStorage().getTaskRuntimeDir(), date,
                safePath(flowInstanceId), safePath(taskInstanceId));
    }

    private String json(Object value) throws Exception {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
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
}
