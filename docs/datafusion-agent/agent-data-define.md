# 调度 Agent 数据结构定义

> 本文档是 agent 运行时字段、RPC 映射和本地文件格式的事实源。worker 通用模型见 [scheduler-worker-data-define.md](../datafusion-scheduler-worker/scheduler-worker-data-define.md)。

## 1. 表结构

无。`datafusion-agent` 第一版不直接持久化数据库表。

## 2. 配置模型

| 对象 | 字段 | 类型 | 默认值 | 说明 |
|------|------|------|--------|------|
| `AgentProperties` | `modules` | `String` | `${user.dir}` | agent 模块根目录 |
| `AgentProperties.Worker` | `id` | `String` | 空 | 未配置时由 `hostName:port` 推导 |
| `AgentProperties.Worker` | `ip` | `String` | 空 | 未配置时读取本机 IP |
| `AgentProperties.Worker` | `port` | `Integer` | `8081` | HTTP 端口 |
| `AgentProperties.Worker` | `hostName` | `String` | 空 | 未配置时读取本机 hostName |
| `AgentProperties.Worker` | `acceptTasksBeforeRegistered` | `boolean` | `false` | 未注册 manager 前是否允许接收任务 |
| `AgentProperties.Manager` | `baseUrl` | `String` | 空 | manager HTTP 地址 |
| `AgentProperties.Manager` | `enabled` | `boolean` | `true` | 是否启用 manager 注册心跳 |
| `AgentProperties.Manager` | `heartbeatIntervalMs` | `long` | `15000` | 心跳间隔 |
| `AgentProperties.Storage` | `logsDir` | `String` | `logs` | `${modules}` 下的日志目录名 |
| `AgentProperties.Storage` | `taskStatusDir` | `String` | `task-status` | `${modules}` 下的任务状态目录名 |
| `AgentProperties.StateRefresh` | `intervalMs` | `long` | `15000` | 状态刷新间隔 |
| `AgentProperties.StateRefresh` | `unknownThreshold` | `int` | `3` | 连续 UNKNOWN 后推进状态的阈值 |
| `AgentProperties` | `taskPool` | `ThreadPoolConfig` | `8/16/512/60` | 任务 RPC 控制和插件提交执行共用线程池；同步提交占用时间较长，异步提交很快释放 |
| `AgentProperties` | `reportPool` | `ThreadPoolConfig` | `2/4/512/60` | 任务状态刷新结果和插件结果上报线程池 |
| `AgentProperties.ThreadPoolConfig` | `corePoolSize` | `int` | 必填 | 核心线程数 |
| `AgentProperties.ThreadPoolConfig` | `maxPoolSize` | `int` | 必填 | 最大线程数 |
| `AgentProperties.ThreadPoolConfig` | `queueCapacity` | `int` | 必填 | 队列容量 |
| `AgentProperties.ThreadPoolConfig` | `keepAliveSeconds` | `int` | 必填 | 线程存活时间，单位秒 |

线程池配置只保留两类业务线程池：`taskPool`、`reportPool`。心跳调度器和状态刷新调度器是 agent 内部 single scheduler，不作为独立业务池暴露配置。

## 3. RPC 映射

agent 对 manager 暴露：

| RPC | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /internal/scheduler/submitTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 提交任务 |
| `POST /internal/scheduler/stopTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 停止任务 |
| `POST /internal/scheduler/killTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 强制停止任务 |
| `POST /internal/scheduler/finishTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 任务终态后的本地清理入口 |

agent 调用 manager：

| RPC | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST {manager}/internal/schedule/worker/register` | `Worker` | 无固定解析 | manager 响应 | 注册 worker |
| `POST {manager}/internal/schedule/worker/heartbeat` | `Worker` | 无固定解析 | manager 响应 | worker 心跳 |
| `POST {manager}/internal/schedule/worker/offline` | `Worker` | 无固定解析 | manager 响应 | worker 下线 |
| `POST {manager}/internal/schedule/reportTaskResult` | `TaskResult` | 无固定解析 | manager 响应 | 上报任务结果 |

TODO：恢复上报计划需要 manager 提供“按 worker 查询未完成任务”的接口。

## 4. 层间转换

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `AgentProperties` -> `Worker` | 启动时组合 worker 配置、本机 IP、hostName、插件类型和时间字段 | `id` 未配置时使用 `hostName:port` |
| Spring `PluginTaskExecutor` Beans -> `WorkerPluginLoader` | 收集 Spring 容器中的插件执行器 | 生成 `pluginTypes` 并注册到 worker router |
| Spring `PluginRunModeStateMapping` Beans -> 状态映射注册表 | 按 `pluginType + runMode` 建立映射 | 状态刷新计划使用 |
| RPC `TaskRequest` -> `WorkerTaskOperator` | `AgentExecutorRpcProvider` 投递到 `agentTaskPool` 后调用 worker 框架 | agent 未 ready 时默认拒绝 |
| `RunningTaskContext` -> `WorkerTaskExecutionState` | agent 侧上下文存储转换 | 记录 `pluginType/runMode/appId/pluginParam/status`，用于状态恢复和上报计划 |
| `WorkerTaskExecutionState` -> 本地状态文件 | 写入 `taskStatus.log` 和 `{taskInstanceId}.state` | 第一版文件实现，后续可替换 Redis |
| `.state` -> `AgentTaskStateReportScheduler` | 启动或周期刷新时读取未清理状态 | 不恢复进程句柄，不干预终端任务 |
| `TaskResult` -> manager | `ManagerTaskResultReporter` 投递到 `agentReportPool` 后 HTTP 上报 | manager 幂等消费；失败等待下一轮状态刷新 |

