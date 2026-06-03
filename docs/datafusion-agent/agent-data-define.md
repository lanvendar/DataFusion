# 调度 Agent 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

无。`datafusion-agent` 第一版不直接持久化数据库表，任务状态写入本地文件。

## 2. Entity / PO 映射

无。

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `AgentProperties` | `Internal` | Spring 配置 | `modules` | `String` | 默认 `${user.dir}` | agent 模块根目录 |
| `AgentProperties.Worker` | `Internal` | worker 本机配置 | `id` | `String` | 可选 | 未配置时由 `hostName:port` 推导 |
| `AgentProperties.Worker` | `Internal` | worker 本机配置 | `ip` | `String` | 可选 | 未配置时读取本机 IP |
| `AgentProperties.Worker` | `Internal` | worker 本机配置 | `port` | `Integer` | 默认 `8081` | HTTP 端口 |
| `AgentProperties.Worker` | `Internal` | worker 本机配置 | `hostName` | `String` | 可选 | 未配置时读取本机 hostName |
| `AgentProperties.Worker` | `Internal` | worker 本机配置 | `acceptTasksBeforeRegistered` | `boolean` | 默认 `false` | 未注册 manager 前是否允许接收任务 |
| `AgentProperties.Manager` | `Internal` | manager 通信配置 | `baseUrl` | `String` | 可选 | manager HTTP 地址 |
| `AgentProperties.Manager` | `Internal` | manager 通信配置 | `enabled` | `boolean` | 默认 `true` | 是否启用 manager 注册心跳 |
| `AgentProperties.Manager` | `Internal` | manager 通信配置 | `heartbeatIntervalMs` | `long` | 默认 `15000` | 心跳间隔 |
| `AgentProperties.Storage` | `Internal` | 本地文件配置 | `logsDir` | `String` | 默认 `logs` | `${modules}` 下的日志目录名 |
| `AgentProperties.Storage` | `Internal` | 本地文件配置 | `taskStatusDir` | `String` | 默认 `task-status` | `${modules}` 下的任务状态目录名 |
| `AgentProperties.ThreadPoolConfig` | `Internal` | 线程池配置 | `corePoolSize` | `int` | 必填 | 核心线程数 |
| `AgentProperties.ThreadPoolConfig` | `Internal` | 线程池配置 | `maxPoolSize` | `int` | 必填 | 最大线程数 |
| `AgentProperties.ThreadPoolConfig` | `Internal` | 线程池配置 | `queueCapacity` | `int` | 必填 | 队列容量 |
| `AgentProperties.ThreadPoolConfig` | `Internal` | 线程池配置 | `keepAliveSeconds` | `int` | 必填 | 线程存活时间 |
| `TaskRequest` | `Request` | manager 调用 agent 控制任务 | 见 worker 数据定义 | 见 worker 数据定义 | 见 worker 数据定义 | 来自 `datafusion-common-data` |
| `TaskResult` | `Response` | agent 返回或上报任务结果 | 见 worker 数据定义 | 见 worker 数据定义 | 见 worker 数据定义 | 来自 `datafusion-common-data` |
| `Worker` | `Request` | agent 注册和心跳上报 | 见 worker 数据定义 | 见 worker 数据定义 | 见 worker 数据定义 | 来自 `datafusion-common-data` |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /internal/schedule/submitTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 提交任务 |
| `POST /internal/schedule/stopTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 停止任务 |
| `POST /internal/schedule/killTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 强制停止任务 |
| `POST /internal/schedule/finishTask` | `TaskRequest` | `TaskResult` | `Result<T>` | 任务完成收尾 |
| `POST {manager}/internal/schedule/worker/register` | `Worker` | 无固定解析 | manager 响应 | 注册 worker |
| `POST {manager}/internal/schedule/worker/heartbeat` | `Worker` | 无固定解析 | manager 响应 | worker 心跳 |
| `POST {manager}/internal/schedule/worker/offline` | `Worker` | 无固定解析 | manager 响应 | worker 下线 |
| `POST {manager}/internal/schedule/reportTaskResult` | `TaskResult` | 无固定解析 | manager 响应 | 上报任务结果 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `AgentProperties` -> `Worker` | 启动时组合 worker 配置、本机 IP、hostName、插件类型和时间字段 | `id` 未配置时使用 `hostName:port` |
| Spring `PluginTaskExecutor` Beans -> `WorkerPluginLoader` | 收集 Spring 容器中的插件执行器 | 生成 `pluginTypes` 并注册到 worker router |
| HTTP `TaskRequest` -> `WorkerTaskOperator` | Controller 提交到 `agentTaskControlPool` 后调用 worker 框架 | agent 未 ready 时默认拒绝 |
| `TaskResult` -> 本地状态文件 | 写入 `taskStatus.log` 和 `{taskInstanceId}.state` | 路径位于 `${modules}/task-status` |
| `TaskResult` -> manager | `ManagerTaskResultReporter` 投递到 `agentResultReportPool` 后 HTTP 上报 | 上报失败第一版只记录日志 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `submitMode` | 不涉及 | `SubmitModeEnum` | 透传 worker 框架 | `SYNC` / `ASYNC` |
| `taskState` | 不涉及 | `StatusEnum` | 透传 worker 框架 | 任务状态 |
| `definition` | 不涉及 | `JsonNode` | 透传插件执行器 | agent 不解析业务结构 |
| `pluginParam` | 不涉及 | `JsonNode` | 透传插件执行器 | agent 不解析业务结构 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | API 响应包装 | agent HTTP 返回 |
| `ThreadPoolBuilder` | `datafusion-common` | 创建线程池 | agent 所有任务相关线程池 |
| `TaskRequest` / `TaskResult` / `Worker` | `datafusion-common-data` | 通信模型 | manager/agent/worker 共享 |
| `WorkerTaskOperator` / `WorkerTaskService` | `datafusion-scheduler-worker` | worker 框架入口 | agent 装配并调用 |
| `PluginTaskExecutor` / `WorkerPluginLoader` | `datafusion-scheduler-worker` | 插件加载和执行契约 | agent 收集 Spring Bean |
