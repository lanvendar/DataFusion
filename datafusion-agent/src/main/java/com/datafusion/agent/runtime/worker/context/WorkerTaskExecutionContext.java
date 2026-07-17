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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于文件的 Worker 任务执行状态存储.
 *
 * <p>{@code .snap} 是可覆盖的提交快照，不参与任务锁；{@code .state} 是带 revision 的运行态，写入必须在任务锁内完成。
 * 两类文件都通过临时文件替换，单次读取可以无锁获得完整快照。线程安全的内存缓存保存最近运行上下文。
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
     * 任务执行上下文缓存.
     */
    private final Cache<String, RunningTaskContext> executionCache;

    /**
     * 任务运行态 CAS 写入使用的任务级内存锁.
     */
    private final Cache<String, ReentrantLock> taskLocks;

    /**
     * 构造函数.
     *
     * @param properties agent 配置
     */
    public WorkerTaskExecutionContext(AgentProperties properties) {
        this.properties = properties;
        this.executionCache = Caffeine.newBuilder().build();
        this.taskLocks = Caffeine.newBuilder().weakValues().build();
    }

    /**
     * 原子覆盖任务提交快照并更新内存索引，不占用状态锁.
     *
     * @param snapshot 任务提交快照
     */
    @Override
    public void saveSnapshot(WorkerTaskExecutionSnap snapshot) {
        if (snapshot == null || isBlank(snapshot.getTaskInstanceId())) {
            return;
        }
        try {
            Path snapshotFile = snapshotFile(snapshot);
            Files.createDirectories(snapshotFile.getParent());
            writeJsonAtomically(snapshotFile, snapshot);
            updateContext(snapshot.getTaskInstanceId(), context -> {
                context.setSnapshot(snapshot);
                if (context.getWorkDirPath() == null) {
                    context.setWorkDirPath(snapshotFile.getParent().toString());
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("记录任务提交快照失败: " + snapshot.getTaskInstanceId(), e);
        }
    }

    /**
     * 读取任务提交快照，优先使用缓存目录定位，缓存未命中时回退到本地文件扫描.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务提交快照
     */
    @Override
    public Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return context(taskInstanceId)
                .flatMap(this::workDir)
                .map(path -> taskFile(path, taskInstanceId, SNAPSHOT_FILE_SUFFIX))
                .filter(Files::exists)
                .flatMap(this::readSnapshotFile)
                .or(() -> findRuntimeFile(taskInstanceId, SNAPSHOT_FILE_SUFFIX)
                        .flatMap(this::readSnapshotFile));
    }

    /**
     * 在任务锁内校验 revision、原子写入运行态并同步内存索引.
     *
     * <p>锁仅覆盖当前 revision 复读、文件替换和缓存更新。revision 不一致表示查询或等锁期间已有其他状态写入，
     * 此时记录警告并返回 {@code false}，不覆盖最新状态。序列化或文件异常直接抛出。
     *
     * @param state            任务运行态
     * @param expectedRevision 预期的当前 revision，首次写入为 0
     * @return 是否写入成功
     */
    @Override
    public boolean saveState(WorkerTaskExecutionState state, long expectedRevision) {
        if (state == null || isBlank(state.getTaskInstanceId())) {
            log.warn("任务运行态无效, 拒绝写入");
            return false;
        }
        WorkerTaskExecutionState savedState = copyState(state);
        ReentrantLock lock = taskLocks.get(savedState.getTaskInstanceId(), key -> new ReentrantLock());
        lock.lock();
        try {
            Path stateFile = stateFile(savedState);
            Files.createDirectories(stateFile.getParent());
            WorkerTaskExecutionState oldState = Files.exists(stateFile)
                    ? OBJECT_MAPPER.readValue(stateFile.toFile(), WorkerTaskExecutionState.class) : null;
            long currentRevision = oldState == null ? 0L : oldState.getRevision();
            if (currentRevision != expectedRevision) {
                log.warn("任务运行态 revision 已变化, 拒绝写入, taskInstanceId={}, expectedRevision={},"
                                + " currentRevision={}, currentStatus={}, nextStatus={}",
                        savedState.getTaskInstanceId(), expectedRevision, currentRevision,
                        oldState == null ? null : oldState.getStatus(), savedState.getStatus());
                return false;
            }
            if (oldState != null) {
                if (savedState.getAppId() == null) {
                    savedState.setAppId(oldState.getAppId());
                }
                if (savedState.getWorkerId() == null) {
                    savedState.setWorkerId(oldState.getWorkerId());
                }
                if (savedState.getWorkDirPath() == null) {
                    savedState.setWorkDirPath(oldState.getWorkDirPath());
                }
                if (savedState.getExitCode() == null) {
                    savedState.setExitCode(oldState.getExitCode());
                }
            }
            if (savedState.getWorkDirPath() == null) {
                savedState.setWorkDirPath(stateFile.getParent().toString());
            }
            savedState.setUpdateTime(System.currentTimeMillis());
            savedState.setRevision(currentRevision + 1L);

            writeJsonAtomically(stateFile, savedState);
            updateContext(savedState.getTaskInstanceId(), context -> context.setExecutionState(savedState));
            if (shouldAppendStatusLog(oldState, savedState)) {
                try {
                    appendStatusLog(stateFile.getParent(), savedState);
                } catch (Exception e) {
                    log.warn("追加任务状态日志失败, taskInstanceId={}", savedState.getTaskInstanceId(), e);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("记录任务运行态失败: " + savedState.getTaskInstanceId(), e);
        } finally {
            lock.unlock();
        }
        state.setAppId(savedState.getAppId());
        state.setWorkerId(savedState.getWorkerId());
        state.setWorkDirPath(savedState.getWorkDirPath());
        state.setExitCode(savedState.getExitCode());
        state.setUpdateTime(savedState.getUpdateTime());
        state.setRevision(savedState.getRevision());
        return true;
    }

    /**
     * 无锁读取一个完整的运行态文件快照.
     *
     * @param taskInstanceId 任务实例 ID
     * @return 任务运行态
     */
    @Override
    public Optional<WorkerTaskExecutionState> readState(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return context(taskInstanceId)
                .flatMap(this::workDir)
                .map(path -> taskFile(path, taskInstanceId, STATE_FILE_SUFFIX))
                .filter(Files::exists)
                .flatMap(this::readStateFile)
                .or(() -> findRuntimeFile(taskInstanceId, STATE_FILE_SUFFIX)
                        .flatMap(this::readStateFile));
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
        return executionCache.get(request.getTaskInstanceId(), key -> RunningTaskContext.fromRequest(request));
    }

    @Override
    public void save(RunningTaskContext context) {
        if (context == null || isBlank(context.getTaskInstanceId())) {
            return;
        }
        executionCache.put(context.getTaskInstanceId(), context);
        saveSnapshot(context.getSnapshot());
        WorkerTaskExecutionState state = context.getExecutionState();
        if (state != null) {
            saveState(state, state.getRevision());
        }
    }

    /**
     * Agent 启动时一次性扫描本地状态，并只恢复 Manager 返回的未完成任务.
     *
     * @param requests Manager 返回的未完成任务
     */
    @Override
    public void restoreListeningTasks(List<TaskRequest> requests) {
        if (requests == null) {
            return;
        }
        // 先建立 taskInstanceId 到目录的索引，避免恢复每个任务时重复遍历运行目录。
        Map<String, Path> localTaskDirs = findRuntimeFiles(STATE_FILE_SUFFIX).stream()
                .collect(Collectors.toMap(this::taskInstanceId, Path::getParent, (left, right) -> right));
        for (TaskRequest request : requests) {
            if (request == null || isBlank(request.getTaskInstanceId())) {
                continue;
            }
            restoreContext(request, localTaskDirs.get(request.getTaskInstanceId()))
                    .ifPresent(context -> executionCache.put(request.getTaskInstanceId(), context));
        }
    }

    /**
     * 删除任务运行态、提交快照和运行期 JSON 文件，并清理内存索引.
     *
     * @param taskInstanceId 任务实例 ID
     */
    @Override
    public void deleteExecution(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        Path executionDir = context(taskInstanceId)
                .flatMap(this::workDir)
                .or(() -> findRuntimeFile(taskInstanceId, STATE_FILE_SUFFIX).map(Path::getParent))
                .or(() -> findRuntimeFile(taskInstanceId, SNAPSHOT_FILE_SUFFIX).map(Path::getParent))
                .orElse(null);

        executionCache.invalidate(taskInstanceId);
        if (executionDir == null) {
            return;
        }
        try {
            Files.deleteIfExists(taskFile(executionDir, taskInstanceId, STATE_FILE_SUFFIX));
            Files.deleteIfExists(taskFile(executionDir, taskInstanceId, SNAPSHOT_FILE_SUFFIX));
            deleteRuntimeJsonFiles(executionDir);
        } catch (Exception e) {
            log.warn("删除任务执行文件失败, taskInstanceId={}", taskInstanceId, e);
        }
    }

    @Override
    public void removeContext(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        executionCache.invalidate(taskInstanceId);
    }

    private Path snapshotFile(WorkerTaskExecutionSnap snapshot) {
        String taskInstanceId = snapshot.getTaskInstanceId();
        Path workDir = context(taskInstanceId)
                .flatMap(this::workDir)
                .orElseGet(() -> executionDir(snapshot.getFlowInstanceId(), taskInstanceId));
        return taskFile(workDir, taskInstanceId, SNAPSHOT_FILE_SUFFIX);
    }

    private Path stateFile(WorkerTaskExecutionState state) {
        String taskInstanceId = state.getTaskInstanceId();
        Path workDir = context(taskInstanceId)
                .flatMap(this::workDir)
                .orElseGet(() -> isBlank(state.getWorkDirPath())
                        ? executionDir(null, taskInstanceId) : Path.of(state.getWorkDirPath()));
        return taskFile(workDir, taskInstanceId, STATE_FILE_SUFFIX);
    }

    private Path taskFile(Path workDir, String taskInstanceId, String suffix) {
        return workDir.resolve(taskInstanceId + suffix);
    }

    private String taskInstanceId(Path stateFile) {
        String name = stateFile.getFileName().toString();
        return name.substring(0, name.length() - STATE_FILE_SUFFIX.length());
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

    private Optional<Path> findRuntimeFile(String taskInstanceId, String suffix) {
        return findRuntimeFiles(suffix)
                .stream()
                .filter(path -> path.getFileName().toString().equals(taskInstanceId + suffix))
                .findFirst();
    }

    private List<Path> findRuntimeFiles(String suffix) {
        Path root = taskRuntimeRoot();
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(suffix)).toList();
        } catch (Exception e) {
            log.warn("读取任务运行文件列表失败, root={}, suffix={}", root, suffix, e);
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

    private Optional<Path> workDir(RunningTaskContext context) {
        if (context == null || isBlank(context.getWorkDirPath())) {
            return Optional.empty();
        }
        return Optional.of(Path.of(context.getWorkDirPath()));
    }

    private Optional<RunningTaskContext> restoreContext(TaskRequest request, Path taskDir) {
        return Optional.ofNullable(taskDir).map(path -> {
            String taskInstanceId = request.getTaskInstanceId();
            WorkerTaskExecutionSnap snapshot = readSnapshotFile(
                    taskFile(taskDir, taskInstanceId, SNAPSHOT_FILE_SUFFIX))
                    .orElse(null);
            WorkerTaskExecutionState state = readStateFile(taskFile(taskDir, taskInstanceId, STATE_FILE_SUFFIX))
                    .orElse(null);
            RunningTaskContext context = state == null ? RunningTaskContext.fromRequest(request)
                    : RunningTaskContext.fromSnapshotAndState(snapshot, state);
            context.updateRequest(request);
            if (context.getWorkDirPath() == null) {
                context.setWorkDirPath(taskDir.toString());
            }
            return context;
        });
    }

    private void updateContext(String taskInstanceId, Consumer<RunningTaskContext> updater) {
        if (isBlank(taskInstanceId) || updater == null) {
            return;
        }
        executionCache.asMap().compute(taskInstanceId, (key, cachedContext) -> {
            RunningTaskContext context = cachedContext == null ? new RunningTaskContext() : cachedContext;
            context.setTaskInstanceId(taskInstanceId);
            updater.accept(context);
            return context;
        });
    }

    private Optional<RunningTaskContext> context(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(executionCache.getIfPresent(taskInstanceId));
    }

    /**
     * 复制待持久化状态，避免 revision 和更新时间在文件写入成功前泄漏到调用方对象.
     *
     * @param state 原始任务运行态
     * @return 待持久化任务运行态
     */
    private WorkerTaskExecutionState copyState(WorkerTaskExecutionState state) {
        return WorkerTaskExecutionState.builder()
                .taskInstanceId(state.getTaskInstanceId())
                .workerId(state.getWorkerId())
                .appId(state.getAppId())
                .workDirPath(state.getWorkDirPath())
                .status(state.getStatus())
                .revision(state.getRevision())
                .exitCode(state.getExitCode())
                .updateTime(state.getUpdateTime())
                .result(state.getResult())
                .outputVars(state.getOutputVars())
                .build();
    }

    /**
     * 通过同目录临时文件替换目标文件，确保无锁读取不会看到半个 JSON.
     *
     * <p>文件系统不支持 {@link StandardCopyOption#ATOMIC_MOVE} 时退化为普通替换；临时文件仍在写完后才参与替换。
     *
     * @param target 目标文件
     * @param value  待序列化对象
     * @throws Exception 序列化或文件写入失败
     */
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

    private void appendStatusLog(Path executionDir, WorkerTaskExecutionState state) throws Exception {
        Files.writeString(TaskRuntimeFiles.stateLog(executionDir),
                statusLine(state) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String statusLine(WorkerTaskExecutionState state) {
        return "time:" + state.getUpdateTime()
                + "|workerId:" + safeText(state.getWorkerId())
                + "|appId:" + safeText(state.getAppId())
                + "|revision:" + state.getRevision()
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
