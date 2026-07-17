# 调度 Agent 数据结构定义

> 本文档是 agent 公共配置、RPC 映射和本地文件格式的事实源。DataX 字段见
> [plugin-datax-data-define.md](./plugin-datax-data-define.md)，Shell 字段见
> [plugin-shell-data-define.md](./plugin-shell-data-define.md)，Spider 字段见
> [plugin-spider-data-define.md](./plugin-spider-data-define.md)，API 字段见
> [plugin-api-data-define.md](./plugin-api-data-define.md)，Flink 字段见
> [plugin-flink-data-define.md](./plugin-flink-data-define.md)。worker 通用模型见
> [scheduler-worker-data-define.md](../datafusion-scheduler-worker/scheduler-worker-data-define.md)。

## 1. 表结构

无。`datafusion-agent` 不直接持久化数据库表。

## 2. 配置模型

| 对象 | 字段 | 类型 | 默认值 | 说明 |
|------|------|------|--------|------|
| `AgentProperties` | `modules` | `String` | `${user.dir}` | agent 模块根目录，不参与任务运行目录拼接 |
| `AgentProperties.Worker` | `workerCode` | `String` | 空 | agent 稳定编码；配置后使用配置值，未配置时由 `hostName:ip:port` 生成稳定 UUID 字符串，主机名、IP、端口都不可用时使用 `00000000-0000-0000-0000-000000000001` |
| `AgentProperties.Worker` | `ip` | `String` | 空 | 未配置时读取本机 IP |
| `AgentProperties.Worker` | `port` | `Integer` | `8081` | HTTP 端口 |
| `AgentProperties.Worker` | `hostName` | `String` | 空 | 未配置时读取本机 hostName |
| `AgentProperties.Worker` | `pluginTypes` | `String` | 空 | worker 可承接插件类型，逗号分隔；为空时上报当前 agent 已加载的全部插件 |
| `AgentProperties.Worker` | `acceptTasksBeforeRegistered` | `boolean` | `false` | 未注册 manager 前是否允许接收任务 |
| `AgentProperties.Manager` | `baseUrl` | `String` | 空 | manager HTTP 地址 |
| `AgentProperties.Manager` | `enabled` | `boolean` | `true` | 是否启用 manager 注册心跳 |
| `AgentProperties.Manager` | `heartbeatIntervalMs` | `long` | `15000` | 心跳间隔 |
| `AgentProperties.Manager` | `connectTimeoutMs` | `long` | `5000` | Agent 调用 Manager 的 HTTP 连接超时 |
| `AgentProperties.Manager` | `readTimeoutMs` | `long` | `10000` | Agent 调用 Manager 的 HTTP 读取超时，避免监听线程长期阻塞 |
| `AgentProperties.Storage` | `logsDir` | `String` | `logs` | agent 自身日志目录名，不作为插件任务运行目录 |
| `AgentProperties.Storage` | `taskRuntimeDir` | `String` | `/opt/datafusion/task-runtime` | 任务运行态绝对根目录，不与 `${modules}` 拼接 |
| `AgentProperties.StateRefresh` | `intervalMs` | `long` | `15000` | 状态刷新间隔 |
| `AgentProperties.StateRefresh` | `unknownThreshold` | `int` | `3` | 连续 UNKNOWN 后推进状态的阈值 |
| `AgentProperties.StateRefresh` | `listenerPoolSize` | `int` | `2` | 任务级状态监听调度线程数 |
| `AgentProperties.StateRefresh` | `listenerRetentionMs` | `long` | `86400000` | 终态成功上报后监听注册项的最长保留时间，对应 `LISTENER_RETENTION_MS` |
| `AgentProperties.StateRefresh` | `listenerRetentionNum` | `int` | `512` | 最多保留的终态监听注册项数量，对应 `LISTENER_RETENTION_NUM` |
| `AgentProperties.Kubernetes` | `apiServer`, `token`, `tokenFile`, `caCertFile` | `String` | service account 文件 | Kubernetes client 连接配置 |
| `AgentProperties` | `taskPool` | `ThreadPoolConfig` | `8/16/512/60` | Worker 异步任务执行；当前也供本地进程 watcher 等待退出 |

### 2.1 进程内监听和锁模型

