# 调度 Agent 设计

> 数据结构见 [agent-data-define.md](./agent-data-define.md)。  
> DataX 插件见 [plugin-datax-design.md](./plugin-datax-design.md)，  
> Shell 插件见 [plugin-shell-design.md](./plugin-shell-design.md)，  
> Spider 插件见 [plugin-spider-design.md](./plugin-spider-design.md)，  
> API 插件见 [plugin-api-design.md](./plugin-api-design.md)，  
> Flink 插件见 [plugin-flink-design.md](./plugin-flink-design.md)。  
> worker 框架契约见 [../datafusion-scheduler-worker/scheduler-worker-design.md](../datafusion-scheduler-worker/scheduler-worker-design.md)。

## 定位

`datafusion-agent` 是 `datafusion-scheduler-worker` 的运行时应用。Agent 负责 Spring Boot 启动、
RPC Provider、Worker 协议 Client、结果上报 HTTP 实现、线程池、文件版执行存储、任务级监听和具体插件。

边界：

- `datafusion-scheduler-worker` 定义中性 SPI 和模型。
- `datafusion-agent` 实现 SPI，并对接 manager、本地文件、Kubernetes 等运行时系统。
- 插件的参数、执行细节和状态映射写入各插件设计文档。

## 启动流程

```text
读取 AgentProperties
    -> 初始化 taskPool / taskStateListenerScheduler
    -> 装配 FileWorkerTaskExecutionStore
    -> 由 Spring 注入 PluginTaskExecutor 和 PluginRunModeStateMapping 列表
    -> 装配 WorkerPluginRouter / WorkerTaskStateCoordinator
    -> 装配 AgentWorkerClient / AgentTaskResultReporter
    -> 用 AgentTaskStateListenerRegistry 装饰 AgentTaskResultReporter
    -> 装配唯一 WorkerService
    -> AgentLifecycle 调用 WorkerService.start
    -> WorkerService 注册、恢复、启动心跳并进入 ready
    -> AgentExecutorRpcProvider 只向 WorkerService 转发 RPC
```

Manager 注册及未完成任务恢复完成前默认不接收新任务；`acceptTasksBeforeRegistered=true` 时允许本地开发绕过。

agent 启动时先生成 `workerCode`：配置了 `datafusion.agent.worker.worker-code` 时使用配置值；
未配置时使用 `hostName:ip:port` 生成稳定 UUID 字符串；主机名、IP、端口都不可用时使用
`00000000-0000-0000-0000-000000000001`。manager 注册成功后返回
`scheduler_worker_registry.id`，agent 将该 UUID 保存为 `Worker.id`，后续 `TaskRequest.workerResult.workerId`、
`TaskResult.workerResult.workerId`、`.snap`、`.state`、`state.log` 都使用这个 `workerId`。注册成功返回的
`Worker` 同时写入 `/opt/datafusion-builtin/datafusion-agent/worker.config`；agent 重启后如果
本地 `workerCode` 未变化，则先恢复该 `Worker.id`，后续 heartbeat/offline 只用 `id`。
`datafusion.agent.worker.plugin-types` 为空时，agent 注册上报当前进程已加载的全部插件；非空时按逗号拆分，
只上报配置和已加载插件的交集，用于部署时把不同插件固定到不同 worker 池。
`SPIDER + LOCAL` 插件入口随 agent 启动加载；spider 专用节点通过 `DATAFUSION_WORKER_PLUGIN_TYPES=SPIDER`
只上报 `SPIDER` 能力。
`API + LOCAL` 插件入口随 agent 启动加载；API 专用节点通过 `DATAFUSION_WORKER_PLUGIN_TYPES=API`
只上报 `API` 能力。

## 目录规范

agent 自身服务日志固定归属：

```text
/opt/datafusion/logs/datafusion-agent/
```

该目录通过 `Worker.workerLogDir` 在注册时上报给 manager，并落到
`scheduler_worker_registry.log_dir`。`workerLogDir` 是 worker 级服务日志目录，不属于单个任务结果。

任务运行态、插件运行产物和插件日志固定归属：

```text
/opt/datafusion/task-runtime/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/
```

