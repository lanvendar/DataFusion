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
taskInstanceId + attemptNo + actionType
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

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-master -am
```
