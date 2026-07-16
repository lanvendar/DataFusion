# 调度 Agent 设计

> 数据结构见 [agent-data-define.md](./agent-data-define.md)。  
> DataX 插件见 [plugin-datax-design.md](./plugin-datax-design.md)，  
> Shell 插件见 [plugin-shell-design.md](./plugin-shell-design.md)，  
> Spider 插件见 [plugin-spider-design.md](./plugin-spider-design.md)，  
> API 插件见 [plugin-api-design.md](./plugin-api-design.md)，  
> Flink 插件见 [plugin-flink-design.md](./plugin-flink-design.md)。  
> worker 框架契约见 [../datafusion-scheduler-worker/scheduler-worker-design.md](../datafusion-scheduler-worker/scheduler-worker-design.md)。

## 定位

`datafusion-agent` 是 `datafusion-scheduler-worker` 的运行时应用。agent 负责 Spring Boot 启动、
RPC Provider、manager client、线程池、worker 契约装配、插件执行、任务状态存储、心跳和结果上报。

边界：

- `datafusion-scheduler-worker` 定义中性 SPI 和模型。
- `datafusion-agent` 实现 SPI，并对接 manager、本地文件、Kubernetes 等运行时系统。
- 插件的参数、执行细节和状态映射写入各插件设计文档。

## 启动流程

```text
读取 AgentProperties
    -> 初始化 taskPool / reportPool
    -> 装配 WorkerTaskService / WorkerTaskOperatorRouter
    -> 加载 PluginTaskExecutor 和 PluginRunModeStateMapping
    -> 注册 worker 到 manager
    -> 按 workerId 从 manager 获取属于自己的未完成任务清单
    -> 按任务清单恢复本地 WorkerTaskExecutionState
    -> 启动心跳和状态上报计划
    -> 暴露 AgentExecutorRpcProvider
```

manager 注册成功前默认不接收新任务；`acceptTasksBeforeRegistered=true` 时允许本地开发绕过。

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

`AgentExecutorRpcProvider` 只做 RPC 适配、ready 校验、线程池隔离和返回包装。任务执行语义进入
worker 框架。`HttpManagerClient` 负责 outbound RPC，`ManagerTaskResultReporter` 负责结果上报。

`worker/register` 返回 `Result<Worker>`，用于把 manager 生成或匹配到的 `Worker.id` 回传给
agent。`worker/heartbeat` 只提交 `id/lastHeartbeatTime`，`worker/offline` 只提交 `id`。
`Worker.workerCode` 只用于注册时定位或创建 registry 记录，不作为任务实例 `workerId`。
`worker/tasks` 在注册成功后调用，只提交 `id`，返回属于该 worker 的未完成 `TaskRequest` 清单。

## 插件与运行模式

`pluginType` 与 `runMode` 共同路由插件执行器和状态映射器，例如 `DATAX + LOCAL`、
`DATAX + K8S`。`runMode` 是 `TaskRequest` 标准字段，不写入 `pluginParam`。

通用插件闭环：

```text
TaskRequest(pluginType, runMode, taskData, pluginParam)
    -> WorkerTaskOperatorRouter.route(pluginType, runMode)
    -> PluginTaskExecutor.validateTaskRequest
    -> PluginTaskExecutor.submitTask
    -> 写入 WorkerTaskExecutionSnap / WorkerTaskExecutionState
    -> PluginRunModeStateMapping.mapState
    -> TaskResultReporter.report
    -> manager 调用 finishTask 后 destroyTask 并删除状态记录
```

插件参数只来自 `pluginParam` 和 `taskData`，不从 `application.yml` 读取第三方运行参数。模板只保存静态命令
或 manifest 骨架；工作目录、日志文件、运行 ID 由 agent 或插件执行器生成。

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

- `.snap` 保存提交快照，包含 `workerId`、`pluginType`、`runMode`、`submitMode`、`taskData`、`pluginParam`。
- `.state` 保存运行态，包含 `workerId`、`appId`、`workDirPath`、`status`、`exitCode`、`result`、`outputVars`。
- `stdout.log`、`stderr.log`、`state.log` 是 agent 标准任务日志，文件名由 `TaskRuntimeFiles` 统一定义。
- `state.log` 保存状态变化流水，终态后保留。
- 终态结果上报成功后停止监听；成功终态删除 `.snap` 和 `.state`，非成功终态保留 `.snap` 和 `.state` 用于排查。
- `WorkerTaskExecutionStore` 是 agent 本地运行态的唯一缓存入口；`executionCache` 中存在 `taskInstanceId`
  表示任务仍是当前运行上下文并参与状态监听，不存在则不再监听，也不作为 submit/stop/kill/finish 的当前上下文。
- `executionCache` 中保存 `RunningTaskContext`，上下文直接组合 `.snap` 对应的
  `WorkerTaskExecutionSnap` 和 `.state` 对应的 `WorkerTaskExecutionState`，避免在内存态重复声明同一批属性。
- `AgentWorkerTaskContextStorage` 只做 `RunningTaskContext` 与 `WorkerTaskExecutionStore` 的适配，不再维护独立
  `contextMap`。
- `saveState` 更新 `.state` 时同步比较旧 `.state`，当 `status`、`appId` 或 `exitCode` 变化时追加 `state.log`，避免 watcher 或状态刷新器原地修改运行态对象导致终态漏记。
- `exitCode` 是本地运行态诊断字段，不进入 `TaskResult.workerResult`。
- 控制请求可以只携带 `taskInstanceId`；agent 通过 `.snap + .state` 恢复插件类型、运行模式和运行引用。

## 状态上报

```text
WorkerTaskExecutionStore.listListeningStates
    -> 按 taskInstanceId 读取 WorkerTaskExecutionSnap
    -> 按 snap.pluginType + snap.runMode 找状态映射器
    -> mapState(state) 得到 StatusEnum
    -> 状态变化后 saveState
    -> TaskResultReporter.report
    -> 终态上报成功后停止监听
```

状态刷新只查询终端状态，不主动停止、强杀或重启任务。manager 不可用或结果上报失败时保留监听，
等待下一轮继续上报。终态上报成功后不再重复上报；其中 `StatusEnum.isSuccess()` 为 true 的终态删除
`.snap/.state`，其他终态保留 `.snap/.state`。

`readState` / `readSnapshot` 可以读取保留的 `.state/.snap` 用于诊断或插件控制，但不会把任务重新加入
`executionCache`；只有 `saveContext` / `saveState` / `saveSnapshot` 和 `restoreListeningTasks` 会主动恢复当前运行上下文。

agent 启动恢复不扫描全部 `${taskRuntimeDir}`。恢复流程为：

```text
register 成功返回 Worker.id
    -> 调用 manager worker/tasks 获取本 worker 未完成任务清单
    -> 对清单内 taskInstanceId 读取本地 .snap/.state
    -> 恢复状态监听和上报
```

如果 manager 已人工处理某个任务，该任务不会出现在清单中，agent 即使本地残留 `.state` 也不恢复、不上报。

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
