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
| `TaskResult` | `Response` | worker 返回提交结果或异步上报结果 | `workerResult` | `WorkerResult` | 可选 | worker 执行结果，只承载 worker 侧运行信息 |
| `WorkerResult` | `ResponsePart` | `TaskResult.workerResult` | `outputVars` | `Map<String, Variable>` | 可选 | 输出变量 |
| `WorkerResult` | `ResponsePart` | `TaskResult.workerResult` | `workerId` | `String` | 可选 | worker ID，使用 manager 返回的 `Worker.id` |
| `WorkerResult` | `ResponsePart` | `TaskResult.workerResult` | `appId` | `String` | 可选 | 外部终端任务 ID，如 PID、Kubernetes Job name |
| `WorkerResult` | `ResponsePart` | `TaskResult.workerResult` | `workDirPath` | `String` | 可选 | agent 任务运行目录；manager 只通过该目录读取标准日志 |
| `WorkerResult` | `ResponsePart` | `TaskResult.workerResult` | `message` | `String` | 可选 | 简短执行说明或错误摘要 |
| `WorkerResult` | `ResponsePart` | `TaskResult.workerResult` | `pluginLogUri` | `String` | 可选 | 插件日志入口，本地文件、对象存储 URI 或第三方运行时 URI |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `id` | `String` | 必填 | worker ID |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `ip` | `String` | 必填 | worker IP |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `port` | `Integer` | 必填 | worker HTTP 端口 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `pluginTypes` | `List<String>` | 可选 | worker 支持的插件类型 |
| `Worker` | `Internal` | master/manager 维护 worker 节点 | `status` | `Integer` | 必填 | `STATUS_UP` / `STATUS_DOWN` |
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
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `result` | `JsonNode` | 可选 | 最近执行摘要，最终上报时映射为 `WorkerResult.message/pluginLogUri` |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `createTime` | `Long` | 必填 | 上下文创建时间 |
| `RunningTaskContext` | `Internal` | worker 记录运行中任务 | `updateTime` | `Long` | 必填 | 上下文更新时间 |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `taskInstanceId` | `String` | 必填 | 任务实例 ID |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `taskName` | `String` | 可选 | 任务名称 |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `pluginType` | `String` | 必填 | 插件类型 |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `runMode` | `String` | 可选 | 终端运行模式 |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `workId` | `String` | 可选 | worker 节点 ID |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `taskData` | `JsonNode` | 可选 | 渲染后的任务执行数据 |
| `WorkerTaskExecutionSnap` | `Internal` | worker 任务提交快照 | `pluginParam` | `JsonNode` | 可选 | 插件参数 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `taskInstanceId` | `String` | 必填 | 任务实例 ID |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `appId` | `String` | 可选 | 终端任务 ID |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `workDirPath` | `String` | 可选 | agent 任务运行目录；用于恢复和上报 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `status` | `StatusEnum` | 可选 | 最近任务状态 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `exitCode` | `Integer` | 可选 | 本地运行态诊断字段，仅用于 agent 状态映射和 `state.log`，不进入 `WorkerResult` |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `updateTime` | `Long` | 可选 | 状态更新时间 |
| `WorkerTaskExecutionState` | `Internal` | worker 任务运行态 envelope | `result` | `JsonNode` | 可选 | 本地轻量执行摘要，最终上报时映射为 `WorkerResult.message/pluginLogUri` |
| `WorkerListener` | `Port` | worker 生命周期端口 | `register` | `Worker -> Worker` | 必填 | agent 注册或重连恢复上线 |
| `WorkerListener` | `Port` | worker 生命周期端口 | `heartbeat` | `workerId,lastHeartbeatTime -> Worker` | 必填 | agent 心跳，按 workerId 更新上线和心跳时间 |
| `WorkerListener` | `Port` | worker 生命周期端口 | `offline` | `workerId -> Worker` | 必填 | agent 主动下线 |
| `WorkerListener` | `Port` | worker 生命周期端口 | `timeoutOffline` | `timeoutMs -> int` | 必填 | 将心跳超时 worker 标记下线 |
| `WorkerListener` | `Port` | worker 生命周期端口 | `getTaskInsByWorkerId` | `workerId -> List<TaskRequest>` | 必填 | agent 注册成功后获取属于自己的未完成任务清单 |
| `WorkerOperator` | `Port` | worker 人工操作端口 | `active` | `workerId -> Worker` | 必填 | 手工置为有效，不修改运行态状态 |
| `WorkerOperator` | `Port` | worker 人工操作端口 | `inactive` | `workerId -> Worker` | 必填 | 手工置为无效，不修改运行态状态 |
| `WorkerOperator` | `Port` | worker 人工操作端口 | `delete` | `workerId -> boolean` | 必填 | 真删除 worker 注册记录 |

## 4. API 数据映射

