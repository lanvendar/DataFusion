# 调度 Worker 数据结构定义

> 本文档是 `datafusion-scheduler-worker` 重构后的模型、端口和转换规则事实源。框架不定义数据库表；运行时持久化由 `datafusion-agent` 实现。

## 1. 数据库模型

无。`datafusion-scheduler-worker` 是框架 jar，不直接访问数据库。

## 2. 通信模型

本次重构不修改 common-data 中既有 RPC DTO。

### 2.1 TaskRequest

| 字段 | 类型 | 规则 | 说明 |
|------|------|------|------|
| `flowInstanceId` | `String` | submit 可选，控制请求可省略 | 流程实例 ID |
| `taskInstanceId` | `String` | 必填 | Worker 幂等键 |
| `taskName` | `String` | 可选 | 任务名称 |
| `taskState` | `StatusEnum` | 可选 | Manager/Master 当前状态，不直接作为本地权威状态 |
| `taskData` | `JsonNode` | submit 可选 | 渲染后的插件业务数据 |
| `pluginType` | `String` | submit 必填 | 插件路由键 |
| `runMode` | `String` | submit 必填 | 运行模式路由键 |
| `pluginParam` | `JsonNode` | submit 可选 | 插件运行配置 |
| `submitMode` | `SubmitModeEnum` | 默认 `SYNC` | 只表示提交确认方式 |
| `workerResult` | `WorkerResult` | 可选 | 请求中的 workerId 或兼容运行引用；最终以本地 `.state` 为准 |

`TaskRequest` 是动作输入来源。submit 时由它生成完整新快照；stop/kill/finish 可以只携带 `taskInstanceId`，其他参数从本地 `.snap/.state` 恢复。

### 2.2 TaskResult

| 字段 | 类型 | 规则 | 说明 |
|------|------|------|------|
| `taskInstanceId` | `String` | 必填 | 任务实例 ID |
| `flowInstanceId` | `String` | 可选 | 来自当前 `.snap` |
| `taskName` | `String` | 可选 | 来自当前 `.snap` |
| `taskState` | `StatusEnum` | 必填 | 必须是最终实际落盘状态，不能返回 CAS 失败的候选状态 |
| `submitMode` | `SubmitModeEnum` | 可选 | 来自当前 `.snap` |
| `workerResult` | `WorkerResult` | 可选 | 由当前 `.state` 映射 |

### 2.3 WorkerResult

`WorkerResult` 同时作为插件动作返回值和 `TaskResult.workerResult`，结构保持不变。

| 字段 | 类型 | 规则 | 说明 |
|------|------|------|------|
| `outputVars` | `Map<String, Variable>` | 可选 | 输出变量 |
| `workerId` | `String` | 可选 | Manager 分配的 `Worker.id` |
| `appId` | `String` | 可选 | PID、Kubernetes Job/Deployment 等运行引用 |
| `workDirPath` | `String` | 可选 | Agent 任务运行目录 |
| `message` | `String` | 可选 | 动作摘要或错误说明 |
| `pluginLogUri` | `String` | 可选 | 插件日志入口 |

`WorkerResult` 不增加 `status`、`revision` 或 `exitCode`。插件正常返回的状态语义由动作契约统一定义，异常由 `WorkerService/WorkerTaskStateCoordinator` 映射为失败状态。

### 2.4 Worker

| 字段 | 类型 | 规则 | 说明 |
|------|------|------|------|
| `id` | `String` | 注册成功后必填 | Manager 分配的 Worker ID |
| `workerCode` | `String` | 注册必填 | Agent 稳定编码 |
| `ip` | `String` | 必填 | Worker IP |
| `port` | `Integer` | 必填 | Agent RPC 端口 |
| `pluginTypes` | `List<String>` | 可选 | 当前 Worker 支持的插件类型 |
| `status` | `Integer` | 必填 | 上线/下线状态 |
| `hostName` | `String` | 必填 | 主机名 |
| `registerTime` | `Long` | 可选 | 注册时间 |
| `lastHeartbeatTime` | `Long` | 可选 | 最近心跳时间 |
| `workerLogDir` | `String` | 可选 | Agent 服务日志目录 |
| `updateTime` | `Long` | 可选 | 更新时间 |

## 3. 持久化执行模型

### 3.1 WorkerTaskExecutionSnap

`.snap` 是最近一次已接收提交的完整配置结果。

| 字段 | 类型 | 生命周期 | 说明 |
|------|------|----------|------|
| `flowInstanceId` | `String` | 每次 submit 整体覆盖 | 流程实例 ID |
| `taskInstanceId` | `String` | 固定 | 任务实例 ID |
| `taskName` | `String` | 每次 submit 整体覆盖 | 任务名称 |
| `pluginType` | `String` | 每次 submit 整体覆盖 | 插件路由键 |
| `runMode` | `String` | 插件校验归一化后整体覆盖 | 运行模式路由键 |
| `workerId` | `String` | 每次 submit 整体覆盖 | 当前 Worker ID |
| `taskData` | `JsonNode` | 每次 submit 整体覆盖 | 插件业务数据 |
| `pluginParam` | `JsonNode` | 每次 submit 整体覆盖 | 插件运行配置 |
| `submitMode` | `SubmitModeEnum` | 每次 submit 整体覆盖 | 提交方式 |

