package com.datafusion.agent.runtime.worker.context;

import com.datafusion.agent.config.AgentProperties;
import com.datafusion.scheduler.model.TaskRuntimeFiles;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionSnap;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionState;
import com.datafusion.scheduler.worker.context.WorkerTaskExecutionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于文件的 Worker 任务执行存储.
 *
 * <p>{@code .snap} 保存任务提交快照，{@code .state} 保存带 revision 的任务运行态。文件存储是权威数据源，
 * 内存只缓存任务目录和已成功落盘的状态副本，不缓存 {@code RunningTaskContext}。
 *
 * @author datafusion
 * @version 1.0.0, 2026/7/18
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
     * JSON file suffix.
     */
    private static final String JSON_FILE_SUFFIX = ".json";

    /**
     * JSON 序列化器.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 任务运行态根目录.
     */
    private final Path taskRuntimeRoot;

    /**
     * 任务执行目录索引.
     */
    private final Cache<String, Path> executionDirs;

    /**
     * 已成功落盘的任务状态投影.
     */
    private final Cache<String, WorkerTaskExecutionState> states;

    /**
     * 任务运行态 CAS 写入使用的任务级内存锁.
     */
    private final Cache<String, ReentrantLock> taskLocks;

    /**
     * 构造函数.
     *
     * @param properties Agent 配置
     */
    public FileWorkerTaskExecutionStore(AgentProperties properties) {
        if (properties == null || properties.getStorage() == null
                || isBlank(properties.getStorage().getTaskRuntimeDir())) {
            throw new IllegalArgumentException("taskRuntimeDir不能为空");
        }
        this.taskRuntimeRoot = Path.of(properties.getStorage().getTaskRuntimeDir());
        this.executionDirs = Caffeine.newBuilder().build();
        this.states = Caffeine.newBuilder().build();
        this.taskLocks = Caffeine.newBuilder().weakValues().build();
    }

    /**
     * 原子覆盖任务提交快照并返回规范任务目录.
     *
     * @param snapshot 任务提交快照
     * @return 规范任务目录
     */
    @Override
    public String saveSnapshot(WorkerTaskExecutionSnap snapshot) {
        if (snapshot == null || isBlank(snapshot.getTaskInstanceId())) {
            throw new IllegalArgumentException("任务提交快照或taskInstanceId不能为空");
        }
        String taskInstanceId = snapshot.getTaskInstanceId();
        Path executionDir = resolveExecutionDir(taskInstanceId, snapshot.getFlowInstanceId(), null);
        Path snapshotFile = taskFile(executionDir, taskInstanceId, SNAPSHOT_FILE_SUFFIX);
        try {
            Files.createDirectories(executionDir);
            writeJsonAtomically(snapshotFile, snapshot);
            executionDirs.put(taskInstanceId, executionDir);
            return executionDir.toString();
        } catch (Exception e) {
            throw new IllegalStateException("记录任务提交快照失败: " + taskInstanceId, e);
        }
    }

    /**
     * 按任务实例 ID 读取任务提交快照.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务提交快照
     */
    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return findExecutionDir(taskInstanceId)
                .map(path -> taskFile(path, taskInstanceId, SNAPSHOT_FILE_SUFFIX))
                .filter(Files::exists)
                .flatMap(this::readSnapshotFile);
    }

    /**
     * 在任务锁内校验 revision 并原子写入完整候选运行态.
     *
     * <p>存储层不合并旧状态字段。文件替换成功后才更新状态缓存，写入成功时 revision 自增 1。
     *
     * @param state            完整候选运行态
     * @param expectedRevision 预期 revision
     * @return 是否写入成功
     */
    @Override
    public boolean saveState(WorkerTaskExecutionState state, long expectedRevision) {
        if (state == null || isBlank(state.getTaskInstanceId())) {
            log.warn("任务运行态无效, 拒绝写入");
            return false;
        }
        WorkerTaskExecutionState candidate = copyState(state);
        String taskInstanceId = candidate.getTaskInstanceId();
        ReentrantLock lock = taskLocks.get(taskInstanceId, key -> new ReentrantLock());
        lock.lock();
        try {
            Path executionDir = resolveExecutionDir(taskInstanceId, null, candidate.getWorkDirPath());
            Path stateFile = taskFile(executionDir, taskInstanceId, STATE_FILE_SUFFIX);
            Files.createDirectories(executionDir);
            WorkerTaskExecutionState currentState = currentState(taskInstanceId, stateFile);
            long currentRevision = currentState == null ? 0L : currentState.getRevision();
            if (currentRevision != expectedRevision) {
                log.warn("任务运行态 revision 已变化, 拒绝写入, taskInstanceId={}, expectedRevision={},"
                                + " currentRevision={}, currentStatus={}, nextStatus={}",
                        taskInstanceId, expectedRevision, currentRevision,
                        currentState == null ? null : currentState.getStatus(), candidate.getStatus());
                return false;
            }
            if (isBlank(candidate.getWorkDirPath())) {
                candidate.setWorkDirPath(executionDir.toString());
            }
            candidate.setUpdateTime(System.currentTimeMillis());
            candidate.setRevision(currentRevision + 1L);

            writeJsonAtomically(stateFile, candidate);
            if (shouldAppendStatusLog(currentState, candidate)) {
                appendStatusLogSafely(executionDir, candidate);
            }
            executionDirs.put(taskInstanceId, executionDir);
            states.put(taskInstanceId, copyState(candidate));
            copyPersistedState(candidate, state);
            return true;
        } catch (Exception e) {
            throw new IllegalStateException("记录任务运行态失败: " + taskInstanceId, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 按任务实例 ID 读取任务运行态副本.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务运行态副本
     */
    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        WorkerTaskExecutionState cachedState = states.getIfPresent(taskInstanceId);
        if (cachedState != null) {
            return Optional.of(copyState(cachedState));
        }
        Optional<Path> executionDir = findExecutionDir(taskInstanceId);
        if (executionDir.isEmpty()) {
            return Optional.empty();
        }
        Path stateFile = taskFile(executionDir.get(), taskInstanceId, STATE_FILE_SUFFIX);
        Optional<WorkerTaskExecutionState> state = readStateFile(stateFile);
        state.ifPresent(value -> states.put(taskInstanceId, copyState(value)));
        return state.map(this::copyState);
    }

    /**
     * 从本地完整执行记录中恢复 Manager 指定的任务.
     *
     * <p>恢复前清空目录和状态投影，只加载传入任务 ID 与本地同目录有效 {@code .snap/.state} 的交集。
     *
     * @param taskInstanceIds Manager 返回的未完成任务 ID
     */
    @Override
    public Set<String> restoreExecutions(Collection<String> taskInstanceIds) {
        executionDirs.invalidateAll();
        states.invalidateAll();
        if (taskInstanceIds == null || taskInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> requestedIds = taskInstanceIds.stream()
                .filter(id -> !isBlank(id))
                .collect(Collectors.toSet());
        if (requestedIds.isEmpty()) {
            return Collections.emptySet();
        }
        Map<String, Path> selectedStateFiles = selectStateFiles(requestedIds);
        Set<String> restoredIds = new HashSet<>();
        selectedStateFiles.forEach((taskInstanceId, stateFile) -> {
            if (restoreExecution(taskInstanceId, stateFile)) {
                restoredIds.add(taskInstanceId);
            }
        });
        return Collections.unmodifiableSet(restoredIds);
    }

    /**
     * 删除任务执行快照、运行态和任务目录下的运行期 JSON 文件.
     *
     * @param taskInstanceId 任务实例 ID
     */
    @Override
    public void deleteExecution(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        Path executionDir = findExecutionDir(taskInstanceId).orElse(null);
        if (executionDir == null) {
            executionDirs.invalidate(taskInstanceId);
            states.invalidate(taskInstanceId);
            return;
        }
        try {
            deleteRuntimeJsonFiles(executionDir);
            Files.deleteIfExists(taskFile(executionDir, taskInstanceId, SNAPSHOT_FILE_SUFFIX));
            Files.deleteIfExists(taskFile(executionDir, taskInstanceId, STATE_FILE_SUFFIX));
            executionDirs.invalidate(taskInstanceId);
            states.invalidate(taskInstanceId);
        } catch (Exception e) {
            throw new IllegalStateException("删除任务执行文件失败: " + taskInstanceId, e);
        }
    }

    private WorkerTaskExecutionState currentState(String taskInstanceId, Path stateFile) throws IOException {
        WorkerTaskExecutionState cachedState = states.getIfPresent(taskInstanceId);
        if (cachedState != null) {
            return copyState(cachedState);
        }
        if (!Files.exists(stateFile)) {
            return null;
        }
        try {
            WorkerTaskExecutionState state = OBJECT_MAPPER.readValue(stateFile.toFile(),
                    WorkerTaskExecutionState.class);
            states.put(taskInstanceId, copyState(state));
            return state;
        } catch (IOException e) {
            throw new IOException("读取当前任务运行态失败: " + stateFile, e);
        }
    }

    private Path resolveExecutionDir(String taskInstanceId, String flowInstanceId, String requestedWorkDir) {
        Path cachedDir = executionDirs.getIfPresent(taskInstanceId);
        if (cachedDir != null) {
            return cachedDir;
        }
        Optional<Path> existingDir = locateExecutionDir(taskInstanceId);
        Path executionDir = existingDir.orElseGet(() -> isBlank(requestedWorkDir)
                ? newExecutionDir(flowInstanceId, taskInstanceId) : Path.of(requestedWorkDir));
        executionDirs.put(taskInstanceId, executionDir);
        return executionDir;
    }

    private Optional<Path> findExecutionDir(String taskInstanceId) {
        Path cachedDir = executionDirs.getIfPresent(taskInstanceId);
        if (cachedDir != null) {
            return Optional.of(cachedDir);
        }
        Optional<Path> executionDir = locateExecutionDir(taskInstanceId);
        executionDir.ifPresent(path -> executionDirs.put(taskInstanceId, path));
        return executionDir;
    }

    private Optional<Path> locateExecutionDir(String taskInstanceId) {
        List<Path> stateFiles = findRuntimeFiles(STATE_FILE_SUFFIX).stream()
                .filter(path -> path.getFileName().toString().equals(taskInstanceId + STATE_FILE_SUFFIX))
                .toList();
        Optional<Path> stateFile = preferredStateFile(stateFiles);
        if (stateFile.isPresent()) {
            return stateFile.map(Path::getParent);
        }
        return findRuntimeFiles(SNAPSHOT_FILE_SUFFIX).stream()
                .filter(path -> path.getFileName().toString().equals(taskInstanceId + SNAPSHOT_FILE_SUFFIX))
                .max(this::compareLastModified)
                .map(Path::getParent);
    }

    private Map<String, Path> selectStateFiles(Set<String> requestedIds) {
        Map<String, Path> selectedFiles = new HashMap<>();
        Map<String, Integer> fileCounts = new HashMap<>();
        for (Path stateFile : findRuntimeFiles(STATE_FILE_SUFFIX)) {
            String taskInstanceId = taskInstanceId(stateFile, STATE_FILE_SUFFIX);
            if (!requestedIds.contains(taskInstanceId)) {
                continue;
            }
            fileCounts.merge(taskInstanceId, 1, Integer::sum);
            selectedFiles.compute(taskInstanceId, (key, currentFile) -> currentFile == null
                    || compareStateFiles(stateFile, currentFile) > 0 ? stateFile : currentFile);
        }
        fileCounts.forEach((taskInstanceId, count) -> {
            if (count > 1) {
                log.warn("发现重复任务执行目录, taskInstanceId={}, count={}, selected={}",
                        taskInstanceId, count, selectedFiles.get(taskInstanceId));
            }
        });
        return selectedFiles;
    }

    private Optional<Path> preferredStateFile(List<Path> stateFiles) {
        return stateFiles.stream().max(this::compareStateFiles);
    }

    private int compareStateFiles(Path left, Path right) {
        WorkerTaskExecutionState leftState = readStateFile(left).orElse(null);
        WorkerTaskExecutionState rightState = readStateFile(right).orElse(null);
        if (leftState == null && rightState == null) {
            return compareLastModified(left, right);
        }
        if (leftState == null) {
            return -1;
        }
        if (rightState == null) {
            return 1;
        }
        int revisionComparison = Long.compare(leftState.getRevision(), rightState.getRevision());
        return revisionComparison == 0 ? compareLastModified(left, right) : revisionComparison;
    }

    private int compareLastModified(Path left, Path right) {
        return Long.compare(lastModified(left), lastModified(right));
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private boolean restoreExecution(String taskInstanceId, Path stateFile) {
        Path executionDir = stateFile.getParent();
        Path snapshotFile = taskFile(executionDir, taskInstanceId, SNAPSHOT_FILE_SUFFIX);
        if (!Files.exists(snapshotFile)) {
            return false;
        }
        Optional<WorkerTaskExecutionSnap> snapshot = readSnapshotFile(snapshotFile);
        Optional<WorkerTaskExecutionState> state = readStateFile(stateFile);
        if (snapshot.isEmpty() || state.isEmpty()) {
            return false;
        }
        if (!taskInstanceId.equals(snapshot.get().getTaskInstanceId())
                || !taskInstanceId.equals(state.get().getTaskInstanceId())) {
            log.warn("任务执行文件中的taskInstanceId不一致, taskInstanceId={}, path={}",
                    taskInstanceId, executionDir);
            return false;
        }
        executionDirs.put(taskInstanceId, executionDir);
        states.put(taskInstanceId, copyState(state.get()));
        return true;
    }

    private Path taskFile(Path executionDir, String taskInstanceId, String suffix) {
        return executionDir.resolve(taskInstanceId + suffix);
    }

    private String taskInstanceId(Path taskFile, String suffix) {
        String name = taskFile.getFileName().toString();
        return name.substring(0, name.length() - suffix.length());
    }

    private List<Path> findRuntimeFiles(String suffix) {
        if (!Files.exists(taskRuntimeRoot)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(taskRuntimeRoot)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .toList();
        } catch (Exception e) {
            log.warn("读取任务运行文件列表失败, root={}, suffix={}", taskRuntimeRoot, suffix, e);
            return Collections.emptyList();
        }
    }

    private Optional<WorkerTaskExecutionSnap> readSnapshotFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.readValue(path.toFile(), WorkerTaskExecutionSnap.class));
        } catch (Exception e) {
            log.warn("读取任务提交快照失败, path={}", path, e);
            return Optional.empty();
        }
    }

    private Optional<WorkerTaskExecutionState> readStateFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.readValue(path.toFile(), WorkerTaskExecutionState.class));
        } catch (Exception e) {
            log.warn("读取任务运行态失败, path={}", path, e);
            return Optional.empty();
        }
    }

    private void deleteRuntimeJsonFiles(Path executionDir) throws IOException {
        if (executionDir == null || !Files.exists(executionDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(executionDir)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(JSON_FILE_SUFFIX)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean shouldAppendStatusLog(WorkerTaskExecutionState oldState, WorkerTaskExecutionState newState) {
        if (oldState == null) {
            return true;
        }
        return oldState.getStatus() != newState.getStatus()
                || !Objects.equals(oldState.getAppId(), newState.getAppId())
                || !Objects.equals(oldState.getExitCode(), newState.getExitCode());
    }

    private void appendStatusLogSafely(Path executionDir, WorkerTaskExecutionState state) {
        try {
            Files.writeString(TaskRuntimeFiles.stateLog(executionDir), statusLine(state) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("追加任务状态日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
        }
    }

    private String statusLine(WorkerTaskExecutionState state) {
        return "time:" + state.getUpdateTime()
                + "|workerId:" + safeText(state.getWorkerId())
                + "|appId:" + safeText(state.getAppId())
                + "|revision:" + state.getRevision()
                + "|status:" + (state.getStatus() == null ? "" : state.getStatus().name())
                + "|exitCode:" + (state.getExitCode() == null ? "" : state.getExitCode());
    }

    private WorkerTaskExecutionState copyState(WorkerTaskExecutionState state) {
        return state == null ? null : state.copy();
    }

    private void copyPersistedState(WorkerTaskExecutionState source, WorkerTaskExecutionState target) {
        WorkerTaskExecutionState copy = copyState(source);
        target.setWorkerId(copy.getWorkerId());
        target.setAppId(copy.getAppId());
        target.setWorkDirPath(copy.getWorkDirPath());
        target.setStatus(copy.getStatus());
        target.setRevision(copy.getRevision());
        target.setExitCode(copy.getExitCode());
        target.setUpdateTime(copy.getUpdateTime());
        target.setResult(copy.getResult());
        target.setOutputVars(copy.getOutputVars());
    }

    private void writeJsonAtomically(Path target, Object value) throws Exception {
        String content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        Path temporaryFile = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private Path newExecutionDir(String flowInstanceId, String taskInstanceId) {
        String date = LocalDate.now().format(DATE_FORMATTER);
        return taskRuntimeRoot.resolve(date).resolve(safePath(flowInstanceId)).resolve(safePath(taskInstanceId));
    }

    private String safePath(String value) {
        return isBlank(value) ? "unknown" : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
