# 调度 Manager 运行时设计

`datafusion-manager` 是 master 的运行时应用层，负责承载 `datafusion-scheduler-master`，提供 Spring Boot 启动、HTTP 接口、配置加载、持久化适配、worker 注册心跳、任务结果上报、任务监控和容错处理。

当前第一版先完成调度核心在 manager 内真正运行的闭环：装配并启动 `MasterService`，恢复已启用调度，从数据库恢复 worker registry，接收 agent 注册/心跳/下线/任务结果上报，并通过 HTTP 调用 agent 执行任务。心跳超时、任务超时和 failover 作为后续能力。

数据结构定义见 [scheduler-master-data-define.md](./scheduler-master-data-define.md)。设计文档只描述运行行为、数据流、接口、实现边界和验证，不重复完整字段表。

## 边界

`datafusion-scheduler-master` / `datafusion-scheduler-worker` 是调度框架层，仅作为 jar 包提供核心能力、接口契约、状态机、模型和默认实现。

`datafusion-manager` / `datafusion-agent` 是运行时应用层，负责 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、部署和外部系统集成。

禁止反向依赖：框架层不得依赖运行时应用层；运行时应用层可以依赖并装配框架层。

## 职责

- 装配并启动 `MasterService`。
- 提供 `MasterStorage` 的 manager 持久化适配。
- 提供 `WorkerStorageImpl` 作为 manager 运行期 Worker Registry 的数据库持久化适配。
- 提供 worker 注册、心跳、下线和任务结果上报 RPC 接口。
- 通过 HTTP RESTful 接口调用 agent。
- 启动时从数据库恢复 worker registry，启动后通过 agent 主动注册和心跳刷新运行态。
- 后续通过 Nacos 或静态配置发现 agent 服务地址。
- 后续监控任务执行状态，处理超时、失败重试和 worker 失联容错。
- 后续保存 manager 端调度日志。

## 模型归属

- `TaskRequest`、`TaskResult`、worker 注册/心跳相关 DTO 来自 `datafusion-common-data`。
- `WorkerManager`、`WorkerStorage`、worker 选择策略和 master 状态机来自 `datafusion-scheduler-master`。
- `datafusion-manager` 负责将 HTTP 入参、持久化记录和框架模型进行装配转换，不反向暴露 manager 专属模型给框架层。

## 注册与发现

目标方案采用“服务发现 + 调度注册表”的混合方案：

- Nacos 负责发现 agent HTTP 服务地址。
- manager Worker Registry 负责保存 worker 调度状态、插件能力、心跳时间和更新时间。
- 任务分配以 Worker Registry 为准，Nacos 只作为寻址和实例存活参考。
- 静态 agent 地址作为本地开发和无注册中心部署的兜底方案。

当前第一版采用 agent 主动注册方案：

- agent 启动后调用 manager 内部接口注册 worker。
- agent 按固定间隔向 manager 上报心跳。
- manager 将 worker 保存在 `scheduler_worker_registry` 中。
- manager 选择 worker 时以 `WorkerManager.lookupWorker(pluginType)` 为准。
- agent 下线时调用 manager 下线接口，manager 将 worker 状态置为 `STATUS_DOWN`。
- manager 进程重启后先从数据库恢复 worker registry，再等待 agent 重新注册和心跳刷新运行态。

不建议只依赖 Nacos 元数据承载调度状态。Nacos 适合服务寻址，不适合作为任务绑定关系、插件能力、负载水位、failover 状态和审计信息的唯一事实来源。

可调度判断规则：

- Nacos 或静态配置中存在可访问的 agent 地址。
- Worker Registry 中存在对应 `workerId`。
- `scheduler_worker_registry.is_active = 1`。
- worker 状态为 `STATUS_UP`。
- `lastHeartbeatTime` 未超过心跳超时阈值。
- worker 的 `pluginTypes` 包含任务需要的 `pluginType`。

运行时可扩展 `WorkerDiscovery` 适配 Nacos、静态配置、Kubernetes Endpoint、Redis TTL 或数据库注册表，但这些实现不应进入 `datafusion-scheduler-master`。

## HTTP 接口

manager 调用 agent：

