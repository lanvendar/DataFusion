# 调度 Worker 数据结构定义

> 本文档是 `datafusion-scheduler-worker` 的字段、类型、校验和层间映射事实源。Worker 框架不定义数据库表，运行时持久化由 `datafusion-agent` 实现。

## 1. 数据库数据模型

无。`datafusion-scheduler-worker` 是调度框架 jar，不直接持久化数据库表。

## 2. Java 后端数据模型

### 2.1 通信模型

| 对象 | 类型 | 场景 | 字段 | 字段类型 | 校验 / 规则 | 说明 |
|------|------|------|------|----------|-------------|------|
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `taskInstanceId` | `String` | 必填 | 任务实例 ID，worker 幂等键 |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `taskName` | `String` | 可选 | 任务名称 |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `taskState` | `StatusEnum` | 可选 | master 当前任务状态 |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `taskData` | `JsonNode` | 可选 | 渲染后的任务执行数据，插件自行解释 |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `pluginType` | `String` | `submitTask` 必填 | 插件路由键 |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `pluginParam` | `JsonNode` | 可选 | 插件配置参数，插件自行解释 |
| `TaskRequest` | Request | master/manager 请求 worker 操作任务 | `submitMode` | `SubmitModeEnum` | 可选，默认 `SYNC` | 提交模式 |
| `TaskRequest` | Request | 状态同步或控制请求上下文补齐 | `workerResult` | `WorkerResult` | 可选 | worker、外部任务和运行目录信息 |
| `TaskResult` | Response | worker 返回提交结果或异步上报结果 | `taskInstanceId` | `String` | 可选 | 任务实例 ID |
| `TaskResult` | Response | worker 返回提交结果或异步上报结果 | `flowInstanceId` | `String` | 可选 | 流程实例 ID |
| `TaskResult` | Response | worker 返回提交结果或异步上报结果 | `taskName` | `String` | 可选 | 任务名称 |
| `TaskResult` | Response | worker 返回提交结果或异步上报结果 | `taskState` | `StatusEnum` | 可选 | 任务状态 |
| `TaskResult` | Response | worker 返回提交结果或异步上报结果 | `submitMode` | `SubmitModeEnum` | 可选，默认 `SYNC` | worker 端提交模式 |
| `TaskResult` | Response | worker 返回提交结果或异步上报结果 | `workerResult` | `WorkerResult` | 可选 | worker 侧运行信息 |
| `WorkerResult` | ResponsePart | `TaskResult.workerResult` | `outputVars` | `Map<String, Variable>` | 可选 | 输出变量 |
| `WorkerResult` | ResponsePart | `TaskResult.workerResult` | `workerId` | `String` | 可选 | manager 返回的 `Worker.id` |
| `WorkerResult` | ResponsePart | `TaskResult.workerResult` | `appId` | `String` | 可选 | 外部终端任务 ID，如 PID、Kubernetes Job name |
| `WorkerResult` | ResponsePart | `TaskResult.workerResult` | `workDirPath` | `String` | 可选 | agent 任务运行目录，manager 通过该目录读取标准日志 |
| `WorkerResult` | ResponsePart | `TaskResult.workerResult` | `message` | `String` | 可选 | 简短执行说明或错误摘要 |
| `WorkerResult` | ResponsePart | `TaskResult.workerResult` | `pluginLogUri` | `String` | 可选 | 插件日志入口 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `id` | `String` | 必填 | worker ID |
| `Worker` | Internal | master/manager 维护 worker 节点 | `workerCode` | `String` | 注册时必填 | worker 稳定编码 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `ip` | `String` | 必填 | worker IP |
| `Worker` | Internal | master/manager 维护 worker 节点 | `port` | `Integer` | 必填 | worker HTTP 端口 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `pluginTypes` | `List<String>` | 可选 | worker 支持的插件类型 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `status` | `Integer` | 必填 | `STATUS_UP` / `STATUS_DOWN` |
| `Worker` | Internal | master/manager 维护 worker 节点 | `hostName` | `String` | 必填 | worker 主机名 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `registerTime` | `Long` | 可选 | 首次注册时间 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `lastHeartbeatTime` | `Long` | 可选 | 最近心跳时间 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `workerLogDir` | `String` | 可选 | worker 服务日志目录 |
| `Worker` | Internal | master/manager 维护 worker 节点 | `updateTime` | `Long` | 可选 | 最近更新时间 |

