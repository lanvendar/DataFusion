# 调度 Agent 设计

> 数据结构定义见 [agent-data-define.md](./agent-data-define.md)，本文档不重复字段定义。

`datafusion-agent` 是 worker 的运行时应用层，负责承载 `datafusion-scheduler-worker`，提供 Spring Boot 启动、HTTP 接口、配置加载、线程池、插件加载、worker 注册心跳、任务执行集成、任务状态记录和结果上报。

## 边界

`datafusion-scheduler-master` / `datafusion-scheduler-worker` 是调度框架层，仅作为 jar 包提供核心能力、接口契约、状态机、模型和默认实现。

`datafusion-manager` / `datafusion-agent` 是运行时应用层，负责 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、部署和外部系统集成。

禁止反向依赖：框架层不得依赖运行时应用层；运行时应用层可以依赖并装配框架层。

`datafusion-agent` 可以使用 `datafusion-common-spring` 的公共 Web 对象，例如 `Result<T>`、错误响应和校验能力。`datafusion-scheduler-worker` 不使用这些 Spring/Web 对象。

## 职责

- 启动并装配 worker 框架组件。
- 初始化通过 `ThreadPoolBuilder` 创建的线程池。
- 扫描并加载本节点支持的任务插件。
- 向 Nacos 注册 agent 服务实例。
- 向 manager 注册 worker 调度信息并发送心跳。
- 暴露 master 调用的内部任务控制 HTTP 接口。
- 将任务请求提交到 worker 框架和插件执行器。
- 记录任务状态、终端任务 ID 和任务日志。
- 将任务结果异步上报 manager。
- 启动时恢复本节点未完成任务。

## 模块依赖

```text
datafusion-agent
    -> datafusion-scheduler-worker
    -> datafusion-common-data
    -> datafusion-common-spring
```

通信模型归属：

- `TaskRequest`、`TaskResult`、worker 注册/心跳相关 DTO 统一从 `datafusion-common-data` 引入。
- `WorkerManager`、`WorkerStorage` 和 worker 选择策略属于 master 侧能力，agent 不直接依赖。
- `datafusion-agent` 不依赖 `datafusion-scheduler-master`，只通过 HTTP 接口与 manager 通信。

## 启动流程

```text
读取配置
    -> 初始化线程池
    -> 初始化 worker 框架组件
    -> 加载插件并生成 pluginTypes
    -> 恢复本节点未完成任务
    -> 向 Nacos 注册 agent 服务
    -> 向 manager 注册 worker 调度信息
    -> 启动心跳
    -> 开放任务控制接口
```

启动规则：

- 线程池必须先于任务接口可用前初始化完成。
- 插件加载完成后才能生成 `pluginTypes`。
- 任务恢复应在 worker 进入可调度状态前完成。
- manager 注册成功后，agent 才能接收新的任务提交。
- 仅 Nacos 注册成功不代表 worker 可调度。

## 注册与发现

agent 采用“双注册”模型：

- 向 Nacos 注册 agent 服务实例，用于 HTTP 服务寻址。
- 向 manager 注册 worker 调度信息，用于任务分配、能力匹配、心跳和 failover。

manager 注册信息至少包含：

- `id`
- `ip`
- `port`
- `hostName`
- `pluginTypes`
- `status`
- `registerTime`
- `lastHeartbeatTime`
- `updateTime`

异常处理：

- Nacos 不可用但 manager 地址可配置时，agent 可以继续向 manager 注册。
- manager 不可用时，agent 不进入可调度状态，并持续重试注册。
- agent 已注册到 Nacos 但未注册到 manager 时，任务接口返回节点未就绪。
- manager 心跳或结果上报失败时，本地任务继续运行，上报进入重试。

## 线程池

agent 收到 manager 的任务控制请求后，HTTP 线程只做参数校验、提交和快速返回，实际操作必须进入通过 `ThreadPoolBuilder` 创建的线程池。

建议线程池：

- `agentTaskControlPool`：处理 run / stop / kill / finish 控制请求。
- `agentTaskRunPool`：执行插件任务或外部应用提交逻辑。
- `agentResultReportPool`：异步上报任务结果。
- `agentHeartbeatPool`：执行注册、心跳和主动下线。
- `agentRecoveryPool`：启动时恢复未完成任务。

线程池名称必须稳定，便于日志和监控定位。任务执行池满时返回资源不足；结果上报池满时写入本地待上报记录并重试。

## 插件加载

agent 调用 worker 框架定义的 `WorkerPluginLoader` 加载插件：

```text
WorkerPluginLoader.loadPlugins
    -> 获得 PluginTaskExecutor 列表
    -> 注册到 WorkerTaskOperatorRouter
    -> 生成 pluginTypes
    -> 注册或心跳时上报 manager
```

部分插件初始化失败不影响 agent 基础启动。失败插件不得出现在 `pluginTypes` 中，并应记录明确日志。

## HTTP 接口

agent 对 manager 暴露任务控制接口：

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

