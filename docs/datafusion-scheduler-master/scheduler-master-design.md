# 调度 Master 设计

`datafusion-scheduler-master` 是调度 master 框架层，只作为 jar 包提供调度核心能力。它负责触发器、流程实例、任务实例、事件依赖、Actor 状态机、worker 节点管理抽象和 master 侧任务操作抽象。运行时装配、HTTP、持久化和外部系统集成由 `datafusion-manager` 承担。

## 边界

`datafusion-scheduler-master` / `datafusion-scheduler-worker` 是调度框架层，仅提供核心能力、接口契约、状态机、模型和默认实现。

`datafusion-manager` / `datafusion-agent` 是运行时应用层，负责 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、部署和外部系统集成。

禁止反向依赖：框架层不得依赖运行时应用层；运行时应用层可以依赖并装配框架层。

## 职责

- 生成 `TriggerInstance`。
- 初始化 `FlowInstance` 和 `TaskInstance`。
- 根据 DAG 和全局事件依赖推进任务状态。
- 根据 worker 状态和插件能力选择可用 worker。
- 通过 `MasterTaskOperator` 向 worker 发起任务运行、停止、强制停止和完成清理请求。
- 通过 `TaskResultHandler` 消费 worker 异步上报的任务结果。
- 聚合 task 状态并推进 flow 状态。

## 模块依赖

```text
datafusion-scheduler-master
    -> datafusion-common-data
    -> datafusion-common

datafusion-manager
    -> datafusion-scheduler-master
    -> datafusion-common-spring
```

模型归属决策：

- `TaskRequest`、`TaskResult`、worker 注册/心跳相关 DTO 下沉到 `datafusion-common-data`，作为 master、manager、worker、agent 之间共享的通信模型。
- `WorkerManager`、`WorkerStorage`、worker 选择策略和 worker 生命周期管理接口保留在 `datafusion-scheduler-master`。
- `datafusion-scheduler-worker` 和 `datafusion-agent` 不依赖 `datafusion-scheduler-master`。

该划分保证通信模型可复用，同时避免 worker 侧为了复用 DTO 产生 `agent -> scheduler-master` 或 `scheduler-worker -> scheduler-master` 依赖。

## 核心接口

### MasterTaskOperator

`MasterTaskOperator` 是 master 侧操作 worker 的抽象，命名为 `Operator` 是为了避免与 `DispatchTriggerThread` 这类调度触发线程混淆。

```java
public interface MasterTaskOperator {
    TaskResult submitTask(TaskInstance taskIns) throws SchedulerException;
    TaskResult stopTask(TaskInstance taskIns) throws SchedulerException;
    TaskResult killTask(TaskInstance taskIns) throws SchedulerException;
    TaskResult finishTask(TaskInstance taskIns) throws SchedulerException;
}
```

- `submitTask`：提交任务到 worker。
- `stopTask`：请求 worker 正常停止任务。
- `killTask`：请求 worker 强制停止任务。
- `finishTask`：任务结束后的 worker 侧收尾动作。

`MasterTaskOperator` 只表达远程任务操作，不包含插件执行逻辑。`submitTask` 返回值只表示提交结果，不表示任务最终成功或失败。

### TaskResultHandler

`TaskResultHandler` 负责消费 worker 异步上报的任务结果，并推进 master 内部状态机。

```java
public interface TaskResultHandler {
    boolean asyncHandle(TaskResult result);
}
```

当前实现类为 `TaskAction`。`TaskResult.taskState` 到 actor 动作的转换规则：

- `RUNNING`、`RUN_SUCCESS`、`RUN_FAILURE` -> `ActionType.RUN`
- `STOP_SUCCESS`、`STOP_FAILURE` -> `ActionType.STOP`
- `KILLED` -> `ActionType.KILL`
- `ENFORCE_SUCCESS` -> `ActionType.ENFORCE_SUCCESS`

## 调度链路

```text
Master 生成 TriggerInstance
    -> 初始化 FlowInstance / TaskInstance
    -> TaskActor 检查 DAG 和事件依赖
    -> MasterTaskOperator.submitTask
    -> worker 返回提交结果
    -> worker 异步上报运行结果
    -> TaskResultHandler.asyncHandle
    -> TaskActor / FlowActor 推进状态
```