## 5. 本地状态文件

状态文件路径：

```text
${modules}/task-status/{date}/{flowInstanceId}/{taskInstanceId}/{taskInstanceId}.state
```

`taskStatus.log` 格式：

```text
appId:{appId}|workId:{workId}|status:{StatusEnum.name}
```

`{taskInstanceId}.state` 写入 `WorkerTaskExecutionState` JSON：

```json
{
  "flowInstanceId": "{flowInstanceId}",
  "taskInstanceId": "{taskInstanceId}",
  "pluginType": "{pluginType}",
  "runMode": "{runMode}",
  "appId": "{appId}",
  "logPath": "{logPath}",
  "workId": "{workId}",
  "status": "{StatusEnum.name}",
  "taskData": {},
  "pluginParam": {},
  "exitCode": null,
  "result": {
    "message": "{message}",
    "pluginType": "{pluginType}",
    "runMode": "{runMode}",
    "pluginLogUri": "{pluginLogUri}",
    "agentLogPath": "{agentLogPath}",
    "exitCode": null
  }
}
```

规则：

- `.state.status` 使用 `StatusEnum.name()`。
- `appId` 统一表示终端任务 ID；`SHELL + LOCAL` 中 `appId=pid`。
- `logPath` 统一表示 agent 管理的本地日志路径；第三方日志 URI 放在 `TaskResult.result` 的结构化 JSON 中。
- `finishTask` 确认终态后删除 `.state`，停止该任务的状态上报计划。

## 6. 插件通信契约

插件必须显式定义 `TaskRequest.taskData`、`TaskRequest.pluginParam` 和 `TaskResult.result` 的 JSON 结构。`TaskResult.logPath` 保留给 agent 管理的本地日志入口；第三方系统日志、对象存储日志和脚本自定义日志放入 `result.pluginLogUri`。

### 6.1 SHELL LOCAL

`SHELL` 当前只支持 `LOCAL` 运行模式。为兼容第一版实现，`command` 可放在 `pluginParam` 或 `taskData`；推荐固定放在 `pluginParam`，任务实例变量放在 `taskData`。

`pluginParam`:

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 否 | `LOCAL` | Shell 第一版固定为 `LOCAL` |
| `command` | `String` | 是 | 无 | 本地可执行命令 |
| `args` | `List<String>` | 否 | 空 | 命令参数 |
| `workDir` | `String` | 否 | agent 当前工作目录 | 进程工作目录 |
| `env` | `Object<String,String>` | 否 | 空 | 环境变量 |
| `pluginLogUri` | `String` | 否 | 空 | 脚本自身写入的第三方日志 URI，仅透传到 `result` |

`taskData`:

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `args` | `List<String>` | 否 | 空 | 任务级参数；后续实现可追加或覆盖 `pluginParam.args` |
| `env` | `Object<String,String>` | 否 | 空 | 任务级环境变量；后续实现可覆盖 `pluginParam.env` |
| `pluginLogUri` | `String` | 否 | 空 | 本任务第三方日志 URI |

`TaskResult.result`:

```json
{
  "message": "LOCAL shell task submitted",
  "pluginType": "SHELL",
  "runMode": "LOCAL",
  "pluginLogUri": "oss://bucket/shell/task-1/stdout.log",
  "agentLogPath": "${modules}/logs/20260609/flow-1/task-1",
  "exitCode": 0
}
```

`SHELL + LOCAL` 运行时必须先渲染 `templates/shell/shell-local.yml` 得到 `LocalProcessSpec`，再由 `ShellLocalPluginTaskExecutor` 启动本地进程。`command/args/env/workDir/pluginLogUri` 的动态值只来自 `pluginParam` 和 `taskData`；`stdout/stderr` 路径由 Agent 根据 `modules/storage.logsDir/flowInstanceId/taskInstanceId` 生成。

## 7. 复用对象

| 对象 | 来源 | 用途 |
|------|------|------|
| `Result<T>` | `datafusion-common-spring` | RPC 响应包装 |
| `TaskRequest` / `TaskResult` / `Worker` | `datafusion-common-data` | manager/agent/worker 通信模型 |
| `WorkerTaskOperator` / `WorkerTaskService` | `datafusion-scheduler-worker` | worker 框架入口 |
| `WorkerTaskContextStorage` / `RunningTaskContext` | `datafusion-scheduler-worker` | 运行上下文契约和快照模型 |
| `WorkerTaskExecutionState` / `WorkerTaskExecutionStateStore` | `datafusion-scheduler-worker` | 状态 envelope 和状态存储 SPI |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 插件执行和状态映射 SPI |
| `TaskResultReporter` | `datafusion-scheduler-worker` | 任务结果上报端口 |
