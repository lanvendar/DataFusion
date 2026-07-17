# 调度 Worker 框架设计

> 数据结构定义见 [scheduler-worker-data-define.md](./scheduler-worker-data-define.md)。本文档描述重构后的目标边界；Java 代码按本文档分阶段迁移，不保留旧运行时抽象的兼容层。

## 1. 定位

`datafusion-scheduler-worker` 是 Worker 子系统的中性框架层。它负责 Worker 生命周期编排、任务入口、插件路由、动作状态协调、执行记录存储契约和结果上报契约，不依赖 Spring、HTTP、文件系统、Kubernetes 或 Manager 实现。

`datafusion-agent` 是该框架的运行时实现：提供 HTTP Client、文件版执行存储、任务级监听器、线程池以及具体插件。

依赖方向保持单向：

```text
datafusion-common-data
        ↑
datafusion-scheduler-worker
        ↑
datafusion-agent
```

## 2. 目标类结构

```text
com.datafusion.scheduler.worker
    WorkerService

com.datafusion.scheduler.worker.client
    WorkerClient
    WorkerIdentityStore

com.datafusion.scheduler.worker.context
    RunningTaskContext
    WorkerTaskExecutionSnap
    WorkerTaskExecutionState
    WorkerTaskExecutionStore

com.datafusion.scheduler.worker.plugin
    PluginTaskExecutor
    PluginRunModeStateMapping
    WorkerPluginRouter

com.datafusion.scheduler.worker.state
    WorkerTaskStateCoordinator

com.datafusion.scheduler.worker.reporter
    TaskResultReporter
    TaskStateListenerRegistry
```

本次重构删除以下旧抽象，不增加兼容适配层：

- `WorkerTaskOperator`：RPC 直接调用 `WorkerService`。
- `WorkerTaskService`：职责并入新的子系统入口 `WorkerService`。
- `WorkerTaskContextStorage`、`CachedWorkerTaskContextStorage`：不再缓存可变 `RunningTaskContext`。
- `WorkerPluginLoader`：Spring 已能直接提供执行器和映射器列表。
- `WorkerTaskOperatorRouter`：由同时路由执行器与状态映射器的 `WorkerPluginRouter` 替代。

## 3. 职责边界

### 3.1 WorkerService

`WorkerService` 类比 Master 侧的 `MasterService`，是 Worker 子系统唯一入口，统一管理：

- `WorkerClient`：注册、恢复、心跳和下线；
- `WorkerIdentityStore`：持久化 Manager 返回的 Worker 身份，支持 Agent 重启后继续使用同一 Worker.id；
- `WorkerPluginRouter`：按 `pluginType + runMode` 路由执行器与状态映射器；
- `WorkerTaskExecutionStore`：读取和保存 `.snap/.state`；
- `WorkerTaskStateCoordinator`：状态准入、迁移和 revision 提交；
- `TaskStateListenerRegistry`：任务监听注册、状态变化上报和终态监听清理；
- `RunningTaskContext`：按一次动作创建，动作结束即释放；
- Worker ready 状态及当前 `Worker.id`。

`WorkerService` 负责请求级编排和 `TaskResult` 构造，不实现插件参数解析、第三方资源操作、状态文件细节或 Manager HTTP 协议。

### 3.2 PluginTaskExecutor

`PluginTaskExecutor` 只负责一个 `pluginType + runMode` 组合的第三方动作：

```java
public interface PluginTaskExecutor {

    String pluginType();

    String runMode();

    void validate(WorkerTaskExecutionSnap snapshot);

    WorkerResult submitTask(RunningTaskContext context);

    WorkerResult stopTask(RunningTaskContext context);

    WorkerResult killTask(RunningTaskContext context);

    boolean finishTask(RunningTaskContext context);
}
```

`WorkerResult` 结构保持不变，只描述动作产生的 worker/runtime 信息。插件执行器不得：