无。`datafusion-scheduler-worker` 不定义 HTTP/RPC Provider；接口由 `datafusion-agent` 运行时应用层提供。

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TaskInstance` -> `TaskRequest` | 由 manager/master 侧运行时转换 | `pluginData.pluginType/pluginParam` 映射为 `pluginType/pluginParam` |
| `TaskRequest` -> `RunningTaskContext` | worker 接收请求后创建或复用上下文 | 幂等键为 `taskInstanceId` |
| `PluginTaskExecutor.submitTask` -> `TaskResult` | 插件返回执行结果 | worker 统一补齐 `taskInstanceId/flowInstanceId/taskName`，worker 侧运行信息写入 `workerResult` |
| `RunningTaskContext` -> `TaskResult` | 重复请求返回最近结果或当前状态 | 已有终态时直接返回终态；运行中按 `submitMode` 返回 `RUNNING` 或 `SUBMIT_SUCCESS` |
| `TaskResult` -> `TaskResultReporter.report` | worker 异步上报 manager/master | 上报实现位于 `datafusion-agent`，worker 只调用接口 |
| `RunningTaskContext` -> `WorkerTaskExecutionSnap` | agent 侧运行时实现转换 | 用于保存提交快照和恢复上下文 |
| `RunningTaskContext` -> `WorkerTaskExecutionState` | agent 侧运行时实现转换 | 用于保存持续刷新的运行态 |
| `WorkerTaskExecutionSnap + WorkerTaskExecutionState` -> `PluginRunModeStateMapping` | 状态刷新计划先按 `snap.pluginType + snap.runMode` 路由，再调用状态映射 | `mapState` 只接收 `WorkerTaskExecutionState`；需要恢复上下文的实现自行读取提交快照 |
| `WorkerListener.getTaskInsByWorkerId` -> agent 状态恢复 | agent 注册成功后按 workerId 获取未完成任务清单 | agent 只恢复清单内任务，避免扫描全部任务运行目录产生脏上报 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `submitMode` | 不涉及 | `SubmitModeEnum` | `SYNC` / `ASYNC` | 替代含义不清晰的 `sync` |
| `taskState` | 不涉及 | `StatusEnum` | 使用 common-data 调度状态 | 提交响应只能返回 `RUNNING` 或 `SUBMIT_SUCCESS` |
| `taskData` | 不涉及 | `JsonNode` | manager/master 传入，worker 不解析结构 | 插件自行解释 |
| `pluginParam` | 不涉及 | `JsonNode` | manager/master 传入，worker 不解析结构 | 插件自行解释 |
| `runMode` | 提交快照 | `String` | 插件自行解释 | 终端运行模式大类，状态映射按 `pluginType + runMode` 路由 |
| `appId` | 运行态存储 | `String` | 插件自行解释 | 终端任务 ID，不再区分本地进程 ID 和第三方任务 ID 字段 |
| `workDirPath` | 结果/状态存储 | `String` | agent/plugin 写入 | 只表示 agent 任务运行目录；标准日志由 `TaskRuntimeFiles` 拼接；插件日志 URI 放入 `workerResult.pluginLogUri` |
| `result` | 结果/状态存储 | `JsonNode` | agent/plugin 写入 | 只保存轻量执行摘要；不得写入密码、完整 job JSON、大体积日志正文或本地退出码；上报时映射为 `WorkerResult` |

### 6.1 WorkerResult

`TaskResult.workerResult` 是结构化 `WorkerResult`。`TaskResult` 只保存任务身份和状态；worker ID、终端任务 ID、任务运行目录、输出变量和插件日志入口都放入 `WorkerResult`。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `outputVars` | `Map<String, Variable>` | 否 | 输出变量 |
| `workerId` | `String` | 否 | worker ID |
| `appId` | `String` | 否 | 外部终端任务 ID |
| `workDirPath` | `String` | 否 | agent 任务运行目录 |
| `message` | `String` | 否 | 简短执行说明或错误摘要 |
| `pluginLogUri` | `String` | 否 | 插件日志入口 |

`WorkerResult` 不包含 `submitMode`、`pluginType`、`runMode`、`exitCode`、`detail` 或任意开放 JSON 扩展字段。`exitCode` 如需保留，只能作为 agent 本地 `.state` / `state.log` 的诊断字段。

示例：

```json
{
  "workerId": "f4b31edf-1f48-4d0c-ba1b-de40ccca6b41",
  "appId": "df-datax-task-1",
  "workDirPath": "/opt/datafusion/task-runtime/20260623/flow-1/task-1",
  "message": "K8S DataX job submitted",
  "pluginLogUri": "k8s://datafusion/jobs/df-datax-task-1"
}
```

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `TaskRequest` | `datafusion-common-data` | 通信 DTO | master/manager/worker/agent 共享 |
| `TaskResult` | `datafusion-common-data` | 通信 DTO | master/manager/worker/agent 共享 |
| `WorkerResult` | `datafusion-common-data` | 通信 DTO 子结构 | `TaskResult.workerResult` 的结构化结果 |
| `TaskRuntimeFiles` | `datafusion-common-data` | 任务运行文件工具 | agent 标准任务日志文件名和路径拼接 |
| `Worker` | `datafusion-common-data` | 通信/节点模型 | master/manager 维护 worker 信息 |
| `WorkerListener` | `datafusion-scheduler-master` | 生命周期端口 | agent 注册、心跳、下线、超时下线和任务清单恢复 |
| `WorkerOperator` | `datafusion-scheduler-master` | 人工操作端口 | UI 有效、无效和删除 |
| `StatusEnum` | `datafusion-common-data` | 状态枚举 | 任务状态 |
| `ActionType` | `datafusion-common-data` | 动作枚举 | 幂等动作类型 |
| `SubmitModeEnum` | `datafusion-common-data` | 提交模式枚举 | 区分同步提交和异步提交 |
| `datafusion-plugin-api` | `datafusion-plugin-api` | 插件 API 依赖 | worker 侧插件执行器可复用插件接口和模型 |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 插件 SPI | 插件执行和状态映射 |
| `WorkerTaskExecutionSnap` / `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | 状态 SPI | 提交快照、运行态 envelope 和存储接口 |
| `TaskResultReporter` | `datafusion-scheduler-worker` | 上报 SPI | 结果上报端口 |
