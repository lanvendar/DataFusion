package com.datafusion.agent.runtime.worker.reporter;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.agent.runtime.AgentRuntimeState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.state.WorkerTaskExecutionStateStore;
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
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Slf4j
public class FileWorkerTaskExecutionStateStore implements WorkerTaskExecutionStateStore {

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
     * agent 运行状态.
     */
    private final AgentRuntimeState runtimeState;

    /**
     * 构造函数.
     *
     * @param properties   agent 配置
     * @param runtimeState agent 运行状态
     */
    public FileWorkerTaskExecutionStateStore(AgentProperties properties, AgentRuntimeState runtimeState) {
        this.properties = properties;
        this.runtimeState = runtimeState;
    }

    @Override
    public void record(WorkerTaskExecutionState state) {
        if (state == null || state.getTaskInstanceId() == null) {
            return;
        }
        try {
            Path stateFile = stateFile(state);
            Path executionDir = stateFile.getParent();
            Files.createDirectories(executionDir);
            Files.writeString(executionDir.resolve("taskStatus.log"), statusLine(state) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(stateFile, stateContent(state), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("记录任务执行状态文件失败, taskInstanceId={}", state.getTaskInstanceId(), e);
        }
    }

    @Override
    public Optional<WorkerTaskExecutionState> read(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.trim().isEmpty()) {
            return Optional.empty();
        }
        return findStateFile(taskInstanceId).flatMap(this::readStateFile);
    }

    @Override
    public List<WorkerTaskExecutionState> listRecords() {
        return findStateFiles()
                .stream()
                .map(this::readStateFile)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public void remove(String taskInstanceId) {
        if (taskInstanceId == null || taskInstanceId.trim().isEmpty()) {
            return;
        }
        findStateFile(taskInstanceId).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("删除任务执行状态文件失败, path={}", path, e);
            }
        });
    }

    private Path executionStatusDir(String flowInstanceId, String taskInstanceId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return Path.of(properties.getModules(), properties.getStorage().getTaskStatusDir(), date,
                safePath(flowInstanceId), safePath(taskInstanceId));
    }

    private Path stateFile(WorkerTaskExecutionState state) {
        Optional<Path> existingStateFile = findStateFile(state.getTaskInstanceId());
        if (existingStateFile.isPresent()) {
            return existingStateFile.get();
        }
        return executionStatusDir(state.getFlowInstanceId(), state.getTaskInstanceId())
                .resolve(state.getTaskInstanceId() + ".state");
    }

    private Optional<Path> findStateFile(String taskInstanceId) {
        return findStateFiles()
                .stream()
                .filter(path -> path.getFileName().toString().equals(taskInstanceId + ".state"))
                .findFirst();
    }

    private String statusLine(WorkerTaskExecutionState state) {
        return "appId:" + safeText(state.getAppId())
                + "|workId:" + safeText(resolveWorkId(state)) + "|status:" + statusName(state);
    }

    private String stateContent(WorkerTaskExecutionState state) throws Exception {
        state.setWorkId(resolveWorkId(state));
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(state);
    }

    private List<Path> findStateFiles() {
        Path root = Path.of(properties.getModules(), properties.getStorage().getTaskStatusDir());
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".state")).toList();
        } catch (Exception e) {
            log.warn("读取任务执行状态文件列表失败, root={}", root, e);
            return Collections.emptyList();
        }
    }

    private Optional<WorkerTaskExecutionState> readStateFile(Path path) {
        try {
            return Optional.of(OBJECT_MAPPER.readValue(path.toFile(), WorkerTaskExecutionState.class));
        } catch (Exception e) {
            log.warn("读取任务执行状态文件失败, path={}", path, e);
            return Optional.empty();
        }
    }

    private String resolveWorkId(WorkerTaskExecutionState state) {
        if (state.getWorkId() != null) {
            return state.getWorkId();
        }
        return runtimeState.getWorker() == null ? "" : runtimeState.getWorker().getId();
    }

    private String safePath(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String statusName(WorkerTaskExecutionState state) {
        return state.getStatus() == null ? "" : state.getStatus().name();
    }
}