- 读写 `WorkerTaskExecutionStore`；
- 保存 `.snap` 或 `.state`；
- 构造 `TaskResult`；
- 直接上报 Manager；
- 决定周期查询得到的系统状态迁移。

动作无法完成时抛出异常；正常返回表示第三方调用已按该动作契约完成。`finishTask` 负责插件自身全部收尾，不再拆分额外的 `destroyTask` 阶段；返回 `false` 或抛异常时必须保留执行记录和监听，以便重试。

### 3.3 RunningTaskContext

`RunningTaskContext` 是一次插件动作的聚合，不是缓存、存储或 DTO 转换器：

```text
snapshot         本次动作使用的最新提交快照
executionState   动作前置态写入成功后的候选副本
previousSnapshot 重新提交前读取的旧快照，仅 submit 可能存在
previousState    重新提交前读取的旧运行态，仅 submit 可能存在
workDirPath      Store 为该 taskInstanceId 解析出的规范任务目录
```

上下文由 `WorkerService` 从请求及 Store 结果创建，传给插件后即丢弃。插件只允许修改本次独占的
`executionState` 候选副本；其他引用只读。Context 不提供 `updateSnapshot`、`mergeState`、`fillRequest`、
`toTaskResult` 等通用合并或 DTO 转换方法。

`TaskRequest` 是调用来源；`.snap + .state` 是 Worker 已接受并执行动作后的结果。控制请求可以只提供 `taskInstanceId`，此时 `WorkerService` 从最新 `.snap/.state` 组装动作上下文。

### 3.4 WorkerTaskStateCoordinator

`WorkerTaskStateCoordinator` 是单一具体类，不再增加接口或工厂。它是系统状态规则的唯一所有者，负责：

- 判断当前状态能否进入 `SUBMITTING/STOPPING/KILLING`；
- 构造动作结果候选状态，并把 `WorkerResult` 合并进候选状态；
- 校验第三方查询映射结果能否覆盖当前状态；
- 处理 LOCAL 进程快速退出的终态竞争；
- 调用 `WorkerTaskExecutionStore.saveState(candidate, expectedRevision)`；
- CAS 失败后读取并返回真实落盘状态。

Coordinator 不负责文件路径、序列化、锁实现、插件路由、第三方查询或 Manager 上报。

周期查询与本地进程退出必须使用不同的提交语义：

- 周期查询结果基于一次查询基线；revision 冲突后直接丢弃，下一周期重新查询。
- LOCAL 进程退出是一次性事实；若 revision 已变化但仍是同一 `appId`，允许重新校验后提交终态，不能无声丢失。

### 3.5 WorkerTaskExecutionStore

`WorkerTaskExecutionStore` 只负责持久化事实和原子写入：

```java
String saveSnapshot(WorkerTaskExecutionSnap snapshot);

Optional<WorkerTaskExecutionSnap> readSnapshot(String taskInstanceId);

boolean saveState(WorkerTaskExecutionState candidate, long expectedRevision);

Optional<WorkerTaskExecutionState> readState(String taskInstanceId);

Set<String> restoreExecutions(Collection<String> taskInstanceIds);

void deleteExecution(String taskInstanceId);
```

`saveSnapshot` 整体覆盖快照并返回该任务的规范 `workDirPath`。同一 `taskInstanceId` 已有路径索引或执行文件时必须复用原目录，不能按当前日期重新创建第二个目录。

`saveState` 在实现内部完成任务级短锁、revision 复读、原子替换和 revision 加一。Store 不校验业务状态迁移，也不自动把旧 `appId/workDirPath/exitCode` 合并回候选状态；需要保留或清空哪些字段由 Coordinator 明确决定。首次动作的 `workDirPath` 使用 `saveSnapshot` 返回值构造，不由各插件重复计算。

`restoreExecutions` 仅在启动恢复时接收 Manager 未完成任务 ID，并返回本地 `.snap/.state`
完整且加载成功的交集，不表示周期扫描。

### 3.6 WorkerPluginRouter