## 提交语义

提交模式使用 `submitMode`，替代含义容易混淆的 `sync`。

- `SYNC`：同步提交。worker 返回前已确认任务进入运行态，提交响应状态为 `RUNNING`。
- `ASYNC`：异步提交。worker 只确认任务已接收或已排队，提交响应状态为 `SUBMIT_SUCCESS`。

无论 `submitMode` 是 `SYNC` 还是 `ASYNC`，`RUN_SUCCESS` / `RUN_FAILURE` 都必须由 worker 异步上报，不能在提交响应中表达。

## Worker 管理

`datafusion-scheduler-master` 只定义 worker 节点模型、选择策略、存储接口和生命周期语义，不直接绑定 Nacos、Kubernetes、Consul、Redis 或数据库等具体发现实现。

worker 记录至少包含：

- `id`
- `ip`
- `port`
- `hostName`
- `pluginTypes`
- `status`
- `registerTime`
- `lastHeartbeatTime`
- `updateTime`

时间字段规则：

- `registerTime`：节点首次注册时间，同一 `workerId` 重复注册不覆盖。
- `lastHeartbeatTime`：最近一次心跳时间，注册和心跳时更新。
- `updateTime`：节点记录最近更新时间，注册、心跳、下线、插件变更或状态变更时更新。

可调度判断由 manager 运行时完成，但 master 侧选择策略应以以下信息为输入：

- worker 状态为 `STATUS_UP`。
- 心跳未超时。
- `pluginTypes` 包含任务需要的 `pluginType`。
- 后续可扩展负载、资源水位、租户隔离、黑名单和历史失败率。

## 任务容错

任务状态保存与实际执行任务无法保证原子性，master 发起重试、停止、强制停止和 failover 时必须携带稳定的幂等标识。

推荐幂等键：

```text
taskInstanceId + actionType
```

worker 失联处理：

```text
检测 worker 心跳超时
    -> 标记 worker 为 STATUS_DOWN
    -> 查询该 worker 上未完成任务
    -> 标记任务进入 FAIL_OVER 或 RUN_FAILURE
    -> 按任务重试策略重新选择 worker
    -> 重新提交任务
```

第一版可以按任务重试策略做 failover；无法确认外部任务状态时必须保守处理，避免误报成功。

## Master 重启运行态恢复

### 背景

`ActorSystem` 是 Master 进程内存状态。Manager / Master 重启后，数据库中的实时流程实例和任务实例仍然存在，但内存中的 `FlowActor`、`TaskActor`、父子关系和 `FlowActor` 内部任务状态缓存会丢失。

现有启动恢复只恢复触发器调度：

```text
SchedulerMasterLifecycle.reloadSchedules
    -> TriggerStorage.getAllScheduledTriggerInfo
    -> MasterService.addSchedule
```

该链路不会恢复已经生成的未完成 `FlowInstance` / `TaskInstance` 的 Actor 运行态。因此实例操作接口直接通知 `actorSystem` 时，可能出现数据库中实例存在但 Actor 不存在的情况。

### 恢复入口

对外恢复入口封装到 `MasterService.reloadSchedules()`。Manager 启动生命周期只调用该入口，不再在
`SchedulerMasterLifecycle` 中直接查询 `TriggerStorage` 并循环调用 `addSchedule`。

`MasterService.reloadSchedules()` 负责串联两类恢复：

```text
MasterService.reloadSchedules
    -> FlowAction 清理初始化阶段实例
    -> 恢复 trigger 调度计划
    -> FlowAction.reloadFlows
```

初始化阶段实例清理和运行态恢复入口都放在 `FlowAction`。`MasterService.reloadSchedules()` 只作为启动恢复总流程编排者：

原因：