`taskRuntimeDir` 是绝对路径，默认 `/opt/datafusion/task-runtime`，不与 `${modules}` 拼接。
`TaskResult.workerResult.workDirPath` 表示任务运行目录，manager 只通过该目录识别标准日志。
`TaskResult.workerResult.pluginLogUri` 表示插件日志入口，可以是任务运行目录下的插件日志文件、对象存储 URI 或第三方运行时 URI。
`TaskResult.workerResult` 不返回 worker 服务日志目录；agent 服务日志通过 `Worker.workerLogDir` 查询。

agent 服务日志按 logback 切分，`workerLogDir` 只保存目录入口。当前标准文件模式为：

```text
datafusion-agent.log
datafusion-agent.error.log
datafusion-agent.{yyyy-MM-dd}.{index}.log
datafusion-agent.error.{yyyy-MM-dd}.{index}.log
```

内置插件资源随 agent 镜像放在：

```text
/opt/datafusion-builtin/plugins/{plugin}/
```

Kubernetes 部署通过 initContainer 将 `/opt/datafusion-builtin/.` 拷贝到共享卷 `/opt/datafusion/`，
从而生成 `/opt/datafusion/plugins/{plugin}/`。当前 DataX LOCAL 默认仍指向
`/opt/datafusion-builtin/plugins/datax`，避免运行时共享卷缺失影响内置插件启动。

## 插件部署规范

插件部署规范对所有 agent 插件生效。插件模块负责产出源资源，agent resources 负责承载随 agent 发布的
标准插件目录，运行环境再把该目录同步到共享盘或容器内固定路径。

### 目录分层

源码侧插件目录：

```text
datafusion-plugin/{plugin-module}/src/main/resources/plugins/{pluginType}/
datafusion-plugin/{plugin-module}/src/main/resources/plugins/{pluginType}/{appName}/
```

agent 发布侧插件目录：

```text
datafusion-agent/src/main/resources/plugins/{pluginType}/
datafusion-agent/src/main/resources/plugins/{pluginType}/{appName}/
```

运行侧插件目录：

```text
/opt/datafusion/plugins/{pluginType}/
/opt/datafusion/plugins/{pluginType}/{appName}/
```

`plugins/{pluginType}` 是插件类型根目录，例如 `plugins/datax`、`plugins/flink`、`plugins/api`。
如果一个插件类型只会有一个发布包，可以直接使用 `plugins/{pluginType}` 作为单包目录；如果同一插件类型
会汇聚多个 app，必须使用 `plugins/{pluginType}/{appName}` 子目录，避免不同插件模块互相覆盖。

### 目录规范

插件类型目录是否承载多个 app 由插件设计文档直接约定。当前规则：

- DataX 可保持单包目录 `plugins/datax/`。
- Flink 使用多 app 目录 `plugins/flink/{appDirName}/`；运行侧 Pod 内目录由
  `pluginParam.flinkAppDir` 直接指定。
- 单个 app 目录只保存该 app 的运行产物和静态样例资源。主 jar、launch mode、构建版本等发布包事实由
  `pluginParam` 和源码工程目录约定说明，不要求 agent 运行时读取发布包清单。

### builder 规范

builder 的职责是把插件模块产物同步到 agent 发布侧插件目录，不负责上传共享盘，不负责提交任务。

规则：

- builder 脚本放在 `src/main/resources/builder/`，不要放进 `src/main/resources/plugins/{pluginType}/...`。
- builder 可以调用 Maven profile 产出 fat jar、thin jar、依赖目录或其他运行产物；依赖解析不应由纯 shell 手写。
- builder 只允许清理和覆盖自己的发布目录。Flink 这类多 app 插件只能清理
  `plugins/flink/{appDirName}/`，不能清空 `plugins/flink/` 根目录。
- 首次构建时，如果 agent 发布侧缺少 `plugins/{pluginType}` 或 app 子目录，builder 负责创建。
- agent 发布侧的 `plugins/` 目录是后续整体打包和上传共享盘的输入。

### 模板规范

agent 模板必须和部署规范使用同一套路径派生规则，避免在模板中散落重复配置。

统一派生规则：

```text
pluginTypeRoot = {sharedPluginRoot}/{pluginType}
appRoot        = 单包插件使用 {pluginTypeRoot}；多 app 插件使用 {pluginTypeRoot}/{appName}
```

模板只接收归一化后的运行变量，不直接读取 builder 字段。通用变量包括：