执行器和状态映射器使用同一个规范化路由键：

```text
RouteKey(normalize(pluginType), normalize(runMode))
```

规范化规则为去除首尾空白并转大写。同一组合存在多个执行器或多个映射器时，装配阶段直接失败。每个可提交的执行器路由必须有对应状态映射器；否则启动失败，不能等任务提交后才永久告警“无 mapping”。路由器只维护索引，不包含插件加载或业务状态逻辑。

### 3.7 WorkerClient 与结果上报

`WorkerClient` 位于 worker 模块，与 Master 的 Worker 管理协议对应，仅包含：

- 注册 Worker；
- 心跳；
- 下线；
- 查询当前 Worker 的未完成任务。

任务结果上报继续使用独立的 `TaskResultReporter`，不放入 `WorkerClient`。

`WorkerIdentityStore` 是独立的本地身份存储端口，只负责读取、保存和删除 Manager 已确认的 Worker 身份。
它不包含注册协议、重试策略或任务状态；Agent 侧由现有本地配置存储实现该端口。

`TaskStateListenerRegistry` 扩展 `TaskResultReporter` 并增加任务监听注册/注销能力。Agent 实现使用装饰器结构：外层 Registry 负责监听、去重和重试，内层 Reporter 只负责一次 HTTP 上报。`WorkerService` 只持有外层 Registry，避免同步动作与 HTTP 传输互相耦合。

## 4. Worker 生命周期

```text
WorkerService.start
    -> WorkerIdentityStore.load
    -> 无有效身份时调用 WorkerClient.register
    -> WorkerIdentityStore.save Manager 返回的 Worker.id
    -> WorkerClient.getUnfinishedTasks
    -> WorkerTaskExecutionStore.restoreExecutions（启动时一次）
    -> Store 返回 Manager 未完成任务与本地完整执行记录的 taskInstanceId 交集
    -> 对交集任务注册监听，reportedStatus 初始为 null
    -> 恢复完成后 ready=true
    -> 周期 heartbeat

WorkerService.stop
    -> ready=false
    -> 停止心跳和任务监听调度
    -> WorkerClient.offline
```

RPC Provider 只检查并调用 `WorkerService`，不再直接管理 `AgentRuntimeState`、监听器或插件路由器。

注册成功但恢复失败时不得重复创建新的 Worker 身份。应保留已注册的 `Worker.id`，继续心跳并重试恢复；只有恢复完成后才进入 ready。

## 5. submit 流程

```text
1. TaskRequest 进入 WorkerService，补齐 workerId/submitMode 并校验 taskInstanceId。
2. 根据 TaskRequest 路由执行器，并构造新的 WorkerTaskExecutionSnap。
3. 在覆盖 .snap 前读取 previousSnapshot/previousState，供 DataX/Spark 重提交清理旧资源。
4. 整体保存新的 .snap；Store 返回并复用该 taskInstanceId 的规范 workDirPath。
5. Coordinator 以当前 .state revision 准入并写入 SUBMITTING。
6. 创建动作级 RunningTaskContext 并调用 executor.validate(context)；校验失败提交 `SUBMIT_FAILURE`。
7. 调用 executor.submit(context)。插件只创建第三方资源并返回 WorkerResult；LOCAL 插件取得进程和 appId 后立即注册退出 watcher。
8. 插件把候选状态设为 SUBMIT_SUCCESS；Coordinator 合并 WorkerResult，以 actionState.revision 提交。
9. 若 LOCAL watcher 已先写入同一 appId 的 RUN_SUCCESS/RUN_FAILURE，动作结果 CAS 失败并返回该终态，不覆盖它。
10. WorkerService 从最终落盘的 .snap/.state 构造 TaskResult。
11. 返回前用本次 TaskResult.taskState 幂等注册监听，作为 Manager 已通过 RPC 获知的 reportedStatus。
```

不得增加 `afterSubmitStateSaved` 钩子。LOCAL watcher 的安全顺序固定为：