- 恢复是由未完成流程实例发起的，流程是运行态恢复的聚合根。
- `FlowAction` 已持有 `TaskAction`，可以先恢复 `FlowActor`，再委托任务域恢复 `TaskActor`。
- `FlowActor` 是 `TaskActor` 的父 Actor，恢复顺序必须先流程后任务。
- `INITIALIZING` / `INIT_SUCCESS` 属于 `StatusPhaseEnum.INITIALIZATION` 阶段，实例可删除；清理属于流程实例生命周期修正，应由流程域负责查询、删除和记录清理结果。

`TaskAction` 不建议提供独立的全局 reload 入口，但建议提供任务域内的恢复能力，供 `FlowAction` 调用：

```text
FlowAction.reloadFlows
    -> 查询未完成 FlowInstance
    -> createFlowActor(flowInstanceId)
    -> taskAction.reloadTasks(flowInstanceId)
    -> 按状态决定是否发送恢复消息
```

`TaskAction` 中保留或新增的职责应限于：

- 按 `flowInstanceId` 查询任务实例。
- 为每个任务实例创建 `TaskActor`，并保持其父 Actor 为对应 `FlowActor`。
- 根据任务实例状态构造需要继续自动推进的 `TaskMsg`。

`TaskAction` 不应自行扫描所有流程实例，也不应直接决定流程级恢复策略，避免形成两个恢复入口。

### 恢复数据范围

第一版恢复只处理实时实例表，不处理历史实例表。历史实例表表示已归档运行记录，不参与 Actor 恢复、自动补偿和手工操作恢复。

恢复候选流程：

```text
status not in (RUN_SUCCESS, ENFORCE_SUCCESS)
```

- `RUN_SUCCESS`、`ENFORCE_SUCCESS`：不恢复，已完成。
- `INITIALIZING`、`INIT_SUCCESS`：属于初始化阶段实例，恢复调度计划前清理，不进入运行态 Actor 恢复。
- `RUN_FAILURE`、`SUBMIT_FAILURE`、`STOP_SUCCESS`、`STOP_FAILURE`、`KILLED`、`UNKNOWN`：只创建 Actor，默认不自动发送动作，保留给手工操作。
- `WAIT_DEPENDENT`、`SUBMITTING`、`SUBMIT_SUCCESS`、`RUNNING`、`STOPPING`、`KILLING`、`ENFORCING_SUCCESS`、`RESTARTING`：创建 Actor，并按状态恢复自动动作或等待后续上报。

恢复候选任务：

```text
flow_instance_id in recoverable flow instances
```

任务是否发送恢复消息由任务自身状态决定。

### 初始化阶段实例清理

`INITIALIZING` 和 `INIT_SUCCESS` 同属 `StatusPhaseEnum.INITIALIZATION` 阶段，表示实例还处于初始化生命周期：

- `INITIALIZING`：`fetchInit` 未完成，流程实例或任务实例可能不完整。
- `INIT_SUCCESS`：`fetchInit` 已完成，流程实例和任务实例已创建，但尚未完成 `dispatchSubmit`。

这两类状态不恢复为运行态 Actor，也不直接调用 `dispatchSubmit`。原因：

- 直接 `dispatchSubmit` 会绕过 dispatch 延迟队列，可能在 `scheduleTime` 未到时提前执行。
- 直接发送 `WAIT` 会绕过原始 `TriggerInstance -> DispatchTriggerThread -> dispatchSubmit` 生命周期。
- 半初始化实例恢复成本高，且容易产生 FlowActor / TaskActor 顺序问题。

恢复策略：

```text
MasterService.reloadSchedules
    -> FlowAction.cleanInitializationInstances
        -> 查询实时表中状态为 INITIALIZING / INIT_SUCCESS 的 FlowInstance
        -> 记录这些实例的 flowId、publishVersion
        -> 删除这些 FlowInstance 及其下属 TaskInstance
        -> 返回清理结果
    -> 恢复 trigger 调度计划
```

清理后调度计划恢复使用删除前记录的最小调度时间重建同一批实例：

```text
FlowAction.cleanInitializationInstances()
    -> cleanedScheduleTime
    -> MasterService.addSchedule(triggerInfo, cleanedScheduleTime, true)
```