规则：

- `.snap` 不做旧值合并，不参与 revision 和任务状态锁；
- 覆盖新 `.snap` 前，`WorkerService` 可读取旧快照放入本次 `RunningTaskContext.previousSnapshot`；
- 插件执行器只读快照，不自行保存或改写；
- API/Spider 委托 Shell 时，持久化的路由仍保持原始 `API/LOCAL` 或 `SPIDER/LOCAL`，临时 Shell 规格不覆盖 `.snap`。

### 3.2 WorkerTaskExecutionState

`.state` 是 Worker 运行态的权威结果。

| 字段 | 类型 | 生命周期 | 说明 |
|------|------|----------|------|
| `taskInstanceId` | `String` | 固定 | 任务实例 ID |
| `workerId` | `String` | 动作提交时写入 | Worker ID |
| `appId` | `String` | 第三方资源创建后写入；重新提交时可清空 | 运行引用 |
| `workDirPath` | `String` | 动作提交后写入 | Agent 任务运行目录 |
| `status` | `StatusEnum` | 每次状态提交更新 | Worker 系统状态 |
| `revision` | `long` | 每次成功写入加一 | 单 Agent 内乐观并发版本 |
| `exitCode` | `Integer` | LOCAL 进程退出后写入；重新提交时清空 | 本地诊断字段 |
| `updateTime` | `Long` | 每次成功写入更新 | 状态更新时间 |
| `result` | `JsonNode` | 动作或终态准备时写入 | `message/pluginLogUri` 等摘要 |
| `outputVars` | `Map<String, Variable>` | 动作或终态准备时写入 | 输出变量 |

Store 接收完整候选状态，不自动回填旧字段。Coordinator 在新的 submit 动作中必须明确清空旧 `appId`、`exitCode` 以及不再适用的结果字段，避免新快照查询旧资源。

### 3.3 RunningTaskContext

| 字段 | 类型 | 生命周期 | 说明 |
|------|------|----------|------|
| `snapshot` | `WorkerTaskExecutionSnap` | 单次动作 | 本次动作使用的新/当前快照 |
| `executionState` | `WorkerTaskExecutionState` | 单次动作 | 动作前置态 CAS 成功后的候选副本；插件只修改该副本，不直接写 Store 或 Cache |
| `previousSnapshot` | `WorkerTaskExecutionSnap` | 单次 submit，可空 | 覆盖新快照前读取的旧配置 |
| `previousState` | `WorkerTaskExecutionState` | 单次 submit，可空 | 重新提交前读取的旧运行引用和状态 |
| `workDirPath` | `String` | 单次动作 | Store 为该 taskInstanceId 返回的规范任务目录；插件参数解析器只使用该值 |

`RunningTaskContext` 不进入长期缓存。`snapshot/previousSnapshot/previousState` 在创建后只读；
`executionState` 是本次动作独占的候选副本，插件可以设置状态、运行引用和退出信息。动作结束后由 Coordinator
合并 `WorkerResult` 并尝试 revision CAS，Context 随即释放。

## 4. 端口与服务模型

| 对象 | 关键方法 | 责任 |
|------|----------|------|
| `WorkerService` | `start/stop/submitTask/stopTask/killTask/finishTask` | Worker 生命周期及任务统一入口 |
| `WorkerClient` | `register/heartbeat/offline/getUnfinishedTasks` | Worker 管理协议；不包含任务结果上报 |
| `WorkerIdentityStore` | `load/save/delete` | 本地 Worker 身份端口；保存 Manager 已确认的 Worker.id，不包含注册协议和重试策略 |
| `PluginTaskExecutor` | `validate/submit/stop/kill/finish` | 插件第三方动作；通过 Context 候选态表达状态，通过原有 `WorkerResult` 返回结果字段 |
| `PluginRunModeStateMapping` | `mapState/prepareFinalReport` | 查询第三方并映射状态；不读写 Store |
| `WorkerPluginRouter` | `routeExecutor/routeStateMapping` | 统一 `(pluginType, runMode)` 路由 |
| `WorkerTaskExecutionStore` | `save/read snapshot/state/restoreExecutions/deleteExecution` | 持久化、规范工作目录和 revision CAS；`saveSnapshot` 返回 workDirPath |
| `WorkerTaskStateCoordinator` | `reserveAction/commitActionResult/commitMappedState/commitLocalProcessExit` | 唯一状态准入和迁移规则 |
| `TaskResultReporter` | `report(TaskResult)` | 一次结果上报端口 |
| `TaskStateListenerRegistry` | `register/unregister/report/shutdown` | 任务监听注册表，也是 `TaskResultReporter` 装饰器端口 |

### 4.1 WorkerTaskExecutionStore CAS

```text
saveState(candidate, expectedRevision)
    -> 获取 taskInstanceId 对应的实现层短锁
    -> 重新读取当前状态 S1
    -> S1.revision != expectedRevision：记录 warn，返回 false，不写文件
    -> revision 一致：candidate.revision = expectedRevision + 1
    -> 原子替换 .state，必要时追加 state.log
    -> 返回 true
```