```text
进程创建成功
-> 获得 appId
-> 注册退出 watcher（携带 taskInstanceId、actionRevision、appId）
-> 返回 WorkerResult
```

### 5.1 同步与异步提交

- `SYNC`：请求线程执行插件提交，但不等待任务运行结束；正常提交为 `SUBMIT_SUCCESS`。
- `ASYNC`：先落 `SUBMITTING`，成功进入执行队列后返回 `SUBMITTING`；后台动作只更新本地状态，监听器负责后续上报。
- 异步队列拒绝只能用本次 action revision 提交 `SUBMIT_FAILURE`，不能覆盖并发产生的新 revision。

`SUBMIT_SUCCESS` 不是终态。对于同一 `appId`，LOCAL 退出事件或第三方映射得到的运行终态允许覆盖它。

### 5.2 重新提交

DataX/Spark 可能需要在创建新资源前清理旧资源。`WorkerService` 必须先读取旧 `.snap/.state`，再覆盖新 `.snap`，并把两组数据同时放进本次 `RunningTaskContext`。插件在 `submitTask` 内先按 `previousSnapshot/previousState` 幂等清理，再按 `snapshot/state` 创建新资源，不增加时序钩子。

旧上下文不进入长期缓存，也不新增 `.previous.snap` 文件。Agent 在“新 `.snap` 已覆盖、旧资源尚未清理”之间崩溃时，
按稳定资源名、旧 `.state.appId` 和新快照执行幂等恢复；新旧配置生成的任务资源路径保持一致。

## 6. stop / kill / finish 流程

控制请求只要求 `taskInstanceId`：

```text
WorkerService 读取最新 .snap/.state
-> Router 定位 PluginTaskExecutor
-> Coordinator 写入 STOPPING/KILLING
-> 创建 RunningTaskContext
-> executor.stopTask/killTask 返回 WorkerResult或抛异常
-> Coordinator 提交动作结果
-> 重读权威状态并构造 TaskResult
-> 用响应状态刷新监听注册
```

`WorkerResult` 不包含状态。插件通过本次 `RunningTaskContext.executionState` 候选副本表达动作结果：K8S 请求已接受时
保持 `STOPPING/KILLING`；LOCAL 已确认进程退出时可写 `STOP_SUCCESS/KILLED`。Coordinator 校验并提交候选状态，
异常分别进入 `STOP_FAILURE/UNKNOWN`。

`STOPPING` 表达控制意图。第三方在该阶段自然完成、取消或资源消失时统一推进为 `STOP_SUCCESS`；只有明确的停止失败才进入 `STOP_FAILURE`，不能回退为 `RUN_SUCCESS/RUN_FAILURE`。

finish 流程：

```text
读取 .snap/.state 并调用 executor.finishTask(context)
-> false 或异常：保留监听、.snap、.state，返回失败
-> true：注销监听，再删除 .snap/.state 和路径索引，返回成功
```

插件未匹配、清理失败或文件删除失败都不能伪装为 finish 成功。

## 7. 状态监听与 revision 竞争

任务级监听每个周期执行以下固定流程：

```text
1. 无锁读取查询基线 Q（status + revision）。
2. mapping.mapState(snapshot, Q)，查询第三方并映射为 StatusEnum。
3. 无锁读取最新状态 S0。
4. Q.revision != S0.revision：查询期间有新动作或状态写入，直接丢弃，不加锁。
5. mappedStatus == S0.status 且没有待重试上报：不写入，不加锁。
6. mappedStatus != S0.status：Coordinator 校验迁移并调用 Store.saveState(candidate, S0.revision)。
7. Store 在任务锁内复读 S1；S1.revision != S0.revision 时返回 false，丢弃本次查询结果。
8. revision 一致时写入候选状态并将 revision + 1；锁外构造并上报 TaskResult。
```