`included=true` 表示重新生成被清理的初始化阶段实例本身，避免重启后跳过这一次应调度批次。
如果没有清理基准时间，则查询实时表最新流程实例，并使用其 `scheduleTime` 和 `included=false` 从下一批继续恢复。
如果没有任何实时实例，则按普通调度恢复逻辑以当前时间或 `TriggerInfo.startTime` 作为基准恢复。

该策略的业务语义：

- 初始化阶段实例视为重启前未真正开始执行，可以丢弃。
- 重启后会重建被清理的初始化阶段实例，保证该批次不跳过。
- 后续调度从实时表最新实例的 `scheduleTime` 之后继续，避免重复实例和提前执行。

实现约束：

- 清理只处理实时表，不处理历史表。
- 删除流程实例前必须先删除其下属任务实例。
- 清理和重新生成调度实例必须保持幂等。
- 正常情况下由 `FlowAction.cleanInitializationInstances()` 在删除前记录同一 `flowId + publishVersion` 下被清理实例的最小
  `scheduleTime`，作为恢复基准时间。
- 清理结果中的 `scheduleTime` 恢复时使用 `included=true`，重建被清理的同一批次。
- 无清理结果时，恢复基准优先使用实时表最新流程实例的 `scheduleTime` 和 `included=false`。
- 如果清理结果和最新实时实例的 `scheduleTime` 均异常为空或小于等于 0，记录日志并退回普通调度恢复基准，避免启动失败。

### 状态恢复原则

恢复分为两类：

1. 只创建 Actor。
2. 创建 Actor 后发送自动恢复消息。

只创建 Actor 适用于“手工前置状态”或终态失败状态。此类状态表示系统不应在重启后擅自继续推进，需要等待用户通过实例操作接口提交明确动作。

发送自动恢复消息适用于“自动前置状态”或过渡状态。此类状态表示重启前系统正在推进调度生命周期，重启后应继续补偿推进。

建议优先级：

```text
成功终态 -> 不恢复
自动/过渡状态 -> 创建 Actor 并发送自动恢复消息
失败/停止终态 -> 只创建 Actor
手工前置状态 -> 只创建 Actor
无法判断 -> 创建 Actor，不发送动作，记录日志
```

### 流程状态恢复

| 流程状态 | 恢复动作 | 说明 |
|----------|----------|------|
| `INITIALIZING` | 清理实时实例 | 初始化阶段实例，恢复调度计划前删除并从后续调度点继续。 |
| `INIT_SUCCESS` | 清理实时实例 | 已初始化但未完成 dispatch，恢复调度计划前删除并从后续调度点继续。 |
| `INIT_FAILURE` | 只创建 Actor | 手工 `INIT` 前置状态。 |
| `WAIT_DEPENDENT` | 发送 `WAIT` | 继续依赖判断；依赖满足后由 `WAIT` 自动触发 `SUBMIT`。 |
| `SUBMITTING` | 只创建 Actor 并恢复任务 Actor | 流程已进入提交阶段，任务恢复会继续推进未完成任务。 |
| `SUBMIT_SUCCESS` | 只创建 Actor 并恢复任务 Actor | 流程状态由任务状态汇总继续驱动，不主动通知 worker。 |
| `RUNNING` | 只创建 Actor 并恢复任务 Actor | 流程状态由任务状态汇总继续驱动，不主动通知 worker。 |
| `STOPPING` | 发送 `STOP` | 停止中的流程只负责向任务广播停止消息，继续停止未完成任务。 |
| `RUN_FAILURE` | 只创建 Actor | 失败终态，等待手工重启、停止或强制成功。 |
| `STOP_SUCCESS` / `STOP_FAILURE` | 只创建 Actor | 停止终态，等待手工操作。 |
| `RUN_SUCCESS` / `ENFORCE_SUCCESS` | 不恢复 | 成功终态。 |

实现约束：