| 变量 | 说明 |
|------|------|
| `pluginType` | 插件类型，例如 `flink`、`datax` |
| `sharedPluginRoot` | 运行侧共享插件根目录，默认 `/opt/datafusion/plugins` |
| `appName` | 多 app 插件的 app 名称 |
| `appRoot` | 根据插件目录约定和任务参数派生出的运行侧 app 根目录 |
| `taskRuntimeDir` | 当前任务运行目录 |
| `labels` / `annotations` / `env` | 已过滤和归一化后的 Kubernetes 元数据 |

插件专用模板只能声明该运行时需要的补充变量。例如 Flink K8S_OPERATOR 模板可以使用
`flinkAppJar` 和 `usrlibPath`，其中发布包相关字段来自 `pluginParam`；job config 由 agent 生成本地快照后
以 `--job <base64(job-json)>` 注入 `FlinkDeployment.spec.job.args`，不要求用户在 `pluginParam.kubernetes`
中重复填写。

模板实现约束：

- 模板渲染前必须先完成参数归一化，生成统一上下文对象，再渲染 YAML / command / manifest。
- 模板中不得直接拼接源码侧路径，例如 `datafusion-plugin/.../src/main/resources/...`。
- 模板中不得读取 builder 专用字段，例如 `mavenProfile`、`builderScriptPath`、`agentPluginDir`。
- 模板中不得重复声明能由插件目录约定派生的目录结构；应使用 `pluginTypeRoot`、`appRoot` 等归一化变量。
- Kubernetes 类模板如果支持用户覆盖 podTemplate，只能通过插件设计文档声明的 allowlist 合并；不得允许用户覆盖
  agent 生成的 volume、volumeMount、initContainer、container name 等运行时关键结构。
- 新增插件模板时，必须先确认插件设计文档中的目录约定和模板变量一致。

### Kubernetes 资源命名规范

Kubernetes 资源名称由 `KubernetesResourceNameUtils` 统一完成 DNS-1123 归一化、63 字符限制和稳定 hash
截断。各插件保留自己的 NameGenerator，用于选择资源角色和序号，不重复实现名称规范。

```text
resourceName(namePrefix, taskInstanceId)
resourceName(namePrefix, role, taskInstanceId)
resourceName(namePrefix, role, ordinal, taskInstanceId)
```

- `pluginParam.kubernetes.namePrefix` 可以覆盖插件代码默认值，`taskData.kubernetes` 不允许覆盖该字段。
- 主资源使用两段式名称；同任务存在资源类型冲突时追加 `role`；同角色存在多个实例时再追加 `ordinal`。
- `role` 和 `ordinal` 由插件代码决定，不开放为任务参数。
- 默认前缀分别为 Spark `df-spark`、Flink `df-flink`、DataX `df-datax`。

## RPC 边界

manager 调用 agent：

```text
POST /internal/scheduler/submitTask
POST /internal/scheduler/stopTask
POST /internal/scheduler/killTask
POST /internal/scheduler/finishTask
```

agent 调用 manager：

```text
POST /internal/schedule/worker/register
POST /internal/schedule/worker/heartbeat
POST /internal/schedule/worker/offline
POST /internal/schedule/worker/tasks
POST /internal/schedule/reportTaskResult
```

`AgentExecutorRpcProvider` 只做 RPC 入站适配和返回包装，直接调用 `WorkerService`。ready 校验、workerId 补齐、
插件路由、状态提交以及监听注册/注销都由 `WorkerService` 统一完成。Provider 不再依赖
`WorkerTaskOperator`、`AgentRuntimeState` 或 `AgentTaskStateListenerRegistry`。

Agent 出站协议拆成两个端口实现：

- `AgentWorkerClient implements WorkerClient`：只调用 register、heartbeat、offline 和 worker/tasks；
- `AgentWorkerConfigStore implements WorkerIdentityStore`：保存和恢复 Manager 已确认的 Worker.id；
- `AgentTaskResultReporter implements TaskResultReporter`：只调用 reportTaskResult，并以
  `Result<Boolean>.data == true` 判定 Manager 真正接收成功。

`AgentTaskStateListenerRegistry` 实现 worker 层的 `TaskStateListenerRegistry`，作为
`AgentTaskResultReporter` 的装饰器：它负责状态监听、去重、失败重试和终态保留，HTTP Reporter 只负责一次传输。
监听线程直接发起上报，不再二次提交到独立上报线程池。HTTP Client 必须配置连接和读取超时，防止监听线程长期阻塞。