```text
POST /internal/scheduler/submitTask
POST /internal/scheduler/stopTask
POST /internal/scheduler/killTask
POST /internal/scheduler/finishTask
```

agent 调用 manager：

```text
POST /internal/schedule/worker/register
POST /internal/schedule/worker/heartbeat
POST /internal/schedule/worker/offline
POST /internal/schedule/reportTaskResult
```

内部接口使用 `datafusion-common-spring` 的公共响应结构，例如 `Result<T>`。接口应具备内部鉴权、签名或网络隔离策略。

路径说明：

- manager 调用 agent 使用 `/internal/scheduler/*`，与 `datafusion-agent` 当前 `AgentExecutorRpcProvider` 保持一致。
- agent 调用 manager 使用 `/internal/schedule/*`，与 `datafusion-agent` 当前 `HttpManagerClient` 保持一致。
- 第一版内部接口先完成链路闭环；鉴权、签名或网络隔离策略后续补齐。

## Spring 装配与启动恢复

manager 通过顶层 `SchedulerMasterConfig` 装配调度核心：

- `MasterStorage`：聚合 `TriggerStorageImpl`、`FlowStorageImpl`、`TaskStorageImpl`、`EventStorageImpl`。
- `Options`：从 Spring `Environment` 读取 `MasterConfigOptions` 中声明的 `master.*` 配置；缺省使用 master 默认值。
- `WorkerStorage`：使用 manager 侧 `WorkerStorageImpl` 适配 `scheduler_worker_registry`，必要时外层保留 `CachedWorkerStorage`。
- `WorkerManager`：由 `WorkerStorage` 构造。
- `MasterTaskOperator`：默认使用 `HttpMasterTaskOperator`。
- `MasterService`：由 `MasterTaskOperator`、`MasterStorage`、`Options` 构造，并在容器销毁时调用 `stop()`。

配置类迁移规则：

- `SchedulerConfig` 迁移到 `com.datafusion.manager.config.SchedulerMasterConfig`，作为 manager 运行时全局配置类。
- `SchedulerMasterLifecycle` 迁移到 `com.datafusion.manager.config` 包下，保持调度核心启动恢复职责。
- `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/config` 不再承载 Spring runtime 配置类。
- 调度领域内的 storage、service、controller、rpc 仍保留在 `com.datafusion.manager.scheduler` 包下。

manager 通过 `SchedulerMasterLifecycle` 启动调度核心：

- Spring Boot 启动完成后执行。
- 从 `MasterStorage.triggerStorage.getAllScheduledTriggerInfo()` 读取已发布且启用的流程调度。
- 调用 `MasterService.addSchedule(triggerInfo, now, true)` 将调度恢复到 master 内部队列。
- 调用 `MasterService.start()` 启动触发实例生成线程和触发实例分发线程。

流程定义域与 master 队列同步规则：

- 发布流程且 `enableSchedule=true` 时，更新数据库后调用 `addSchedule`。
- 启用调度时，更新数据库后调用 `addSchedule`。
- 停用调度时，更新数据库后调用 `stopSchedule`。
- 取消发布时，更新数据库后调用 `stopSchedule`。
- 上述方法需要事务保护；如果缺少有效触发器，启用调度应失败并回滚数据库状态。

`TriggerStorageImpl` 读取触发器时优先使用 `scheduler_flow_info.trigger_id`，并兼容旧的 `flow.id == trigger.id` 共享主键方式。

## Worker Registry

manager 通过 `scheduler_worker_registry` 保存 worker 注册状态、心跳时间、插件能力和运行元信息。完整字段、约束和映射见 [scheduler-master-data-define.md](./scheduler-master-data-define.md)。

关键约束：

- agent 必须上报稳定 `workerId`，manager 保存为 `worker_code`。
- `host_name` 必填。
- `worker_code` 唯一。
- `host + port` 唯一。
- `tenant_id` 第一版仅预留，不参与 worker 选择。
- `zone` 第一版仅预留，不参与 worker 选择。
- `plugins` 保存 pluginType 列表，使用英文逗号分隔。

启动恢复规则：