- `INITIALIZING` / `INIT_SUCCESS` 不走 `FlowAction.reloadFlows` 的 Actor 恢复分支。
- `WAIT_DEPENDENT -> WAIT` 恢复时，`FlowWaitMsgHandler` 的自动前置状态需要允许 `WAIT_DEPENDENT`。
- `SUBMITTING`、`SUBMIT_SUCCESS`、`RUNNING` 的流程实例不主动发送 worker 消息，避免流程级恢复和任务级恢复重复提交。
- `STOPPING` 恢复时 flow 只负责向未完成任务发送 `STOP` 消息。每条 task `STOP` 消息必须携带
  `flowInstanceId` 和对应的 `taskInstanceId`，幂等判断不放在 flow 层。

### 任务状态恢复

| 任务状态 | 恢复动作 | 说明 |
|----------|----------|------|
| `INITIALIZING` | 跟随流程初始化阶段实例清理 | 不单独恢复，随父流程实例同步删除。 |
| `INIT_SUCCESS` | 跟随流程初始化阶段实例清理或发送 `WAIT` | 所属流程为初始化阶段状态时随父流程实例同步删除；所属流程已进入运行态时继续依赖判断。 |
| `INIT_FAILURE` | 只创建 Actor | 手工前置状态。 |
| `WAIT_DEPENDENT` | 发送 `WAIT` | 继续依赖判断。 |
| `SUBMITTING` | 发送 `RUN` | 提交中状态恢复时进入运行态处理，由 `RUN` 消息继续推进任务状态。 |
| `SUBMIT_SUCCESS` | 只创建 Actor | 不发送 worker 消息，等待 worker 异步上报或用户手工操作。 |
| `SUBMIT_FAILURE` | 只创建 Actor | 失败终态，重启后不自动重试，等待用户手工重启或强制成功。 |
| `RUNNING` | 只创建 Actor | 不发送 worker 消息，等待 worker 异步上报或用户手工操作。 |
| `RUN_FAILURE` | 只创建 Actor | 失败终态，重启后不自动重试，等待用户手工重启或强制成功。 |
| `RESTARTING` | 发送 `SUBMIT` | 重启中应继续提交。 |
| `STOPPING` | 发送 `STOP` | 停止中的任务需要补偿发送停止消息。 |
| `STOP_SUCCESS` / `STOP_FAILURE` | 只创建 Actor | 手工前置状态。 |
| `KILLING` | 发送 `KILL` | 强制停止中应继续推进。 |
| `KILLED` | 只创建 Actor | 手工前置状态。 |
| `ENFORCING_SUCCESS` | 发送 `ENFORCE_SUCCESS` | 强制成功中应完成状态落库和流程汇总。 |
| `ENFORCE_SUCCESS` / `RUN_SUCCESS` | 不恢复 | 成功终态。 |
| `UNKNOWN` | 只创建 Actor | 等待手工操作或外部校准。 |

实现约束：

- `WAIT_DEPENDENT` 恢复时发送 `WAIT`，由等待依赖逻辑判断是否继续触发提交。
- `SUBMITTING` 恢复时发送 `RUN`，不是发送 `SUBMIT`。当前任务状态已处于提交中，恢复后进入运行态处理。
- `SUBMIT_SUCCESS` 和 `RUNNING` 不做 worker 状态反查，不发送 worker 消息。第一版依赖 worker 幂等上报和用户手工操作兜底。
- `SUBMIT_FAILURE` 和 `RUN_FAILURE` 不自动 `RESTART`，避免重启后丢失内存重试次数导致重试语义错误。
- `STOPPING` 恢复时由 flow 侧向任务广播 `STOP`，幂等判断和实际补偿动作在 task 的 `STOPPING` 状态处理。
- task 收到 `STOP` 且 `taskResult == null` 时表示重启恢复补偿，应调用 `masterTaskOperator.stopTask(taskIns)`；
  收到 `STOP` 且 `taskResult != null` 时表示 worker 停止结果上报，按 `STOP_SUCCESS` / `STOP_FAILURE` 更新实例状态。

### 冲突状态决策

部分状态同时出现在自动动作前置状态和手工动作前置状态中，恢复时必须明确优先级。

当前明确冲突状态：

- `SUBMIT_FAILURE`
  - 自动前置：`TaskRestartMsgHandler#getPreState`
  - 手工前置：`TaskRestartMsgHandler#getManualPreState`、`TaskEnforceSuccessMsgHandler#getManualPreState`
