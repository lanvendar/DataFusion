# 调度 Master 数据定义

本文定义 `datafusion-scheduler-master` 当前调度框架对象、通信对象和端口模型。运行机制见 [scheduler-master-design.md](./scheduler-master-design.md)。

## 数据库数据模型

无。

`datafusion-scheduler-master` 不定义库表。持久化由接入方实现 `MasterStorage`、`WorkerStorage` 及其子存储端口。

## Java 数据模型

### 调度定义

| 类 | 字段 | 说明 |
|----|------|------|
| `TriggerInfo` | `payloadId` / `version` / `scheduleFlag` | 被调度对象、版本和是否调度 |
| `TriggerInfo` | `triggerId` / `triggerType` / `triggerExpression` / `triggerPolicy` | 触发器定义 |
| `TriggerInfo` | `startTime` / `endTime` | 生效时间范围 |
| `FlowInfo` | `flowId` / `flowName` / `flowType` / `version` | 流程定义 |
| `FlowInfo` | `flowParam` / `depEventIds` / `eventId` / `pluginData` | 流程参数、依赖事件、产出事件、组件配置 |
| `TaskInfo` | `taskId` / `flowId` / `taskType` / `taskName` / `taskDesc` | 任务定义 |
| `TaskInfo` | `taskParam` / `definition` / `depEventIds` / `eventId` / `pluginData` / `isAble` | 任务参数、JSON 定义、事件、组件和启用状态 |
| `TaskLink` | `id` / `startId` / `endId` | 任务 DAG 边，继承 `Link<String>` |

### 运行实例

| 类 | 字段 | 说明 |
|----|------|------|
| `TriggerInstance` | `payloadId` / `version` / `instanceId` / `scheduleTime` / `scheduleSign` / `state` | 触发实例 |
| `FlowInstance` | `instanceId` / `flowType` / `scheduleTime` / `version` / `startTime` / `endTime` / `state` | 流程运行状态 |
| `FlowInstance` | `flowId` / `flowName` / `flowParam` / `depEventIds` / `eventId` / `pluginData` | 流程定义快照 |
| `TaskInstance` | `instanceId` / `flowInstanceId` / `taskType` / `taskId` / `taskName` / `taskDesc` / `state` | 任务运行状态 |
| `TaskInstance` | `startTime` / `endTime` / `costTime` / `retryTimes` / `maxRetryTimes` / `retryInterval` / `timeout` | 时间、重试和超时 |
| `TaskInstance` | `lastInstanceIds` / `nextInstanceIds` / `depEventIds` / `eventId` | DAG 和事件 |
| `TaskInstance` | `taskParam` / `taskData` / `taskResult` / `pluginData` / `isAble` | 参数、渲染数据、执行结果、组件和启用状态 |
| `GlobalEvent` | `id` / `type` / `flowInstanceId` / `taskInstanceId` / `eventTime` / `timeSegment` / `beginTime` / `endTime` | 业务事件 |

### 通信与消息

| 类 | 字段 | 说明 |
|----|------|------|
| `ParamData` | `vars` | 变量映射，key 为变量编码 |
| `Variable` | `name` / `type` / `value` | 变量值对象 |
| `PluginData` | `pluginType` / `pluginName` / `runMode` / `pluginParam` | 插件配置 |
| `TaskRequest` | `flowInstanceId` / `taskInstanceId` / `taskName` / `taskState` / `taskData` | 下发 worker 的任务数据 |
| `TaskRequest` | `pluginType` / `runMode` / `pluginParam` / `submitMode` / `workerResult` | 插件路由、提交模式和 worker 上下文 |
| `TaskResult` | `taskInstanceId` / `flowInstanceId` / `taskName` / `taskState` / `submitMode` / `workerResult` | worker 返回结果 |
| `Worker` | `id` / `workerCode` / `ip` / `port` / `pluginTypes` / `status` / `hostName` / `registerTime` / `lastHeartbeatTime` / `workerLogDir` / `updateTime` | worker 注册信息 |
| `WorkerResult` | `outputVars` / `workerId` / `appId` / `workDirPath` / `message` / `pluginLogUri` | worker 执行上下文和输出 |
| `FlowMsg` | `flowInstanceId` / `flowId` / `version` / `scheduleTime` / `taskState` / `flowTargetState` / `restoreTaskState` / `actionType` / `isManualAction` | FlowActor 消息 |
| `TaskMsg` | `taskId` / `taskInstanceId` / `flowInstanceId` / `flowParamData` / `version` / `scheduleTime` / `actionType` / `isManualAction` / `taskResult` | TaskActor 消息 |

