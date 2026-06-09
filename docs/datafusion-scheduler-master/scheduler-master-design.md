# 调度 Master 设计

`datafusion-scheduler-master` 是调度 master 框架层，只作为 jar 包提供触发器、流程实例、任务实例、事件依赖、Actor 状态机、执行节点选择和任务操作抽象。Spring Boot、HTTP、持久化、配置和外部系统集成由 `datafusion-manager` 承担。

## 边界

- 框架层：`datafusion-scheduler-master` / `datafusion-scheduler-worker`，只定义核心能力、接口契约、状态机、模型和默认实现。
- 运行时层：`datafusion-manager` / `datafusion-agent`，负责启动、HTTP、配置、持久化、鉴权和部署集成。
- 依赖方向：运行时可以依赖框架层；框架层不得反向依赖运行时。

## 核心职责

- 按触发器策略生成 `TriggerInstance`。
- 初始化 `FlowInstance` 和 `TaskInstance`。
- 根据 DAG 和全局事件依赖推进任务状态。
- 根据执行节点状态和插件能力选择可用节点。
- 通过 `MasterTaskOperator` 向 worker 发起提交、停止、强杀和完成清理。
- 通过 `TaskResultHandler` 消费 worker 异步上报结果。
- 汇总任务状态并推进流程状态。

## 模块依赖

```text
datafusion-scheduler-master
    -> datafusion-common-data
    -> datafusion-common

datafusion-manager
    -> datafusion-scheduler-master
    -> datafusion-common-spring
```

`TaskRequest`、`TaskResult`、worker 注册/心跳 DTO 属于 `datafusion-common-data`。`WorkerManager`、`WorkerStorage`、worker 选择策略和 master 状态机保留在 `datafusion-scheduler-master`。

## 核心接口

`MasterTaskOperator` 是 master 操作 worker 的端口：

```java
public interface MasterTaskOperator {
    TaskResult submitTask(TaskInstance taskIns) throws SchedulerException;
    TaskResult stopTask(TaskInstance taskIns) throws SchedulerException;
    TaskResult killTask(TaskInstance taskIns) throws SchedulerException;
    TaskResult finishTask(TaskInstance taskIns) throws SchedulerException;
}
```

`submitTask` 的返回值只表示提交结果，不表示任务最终成功或失败。最终状态必须通过 worker 异步上报。

`TaskResultHandler` 消费 worker 上报结果：

```java
public interface TaskResultHandler {
    boolean asyncHandle(TaskResult result);
}
```

当前实现类是 `TaskAction`。`TaskResult.taskState` 到 actor action 的映射：

| worker 状态 | actor action |
|-------------|--------------|
| `RUNNING`、`RUN_SUCCESS`、`RUN_FAILURE` | `RUN` |
| `STOP_SUCCESS`、`STOP_FAILURE` | `STOP` |
| `KILLED` | `KILL` |
| `ENFORCE_SUCCESS` | `ENFORCE_SUCCESS` |

## 调度链路

```text
TriggerInfo -> TriggerInstance
    -> FlowAction.fetchInit 初始化流程和任务实例
    -> DispatchTriggerThread 到时 dispatchSubmit
    -> FlowActor / TaskActor 检查依赖
    -> MasterTaskOperator.submitTask
    -> worker 返回提交结果
    -> worker 异步上报运行结果
    -> TaskResultHandler.asyncHandle
    -> TaskActor / FlowActor 推进状态
```

提交模式使用 `submitMode`：

- `SYNC`：worker 返回前已确认任务进入运行态，提交响应状态为 `RUNNING`。
- `ASYNC`：worker 只确认任务已接收或排队，提交响应状态为 `SUBMIT_SUCCESS`。

无论哪种模式，`RUN_SUCCESS` / `RUN_FAILURE` 都必须异步上报。

## 执行节点管理

master 只定义 worker 节点模型、存储接口和选择策略，不绑定 Nacos、Kubernetes、Redis 或数据库。

可调度判断至少依赖：

- 节点状态为 `STATUS_UP`。
- 心跳未超时。
- `pluginTypes` 包含任务需要的 `pluginType`。

具体注册表、服务发现、心跳刷新和页面维护由 manager 实现。

## 重启恢复

### 背景

`ActorSystem` 是进程内存状态。Manager / Master 重启后，数据库实时表中的流程实例和任务实例仍在，但 `FlowActor`、`TaskActor`、父子关系和流程内任务状态缓存会丢失。因此启动恢复必须同时恢复调度计划和未完成实例 actor。

恢复入口统一为 `MasterService.reloadSchedules()`：