### 2.2 运行态模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|------|------|------|----------|----------|------|
| `RunningTaskContext` | worker 进程内运行上下文 | `snapshot` | `WorkerTaskExecutionSnap` | 提交后到终态清理 | 保存提交快照字段 |
| `RunningTaskContext` | worker 进程内运行上下文 | `executionState` | `WorkerTaskExecutionState` | 提交后到终态清理 | 保存运行态字段 |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `flowInstanceId` | `String` | 提交后固定 | 流程实例 ID |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `taskInstanceId` | `String` | 提交后固定 | 任务实例 ID |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `taskName` | `String` | 提交后固定 | 任务名称 |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `pluginType` | `String` | 提交后固定 | 插件类型 |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `runMode` | `String` | 插件提交解析后确定 | 终端运行模式 |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `workerId` | `String` | 提交后固定 | worker 节点 ID |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `taskData` | `JsonNode` | 提交后固定 | 渲染后的任务执行数据 |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `pluginParam` | `JsonNode` | 提交后固定 | 插件参数 |
| `WorkerTaskExecutionSnap` | 可持久化提交快照 | `submitMode` | `SubmitModeEnum` | 提交后固定 | 提交模式 |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `taskInstanceId` | `String` | 提交后固定 | 任务实例 ID |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `workerId` | `String` | 提交后固定 | worker 节点 ID |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `appId` | `String` | 提交后写入 | 终端任务 ID |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `workDirPath` | `String` | 提交后写入 | agent 任务运行目录 |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `status` | `StatusEnum` | 状态刷新时更新 | 最近任务状态 |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `exitCode` | `Integer` | 本地进程终态后写入 | 本地诊断字段，不进入 `WorkerResult` |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `updateTime` | `Long` | 状态刷新时更新 | 状态更新时间 |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `result` | `JsonNode` | 插件写入 | 轻量执行摘要，上报时映射到 `WorkerResult.message/pluginLogUri` |
| `WorkerTaskExecutionState` | 可持久化运行态 envelope | `outputVars` | `Map<String, Variable>` | 插件写入 | 输出变量，上报时映射到 `WorkerResult.outputVars` |

### 2.3 端口模型

| 对象 | 类型 | 方法 / 字段 | 规则 | 说明 |
|------|------|-------------|------|------|
| `WorkerListener` | Port | `register(Worker)` | agent 注册或重连恢复上线 | manager 侧实现 |
| `WorkerListener` | Port | `heartbeat(workerId,lastHeartbeatTime)` | 按 workerId 更新上线和心跳时间 | manager 侧实现 |
| `WorkerListener` | Port | `offline(workerId)` | agent 主动下线 | manager 侧实现 |
| `WorkerListener` | Port | `timeoutOffline(timeoutMs)` | 将心跳超时 worker 标记下线 | manager 侧实现 |
| `WorkerListener` | Port | `getTaskInsByWorkerId(workerId)` | 返回未完成任务清单 | agent 启动恢复使用 |
| `WorkerOperator` | Port | `active(workerId)` | 手工置为有效 | 不修改运行态状态 |
| `WorkerOperator` | Port | `inactive(workerId)` | 手工置为无效 | 不修改运行态状态 |
| `WorkerOperator` | Port | `delete(workerId)` | 删除 worker 注册记录 | manager 侧实现 |

## 3. API 数据映射

无。`datafusion-scheduler-worker` 不定义 HTTP/RPC Provider；接口由 `datafusion-agent` 运行时应用层提供。