`worker/register` 返回 `Result<Worker>`，用于把 manager 生成或匹配到的 `Worker.id` 回传给
agent。`worker/heartbeat` 只提交 `id/lastHeartbeatTime`，`worker/offline` 只提交 `id`。
`Worker.workerCode` 只用于注册时定位或创建 registry 记录，不作为任务实例 `workerId`。
`worker/tasks` 在注册成功后调用，只提交 `id`，返回属于该 Worker 的未完成 `TaskRequest` 清单。
注册成功但恢复失败时保留已获得的 `Worker.id`；恢复重试期间保持心跳和 `ready=false`，不反复注册新身份。

## 插件与运行模式

`pluginType` 与 `runMode` 共同路由插件执行器和状态映射器，例如 `DATAX + LOCAL`、
`DATAX + K8S`。`runMode` 是 `TaskRequest` 标准字段，不写入 `pluginParam`。

通用插件闭环：

```text
TaskRequest(pluginType, runMode, taskData, pluginParam)
    -> WorkerService 构造 WorkerTaskExecutionSnap
    -> WorkerPluginRouter.routeExecutor(pluginType, runMode)
    -> PluginTaskExecutor.validate(snapshot)
    -> FileWorkerTaskExecutionStore 保存完整 .snap
    -> WorkerTaskStateCoordinator CAS 写入动作前置态
    -> WorkerService 创建动作级 RunningTaskContext
    -> PluginTaskExecutor 执行第三方动作并返回 WorkerResult
    -> WorkerTaskStateCoordinator CAS 提交动作结果
    -> WorkerService 从最终 .snap/.state 构造 TaskResult
    -> AgentTaskStateListenerRegistry 注册任务并装饰 AgentTaskResultReporter
    -> manager 调用 finishTask，插件清理成功后才注销监听并删除执行记录
```

插件参数只来自 `pluginParam` 和 `taskData`，不从 `application.yml` 读取第三方运行参数。模板只保存静态命令
或 manifest 骨架；工作目录、日志文件、运行 ID 由 agent 或插件执行器生成。

目标插件接口以 `RunningTaskContext` 为参数，以既有 `WorkerResult` 为返回值。Flink/Spark/DataX 的专用
`*TaskResult` 删除；插件不得再读取 Store、保存 `.snap/.state`、构造 `TaskResult` 或直接上报 Manager。
插件参数 Resolver 以 `WorkerTaskExecutionSnap` 为配置来源，并在需要运行引用时读取 Context 中的 State。

API/Spider 对 Shell 的复用只发生在一次插件调用内部：持久化 `.snap.pluginType` 必须继续保持 `API` 或 `SPIDER`，
临时 Shell 进程规格不得覆盖路由快照。工作目录从 Context 中的规范路径取得，不能按 `LocalDate.now()` 重新推导，
避免跨日 stop/kill/finish 定位到不同目录。

## 目标运行时类结构

```text
com.datafusion.agent
├── config
│   ├── AgentConfiguration
│   └── AgentProperties
├── rpc
│   ├── AgentWorkerClient
│   ├── AgentTaskResultReporter
│   └── AgentExecutorRpcProvider
└── runtime
    ├── AgentLifecycle
    ├── AgentWorkerConfigStore
    └── worker
        ├── context
        │   └── FileWorkerTaskExecutionStore
        ├── reporter
        │   └── AgentTaskStateListenerRegistry
        └── plugin
            └── 各 PluginTaskExecutor / PluginRunModeStateMapping / LOCAL watcher
```

迁移关系：

| 当前类 | 目标 |
|--------|------|
| `ManagerClient` | 删除；拆成 worker 层 `WorkerClient` 与独立 `TaskResultReporter` |
| `HttpManagerClient` | `AgentWorkerClient implements WorkerClient` |
| `ManagerTaskResultReporter` | `AgentTaskResultReporter implements TaskResultReporter` |
| `AgentWorkerConfigStore` | 保留，并实现 `WorkerIdentityStore` |
| `WorkerTaskExecutionContext` | `FileWorkerTaskExecutionStore` |
| `AgentRuntimeState` | 删除；ready 和当前 Worker 由 `WorkerService` 唯一持有 |
| `AgentLifecycle` | 只做 Spring 生命周期适配，调用 `WorkerService.start/stop` |
| `AgentExecutorRpcProvider` | 只依赖 `WorkerService` |