接口响应使用 `datafusion-common-spring` 的公共响应结构，例如 `Result<T>`。

## 提交语义

异步提交：

```text
接收 submitTask
    -> 提交到线程池
    -> 返回 SUBMIT_SUCCESS + submitMode=ASYNC
    -> 后台执行
    -> 异步上报 RUNNING / RUN_SUCCESS / RUN_FAILURE
```

同步提交：

```text
接收 submitTask
    -> 提交并确认任务进入运行态
    -> 返回 RUNNING + submitMode=SYNC
    -> 异步上报 RUN_SUCCESS / RUN_FAILURE
```

任务最终成功或失败只能通过 `/internal/schedule/reportTaskResult` 异步上报。

## 任务状态恢复

agent 启动时需要恢复上次在本节点执行但未完成的任务。

数据来源：

- manager 查询到的本 worker 未完成任务。
- agent 本地任务状态文件。
- 外部系统中的终端任务 ID，例如 PID、Flink Job ID、Yarn Application ID。

处理规则：

- 仍在运行的任务恢复本地上下文。
- 已消失或状态不确定的任务上报失败、强制终止或待人工处理。
- 恢复流程必须幂等，重复启动不能重复推进错误状态。

## 日志与状态文件

agent 任务日志根目录：

```text
${modules}/logs/
    执行日期/
        流程实例id/
            任务实例id/
                *.log
                *.err.log
```

agent 任务状态根目录：

```text
${modules}/task-status/
    执行日期/
        流程实例id/
            任务实例id/
                *.state
                taskStatus.log
```

`taskStatus.log` 推荐格式：

```text
appid:123|pid:111|workId:456|status:running
```

其中 `appid` 表示 Flink Job ID、Yarn Application ID、DataX 任务 ID 等外部应用 ID；`pid` 表示本地进程 ID；`workId` 表示 worker 节点 ID。

## 幂等

agent 必须处理重复提交、重复停止、重复强制停止和重复结果上报。

推荐幂等键：

```text
taskInstanceId + attemptNo + actionType
```

规则：

- 同一 attempt 的重复 `submitTask` 不重复启动任务。
- 重复 `stopTask` / `killTask` 返回当前控制结果或当前终态。
- 结果上报失败时可重试，manager 端必须能幂等消费。
- 本地状态写入和实际执行无法原子化时，以终端任务 ID 和幂等键恢复状态。

## 实现清单

- 改造为 Spring Boot worker 运行时应用。
- 装配 worker 框架组件和公共线程池。
- 实现 worker 注册、心跳和下线。
- 实现内部任务控制 API。
- 实现插件扫描、加载和 `pluginTypes` 上报。
- 实现任务状态文件、日志和终端任务 ID 记录。
- 实现 manager 短暂不可用时的心跳、注册和结果上报重试。
- 实现启动恢复。

## 第一版实现边界

已实现：

- Spring Boot 入口 `AgentApplication`。
- `AgentProperties` 配置对象。
- 使用 `ThreadPoolBuilder` 装配 `agentTaskControlPool`、`agentTaskRunPool`、`agentResultReportPool`、`agentHeartbeatPool`、`agentRecoveryPool`。
- 装配 `WorkerTaskService`、`WorkerTaskOperatorRouter`、`WorkerPluginLoader`、`WorkerTaskContextStore`。
- 通过 Spring 容器中的 `PluginTaskExecutor` Bean 生成 `pluginTypes`。
- 暴露 `/internal/schedule/submitTask`、`/stopTask`、`/killTask`、`/finishTask`。
- 使用 `Result<T>` 包装 HTTP 响应。
- 启动后按配置向 manager 注册，注册成功后进入 ready，并周期心跳。
- 关闭时向 manager 下线。
- 结果上报投递到 `agentResultReportPool`。
- 本地写入 `${modules}/task-status/{date}/{flowInstanceId}/{taskInstanceId}/taskStatus.log` 和 `{taskInstanceId}.state`。

暂不实现：

- 不直接引入 Nacos discovery 具体实现；保留给后续 Spring Cloud 或 Nacos client 装配。
- 不实现真实插件目录扫描；第一版通过 Spring Bean 收集 `PluginTaskExecutor`。
- 不实现跨进程任务恢复；保留 `agentRecoveryPool` 和本地状态文件作为后续恢复基础。
- 不实现结果上报持久化重试队列；第一版上报失败记录日志。
- 不实现具体 DataX、Flink、Spark、Shell 插件执行逻辑。

## 验证

- agent 启动后初始化线程池并加载插件。
- agent 注册后 manager 能看到 `pluginTypes`。
- agent 心跳能更新 `lastHeartbeatTime`。
- `/submitTask` 返回 `SUBMIT_SUCCESS` 或 `RUNNING`，最终状态异步上报。
- stop / kill 能定位终端任务并上报结果。
- manager 短暂不可用后结果上报能重试成功。
- agent 重启后能恢复未完成任务。

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