| 对象 | 字段 | 生命周期 | 说明 |
|------|------|----------|------|
| `AgentTaskStateListenerRegistry.listeners` / `TaskStateListener` | `taskInstanceId -> TaskStateListener`；监听项包含 `future`, `reportedStatus`, `terminalSince` | 任务注册监听到 `finishTask` 或终态保留策略淘汰 | 活跃和终态保留监听共用唯一注册表；任务控制注册以本次 RPC 返回的 `TaskResult.taskState` 初始化 `reportedStatus`，不重读 `.state`，后续只在 Manager 成功接收状态后推进，上报失败时保持不变，启动恢复时初始为 `null`；注册和注销只使用并发 Map 原子操作，不获取任务状态锁；启动恢复在 Agent ready 前完成；`terminalSince > 0` 表示已进入终态保留期；终态上报成功后 `future` 从周期刷新切换为一次性延迟清理，任务重启注册时再切回周期刷新；容量检查只在终态进入事件发生时按 `terminalSince` 排序；不使用 `listenerToken`；注册项不写入 `.state` |
| `WorkerTaskExecutionContext.taskLocks` | `taskInstanceId -> ReentrantLock` | Agent 进程内 `.state` CAS 写入期间 | 只在 `saveState(state, expectedRevision)` 内部串行化同一任务的 revision 复读、文件替换和缓存更新；不暴露为 Worker SPI；单次 `.state` 读取、删除、Manager 上报和 `.snap` 读写不持有该锁 |

监听注册项和任务锁都由 Agent 进程拥有。一个任务只属于一个 Agent，不处理多个 Agent 同时写同一任务 `.state` 的场景。

## 3. RPC 映射

agent 对 manager 暴露：

| RPC | 请求对象 | 响应 data | 说明 |
|-----|----------|-----------|------|
| `POST /internal/scheduler/submitTask` | `TaskRequest` | `TaskResult` | 提交任务 |
| `POST /internal/scheduler/stopTask` | `TaskRequest` | `TaskResult` | 停止任务 |
| `POST /internal/scheduler/killTask` | `TaskRequest` | `TaskResult` | 强制停止任务 |
| `POST /internal/scheduler/finishTask` | `TaskRequest` | `TaskResult` | 任务终态后的本地清理入口 |

`TaskRequest.pluginType` 与 `TaskRequest.runMode` 是执行器路由键；`pluginParam` 只承载插件配置。

agent 调用 manager：

| RPC | 请求对象 | 说明 |
|-----|----------|------|
| `POST {manager}/internal/schedule/worker/register` | `Worker` | 注册 worker，携带 `workerCode/hostName/ip/port/pluginTypes/workerLogDir`，响应 `Result<Worker>` |
| `POST {manager}/internal/schedule/worker/heartbeat` | `Worker` | worker 心跳，只携带 `id/lastHeartbeatTime`，响应 `Result<Worker>` |
| `POST {manager}/internal/schedule/worker/offline` | `Worker` | worker 下线，只携带 `id`，响应 `Result<Worker>` |
| `POST {manager}/internal/schedule/worker/tasks` | `Worker` | 注册成功后按 `id` 获取属于当前 worker 的未完成 `TaskRequest` 清单 |
| `POST {manager}/internal/schedule/reportTaskResult` | `TaskResult` | 上报任务结果 |

agent 本地 worker 配置：

```text
/opt/datafusion-builtin/datafusion-agent/worker.config
```

该文件保存 manager 注册成功返回的 `Worker`。agent 重启后如果本地 `workerCode` 与文件中的
`workerCode` 一致，则复用文件中的 `Worker.id`，后续 heartbeat/offline 不再依赖 `workerCode` 查找。

## 4. 本地运行态文件

任务运行态目录：

```text
${taskRuntimeDir}/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/
    {taskInstanceId}.snap
    {taskInstanceId}.state
    stdout.log
    stderr.log
    state.log
```

`taskRuntimeDir` 默认 `/opt/datafusion/task-runtime`。每个任务工作目录、插件运行产物、插件日志和状态文件
都必须位于 `${taskRuntimeDir}/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/` 下。

`{taskInstanceId}.snap` 是提交快照：

```json
{
  "flowInstanceId": "{flowInstanceId}",
  "taskInstanceId": "{taskInstanceId}",
  "taskName": "{taskName}",
  "pluginType": "{pluginType}",
  "runMode": "{runMode}",
  "workerId": "{workerId}",
  "taskData": {},
  "pluginParam": {}
}
```

`{taskInstanceId}.state` 是运行态：

```json
{
  "taskInstanceId": "{taskInstanceId}",
  "workerId": "{workerId}",
  "appId": "{appId}",
  "workDirPath": "{workDirPath}",
  "status": "{StatusEnum.name}",
  "revision": 1,
  "exitCode": null,
  "updateTime": 1780000000000,
  "result": {
    "message": "{message}",
    "pluginLogUri": "{pluginLogUri}"
  }
}
```

`state.log` 是任务状态变化流水：

```text
time:1780000000000|workerId:{workerId}|appId:123|revision:1|status:RUNNING|exitCode:
time:1780000001000|workerId:{workerId}|appId:123|revision:2|status:RUN_SUCCESS|exitCode:0
```