如果 `AgentTaskStateListenerRegistry` 和被装饰的 `AgentTaskResultReporter` 都实现 `TaskResultReporter`，
`AgentConfiguration` 必须使用明确 Bean 名或 `@Qualifier`，不能依赖按类型猜测注入。

## 状态存储

每个任务运行目录包含：

```text
{taskInstanceId}.snap
{taskInstanceId}.state
stdout.log
stderr.log
state.log
```

规则：

- `.snap` 保存最近一次提交快照，包含 `workerId`、`pluginType`、`runMode`、`submitMode`、`taskData`、`pluginParam`；
  由 `WorkerService` 在调用插件前整体覆盖，不做字段合并或防御性深拷贝。重新提交必须在覆盖前提取旧 `.snap/.state`
  放入本次 `RunningTaskContext`，供 DataX/Spark 清理旧资源。插件不构造或覆盖 `.snap`。
- `.state` 保存运行态，包含 `workerId`、`appId`、`workDirPath`、`status`、`revision`、`exitCode`、`result`、`outputVars`。
- `stdout.log`、`stderr.log`、`state.log` 是 agent 标准任务日志，文件名由 `TaskRuntimeFiles` 统一定义。
- `state.log` 保存状态变化流水，终态后保留。
- `FileWorkerTaskExecutionStore` 是本地执行记录唯一入口，不缓存 `RunningTaskContext`，内部维护
  `taskInstanceId -> executionDir` 路径索引和 `taskInstanceId -> WorkerTaskExecutionState` 状态投影缓存。
- 路径索引使用普通 `Cache`，不使用 `LoadingCache`。启动扫描、保存和显式定位成功时写入；读取先查缓存，
  未命中才定位一次并回填；成功删除后失效。`saveSnapshot` 返回规范 workDirPath，同一 taskInstanceId 始终复用
  已有目录。路径缓存不参与状态锁。
- 状态缓存同样使用普通 `Cache`。缓存未命中时显式读取 `.state`，启动恢复时只加载 Manager 未完成任务与本地
  完整记录的交集；缓存只保存文件成功落盘后的副本，`readState` 返回防御性副本，禁止插件直接修改缓存对象。
- `saveState(state, expectedRevision)` 在任务锁内复读旧 `.state`；当前 revision 不等于预期值时记录
  `warn`、返回 `false` 且不写入，一致时将候选状态 `revision` 加一并原子写入。Store 不自动合并旧字段；
  新 submit 是否清空 `appId/exitCode/result/outputVars` 由 Coordinator 明确决定。
- `FileWorkerTaskExecutionStore` 为每个 `taskInstanceId` 创建进程内 `ReentrantLock`，但锁只封装在
  `saveState` 内部，不暴露给 Worker SPI 或调用方。`.state` 通过临时文件原子替换；单次读取、删除、
  Manager 上报和 `.snap` 读写都不持有该锁。锁不持久化，不处理多 Agent 写同一任务的场景。
- `WorkerTaskStateCoordinator` 在调用插件前用 revision CAS 将可执行的 submit/stop/kill 写为
  `SUBMITTING/STOPPING/KILLING`。当前状态不允许动作或 CAS 失败时直接返回当前状态，不调用插件。
  Master 恢复重复下发 `STOPPING/KILLING` 时只重试幂等插件动作，不重复写入中间态；重复 `SUBMITTING` 不重复提交。
- 插件只修改本次 `RunningTaskContext.executionState` 候选副本并返回原有 `WorkerResult`；Coordinator 使用动作
  前置态 revision 构造并提交候选状态。CAS 失败时
  `WorkerService` 读取最新 `.state` 并按它构造响应，不能返回未落盘的插件结果。
- `WorkerService` 在返回 RPC 前，以实际响应状态注册周期监听。Shell/DataX LOCAL 在进程创建并获得 appId 后立即注册
  退出 watcher，再返回 `WorkerResult`；不引入 `afterSubmitStateSaved` 钩子。