```text
MasterService.reloadSchedules
    -> FlowAction.cleanInitializationInstances
    -> 恢复 trigger 调度计划
    -> FlowAction.reloadFlows
```

### 初始化阶段清理

`INITIALIZING` / `INIT_SUCCESS` 属于初始化阶段，表示实例尚未完成从 trigger dispatch 到运行态 actor 的完整生命周期。重启时不直接恢复为 actor，也不直接发送 `WAIT`，避免绕过触发队列导致未到 `scheduleTime` 就执行。

清理规则：

- 只处理实时表，不处理历史表。
- 删除 `INITIALIZING` / `INIT_SUCCESS` 流程实例前，先删除其下属任务实例。
- 按 `flowId + publishVersion` 记录被删除实例的最小 `scheduleTime`。
- 后续使用该最小 `scheduleTime` 调用 `addSchedule(triggerInfo, scheduleTime, true)`，重新生成被清理批次，避免跳批。

### 调度恢复水位

每个已启用 trigger 的恢复优先级：

| 优先级 | 条件 | baseTime | included | 说明 |
|--------|------|----------|----------|------|
| 1 | 存在被清理的初始化阶段实例 | 最小清理 `scheduleTime` | `true` | 重建被清理批次 |
| 2 | 无清理结果，但实时表存在最新实例 | 最新实时实例 `scheduleTime` | `false` | 从下一批继续 |
| 3 | 无实时实例 | `now` 或 `trigger.startTime` | `true` | 普通启动恢复 |

实时表最新实例由 `FlowStorage.getLastInstance(flowId, version)` 查询。历史表不参与恢复水位。

### Actor 恢复

`FlowAction.reloadFlows()` 只恢复非成功、非初始化阶段的实时流程实例：

```text
FlowAction.reloadFlows
    -> 查询未完成流程实例
    -> createFlowActor(flowInstanceId)
    -> 回灌任务状态到 FlowActor 缓存
    -> taskAction.reloadTasks(flowInstanceId)
    -> 按流程状态发送必要恢复消息
```

`TaskAction.reloadTasks(flowInstanceId)` 是流程内恢复能力，不提供全局扫描入口。

### 状态恢复矩阵

流程状态：

| 状态 | 恢复动作 |
|------|----------|
| `INITIALIZING`、`INIT_SUCCESS` | 清理实时实例并重建调度批次 |
| `WAIT_DEPENDENT` | 创建 Actor 并发送 `WAIT` |
| `STOPPING` | 创建 Actor 并发送 `STOP` |
| `SUBMITTING`、`SUBMIT_SUCCESS`、`RUNNING` | 创建 Actor，任务恢复继续推进 |
| `INIT_FAILURE`、`RUN_FAILURE`、`STOP_SUCCESS`、`STOP_FAILURE`、`KILLED`、`UNKNOWN` | 只创建 Actor，等待手工操作 |
| `RUN_SUCCESS`、`ENFORCE_SUCCESS` | 不恢复 |

任务状态：

| 状态 | 恢复动作 |
|------|----------|
| `INITIALIZING` | 随初始化阶段流程实例清理 |
| `INIT_SUCCESS`、`WAIT_DEPENDENT` | 发送 `WAIT` |
| `SUBMITTING` | 发送 `RUN` |
| `STOPPING` | 发送 `STOP` |
| `KILLING` | 发送 `KILL` |
| `ENFORCING_SUCCESS` | 发送 `ENFORCE_SUCCESS` |
| `SUBMIT_SUCCESS`、`RUNNING`、`SUBMIT_FAILURE`、`RUN_FAILURE`、`STOP_SUCCESS`、`STOP_FAILURE`、`KILLED`、`UNKNOWN` | 只创建 Actor |
| `RUN_SUCCESS`、`ENFORCE_SUCCESS` | 不恢复 |

`SUBMIT_FAILURE` / `RUN_FAILURE` 重启后不自动 `RESTART`，因为当前重试次数依赖内存字段，未完整持久化。恢复后由用户手工重启或强制成功。

### 幂等要求

- Actor 已存在时复用现有 Actor。
- 同一实例重复恢复不能重复提交 worker。
- 自动恢复消息发送前应再次检查状态仍符合恢复条件。
- worker 操作必须携带稳定幂等键，推荐 `taskInstanceId + actionType`。

## 第一版不处理

- 历史实例恢复。
- 成功终态恢复。
- 分布式多 Master 同时恢复同一实例。
- worker 侧真实状态反查。
- 重试次数持久化迁移。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-master -am
```

重点覆盖：未完成实例 actor 恢复、初始化阶段实例重建、`SUBMITTING` 任务恢复不重复提交、失败任务重启后等待手工操作、成功终态不恢复。