## 端口模型

| 端口 | 方法 | 说明 |
|------|------|------|
| `MasterStorage` | `getTriggerStorage` / `getFlowStorage` / `getTaskStorage` / `getEventStorage` / `invalidateSchedulerInfo` | 调度存储聚合 |
| `SchedulerTrigger` | `fetchInit` / `dispatchSubmit` / `cleanInitializationInstance` / `killDelay` | Trigger 线程驱动流程初始化、提交和未执行初始化实例精准清理 |
| `TriggerStorage` | `getAllScheduledTriggerInfo` / `getTriggerInfo` / `saveTriggerInfo` / `getTriggerInstance` / `getLastTriggerInstance` / `saveTriggerInstance` | 触发器存储 |
| `FlowStorage` | `getFlowInfo` / `getAllFlowInfo` / `getInstanceById` / `saveInstance` / `removeInstanceById` / `getAvailableInstance` / `getLastInstance` | 流程存储 |
| `TaskStorage` | `getTaskInfo` / `getTaskInfoByFlowId` / `getTaskInfoLink` / `getInstanceById` / `saveInstance` / `removeInstanceById` / `getTaskInsIdsByFlowInsId` / `removeTaskInsByFlowInsId` | 任务存储 |
| `EventStorage` | `save` / `loadByEventId` / `loadByEventKey` | 事件存储 |
| `MasterTaskOperator` | `submitTask` / `stopTask` / `killTask` / `finishTask` | master 操作 worker |
| `TaskResultHandler` | `asyncHandle` | worker 异步结果回调 |
| `WorkerStorage` | `getWorker` / `getWorkers` / `updateWorker` / `register` / `heartbeat` / `offline` / `offlineAllWorkers` / `timeoutOffline` / `active` / `inactive` / `delete` / `getTaskInsByWorkerId` | worker 存储 |
| `WorkerListener` | `register` / `heartbeat` / `offline` / `timeoutOffline` / `getTaskInsByWorkerId` / `getWorker` / `lookupWorker` | agent 生命周期和 worker 选择 |
| `WorkerOperator` | `active` / `inactive` / `delete` | worker 人工操作，均返回操作是否成功 |

## 前端数据模型

无。

## 枚举和值域

| 类型 | 值 |
|------|----|
| `TriggerTypeEnum` | `CRON` / `INTERVAL` |
| `TriggerPolicyEnum` | `EXECUTE_ONCE` / `SERIAL_WAIT` / `PARALLEL` / `DISCARD_NEW` / `DISCARD_OLD` |
| `FlowTypeEnum` | `STREAM` / `BATCH` |
| `StatusEnum` | 初始化、等待、提交、运行、停止、强杀、重启、失败转移、未知等状态 |
| `ActionType` | `INIT` / `REINIT` / `WAIT` / `SUBMIT` / `RUN` / `STOP` / `RESTART` / `KILL` / `ENFORCE_SUCCESS` |
| `SubmitModeEnum` | `SYNC` / `ASYNC` |

## 数据映射

1. `TriggerInfo` 生成 `TriggerInstance`。
2. `TriggerInstance` 初始化 `FlowInstance` 和 `TaskInstance`。
3. `TaskInfo` 与 `TaskLink` 转换为任务实例 DAG。
4. `TaskInstance` 通过 `MasterTaskOperator` 下发 worker。
5. worker 返回 `TaskResult`，由 `TaskResultHandler` 转换为 Actor 消息。
6. 任务或流程完成后生成 `GlobalEvent`，用于下游事件依赖。