规则：

- `.state.status` 使用 `StatusEnum.name()`。
- `workerId` 使用 manager 返回的 `Worker.id`，即 `scheduler_worker_registry.id` 的 UUID 字符串，并随 `TaskRequest` / `TaskResult` / `.snap` / `.state` 透传。
- `Worker.workerCode` 使用 agent 配置或推导出的稳定编码，落到 `scheduler_worker_registry.worker_code`，不在 `TaskRequest` 中新增顶层 `workerId` 字段。
- `appId` 统一表示终端任务 ID。
- `.snap` 只保存最近一次提交快照和插件配置参数，不保存运行时观测字段；由 `WorkerTaskService` 在调用插件前
  整体原子覆盖，不读取旧快照、不做字段合并或对象复制，插件不重复保存，也不参与 `.state.revision` 和任务状态锁。
- `.state` 只保存通用运行态，不回写 `taskData` / `pluginParam`。
- `.state.revision` 是任务运行态版本号，首次写入为 `1`。每次调用方必须提供查询基线的
  `expectedRevision`；存储层复读当前 revision，一致时写入并加一，不一致时记录 `warn`、返回 `false` 且不写入。
- `.state` 通过临时文件原子替换；监听周期只在第三方查询前读取一次 Q。映射状态不同时调用
  `saveState(state, Q.revision)`；查询或等锁期间的其他写入会使 CAS 失败，本轮映射结果被丢弃。
- `.state` 不保存锁、监听器、`listenerToken` 或单独的控制代次；任务锁和监听注册项只存在于 Agent 内存。
- `workDirPath` 统一表示任务运行目录，manager 只通过该目录读取标准任务日志。
- `stdout.log`、`stderr.log`、`state.log` 是 agent 标准任务日志，文件名由 `TaskRuntimeFiles` 统一定义。
- `state.log` 每行的 `revision` 记录本条流水对应的 `.state.revision`，用于关联状态文件版本和并发写入顺序。
- `TaskResult.workerResult.workDirPath` 表示任务运行目录；manager 查询任务日志时只使用该目录。
- `TaskResult.workerResult.pluginLogUri` 表示插件日志入口。
- `TaskResult.workerResult` 不返回 agent 自身服务日志入口；worker 服务日志目录由 `Worker.workerLogDir` 在注册时上报。
- `saveState` 更新 `.state` 时同步比较旧 `.state`，当 `status`、`appId` 或 `exitCode` 变化时追加 `state.log`。
- `finishTask` 确认终态后直接删除实际存在的 `.state` / `.snap` 并使 `executionCache` 失效，不占用状态写入锁；
  继续保留 `stdout.log` / `stderr.log` / `state.log`。
- 监听注册项被终态保留策略淘汰时只取消内存监听，不删除 `.state` / `.snap`；后续 UI 强制成功仍可通过 `finishTask` 清理本地文件。

## 5. WorkerResult 结构

插件上报给 manager 的结果统一写入 `TaskResult.workerResult`。`TaskResult` 只承载任务身份和状态；worker 侧运行信息写入 `WorkerResult`。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `outputVars` | `Map<String, Variable>` | 否 | 输出变量 |
| `workerId` | `String` | 否 | worker ID，使用 manager 返回的 `Worker.id` |
| `appId` | `String` | 否 | 终端任务 ID，如 PID、Kubernetes Job name |
| `workDirPath` | `String` | 否 | 任务运行目录 |
| `message` | `String` | 否 | 简短执行说明或错误摘要 |
| `pluginLogUri` | `String` | 否 | 插件日志入口，本地文件、对象存储 URI 或第三方运行时 URI |

`WorkerResult` 不包含 `exitCode`。本地进程退出码只保存在 `.state.exitCode` 和 `state.log`，用于 agent 状态映射和诊断，不持久化到 manager 的 `worker_result` 结构中。

## 6. 复用对象

| 对象 | 来源 | 用途 |
|------|------|------|
| `Result<T>` | `datafusion-common-spring` | RPC 响应包装 |
| `TaskRequest` / `TaskResult` / `Worker` | `datafusion-common-data` | manager/agent/worker 通信模型 |
| `WorkerTaskOperator` / `WorkerTaskService` | `datafusion-scheduler-worker` | worker 框架入口 |
| `RunningTaskContext` | `datafusion-scheduler-worker` | 运行上下文 |
| `WorkerTaskExecutionSnap` / `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | 提交快照、运行态和状态存储 SPI |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 插件执行和状态映射 SPI |
| `TaskResultReporter` | `datafusion-scheduler-worker` | 任务结果上报端口 |
| `TaskRuntimeFiles` | `datafusion-common-data` | agent 标准任务日志文件名和路径拼接工具 |