首次写入的 `expectedRevision` 为 `0`。文件、序列化或原子替换失败属于基础设施异常，向调用方抛出；`false` 只表示 revision 竞争失败。

### 4.2 TaskStateListenerRegistry

建议契约：

```java
public interface TaskStateListenerRegistry extends TaskResultReporter {

    void register(String taskInstanceId, StatusEnum acknowledgedStatus);

    void unregister(String taskInstanceId);

    void shutdown();
}
```

`acknowledgedStatus` 是本次同步 RPC 实际返回给 Manager 的状态。启动恢复时传 `null`，使本地最新状态重新上报。Manager 接收失败时不得推进监听项内部的 `reportedStatus`。

## 5. 层间转换规则

| 方向 | 规则 | 特殊处理 |
|------|------|----------|
| `TaskRequest` -> `WorkerTaskExecutionSnap` | `WorkerService` 构造完整新快照 | 先保存完整快照并取得规范 workDirPath，再用动作 Context 校验；校验失败提交 `SUBMIT_FAILURE` |
| `.snap + .state` -> `RunningTaskContext` | `WorkerService` 在每次动作前组装 | 不缓存；控制请求可只有 taskInstanceId |
| `previous .snap/.state + current .snap/.state` -> `RunningTaskContext` | 重新 submit 在覆盖新快照前先提取旧值 | DataX/Spark 用旧值清理旧资源 |
| `PluginTaskExecutor` -> `RunningTaskContext.executionState + WorkerResult` | 插件修改动作候选状态，返回运行引用、目录、消息、日志和输出变量 | `WorkerResult` 不增加 StatusEnum/revision |
| `executionState + WorkerResult` -> `.state` | Coordinator 构造完整候选状态并 CAS | CAS 失败返回最新落盘状态 |
| `.snap + .state` -> `TaskResult` | `WorkerService` 构造 RPC 响应；Registry 构造异步上报 | `taskState` 必须来自真实落盘状态 |
| `.snap + queryBaseline` -> `PluginRunModeStateMapping` | Registry 锁外查询第三方并映射 | 查询 CAS 失败直接丢弃 |
| LOCAL 进程退出 -> `.state` | watcher 携带 `actionRevision + appId + exitCode` 交给 Coordinator | 同 appId 可越过一次 revision 变化提交终态 |
| Manager 未完成任务 -> `restoreExecutions` -> Registry | Store 只恢复 Manager 指定且本地 `.snap/.state` 完整的任务 | 只启动扫描一次，不周期全量扫描 |

Agent 文件 Store 内部可缓存 `taskInstanceId -> WorkerTaskExecutionState`。缓存只保存文件成功落盘后的状态副本；
`readState` 不返回缓存原对象，缓存未命中回读 `.state`，重启时只加载 Manager 未完成任务与本地记录的交集。
因此缓存优化对象重建和文件读取，但不改变 `.state + revision` 的权威语义。

## 6. 动作状态约束

已确定：

```text
submit 开始      -> SUBMITTING
submit 正常返回  -> SUBMIT_SUCCESS
submit 异常      -> SUBMIT_FAILURE
同一 appId 终态  -> 可覆盖 SUBMIT_SUCCESS
```

建议但待确认：

```text
stop 开始/正常返回 -> STOPPING
stop 异常          -> STOP_FAILURE
状态源确认停止     -> STOP_SUCCESS

kill 开始/正常返回 -> KILLING
kill 异常          -> UNKNOWN
状态源确认强停     -> KILLED
```

如果 LOCAL stop/kill 仍要求插件同步返回 `STOP_SUCCESS/KILLED`，必须为插件结果增加状态信号；当前 `WorkerResult` 无法区分“请求已接受”和“资源已确认终止”。

## 7. 生命周期与清理规则

- 一个 `taskInstanceId` 只属于一个 Agent，不处理跨 Agent 写同一 `.state`。
- `.snap/.state` 在终态上报后继续保留，直到 Manager 调用 finish 或显式清理。
- `finishTask=false` 或异常时保留插件资源、监听、`.snap/.state`，以便重试。
- 仅 `finishTask=true` 后注销监听并删除执行记录。
- 终态监听内存项受 `LISTENER_RETENTION_MS/LISTENER_RETENTION_NUM` 约束；淘汰监听不删除执行文件。
- `WorkerTaskExecutionStore.restoreExecutions` 只用于启动恢复，不引入健康扫描线程。
- 同一 `taskInstanceId` 始终复用同一执行目录；插件 Resolver 不得使用 `LocalDate.now()` 重新生成路径。

## 8. 不变项

- 不改变 `TaskRequest`、`TaskResult`、`WorkerResult` 的字段。
- 不改变 `.snap/.state` JSON 字段。
- `revision` 仍是 `.state` 唯一并发版本。
- `exitCode` 只保存在 Agent `.state/state.log`，不进入 `WorkerResult`。
- `pluginType + runMode` 仍是执行器和状态映射器的共同路由键。