## 4. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TaskInstance` -> `TaskRequest` | manager/master 侧运行时转换 | `pluginData.pluginType/pluginParam` 映射为 `pluginType/pluginParam` |
| `TaskRequest` -> `RunningTaskContext` | worker 接收请求后创建或复用上下文 | 幂等键为 `taskInstanceId`；worker 信息来自 `request.workerResult` |
| `PluginTaskExecutor.validateTaskRequest` -> `TaskRequest` | 插件在提交前校验任务请求 | 校验失败返回 `SUBMIT_FAILURE` |
| `PluginTaskExecutor.submitTask` -> `TaskResult` | 插件返回执行结果 | worker 补齐任务身份、`submitMode` 和 `workerResult.workerId` |
| `RunningTaskContext` -> `TaskResult` | 重复请求返回最近结果或当前状态 | 已有终态直接返回终态；无状态时 `ASYNC` 返回 `SUBMITTING`，`SYNC` 返回 `RUNNING` |
| `TaskResult` -> `TaskResultReporter.report` | worker 异步上报 manager/master | 上报实现位于 `datafusion-agent` |
| `RunningTaskContext` -> `WorkerTaskExecutionSnap` | agent 侧运行时转换 | 保存提交快照和恢复上下文 |
| `RunningTaskContext` -> `WorkerTaskExecutionState` | agent 侧运行时转换 | 保存持续刷新的运行态 |
| `WorkerTaskExecutionSnap + WorkerTaskExecutionState` -> `PluginRunModeStateMapping` | 状态刷新计划按 `snap.pluginType + snap.runMode` 路由 | 状态映射器读取运行态，必要上下文从提交快照恢复 |
| `WorkerListener.getTaskInsByWorkerId` -> agent 状态恢复 | agent 注册成功后按 workerId 获取未完成任务清单 | agent 只恢复清单内任务 |

## 5. 状态 / 枚举模型

| 字段 / 枚举 | 存储位置 | Java 类型 | 规则 | 说明 |
|-------------|----------|-----------|------|------|
| `submitMode` | `TaskRequest` / `TaskResult` / `WorkerTaskExecutionSnap` | `SubmitModeEnum` | `SYNC` / `ASYNC`，默认 `SYNC` | 只表达提交方式 |
| `taskState` / `status` | `TaskResult` / `WorkerTaskExecutionState` | `StatusEnum` | 使用 common-data 调度状态 | 插件私有状态必须映射后再上报 |
| `taskData` | `TaskRequest` / `WorkerTaskExecutionSnap` | `JsonNode` | worker 不解析结构 | 插件自行解释 |
| `pluginParam` | `TaskRequest` / `WorkerTaskExecutionSnap` | `JsonNode` | worker 不解析结构 | 插件自行解释 |
| `runMode` | `WorkerTaskExecutionSnap` | `String` | 插件自行解释 | 状态映射按 `pluginType + runMode` 路由 |
| `appId` | `WorkerResult` / `WorkerTaskExecutionState` | `String` | 插件写入 | 终端任务 ID |
| `workDirPath` | `WorkerResult` / `WorkerTaskExecutionState` | `String` | agent/plugin 写入 | agent 任务运行目录 |
| `result` | `WorkerTaskExecutionState` | `JsonNode` | agent/plugin 写入 | 不写密码、完整 job JSON、大体积日志正文或本地退出码 |

`WorkerResult` 不包含 `submitMode`、`pluginType`、`runMode`、`exitCode`、`detail` 或任意开放 JSON 扩展字段。`exitCode` 只作为 agent 本地 `.state` / `state.log` 诊断字段。

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

## 6. 复用对象

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
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 插件 SPI | 插件执行和状态映射 |
| `WorkerTaskExecutionSnap` / `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | 状态 SPI | 提交快照、运行态 envelope 和存储接口 |
| `TaskResultReporter` | `datafusion-scheduler-worker` | 上报 SPI | 结果上报端口 |