- `RUN_FAILURE`
  - 自动前置：`TaskRestartMsgHandler#getPreState`
  - 手工前置：`TaskRestartMsgHandler#getManualPreState`、`TaskEnforceSuccessMsgHandler#getManualPreState`

恢复决策：

```text
SUBMIT_FAILURE / RUN_FAILURE -> 只创建 Actor，不自动 RESTART
```

原因是自动重试依赖 `TaskInstance.retryTimes` / `maxRetryTimes` 等运行期内存字段，其中 `retryTimes` 会通过 `taskIns.setRetryTimes(retryTimes + 1)` 在内存对象中推进。当前持久化实体未表达该字段，Master 重启后无法准确恢复重试次数。如果重启时继续自动发送 `RESTART`，可能导致重复提交或超过预期的自动重试语义。

因此重启恢复时，`SUBMIT_FAILURE` / `RUN_FAILURE` 只恢复 Actor，不发送自动消息。后续由用户通过实例操作接口手工重启、强制成功或其他手工动作继续处理。

### FlowActor 状态缓存恢复

`FlowActor` 内部使用任务状态缓存聚合流程状态。恢复 `FlowActor` 后必须恢复该缓存，否则后续单个任务状态变化可能导致流程汇总误判。

恢复缓存有两种候选方式：

1. 通过 `FlowMsg(actionType=RUN, taskState=...)` 回灌所有任务状态。
2. 提供专用的 Actor 恢复入口，只更新缓存，不触发正常状态机副作用。

建议优先采用专用恢复入口或专用恢复消息，避免通过普通 `RUN` 消息触发事件发送、流程终态更新或其他运行期副作用。
缓存恢复只更新 `FlowActor` 内存中的任务状态缓存，不更新 `FlowInstance.state`，不发送全局事件，不销毁 Actor。
后续真实任务状态变化再通过正常 `RUN` 消息驱动流程状态汇总。

### 恢复顺序

```text
MasterService.reloadSchedules
    -> FlowAction 清理 INITIALIZING / INIT_SUCCESS 初始化阶段实例
    -> 恢复 trigger 调度计划
    -> FlowAction.reloadFlows

FlowAction.reloadFlows
    -> 从 FlowStorage 查询未完成流程实例（不包含已清理的初始化阶段实例）
    -> 为每个流程创建 FlowActor
    -> 查询流程下 TaskInstance
    -> 恢复 FlowActor 任务状态缓存
    -> 委托 TaskAction.reloadTasks 创建 TaskActor
    -> 根据任务状态发送自动恢复消息
    -> 根据流程状态发送流程级自动恢复消息
```

恢复过程必须幂等：

- Actor 已存在时复用现有 Actor。
- 同一实例重复恢复不能重复提交 worker 任务。
- 自动恢复消息应携带稳定幂等键，或在发送前检查状态仍符合恢复条件。

### 实现计划

第一阶段只修复重启恢复 bug，不做架构改造。

1. 复用现有实时存储接口能力。
   - 复用 `FlowStorage.getAvailableInstance(null)` 查询实时未完成流程实例，由 `FlowAction` 过滤初始化阶段状态。
   - 复用 `TaskStorage.removeTaskInsByFlowInsId(flowInsId)` 和 `FlowStorage.removeInstanceById(flowInsId)` 删除实例。
   - `FlowAction.cleanInitializationInstances()` 返回删除前记录的最小 `scheduleTime`，作为重建同一批次的调度恢复基准来源。
   - 复用 `FlowStorage.getLastInstance(flowId, version)` 查询实时表最新流程实例，作为无清理结果时续调度基准。

2. 在 `FlowAction` 中实现初始化阶段实例清理。
   - 提供流程域方法供 `MasterService.reloadSchedules()` 调用，例如 `cleanInitializationInstances()`。
   - 查询 `INITIALIZING` / `INIT_SUCCESS` 的流程实例。
   - 按流程实例逐条先删除下属任务实例，再删除流程实例，确保流程和任务同步清理。
   - 返回被清理实例所属的 `flowId + publishVersion + min(scheduleTime)`，用于 `MasterService` 后续恢复调度基准。
   - 单条清理失败只记录日志，不阻断其他流程恢复。
   - `MasterService` 不直接查询或删除流程、任务实例，只消费 `FlowAction` 返回的清理结果。