- manager 启动时 `WorkerStorageImpl` 从 `scheduler_worker_registry` 加载 `is_active = 1` 的 worker 记录。
- 为避免使用过期 worker，启动后已恢复 worker 需要等待 agent 注册或心跳刷新运行态。
- `WorkerManager` 初始化阶段可将恢复出的在线 worker 置为 `STATUS_DOWN`，随后由 agent 心跳重新置为 `STATUS_UP`。
- agent 未恢复心跳前，worker 不参与任务分配。

处理规则：

- 注册：新增或更新 worker，状态置为 `STATUS_UP`。
- 心跳：更新 `lastHeartbeatTime`、`updateTime`，同步插件能力变化；不更新界面审计语义。
- 下线：状态置为 `STATUS_DOWN`，更新 `updateTime`。
- 超时：后续由 manager 定时检测 `lastHeartbeatTime` 并触发失联处理。
- 定位已有 worker 时优先使用 `worker_code`，找不到时按 `host + port` 回退；`host_name` 不作为唯一键。

调度查找规则：

- 只查询 `is_active = 1` 的 worker。
- 只选择 `status = STATUS_UP` 的 worker。
- 只选择 `pluginTypes` 包含任务 `pluginType` 的 worker。
- 第一版不按租户过滤。
- 第一版不按 `zone` 过滤。

第一版实现约束：

- `WorkerStorageImpl` 进入当前第一版，实现 `WorkerStorage` 的 DB 持久化。
- agent/worker 端需要补充稳定 `workerId` 生成规则。
- worker 心跳超时检测和失联 failover 暂不实现。

## Manager RPC

manager 侧内部 RPC 放在 `com.datafusion.manager.scheduler.rpc` 包下，与 `datafusion-agent` 的 `com.datafusion.agent.rpc` 对齐。

RPC Provider 拆分：

- `WorkerRpcProvider`：处理 agent -> manager 的 worker 注册、心跳、下线。
- `SchedulerRpcProvider`：处理 agent -> manager 的 task result 上报，并调用 `MasterService.getTaskAction().asyncHandle(taskResult)` 推进状态机。

路径保持不变：

```text
POST /internal/schedule/worker/register
POST /internal/schedule/worker/heartbeat
POST /internal/schedule/worker/offline
POST /internal/schedule/reportTaskResult
```

拆分规则：

- `WorkerRpcProvider` 注入 `WorkerManager`，不直接依赖 `MasterService`。
- `SchedulerRpcProvider` 注入 `MasterService`，不处理 worker 注册状态。
- 原 `SchedulerInternalController` 不再放在 `controller` 包；迁移后删除或重命名为上述两个 Provider。
- RPC Provider 仍使用 `Result<T>` 作为返回包装。

## 任务监控与容错

manager 负责监控任务状态并调用 `TaskResultHandler` 推进 master 状态机。

状态来源：

- agent 异步上报 `RUNNING`、`RUN_SUCCESS`、`RUN_FAILURE`、`STOP_SUCCESS`、`STOP_FAILURE`、`KILLED`。
- 后续 manager 定时检测任务超时。
- 后续 manager 定时检测 worker 心跳超时。

现有基础能力：

- agent 上报任务结果到 `/internal/schedule/reportTaskResult`。
- manager 调用 `MasterService.getTaskAction().asyncHandle(taskResult)` 推进 master 状态机。
- `HttpMasterTaskOperator` 已具备 `submitTask`、`stopTask`、`finishTask` 调用 agent 的基础实现。

当前设计需要补齐：

- `HttpMasterTaskOperator` 的 manager -> agent 路径统一为 `/internal/scheduler/*`。
- `HttpMasterTaskOperator.killTask` 调用 agent `/internal/scheduler/killTask`。
- worker 响应为空、解析失败或业务失败时返回可控 `TaskResult`，避免空指针中断调度线程。

第一版暂不实现：

- 任务超时检测。
- worker 心跳超时检测。
- 失败重试和 failover。
- manager 兜底写入失败状态。

超时处理：

```text
任务超过超时时间
    -> 调用 stopTask
    -> stop 超时或失败后调用 killTask
    -> 等待 agent 上报结果
    -> 必要时由 manager 写入兜底失败状态
```

