# 调度 Master 设计

> 数据结构见 [scheduler-master-data-define.md](./scheduler-master-data-define.md)。本文只描述当前模块边界、调度链路、恢复逻辑和 worker 协作。

`datafusion-scheduler-master` 是调度 master 框架层，以 jar 形式提供触发器、流程实例、任务实例、事件依赖、Actor 状态机、worker 选择和任务操作端口。Spring Boot、HTTP、数据库持久化和部署集成由运行时模块装配。

## 模块边界

- `datafusion-scheduler-master`：调度核心、Actor、存储端口、worker 管理端口和内存实现。
- `datafusion-common-data`：调度通信 DTO、状态枚举、变量对象。
- `datafusion-manager`：主运行时，提供 HTTP、外部配置和数据库存储适配。
- `datafusion-agent`：worker 运行时，负责注册、心跳、插件执行和状态上报。

框架层不反向依赖 manager 或 agent。

## 调度链路

```text
TriggerInfo
    -> TriggerInstance
    -> FlowAction.fetchInit
    -> TaskAction.fetchInit
    -> DispatchTriggerThread.dispatchSubmit
    -> FlowActor / TaskActor
    -> MasterTaskOperator
    -> worker
    -> TaskResultHandler.asyncHandle
```

`fetchInit` 阶段直接创建流程实例和任务实例记录，不创建 Actor；到 `dispatchSubmit` 时创建 `FlowActor`、`TaskActor` 并发送 `WAIT` 消息。

任务实例 id 由 `flowInstanceId + "_" + taskId` 稳定生成 UUID。任务定义 DAG 使用 `TaskLink` 从 task id 转为 task instance id，再写入 `lastInstanceIds` 和 `nextInstanceIds`。

## Worker 协作

`MasterTaskOperator` 是 master 到 worker 的任务操作端口，包含提交、停止、强杀和完成清理。`submitTask` 的返回值表示提交结果，不表示最终运行结果。

worker 结果通过 `TaskResultHandler.asyncHandle` 回到 master。当前状态到 Actor 动作映射为：

| worker 状态 | Actor 动作 |
|-------------|------------|
| `SUBMITTING` / `SUBMIT_SUCCESS` / `SUBMIT_FAILURE` / `RUNNING` / `RUN_SUCCESS` / `RUN_FAILURE` / `UNKNOWN` | `RUN` |
| `STOP_SUCCESS` / `STOP_FAILURE` | `STOP` |
| `KILLED` | `KILL` |
| `ENFORCE_SUCCESS` | `ENFORCE_SUCCESS` |

`WorkerManager` 实现 `WorkerListener` 和 `WorkerOperator`。启动时调用 `offlineAllWorkers()`，注册和心跳后 worker 才重新参与调度。`lookupWorker(pluginType)` 当前从在线且支持该插件的 worker 中随机选择。

## 提交模式

`TaskRequest.submitMode` 默认 `SYNC`。

- `SYNC`：worker 在当前请求线程提交插件任务；提交成功返回 `SUBMIT_SUCCESS`，提交失败返回 `SUBMIT_FAILURE`。
- `ASYNC`：worker 先返回 `SUBMITTING`，后台提交后再异步上报 `SUBMIT_SUCCESS` 或 `SUBMIT_FAILURE`。

`RUNNING`、`RUN_SUCCESS`、`RUN_FAILURE` 等运行状态都通过 `TaskResultHandler.asyncHandle` 上报。

## 重启恢复

恢复入口是 `MasterService.reloadSchedules()`：

```text
FlowAction.cleanInitializationInstances
    -> 恢复已启用 TriggerInfo
    -> FlowAction.reloadFlows
```

初始化阶段流程实例指 `INITIALIZING` 和 `INIT_SUCCESS`。恢复时删除这些流程实例及其任务实例，按 `flowId + "_" + version` 记录最小 `scheduleTime`，再以该时间重新生成调度批次。

调度恢复水位：

1. 存在被清理的初始化阶段实例：从最小清理 `scheduleTime` 开始，`included=true`。
2. 否则存在最新实时流程实例：从该实例 `scheduleTime` 之后继续，`included=false`。
3. 否则从当前时间恢复，`TriggerInfo` 内部会按 `startTime` 兜底。

`FlowAction.reloadFlows()` 只恢复非成功、非初始化阶段的流程实例。恢复时创建 `FlowActor`，回灌任务状态到流程 Actor，再调用 `TaskAction.reloadTasks(flowInstanceId)`。

当前自动恢复动作：

| 对象 | 状态 | 动作 |
|------|------|------|
| Flow | `WAIT_DEPENDENT` | `WAIT` |
| Flow | `STOPPING` | `STOP` |
| Task | `INIT_SUCCESS` / `WAIT_DEPENDENT` | `WAIT` |
| Task | `SUBMITTING` | `RUN` |
| Task | `RESTARTING` | `SUBMIT` |
| Task | `STOPPING` | `STOP` |
| Task | `KILLING` | `KILL` |
| Task | `ENFORCING_SUCCESS` | `ENFORCE_SUCCESS` |

其它非成功状态只恢复 Actor，不主动发送动作。

## 流程状态聚合

`FlowActor` 根据任务状态计数聚合流程状态。所有任务成功时流程成功；所有任务终态且存在停止终态时流程停止成功；所有任务终态且存在 `UNKNOWN` 时流程为 `UNKNOWN`；所有任务终态且存在普通失败终态时流程失败；未终态时按等待、提交中或运行中状态推进。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-master -am
```