3. 恢复 trigger 调度计划。
   - 遍历 `TriggerStorage.getAllScheduledTriggerInfo()`。
   - 如果当前 `triggerInfo` 对应的 `flowId + publishVersion` 有初始化阶段实例被清理，则读取清理结果中的最小
     `scheduleTime`。
   - 如果能从清理结果取到有效 `scheduleTime`，调用 `masterService.addSchedule(triggerInfo, scheduleTime, true)`。
   - 如果没有清理记录，则查询实时表最新流程实例；如果能取到有效 `scheduleTime`，调用
     `masterService.addSchedule(triggerInfo, scheduleTime, false)`。
   - 如果清理结果和最新实时实例都没有有效 `scheduleTime`，继续使用现有普通恢复逻辑。

4. 恢复运行态 Actor。
   - `FlowAction.reloadFlows()` 只处理未完成且未被初始化阶段清理的实时流程实例。
   - 删除 `FlowAction.reloadFlow()` 中对 `INITIALIZING` 重新 `fetchInit` 的分支。
   - `INIT_SUCCESS` 流程不再通过 `WAIT` 恢复；初始化阶段清理后不应进入该分支。
   - 只要流程不是成功终态，都创建 `FlowActor` 并恢复任务状态缓存。

5. 恢复任务 Actor。
   - `TaskAction.reloadTasks(flowInstanceId)` 只作为 `FlowAction` 的流程内恢复能力，不新增全局扫描入口。
   - 任务 `INITIALIZING` 不单独恢复；所属初始化阶段流程实例及其下属任务实例已被同步清理。
   - 任务 `INIT_SUCCESS` / `WAIT_DEPENDENT` 发送 `WAIT`。
   - 任务 `SUBMITTING` 发送 `RUN`。
   - 任务 `SUBMIT_SUCCESS` / `RUNNING` / `SUBMIT_FAILURE` / `RUN_FAILURE` / `UNKNOWN` 只创建 Actor。
   - 任务 `STOPPING` / `KILLING` / `ENFORCING_SUCCESS` 分别发送 `STOP` / `KILL` / `ENFORCE_SUCCESS`。

6. 校验范围。
   - 编译验证：`mvn -DskipTests compile -pl datafusion-manager -am`。
   - 覆盖场景：初始化阶段实例清理后不提前执行、未完成运行态实例可手工操作、`SUBMITTING` 任务恢复不会重复提交、成功终态不恢复。

### 不在第一版处理

- 历史实例恢复。重启恢复只查询实时表，不查询历史表。
- 成功终态实例恢复。
- 分布式多 Master 同时恢复同一实例。
- worker 侧任务真实状态反查。
- 复杂重试策略和重试次数持久化迁移。

## 实现清单

- 为 `Worker` 补充 `registerTime`、`lastHeartbeatTime`、`updateTime`。
- 完善 `WorkerStorageMem`，支持本地开发和单元测试。
- 检查 `CachedWorkerStorage#getTaskInsByWorkerId` 的缓存代理行为。
- 保持 `WorkerManager` 只依赖抽象存储和选择策略。
- 与 manager 配合装配 `HttpMasterTaskOperator`、`WorkerStorageImpl` 和 `TaskResultHandler`。

## 验证

- `WorkerManager` 上线、心跳、下线和按插件选择 worker。
- worker 时间字段更新规则。
- `TaskResultHandler` 对不同 `TaskResult.taskState` 的 actor 消息转换。
- 提交响应只推进 `SUBMIT_SUCCESS` 或 `RUNNING`，最终状态只接受异步上报。
- worker 失联后未完成任务查询、failover 标记和重试提交。
- Master 重启后未完成流程和任务的 Actor 恢复。
- 恢复后手工操作失败任务：重启、强制成功、停止。
- 恢复后自动过渡状态任务继续推进，且不会重复提交已完成任务。

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-master -am
```
