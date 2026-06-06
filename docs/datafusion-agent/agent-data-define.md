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
| `AgentProperties.ThreadPoolConfig` | `corePoolSize` | `int` | 必填 | 核心线程数 |
| `AgentProperties.ThreadPoolConfig` | `maxPoolSize` | `int` | 必填 | 最大线程数 |
| `AgentProperties.ThreadPoolConfig` | `queueCapacity` | `int` | 必填 | 队列容量 |
| `AgentProperties.ThreadPoolConfig` | `keepAliveSeconds` | `int` | 必填 | 线程存活时间，单位秒 |

## 3. RPC 映射

agent 对 manager 暴露：

| RPC | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /internal/schedule/submitTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 提交任务 |
| `POST /internal/schedule/stopTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 停止任务 |
| `POST /internal/schedule/killTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 强制停止任务 |
| `POST /internal/schedule/finishTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 任务终态后的本地清理入口 |

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
| RPC `TaskRequest` -> `WorkerTaskOperator` | `SchedulerExecutorRpcProvider` 投递到 `agentTaskControlPool` 后调用 worker 框架 | agent 未 ready 时默认拒绝 |
| `RunningTaskContext` -> `WorkerTaskExecutionState` | agent 侧上下文存储转换 | 记录 `pluginType/runMode/appId/pluginParam/status`，用于状态恢复和上报计划 |
| `WorkerTaskExecutionState` -> 本地状态文件 | 写入 `taskStatus.log` 和 `{taskInstanceId}.state` | 第一版文件实现，后续可替换 Redis |
| `.state` -> `AgentTaskStateReportScheduler` | 启动或周期刷新时读取未清理状态 | 不恢复进程句柄，不干预终端任务 |
| `TaskResult` -> manager | `ManagerTaskResultReporter` 投递到 `agentResultReportPool` 后 HTTP 上报 | manager 幂等消费；失败等待下一轮状态刷新 |

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
  "workId": "{workId}",
  "status": "{StatusEnum.name}",
  "taskData": {},
  "pluginParam": {},
  "exitCode": null,
  "result": "{result}"
}
```

规则：

- `.state.status` 使用 `StatusEnum.name()`。
- `appId` 统一表示终端任务 ID；`SHELL + LOCAL` 中 `appId=pid`。
- `finishTask` 确认终态后删除 `.state`，停止该任务的状态上报计划。

## 6. 复用对象

| 对象 | 来源 | 用途 |
|------|------|------|
| `Result<T>` | `datafusion-common-spring` | RPC 响应包装 |
| `TaskRequest` / `TaskResult` / `Worker` | `datafusion-common-data` | manager/agent/worker 通信模型 |
| `WorkerTaskOperator` / `WorkerTaskService` | `datafusion-scheduler-worker` | worker 框架入口 |
| `WorkerTaskContextStorage` / `RunningTaskContext` | `datafusion-scheduler-worker` | 运行上下文契约和快照模型 |
| `WorkerTaskExecutionState` / `WorkerTaskExecutionStateStore` | `datafusion-scheduler-worker` | 状态 envelope 和状态存储 SPI |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 插件执行和状态映射 SPI |
| `TaskResultReporter` | `datafusion-scheduler-worker` | 任务结果上报端口 |
