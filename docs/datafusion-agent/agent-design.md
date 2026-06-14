# 调度 Agent 设计

> 数据结构见 [agent-data-define.md](./agent-data-define.md)。worker 框架契约见 [../datafusion-scheduler-worker/scheduler-worker-design.md](../datafusion-scheduler-worker/scheduler-worker-design.md)。

## 定位

`datafusion-agent` 是 `datafusion-scheduler-worker` 的运行时应用。agent 负责 Spring Boot 启动、RPC Provider、manager client、线程池、worker 契约装配、插件执行、任务状态存储、心跳和结果上报。

边界：

- `datafusion-scheduler-worker` 定义中性 SPI 和模型。
- `datafusion-agent` 实现 SPI，并对接 manager、本地文件、Kubernetes、Yarn 等运行时系统。
- 业务插件通过 jar、脚本、镜像或外部 application artifact 运行，不由 agent 设计直接绑定。

## 启动流程

```text
读取 AgentProperties
    -> 初始化 taskPool / reportPool
    -> 装配 WorkerTaskService / WorkerTaskOperatorRouter
    -> 加载 PluginTaskExecutor 和 PluginRunModeStateMapping
    -> 恢复本地未清理 WorkerTaskExecutionState
    -> 注册 worker 到 manager
    -> 启动心跳和状态上报计划
    -> 暴露 AgentExecutorRpcProvider
```

manager 注册成功前默认不接收新任务；`acceptTasksBeforeRegistered=true` 时允许本地开发绕过。

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

`AgentExecutorRpcProvider` 只做 RPC 适配、ready 校验、线程池隔离和返回包装。任务执行语义进入 worker 框架。`HttpManagerClient` 负责 outbound RPC，`ManagerTaskResultReporter` 负责结果上报。

## 插件与运行模式

`pluginType` 路由插件执行器，例如 `SHELL`、`DATAX`、`FLINK`、`SPARK`。

`runMode` 表示终端运行形态，例如 `LOCAL`、`K8S`、`YARN`。状态映射按 `pluginType + runMode` 选择，不能只按 `runMode` 全局处理。

Shell 当前只实现 `LOCAL`，代码归类在 `plugin.shell.local` 包下。后续新增 `SHELL + K8S` 或其他运行模式时，再按 DataX 的模式补充独立 runner 和状态映射。

通用插件闭环：

```text
TaskRequest(taskData, pluginParam)
    -> PluginTaskExecutor.prepareTask
    -> PluginTaskExecutor.submitTask
    -> 写入 WorkerTaskExecutionState
    -> PluginRunModeStateMapping.mapState
    -> TaskResultReporter.report
    -> manager 调用 finishTask 后 destroyTask 并删除状态记录
```

插件参数只来自 `pluginParam` 和 `taskData`，不从 `application.yml` 读取第三方运行参数。运行模板机制见 [plugin-run-mode-template-design.md](./plugin-run-mode-template-design.md)。

## 线程池

| 线程池 | 配置前缀 | 职责 |
|--------|----------|------|
| `agentTaskPool` | `datafusion.agent.task-pool` | RPC 任务控制、插件提交、进程 watcher |
| `agentReportPool` | `datafusion.agent.report-pool` | 任务结果上报到 manager |

心跳和状态刷新调度器是内部单线程调度器，只负责定时触发；真正上报通过 `reportPool`。

## 状态上报

```text
WorkerTaskExecutionStateStore.listRecords
    -> 按 pluginType + runMode 找状态映射器
    -> mapState 得到 StatusEnum
    -> 状态变化后 record
    -> TaskResultReporter.report
    -> manager 调用 finishTask 后 remove
```

规则：

- 状态刷新只查询终端状态，不主动停止、强杀或重启任务。
- `.state.status` 使用 `StatusEnum.name()`。
- manager 幂等消费结果，agent 不维护上报确认状态。
- manager 不可用时保留状态，等待下一轮继续上报。
- 查询暂时不可用时保持原状态；连续异常可推进为 `UNKNOWN`。

## 本地文件

日志目录：

```text
${modules}/logs/{date}/{flowInstanceId}/{taskInstanceId}/
```

状态目录：

```text
${modules}/task-status/{date}/{flowInstanceId}/{taskInstanceId}/
    {taskInstanceId}.state
    taskStatus.log
```

`logPath` 只表示 agent 本地日志入口。三方日志、K8S/Yarn/Flink UI 或对象存储日志地址放入 `TaskResult.result.pluginLogUri`。

## 第一版范围

实现：

- Spring Boot agent 运行时。
- manager 注册、心跳、下线和任务结果上报。
- 四个任务控制 RPC。
- worker 框架装配和 Spring Bean 插件加载。
- 文件版任务执行状态存储。
- `SHELL + LOCAL` 提交、停止、强杀、日志重定向和状态映射。

暂不实现：

- Nacos discovery 具体实现。
- 真实插件目录扫描。
- Redis 状态存储。
- K8S / YARN 通用提交器。DataX K8S 单独见 [datax-run-mode-design.md](./datax-run-mode-design.md)。
- 结果上报持久化队列。
- 本地进程句柄跨机器恢复。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