- `exitCode` 是本地运行态诊断字段，不进入 `TaskResult.workerResult`。
- 控制请求可以只携带 `taskInstanceId`；agent 通过 `.snap + .state` 恢复插件类型、运行模式和运行引用。
- 所有插件 Resolver 使用 `RunningTaskContext.workDirPath`，不得通过 `LocalDate.now()` 重算目录；否则跨日控制和
  重新提交会把状态文件、插件产物和日志拆到不同目录。

DataX/Spark 重新提交时使用当前 `RunningTaskContext.snapshot` 重新计算确定性的 Kubernetes 资源名，先幂等清理
同名旧资源再创建新资源。同一任务实例的任务数据和插件配置由 Master 固定，因此不保存历史快照或历史状态。

## 状态上报

```text
submit/stop/kill 或启动恢复
    -> AgentTaskStateListenerRegistry.register(taskInstanceId)
    -> 为该任务创建 scheduleWithFixedDelay
    -> 锁外读取查询基线 Q 和 snapshot
    -> WorkerPluginRouter 找到 PluginRunModeStateMapping
    -> mapState(snapshot, Q) 查询第三方并映射状态
    -> 非终态的映射状态等于 Q.status 且等于 reportedStatus 时直接结束
    -> WorkerTaskStateCoordinator 校验状态迁移，终态准备使用候选状态副本
    -> 状态或终态结果变化后调用 Store.saveState(candidate, Q.revision)
    -> saveState 在内部短锁中复读 revision；不同则返回 false 并丢弃结果
    -> revision 一致则写入并加一
    -> Registry 从真实落盘状态构造 TaskResult，委托 AgentTaskResultReporter.report
    -> 上报成功后更新 reportedStatus；终态直接进入监听保留期
```

`AgentTaskStateListenerRegistry` 是任务级线程注册器，不再启动统一任务扫描。共享
`ScheduledThreadPoolExecutor`，每个 `taskInstanceId` 只注册一个 `ScheduledFuture`；线程执行粒度是任务，
不是每个任务创建独立操作系统线程。submit/stop/kill 会幂等注册；只有 `finishTask` 插件清理成功后才注销。
注册时的 `reportedStatus` 必须使用本次 RPC 实际返回的 `TaskResult.taskState`，不能重新读取 `.state`；否则 Service
返回到注册之间的 watcher 新写入可能被误判为已经通过控制响应上报。

状态刷新只查询终端状态，不主动停止、强杀或重启任务。每个周期只在第三方查询前读取一次 Q；
查询、映射、迁移校验和 Manager 上报都不持有任务锁。只有需要写入时，`saveState` 才在内部短锁中复读当前 revision；
查询期间或等锁期间发生了 submit/stop/kill 或其他写入时，revision 不一致会使 CAS 失败，本轮映射结果不写入、不上报。
查询回来后只允许以下状态迁移：提交阶段进入提交成功、运行或失败，运行阶段进入运行终态，`STOPPING` 进入
`STOP_SUCCESS/STOP_FAILURE`、`KILLING` 进入 `KILLED`，以及非终态进入 `UNKNOWN`。查询结果不得生成
`STOPPING/KILLING` 等控制中间态；控制中间态只能由任务动作写入。当前状态已经变化或已经进入终态时，
旧查询结果不得覆盖。即使状态枚举相同，只要 `.state.revision` 已变化，后续基于旧 revision 的写入就会被拒绝。

`SUBMIT_SUCCESS` 不是终态。同一 appId 的第三方终态可以覆盖它。LOCAL 退出事件是一次性事实，不复用周期查询的
“CAS 失败即丢弃”规则：watcher 携带 `actionRevision + appId + exitCode`，如果最新状态仍对应同一 appId，
Coordinator 允许终态覆盖 `SUBMIT_SUCCESS/RUNNING`；若已重新提交或 appId 不同则丢弃旧进程事件。

如果 stop/kill 正常返回统一保持 `STOPPING/KILLING`，LOCAL watcher 和 LOCAL 状态映射必须按控制意图解释进程消失：

```text
STOPPING + 进程退出/不存在 -> STOP_SUCCESS
KILLING  + 进程退出/不存在 -> KILLED
其他运行态 + 已取得 exitCode -> RUN_SUCCESS / RUN_FAILURE
其他运行态 + 无 exitCode     -> UNKNOWN
```

