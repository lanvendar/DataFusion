# 调度 Agent 设计

> 数据结构定义见 [agent-data-define.md](./agent-data-define.md)。worker 通用契约见 [scheduler-worker-design.md](../datafusion-scheduler-worker/scheduler-worker-design.md)。

## 1. 定位

`datafusion-agent` 是 `datafusion-scheduler-worker` 的运行时应用。agent 负责 Spring Boot 启动、RPC Provider、manager client、线程池、worker 契约装配、插件运行时实现、任务执行状态存储、状态上报计划和结果上报。

框架与运行时边界：

- `datafusion-scheduler-worker` 定义通用 SPI 和中性模型。
- `datafusion-agent` 实现 worker SPI，并对接 manager、本地文件、后续 Redis、Kubernetes、Yarn 等运行时系统。
- `datafusion-agent` 不依赖具体业务插件模块；业务插件以 jar、Pod + jar、Yarn application jar、脚本或镜像等 artifact 形式运行。

## 2. 目标类目录

```text
com.datafusion.agent
    AgentApplication

com.datafusion.agent.config
    AgentConfiguration
    AgentProperties

com.datafusion.agent.rpc
    SchedulerExecutorRpcProvider
    ManagerClient
    HttpManagerClient
    ManagerTaskResultReporter

com.datafusion.agent.runtime
    AgentLifecycle
    AgentRuntimeState

com.datafusion.agent.runtime.worker.context
    AgentWorkerTaskContextStorage

com.datafusion.agent.runtime.worker.plugin.shell
    ShellLocalPluginTaskExecutor
    ShellLocalRunModeStateMapping

com.datafusion.agent.runtime.worker.reporter
    AgentTaskStateReportScheduler
    FileWorkerTaskExecutionStateStore
```

说明：

- `rpc` 是 manager 与 agent 的远程通信边界。
- `runtime.worker.*` 是 agent 对 `datafusion-scheduler-worker` 契约的运行时实现。
- `ShellLocalRunModeStateMapping` 只表达 `SHELL + LOCAL` 的状态映射。Flink、Spark、DataX 等插件应在各自插件包中提供状态映射实现。

## 3. 启动流程

```text
读取 AgentProperties
    -> 初始化线程池
    -> 装配 WorkerTaskService / WorkerTaskOperatorRouter / WorkerTaskContextStorage
    -> 加载 PluginTaskExecutor 和 PluginRunModeStateMapping
    -> 恢复未清理 WorkerTaskExecutionState 的上报计划
    -> 注册 worker 到 manager
    -> 启动心跳和状态上报计划
    -> 开放 SchedulerExecutorRpcProvider
```

manager 注册成功前默认不接收新任务；`acceptTasksBeforeRegistered=true` 时允许绕过该限制。

## 4. RPC 边界

agent 对 manager 暴露：

```text
POST /internal/schedule/submitTask
POST /internal/schedule/stopTask
POST /internal/schedule/killTask
POST /internal/schedule/finishTask
```

agent 调用 manager：

```text
POST /internal/schedule/worker/register
POST /internal/schedule/worker/heartbeat
POST /internal/schedule/worker/offline
POST /internal/schedule/reportTaskResult
```

`SchedulerExecutorRpcProvider` 只做 RPC 适配、ready 校验、线程池隔离和返回包装；任务执行语义进入 worker 框架。`ManagerClient` / `HttpManagerClient` 是 outbound RPC client。`ManagerTaskResultReporter` 实现 worker 的 `TaskResultReporter`，通过 `ManagerClient` 上报。

TODO：恢复上报计划需要 manager 提供“按 worker 查询未完成任务”的接口。接口路径、请求对象和响应对象待 manager 开发时补充。

## 5. 插件与运行模式

`pluginType` 用于路由 `PluginTaskExecutor`，例如 `SHELL`、`DATAX`、`FLINK`、`SPARK`。

`runMode` 是终端运行形态，例如 `LOCAL`、`K8S`、`YARN`。它不是全局状态查询实现。状态映射按 `pluginType + runMode` 选择 `PluginRunModeStateMapping`。

推荐插件闭环：

```text
TaskRequest(taskData, pluginParam)
    -> PluginTaskExecutor.prepareTask
    -> PluginTaskExecutor.submitTask
    -> 写入 WorkerTaskExecutionState(appId, pluginType, runMode, status)
    -> PluginRunModeStateMapping.mapState
    -> TaskResultReporter.report
    -> manager 调用 finishTask 后 destroyTask 并删除状态记录
```

`deployMode` 是插件内部部署语义，例如 Flink/Spark 的 standalone、session、application；不等同于 agent 的 `runMode`。

## 6. 状态上报计划

任务提交成功后，agent 写入 `WorkerTaskExecutionState`，并通过状态上报计划持续同步状态。

```text
WorkerTaskExecutionStateStore.listRecords
    -> 按 pluginType + runMode 找 PluginRunModeStateMapping
    -> mapState 得到 StatusEnum
    -> 状态变化后 record
    -> TaskResultReporter.report
    -> 终态继续幂等上报
    -> manager 调用 finishTask 后 remove
```

规则：

- 状态刷新只读查询终端状态，不停止、不强杀、不重启任务。
- `.state.status` 使用 `StatusEnum.name()`，不定义 agent 私有状态。
- manager 幂等消费结果，agent 不在状态记录中维护上报确认状态。
- manager 不可用时保留当前状态，等待下一轮刷新继续上报。
- 查询暂时不可用时保持原状态；连续 `UNKNOWN` 达到阈值后可推进为 `UNKNOWN`。
- `finishTask` 是 manager 收到最终状态后的本地资源清理入口。

## 7. 本地文件约定

日志根目录：

```text
${modules}/logs/{date}/{flowInstanceId}/{taskInstanceId}/
```

状态根目录：

```text
${modules}/task-status/{date}/{flowInstanceId}/{taskInstanceId}/
    {taskInstanceId}.state
    taskStatus.log
```

`taskStatus.log` 推荐格式：

```text
appId:{appId}|workId:{workId}|status:{StatusEnum.name}
```

`{taskInstanceId}.state` 使用 JSON 写入 `WorkerTaskExecutionState`。`LOCAL` Shell 任务允许 `appId=pid`；最终成功/失败由 watcher 或包装脚本写入 `exitCode` 和最终 `StatusEnum`，不通过 pid 消失推断成功或失败。

## 8. 第一版边界

第一版实现：

- Spring Boot agent 运行时。
- manager 注册、心跳、下线和任务结果上报。
- `SchedulerExecutorRpcProvider` 的四个任务控制接口。
- worker 框架装配和 Spring Bean 插件加载。
- 文件版 `WorkerTaskExecutionStateStore`。
- 状态上报计划。
- `SHELL + LOCAL` 提交、停止、强杀、日志重定向、退出码监听和状态映射。

暂不实现：

- Nacos discovery 具体实现。
- 真实插件目录扫描。
- Redis 状态存储。
- K8S / YARN 真实提交器和状态映射。
- 结果上报持久化队列。
- 本地进程句柄恢复或第三方任务干预。

## 9. 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
