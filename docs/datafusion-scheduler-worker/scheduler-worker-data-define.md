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
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `taskData` | `JsonNode` | 可选 | 渲染后的任务执行数据 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `appId` | `String` | 可选 | 外部终端任务 ID，如 PID、Flink Job ID、Yarn Application ID |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `pluginType` | `String` | `submitTask` 必填 | 插件类型，用于路由 `PluginTaskExecutor` |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `pluginParam` | `JsonNode` | 可选 | 插件参数 |
| `TaskRequest` | `Request` | master/manager 请求 worker 操作任务 | `submitMode` | `SubmitModeEnum` | 可选，默认 `SYNC` | 提交模式，`SYNC` 或 `ASYNC` |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `taskInstanceId` | `String` | 可选 | 任务实例 ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `taskName` | `String` | 可选 | 任务名称 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `taskState` | `StatusEnum` | 可选 | 任务状态 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `outputVars` | `Map<String, Variable>` | 可选 | 输出变量 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `workerId` | `String` | 可选 | worker ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `appId` | `String` | 可选 | 外部终端任务 ID |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `logPath` | `String` | 可选 | agent 管理的本地日志路径或可被 manager 读取的日志入口 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `submitMode` | `SubmitModeEnum` | 可选，默认 `SYNC` | 提交模式 |
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `result` | `JsonNode` | 可选 | 插件结构化结果 JSON |
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
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `runMode` | `String` | 可选 | 终端运行模式 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `appId` | `String` | 可选 | 外部终端任务 ID |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `taskState` | `StatusEnum` | 可选 | 最近任务状态 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `submitMode` | `SubmitModeEnum` | 必填 | 提交模式 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `submitted` | `boolean` | 必填 | 是否已提交到插件执行器 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `taskData` | `JsonNode` | 可选 | 渲染后的任务执行数据快照 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `pluginParam` | `JsonNode` | 可选 | 插件参数快照 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `outputVars` | `Map<String, Variable>` | 可选 | 最近输出变量 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `logPath` | `String` | 可选 | 最近日志路径 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `result` | `JsonNode` | 可选 | 最近结构化结果 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `createTime` | `Long` | 必填 | 上下文创建时间 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `updateTime` | `Long` | 必填 | 上下文更新时间 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `taskInstanceId` | `String` | 必填 | 任务实例 ID |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `pluginType` | `String` | 可选 | 插件类型 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `runMode` | `String` | 可选 | 终端运行模式 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `appId` | `String` | 可选 | 终端任务 ID |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `logPath` | `String` | 可选 | agent 管理的本地日志路径或可被 manager 读取的日志入口 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `workId` | `String` | 可选 | worker 节点 ID |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `status` | `StatusEnum` | 可选 | 最近任务状态 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `taskData` | `JsonNode` | 可选 | 渲染后的任务执行数据 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `pluginParam` | `JsonNode` | 可选 | 插件参数 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `exitCode` | `Integer` | 可选 | 本地进程退出码 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务执行状态 envelope | `result` | `JsonNode` | 可选 | 执行说明、错误信息或插件返回摘要 |

## 4. API 数据映射

无。`datafusion-scheduler-worker` 不定义 HTTP/RPC Provider；接口由 `datafusion-agent` 运行时应用层提供。

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TaskInstance` -> `TaskRequest` | 由 manager/master 侧运行时转换 | `pluginData.pluginType/pluginParam` 映射为 `pluginType/pluginParam` |
| `TaskRequest` -> `RunningTaskContext` | worker 接收请求后创建或复用上下文 | 幂等键为 `taskInstanceId` |
| `PluginTaskExecutor.submitTask` -> `TaskResult` | 插件返回执行结果 | worker 统一补齐 `taskInstanceId/flowInstanceId/taskName/submitMode` |
| `RunningTaskContext` -> `TaskResult` | 重复请求返回最近结果或当前状态 | 已有终态时直接返回终态；运行中按 `submitMode` 返回 `RUNNING` 或 `SUBMIT_SUCCESS` |
| `TaskResult` -> `TaskResultReporter.report` | worker 异步上报 manager/master | 上报实现位于 `datafusion-agent`，worker 只调用接口 |
| `RunningTaskContext` -> `WorkerTaskExecutionState` | agent 侧运行时实现转换 | 用于任务执行状态存储和恢复上报计划 |
| `WorkerTaskExecutionState` -> `PluginRunModeStateMapping` | 状态刷新计划调用插件状态映射 | 按 `pluginType + runMode` 映射为 `StatusEnum` |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `submitMode` | 不涉及 | `SubmitModeEnum` | `SYNC` / `ASYNC` | 替代含义不清晰的 `sync` |
| `taskState` | 不涉及 | `StatusEnum` | 使用 common-data 调度状态 | 提交响应只能返回 `RUNNING` 或 `SUBMIT_SUCCESS` |
| `taskData` | 不涉及 | `JsonNode` | manager/master 传入，worker 不解析结构 | 插件自行解释 |
| `pluginParam` | 不涉及 | `JsonNode` | manager/master 传入，worker 不解析结构 | 插件自行解释 |
| `runMode` | 状态存储 | `String` | 插件自行解释 | 终端运行模式大类，状态映射按 `pluginType + runMode` 路由 |
| `appId` | 状态存储 | `String` | 插件自行解释 | 终端任务 ID，不再区分本地进程 ID 和第三方任务 ID 字段 |
| `logPath` | 结果/状态存储 | `String` | agent/plugin 写入 | 优先表示 agent 本地日志路径；外部日志 URI 应放入 `result.pluginLogUri` |
| `result` | 结果/状态存储 | `JsonNode` | 插件写入 JSON 对象 | 推荐结构见下方 `TaskResultResult`；不得写入密码、完整 job JSON 或大体积日志正文 |

### 6.1 TaskResultResult

`TaskResult.result` 是 `JsonNode` 类型。新插件必须写入紧凑 JSON 对象，manager 侧优先读取 `result.message` 作为列表摘要。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | `String` | 是 | 简短执行说明或错误摘要 |
| `pluginType` | `String` | 否 | 插件类型，如 `SHELL`、`DATAX` |
| `runMode` | `String` | 否 | 运行模式，如 `LOCAL`、`K8S` |
| `pluginLogUri` | `String` | 否 | 第三方运行系统自身日志入口，如 Kubernetes、Yarn、对象存储、脚本自定义日志 |
| `agentLogPath` | `String` | 否 | agent 本地日志路径，通常与 `TaskResult.logPath` 相同 |
| `exitCode` | `Integer` | 否 | 本地进程退出码 |
| `errorCode` | `String` | 否 | 插件可读错误码 |
| `detail` | `Object` | 否 | 小体积扩展信息，不放密码和大日志正文 |

示例：

```json
{
  "message": "K8S DataX job submitted",
  "pluginType": "DATAX",
  "runMode": "K8S",
  "pluginLogUri": "oss://datafusion/logs/datax/task-1/",
  "agentLogPath": "/opt/datafusion-agent/logs/20260609/flow-1/task-1",
  "detail": {
    "namespace": "datafusion",
    "jobName": "df-datax-task-1"
  }
}
```

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
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 插件 SPI | 插件执行和状态映射 |
| `WorkerTaskExecutionState` / `WorkerTaskExecutionStateStore` | `datafusion-scheduler-worker` | 状态 SPI | 状态 envelope 和存储接口 |
| `TaskResultReporter` | `datafusion-scheduler-worker` | 上报 SPI | 结果上报端口 |