状态相同且已经成功上报时不上报；映射状态写入成功或本地状态与 `reportedStatus` 不同时才上报。
`PluginRunModeStateMapping` 不直接访问 `WorkerTaskExecutionStore`；监听器读取的 `snapshot` 在单次刷新中同时用于
路由、参数恢复、状态映射和终态准备。映射器不得原地修改查询基线；终态准备作用于候选副本，失败的 CAS 不能污染基线。
异步 submit 线程只写 `.state`，不直接调用 Manager；监听线程观察到 `SUBMITTING` 进入
`SUBMIT_SUCCESS/SUBMIT_FAILURE` 后统一上报。Manager 不可用或结果上报失败时不推进 `reportedStatus`，
下一周期即使本地状态没有再次变化也重试。Agent 启动恢复 Manager 未完成任务时，注册项的
`reportedStatus` 初始为 `null`，用于重新对齐可能遗漏的终态。

终态上报成功后立即取消该任务的周期刷新；注册项仍保存在唯一的 `listeners` 注册表中，并通过
`terminalSince > 0` 标识终态保留期。注册项通过一次性延迟任务在 `LISTENER_RETENTION_MS` 后淘汰；保留数量超过
`LISTENER_RETENTION_NUM` 时，只在终态进入事件发生时按 `terminalSince` 排序并淘汰最老注册项，不执行周期性全表扫描。
活跃任务不参与数量淘汰。终态监听淘汰只原子删除内存注册项，不获取任务状态锁，也不删除 `.snap/.state`。
保留期内任务重新 submit 时，同一注册项会取消延迟清理并重新注册周期刷新。
`finishTask` 不受保留期限影响，但仅在插件返回成功后注销监听并删除实际存在的 `.snap/.state`；
失败时保留全部恢复依据。状态文件删除和路径缓存失效不占用 `saveState` 内部锁。

`readState` / `readSnapshot` 可以读取保留的 `.state/.snap` 用于诊断或插件控制，但读取文件本身不会注册监听。
只有任务控制入口和启动恢复流程调用 `AgentTaskStateListenerRegistry.register`。

agent 启动恢复只扫描一次 `${taskRuntimeDir}` 下的 `.state` 文件，并与 Manager 返回的当前 worker 未完成任务
按 `taskInstanceId` 取交集，不启动后续低频全量扫描。恢复流程为：

```text
WorkerService 注册成功返回 Worker.id
    -> 调用 manager worker/tasks 获取本 worker 未完成任务清单
    -> FileWorkerTaskExecutionStore 扫描一次本地执行记录并建立路径索引
    -> 对 Manager 清单和本地索引的交集读取 .snap/.state
    -> 逐个 taskInstanceId 注册监听并标记首次上报待处理
    -> 恢复成功后将 Agent 标记为 ready 并开始接收任务控制请求
```

如果 manager 已人工处理某个任务，该任务不会出现在清单中，agent 即使本地残留 `.state` 也不恢复、不上报。
任务清单查询或恢复失败时 Agent 保持未就绪，保留同一 Worker.id、继续心跳并重试恢复；不重复注册新 Worker。
恢复成功后的 heartbeat 不再重复扫描。
Agent 启动后不做低频全量健康扫描。`acceptTasksBeforeRegistered=true` 仅用于本地开发，会显式绕过这一就绪门禁。

Worker 生命周期内部必须区分 `registered` 与 `ready`：register 成功即保存 Worker.id 并开始 heartbeat；只有未完成任务恢复完成后才设置 ready。恢复失败不能把 registered 重新置为 false，否则下一周期会重复 register。

## 当前范围

- Spring Boot agent 运行时。
- manager 注册、心跳、下线和任务结果上报。
- agent 启动后按 manager 返回的任务清单恢复本 worker 未完成任务。
- 四个任务控制 RPC。
- worker 框架装配和 Spring Bean 插件加载。
- 文件版任务执行状态存储。
- `SHELL + LOCAL`、`SPIDER + LOCAL`、`API + LOCAL`、`DATAX + LOCAL`、`DATAX + K8S`。
- `FLINK + K8S_OPERATOR`，设计见 [plugin-flink-design.md](./plugin-flink-design.md)。
- `SPARK + K8S_OPERATOR`，设计见 [plugin-spark-design.md](./plugin-spark-design.md)。

## 当前不包含

- Redis 状态存储。
- K8S / YARN 通用提交器。
- 结果上报持久化队列。
- 本地进程句柄跨机器恢复。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