失败重试和 failover 由 manager/master 调度端控制。agent 不接收调度重试次数，重复 `submitTask`、`stopTask` 或 `killTask`
应按 `taskInstanceId + actionType` 幂等处理，返回当前状态，不重复启动同一任务实例。

worker 失联处理：

```text
检测 worker 心跳超时
    -> 将 worker 置为 STATUS_DOWN
    -> 查询该 worker 上未完成任务
    -> 标记任务 FAIL_OVER 或 RUN_FAILURE
    -> 按任务重试策略重新选择活跃 worker
    -> 重新提交任务
```

如果任务有外部应用 ID，例如 PID、Flink Job ID 或 Yarn Application ID，应优先查询终端系统状态。无法确认时按失败或待人工处理，避免误报成功。

## 日志

manager 端日志根目录：

```text
${modules}/logs/
    执行日期/
        流程实例id/
            *.log
            *.err.log
```

日志至少记录：

- flow / task 状态变更。
- worker 分配结果。
- agent HTTP 调用结果。
- 超时、重试、failover 和人工操作。

第一版日志处理不在 manager runtime 域内新增能力。任务实例日志读取由 `scheduler-instance` 查询域按 `TaskResult.logPath` 或约定目录只读获取；manager 端按流程实例 ID 分割调度日志后续实现。

## 实现清单

现有基础：

- 新增 worker 注册、心跳、下线接口。
- 新增任务结果上报接口并调用 `TaskResultHandler`。
- 装配 `WorkerStorageMem + CachedWorkerStorage`、`WorkerManager`、`MasterService`、`HttpMasterTaskOperator`。
- 启动时恢复已发布且启用的调度，并启动 master 触发线程。
- 发布、启用、停用、取消发布流程时同步 master 调度队列。
- manager 调用 agent 的 submit / stop / finish HTTP 接口。
- 保留 agent 上报的稳定 worker ID。
- 修正 `TriggerStorageImpl` 使用 `flow.triggerId` 读取触发器。

当前设计待实现：

- `SchedulerConfig` 迁移并重命名为顶层 `com.datafusion.manager.config.SchedulerMasterConfig`。
- `SchedulerMasterLifecycle` 迁移到顶层 `com.datafusion.manager.config` 包。
- `SchedulerInternalController` 拆分为 `com.datafusion.manager.scheduler.rpc.WorkerRpcProvider` 和 `SchedulerRpcProvider`。
- 数据库持久化 `WorkerStorageImpl`。
- manager 启动时从 DB 恢复 `scheduler_worker_registry`，并等待 agent 心跳刷新为可调度状态。
- manager 调用 agent 的 `/internal/scheduler/killTask`。
- manager 调用 agent 路径统一为 `/internal/scheduler/*`。
- worker 响应为空、解析失败或业务失败时返回可控 `TaskResult`。
- agent/worker 端稳定 `workerId` 生成规则。
- worker 心跳超时检测。
- 任务超时检测。
- 失败重试和 failover 幂等处理。
- 内部接口鉴权、签名或网络隔离策略。
- manager 端按流程实例 ID 分割日志。
- Nacos / 静态配置等 agent 服务发现适配。

## 验证

- agent 注册后 manager 能保存 worker 能力。
- manager 重启后能从 `scheduler_worker_registry` 恢复 `is_active = 1` 的 worker 记录。
- agent 未重新心跳前，恢复出的 worker 不参与任务分配。
- 心跳更新 `lastHeartbeatTime` 和插件能力。
- manager 能按 `is_active = 1`、`STATUS_UP` 和 `pluginTypes` 选择 worker。
- agent 上报任务结果后 master 状态机正确推进。
- manager 侧 RPC 保持 `/internal/schedule/*` 路径不变，并由 `WorkerRpcProvider` / `SchedulerRpcProvider` 分别承载。
- 已发布且启用的流程在 manager 重启后能恢复到 master 调度队列。
- 启用调度后能立即加入 master 调度队列。
- 停用调度或取消发布后 master 不再继续生成新的调度实例。
- manager 能调用 agent 的 submit / stop / kill / finish 接口。
- 后续验证任务超时后 manager 发起 stop / kill。
- 后续验证 worker 失联后未完成任务进入 failover 或失败处理。

```powershell
mvn -DskipTests compile -pl datafusion-manager -am
```
