# 调度 Agent 设计

> 数据结构定义见 [agent-data-define.md](./agent-data-define.md)。worker 通用契约见 [scheduler-worker-design.md](../datafusion-scheduler-worker/scheduler-worker-design.md)。

## 1. 定位

`datafusion-agent` 是 `datafusion-scheduler-worker` 的运行时应用。agent 负责 Spring Boot 启动、RPC Provider、manager client、线程池、worker 契约装配、插件运行时实现、任务执行状态存储、状态上报计划和结果上报。

框架与运行时边界：

- `datafusion-scheduler-worker` 定义通用 SPI 和中性模型。
- `datafusion-agent` 实现 worker SPI，并对接 manager、本地文件、后续 Redis、Kubernetes、Yarn 等运行时系统。
- `datafusion-agent` 不依赖具体业务插件模块；业务插件以 jar、Pod + jar、Yarn application jar、脚本或镜像等 artifact 形式运行。DataX LOCAL / K8S 运行模式的独立设计见 [datax-run-mode-design.md](./datax-run-mode-design.md)。

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
    -> 初始化 taskPool / reportPool
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

插件通信契约：

- 每个插件必须在对应 `*-data-define.md` 中明确 `TaskRequest.taskData`、`TaskRequest.pluginParam` 和 `TaskResult.result` 的 JSON 结构。
- `TaskResult.logPath` 只表示 agent 管理的本地日志入口；插件自己的三方日志 URI、K8S/Yarn/Flink UI、对象存储日志地址放入 `TaskResult.result.pluginLogUri`。
- `TaskResult.result` 是 `JsonNode` 结构，通用字段为 `message`、`pluginType`、`runMode`、`pluginLogUri`、`agentLogPath`、`exitCode`、`detail`。
- 插件参数来自 `pluginParam` 和 `taskData`，不从 `application.yml` 读取第三方运行参数。

运行模板边界：

- 插件运行模式统一采用 YAML 静态模板 + `pluginParam/taskData` 渲染参数 + typed `ExecutionSpec` 的结构，详见 [plugin-run-mode-template-design.md](./plugin-run-mode-template-design.md)。
- LOCAL 运行模式渲染为 `LocalProcessSpec`，Runner 只消费命令、工作目录、环境变量和日志重定向。
- K8S / YARN / Flink / Spark 等声明式外部资源运行模式渲染为对应 manifest 或 application spec。
- 模板负责静态结构和显式占位符；参数校验、默认值和任务级覆盖由插件 `ParamResolver` 完成。

## 6. 线程池模型

Agent 只暴露两类业务线程池配置：

| 线程池 | Bean | 配置前缀 | 职责 |
|--------|------|----------|------|
| 任务池 | `agentTaskPool` | `datafusion.agent.task-pool` | RPC 任务控制、插件提交执行、进程 watcher 等任务相关工作 |
| 上报池 | `agentReportPool` | `datafusion.agent.report-pool` | 任务结果上报到 manager |

心跳调度器和状态刷新调度器是内部 single scheduler，只负责定时触发，不作为独立业务线程池暴露配置。心跳任务直接在心跳调度器内执行；任务状态刷新触发后通过 `reportPool` 上报。同步提交会占用 `taskPool` 较久，异步提交只占用到提交动作入队完成。

## 7. 状态上报计划

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

## 8. 本地文件约定

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

`logPath` 是 agent 本地日志入口，路径由 `modules` 和 `storage.logsDir` 组合。三方日志不进入 `logPath`，只放在插件结果 JSON 中。

## 9. 第一版边界

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
- K8S / YARN 通用真实提交器和状态映射。DataX K8S 模式按 [datax-run-mode-design.md](./datax-run-mode-design.md) 单独推进。
- 结果上报持久化队列。
- 本地进程句柄恢复或第三方任务干预。

## 10. 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
