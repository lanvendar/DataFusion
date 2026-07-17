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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 基于文件的 Worker 任务执行状态存储.
 *
 * <p>{@code .snap} 是可覆盖的提交快照，不参与任务锁；{@code .state} 是带 revision 的运行态，写入必须在任务锁内完成。
 * 两类文件都通过临时文件替换，单次读取可以无锁获得完整快照。内存缓存只保存任务目录和最近运行上下文的索引。
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
    private final Cache<String, RunningTaskContext> executionCache;

    /**
     * 任务级内存锁.
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
                .map(path -> snapshotFile(path, taskInstanceId))
                .filter(Files::exists)
                .flatMap(this::readSnapshotFile)
                .or(() -> findSnapshotFile(taskInstanceId).flatMap(this::readSnapshotFile));
    }

    /**
     * 在任务锁内递增 revision、原子写入运行态并同步内存索引.
     *
     * @param state 任务运行态
     */
    @Override
    public void saveState(WorkerTaskExecutionState state) {
        if (state == null || isBlank(state.getTaskInstanceId())) {
            return;
        }
        withTaskLock(state.getTaskInstanceId(), () -> {
            try {
                // revision 生成、文件替换和缓存合并必须属于同一个任务级临界区。
                state.setUpdateTime(System.currentTimeMillis());
                Path stateFile = stateFile(state);
                Files.createDirectories(stateFile.getParent());
                WorkerTaskExecutionState oldState = Files.exists(stateFile) ? readStateFile(stateFile).orElse(null) : null;
                state.setRevision(oldState == null ? 1L : oldState.getRevision() + 1L);
                writeJsonAtomically(stateFile, state);
                updateContext(state.getTaskInstanceId(),
                        context -> context.mergeState(state, stateFile.getParent().toString()));
                if (shouldAppendStatusLog(oldState, state)) {
                    try {
                        appendStatusLog(stateFile.getParent(), state);
                    } catch (Exception e) {
                        log.warn("追加任务状态日志失败, taskInstanceId={}", state.getTaskInstanceId(), e);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("记录任务运行态失败: " + state.getTaskInstanceId(), e);
            }
            return null;
        });
    }

    /**
     * 无锁读取一个完整的运行态文件快照.
     *
     * <p>需要“读取、判断、写入”原子语义的调用方必须显式使用 {@link #withTaskLock(String, Supplier)}，
     * 并在锁内重新校验 revision。
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
                .map(path -> stateFile(path, taskInstanceId))
                .filter(Files::exists)
                .flatMap(this::readStateFile)
                .or(() -> findStateFile(taskInstanceId).flatMap(this::readStateFile));
    }

    @Override
    public RunningTaskContext get(String taskInstanceId) {
        return withTaskLock(taskInstanceId, () -> context(taskInstanceId).orElse(null));
    }

    @Override
    public RunningTaskContext getOrCreate(TaskRequest request) {
        if (request == null || isBlank(request.getTaskInstanceId())) {
            return RunningTaskContext.fromRequest(request);
        }
        return withTaskLock(request.getTaskInstanceId(), () -> context(request.getTaskInstanceId())
                .orElseGet(() -> {
                    RunningTaskContext context = RunningTaskContext.fromRequest(request);
                    executionCache.put(request.getTaskInstanceId(), context);
                    return context;
                }));
    }

    @Override
    public void save(RunningTaskContext context) {
        if (context == null || isBlank(context.getTaskInstanceId())) {
            return;
        }
        withTaskLock(context.getTaskInstanceId(), () -> {
            executionCache.put(context.getTaskInstanceId(), context);
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
            return null;
        });
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
        Map<String, Path> localTaskDirs = findRuntimeFiles(STATE_FILE_SUFFIX, "运行态").stream()
                .collect(Collectors.toMap(this::taskInstanceId, Path::getParent, (left, right) -> right));
        for (TaskRequest request : requests) {
            if (request == null || isBlank(request.getTaskInstanceId())) {
                continue;
            }
            withTaskLock(request.getTaskInstanceId(), () -> {
                restoreContext(request, localTaskDirs.get(request.getTaskInstanceId()))
                        .ifPresent(context -> executionCache.put(request.getTaskInstanceId(), context));
                return null;
            });
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
        withTaskLock(taskInstanceId, () -> {
            Optional<Path> executionDir = context(taskInstanceId)
                    .flatMap(this::workDir)
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
            return null;
        });
    }

    @Override
    public void removeContext(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return;
        }
        withTaskLock(taskInstanceId, () -> {
            executionCache.invalidate(taskInstanceId);
            return null;
        });
    }

    /**
     * 在任务级可重入锁内执行复合状态操作，不影响其他任务.
     *
     * @param taskInstanceId 任务实例 ID
     * @param action         待执行操作
     * @param <T>            返回类型
     * @return 操作结果
     */
    @Override
    public <T> T withTaskLock(String taskInstanceId, Supplier<T> action) {
        Objects.requireNonNull(action, "action不能为空");
        if (isBlank(taskInstanceId)) {
            return action.get();
        }
        ReentrantLock lock = taskLocks.get(taskInstanceId, key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private Path snapshotFile(WorkerTaskExecutionSnap snapshot) {
        return context(snapshot.getTaskInstanceId())
                .flatMap(this::workDir)
                .map(path -> snapshotFile(path, snapshot.getTaskInstanceId()))
                .orElseGet(() -> executionDir(snapshot.getFlowInstanceId(), snapshot.getTaskInstanceId())
                        .resolve(snapshotFileName(snapshot.getTaskInstanceId())));
    }

    private Path snapshotFile(Path executionDir, String taskInstanceId) {
        return executionDir.resolve(snapshotFileName(taskInstanceId));
    }

    private Path stateFile(WorkerTaskExecutionState state) {
        return context(state.getTaskInstanceId())
                .flatMap(this::workDir)
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

    private String snapshotFileName(String taskInstanceId) {
        return taskInstanceId + SNAPSHOT_FILE_SUFFIX;
    }

    private String stateFileName(String taskInstanceId) {
        return taskInstanceId + STATE_FILE_SUFFIX;
    }

    private String taskInstanceId(Path stateFile) {
        String name = fileName(stateFile);
        return name.substring(0, name.length() - STATE_FILE_SUFFIX.length());
    }

    private String fileName(Path path) {
        return path.getFileName().toString();
    }

    private void deleteRuntimeJsonFiles(Path executionDir) throws IOException {
        if (executionDir == null || !Files.exists(executionDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(executionDir)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> fileName(file).endsWith(JSON_FILE_SUFFIX)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private Optional<Path> findSnapshotFile(String taskInstanceId) {
        return findRuntimeFiles(SNAPSHOT_FILE_SUFFIX, "提交快照")
                .stream()
                .filter(path -> fileName(path).equals(snapshotFileName(taskInstanceId)))
                .findFirst();
    }

    private Optional<Path> findStateFile(String taskInstanceId) {
        return findRuntimeFiles(STATE_FILE_SUFFIX, "运行态")
                .stream()
                .filter(path -> fileName(path).equals(stateFileName(taskInstanceId)))
                .findFirst();
    }

    private List<Path> findRuntimeFiles(String suffix, String fileType) {
        Path root = taskRuntimeRoot();
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> fileName(path).endsWith(suffix)).toList();
        } catch (Exception e) {
            log.warn("读取任务{}文件列表失败, root={}", fileType, root, e);
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
            WorkerTaskExecutionSnap snapshot = readSnapshotFile(snapshotFile(taskDir, request.getTaskInstanceId()))
                    .orElse(null);
            WorkerTaskExecutionState state = readStateFile(stateFile(taskDir, request.getTaskInstanceId()))
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
        RunningTaskContext context = context(taskInstanceId).orElseGet(RunningTaskContext::new);
        context.setTaskInstanceId(taskInstanceId);
        updater.accept(context);
        executionCache.put(taskInstanceId, context);
    }

    private Optional<RunningTaskContext> context(String taskInstanceId) {
        if (isBlank(taskInstanceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(executionCache.getIfPresent(taskInstanceId));
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
