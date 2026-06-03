# 调度 Worker 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

无。`datafusion-scheduler-worker` 是调度框架 jar，不直接持久化数据库表。

## 2. Entity / PO 映射

无。

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `taskInstanceId` | `String` | 必填 | 任务实例 ID，幂等键组成部分 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `taskName` | `String` | 可选 | 任务名称 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `taskState` | `StatusEnum` | 可选 | master 当前任务状态 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `definition` | `JsonNode` | 可选 | 渲染后的任务定义 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `appId` | `String` | 可选 | 外部终端任务 ID，如 PID、Flink Job ID、Yarn Application ID |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `pluginType` | `String` | `submitTask` 必填 | 插件类型，用于路由 `PluginTaskExecutor` |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `pluginParam` | `JsonNode` | 可选 | 插件参数 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `attemptNo` | `Integer` | 可选，默认 `0` | 当前任务尝试次数，幂等键组成部分 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `submitMode` | `SubmitModeEnum` | 可选，默认 `SYNC` | 提交模式，`SYNC` 或 `ASYNC` |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `taskInstanceId` | `String` | 可选 | 任务实例 ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `taskName` | `String` | 可选 | 任务名称 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `taskState` | `StatusEnum` | 可选 | 任务状态 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `outputVars` | `Map<String, Variable>` | 可选 | 输出变量 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `workerId` | `String` | 可选 | worker ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `appId` | `String` | 可选 | 外部终端任务 ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `attemptNo` | `Integer` | 可选，默认 `0` | 当前任务尝试次数 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `submitMode` | `SubmitModeEnum` | 可选，默认 `SYNC` | 提交模式 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `result` | `String` | 可选 | 执行说明、错误信息或插件返回摘要 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `id` | `String` | 必填 | worker ID |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `ip` | `String` | 必填 | worker IP |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `port` | `Integer` | 必填 | worker HTTP 端口 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `pluginTypes` | `List<String>` | 可选 | worker 支持的插件类型 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `status` | `Integer` | 必填 | `STATUS_UP` / `STATUS_DOWN` / `STATUS_DELETED` |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `hostName` | `String` | 必填 | worker 主机名 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `registerTime` | `Long` | 可选 | 首次注册时间 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `lastHeartbeatTime` | `Long` | 可选 | 最近心跳时间 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `updateTime` | `Long` | 可选 | 最近更新时间 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `taskInstanceId` | `String` | 必填 | 任务实例 ID |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `taskName` | `String` | 可选 | 任务名称 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `pluginType` | `String` | 必填 | 插件类型 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `attemptNo` | `Integer` | 必填 | 当前尝试次数 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `appId` | `String` | 可选 | 外部终端任务 ID |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `taskState` | `StatusEnum` | 可选 | 最近任务状态 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `submitMode` | `SubmitModeEnum` | 必填 | 提交模式 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `request` | `TaskRequest` | 可选 | 原始请求 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `lastResult` | `TaskResult` | 可选 | 最近一次结果 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `createTime` | `Long` | 必填 | 上下文创建时间 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `updateTime` | `Long` | 必填 | 上下文更新时间 |

## 4. API 数据映射

无。`datafusion-scheduler-worker` 不定义 Controller；HTTP API 由 `datafusion-agent` 运行时应用层提供。

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TaskInstance` -> `TaskRequest` | 由 manager/master 侧运行时转换 | `retryTimes` 映射为 `attemptNo`，`pluginData.pluginType/pluginParam` 映射为 `pluginType/pluginParam` |
| `TaskRequest` -> `RunningTaskContext` | worker 接收请求后创建或复用上下文 | 幂等键为 `taskInstanceId + attemptNo` |
| `PluginTaskExecutor.submitTask` -> `TaskResult` | 插件返回执行结果 | worker 统一补齐 `taskInstanceId/flowInstanceId/taskName/attemptNo/submitMode` |
| `RunningTaskContext` -> `TaskResult` | 重复请求返回最近结果或当前状态 | 已有终态时直接返回终态；运行中按 `submitMode` 返回 `RUNNING` 或 `SUBMIT_SUCCESS` |
| `TaskResult` -> `TaskResultReporter.report` | worker 异步上报 manager/master | 上报实现位于 `datafusion-agent`，worker 只调用接口 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `submitMode` | 不涉及 | `SubmitModeEnum` | `SYNC` / `ASYNC` | 替代含义不清晰的 `sync` |
| `taskState` | 不涉及 | `StatusEnum` | 使用 common-data 调度状态 | 提交响应只能返回 `RUNNING` 或 `SUBMIT_SUCCESS` |
| `attemptNo` | 不涉及 | `Integer` | 缺省按 `0` 处理 | 幂等键组成部分 |
| `definition` | 不涉及 | `JsonNode` | manager/master 传入，worker 不解析结构 | 插件自行解释 |
| `pluginParam` | 不涉及 | `JsonNode` | manager/master 传入，worker 不解析结构 | 插件自行解释 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `TaskRequest` | `datafusion-common-data` | 通信 DTO | master/manager/worker/agent 共享 |
| `TaskResult` | `datafusion-common-data` | 通信 DTO | master/manager/worker/agent 共享 |
| `Worker` | `datafusion-common-data` | 通信/节点模型 | master/manager 维护 worker 信息 |
| `StatusEnum` | `datafusion-common-data` | 状态枚举 | 任务状态 |
| `ActionType` | `datafusion-common-data` | 动作枚举 | 幂等动作类型 |
| `SubmitModeEnum` | `datafusion-common-data` | 提交模式枚举 | 区分同步提交和异步提交 |
| `datafusion-plugin-api` | `datafusion-plugin-api` | 插件 API 依赖 | worker 侧插件执行器可复用插件接口和模型 |