Manager 上报不在任务锁内。上报成功才推进 `reportedStatus`；失败保持旧值并在下一周期重试。最终状态成功上报后停止周期查询，但监听注册项按保留策略暂存，等待 UI 后续 finish/强制成功处理。

UNKNOWN 连续次数必须绑定当前查询基线，而不是只按 taskInstanceId 累加。监听器观察到 revision、status 或 appId 任一变化时立即清零；不能把 RUNNING 阶段的查询失败累计到后续 STOPPING/KILLING 动作。

### 7.1 LOCAL 快速退出

LOCAL watcher 提交终态时同时携带 `actionRevision + appId`：

- 当前仍是原 revision 的 `SUBMITTING`：终态可以直接获胜；
- revision 已进入 `SUBMIT_SUCCESS/RUNNING` 且 `appId` 相同：终态允许覆盖；
- 已有新 `SUBMITTING` 或 `appId` 不同：判定为旧进程事件并丢弃。

LOCAL 退出事件不能简单复用“CAS 失败即丢弃”的周期查询规则。

## 8. 状态恢复与 Store 缓存

Store 的 Agent 文件实现可维护：

```text
Cache<taskInstanceId, executionDir>
Cache<taskInstanceId, WorkerTaskExecutionState>
```

两个缓存都只是 `.snap/.state` 文件的进程内投影，不是新的真相源，也不缓存 `RunningTaskContext`。推荐普通
`Cache` 而非 `LoadingCache`；缓存未命中时由 Store 显式读取文件并回填。规则如下：

- 启动扫描一次本地 `.state/.snap` 并填充路径索引；
- 只将 Manager 未完成任务与本地完整执行记录的交集加载到状态缓存；
- `readState` 返回状态副本，禁止把缓存中的可变 Bean 暴露给 Service、插件或监听器；
- `saveState` 在任务锁内比较 revision，先原子替换 `.state`，成功后再替换状态缓存；文件写入失败时缓存不得前进；
- 保存 `.snap/.state` 时写入索引；
- 读取时先查索引，未命中才显式定位一次并回填；
- 成功删除执行记录后使路径和状态缓存同时失效；
- 缓存容量淘汰不影响正确性，下次读取从文件恢复。

启动扫描发现同一 `taskInstanceId` 存在多个目录时必须记录告警，并按最高 `.state.revision` 选择唯一目录；revision 相同再按文件更新时间选择。不得依赖文件系统遍历的 `findFirst` 顺序。

Agent 不做后续低频全量健康扫描。一个任务只属于一个 Agent，不处理多个 Agent 同时写同一 `.state`。

## 9. 明确不实现

- 不改变 `TaskRequest`、`TaskResult`、`WorkerResult`、`.snap` 或 `.state` 的既有字段结构。
- 不新增 `PluginExecutionResult`、执行器抽象基类、状态策略工厂或通用 RuntimeRef。
- 不在 Store 中实现业务状态机。
- 不缓存可变 `RunningTaskContext`。
- 不向 Store 外暴露状态缓存或允许调用方直接 `put`；所有状态变化仍经过 revision CAS。
- 不在 RPC Provider、插件或监听器中复制状态准入规则。
- 不把任务结果上报合并进 `WorkerClient`。
- 不增加 `afterSubmitStateSaved` 钩子。

## 10. 恢复约束

- Agent 重启时只恢复 Manager 未完成任务与本地完整 `.snap/.state` 记录的交集，并由文件重新填充 Store Cache。
- `SUBMITTING` 且 `appId` 为空时，K8S 实现按确定性资源名查询：存在则继续映射并补齐运行引用，不存在则进入
  `SUBMIT_FAILURE`；LOCAL 没有可恢复 PID 时进入 `SUBMIT_FAILURE`。
- 重提交正常路径使用本次 Context 中的 `previousSnapshot/previousState` 清理旧资源；崩溃恢复依赖稳定资源名、
  `.state` 中仍可用的旧运行引用以及新快照执行幂等清理，不增加 `.previous.snap`。

## 11. 验证

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-worker -am
```
