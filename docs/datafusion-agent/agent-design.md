# 调度 Agent 设计

> 数据结构见 [agent-data-define.md](./agent-data-define.md)。DataX 插件见
> [plugin-datax-design.md](./plugin-datax-design.md)，Shell 插件见
> [plugin-shell-design.md](./plugin-shell-design.md)。worker 框架契约见
> [../datafusion-scheduler-worker/scheduler-worker-design.md](../datafusion-scheduler-worker/scheduler-worker-design.md)。

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
    -> 从 ${taskRuntimeDir} 恢复未清理 WorkerTaskExecutionState
    -> 注册 worker 到 manager
    -> 启动心跳和状态上报计划
    -> 暴露 AgentExecutorRpcProvider
```

manager 注册成功前默认不接收新任务；`acceptTasksBeforeRegistered=true` 时允许本地开发绕过。

agent 启动时先生成 `workerCode`：配置了 `datafusion.agent.worker.worker-code` 时使用配置值；
未配置时使用 `hostName:ip:port` 生成稳定 UUID 字符串；主机名、IP、端口都不可用时使用
`00000000-0000-0000-0000-000000000001`。manager 注册成功后返回
`scheduler_worker_registry.id`，agent 将该 UUID 保存为 `Worker.id`，后续 `TaskRequest.workerId`、
`TaskResult.workerId`、`.snap`、`.state`、`state.log` 都使用这个 `workerId`。注册成功返回的
`Worker` 同时写入 `/opt/datafusion-builtin/datafusion-agent/worker.config`；agent 重启后如果
本地 `workerCode` 未变化，则先恢复该 `Worker.id`，后续 heartbeat/offline 只用 `id`。

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
`TaskResult.workDirPath` 表示任务运行目录，manager 只通过该目录识别标准日志。
`TaskResult.result.pluginLogUri` 表示插件日志入口，可以是任务运行目录下的插件日志文件、对象存储 URI 或第三方运行时 URI。
`TaskResult.result` 不返回 worker 服务日志目录；agent 服务日志通过 `Worker.workerLogDir` 查询。

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
POST /internal/schedule/reportTaskResult
```

`AgentExecutorRpcProvider` 只做 RPC 适配、ready 校验、线程池隔离和返回包装。任务执行语义进入
worker 框架。`HttpManagerClient` 负责 outbound RPC，`ManagerTaskResultReporter` 负责结果上报。

`worker/register` 返回 `Result<Worker>`，用于把 manager 生成或匹配到的 `Worker.id` 回传给
agent。`worker/heartbeat` 只提交 `id/lastHeartbeatTime`，`worker/offline` 只提交 `id`。
`Worker.workerCode` 只用于注册时定位或创建 registry 记录，不作为任务实例 `workerId`。

## 插件与运行模式

`pluginType` 路由插件执行器，例如 `SHELL`、`DATAX`。`runMode` 表示终端运行形态，例如
`LOCAL`、`K8S`。状态映射按 `pluginType + runMode` 选择，不能只按 `runMode` 全局处理。

通用插件闭环：

```text
TaskRequest(taskData, pluginParam)
    -> PluginTaskExecutor.prepareTask
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

- `.snap` 保存提交快照，包含 `workerId`、`pluginType`、`runMode`、`taskData`、`pluginParam`。
- `.state` 保存运行态，包含 `workerId`、`appId`、`workDirPath`、`status`、`exitCode`、`result`。
- `stdout.log`、`stderr.log`、`state.log` 是 agent 标准任务日志，文件名由 `TaskRuntimeFiles` 统一定义。
- `state.log` 保存状态变化流水，finish 后保留；`.snap` 和 `.state` 在 finish 确认终态后删除。
- `saveState` 更新 `.state` 时同步比较旧 `.state`，当 `status`、`appId` 或 `exitCode` 变化时追加 `state.log`，避免 watcher 或状态刷新器原地修改运行态对象导致终态漏记。
- 控制请求可以只携带 `taskInstanceId`；agent 通过 `.snap + .state` 恢复插件类型、运行模式和运行引用。

## 状态上报

```text
WorkerTaskExecutionStore.listListeningStates
    -> 按 taskInstanceId 读取 WorkerTaskExecutionSnap
    -> 按 snap.pluginType + snap.runMode 找状态映射器
    -> mapState(state) 得到 StatusEnum
    -> 状态变化后 saveState
    -> TaskResultReporter.report
    -> manager 调用 finishTask 后 remove
```

状态刷新只查询终端状态，不主动停止、强杀或重启任务。manager 不可用时保留状态，等待下一轮继续上报。

## 第一版范围

实现：

- Spring Boot agent 运行时。
- manager 注册、心跳、下线和任务结果上报。
- 四个任务控制 RPC。
- worker 框架装配和 Spring Bean 插件加载。
- 文件版任务执行状态存储。
- `SHELL + LOCAL`、`DATAX + LOCAL`、`DATAX + K8S`。

暂不实现：

- Redis 状态存储。
- K8S / YARN 通用提交器。
- 结果上报持久化队列。
- 本地进程句柄跨机器恢复。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
