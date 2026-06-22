# 调度 Manager 运行时设计

`datafusion-manager` 是 scheduler master 的运行时应用层，负责装配并启动 `MasterService`，提供 HTTP/RPC 接口、数据库持久化适配、执行节点注册心跳、任务结果上报和 agent 调用。

数据结构见 [scheduler-master-data-define.md](./scheduler-master-data-define.md)。框架层设计见 [../datafusion-scheduler-master/scheduler-master-design.md](../datafusion-scheduler-master/scheduler-master-design.md)。

## 边界

- `datafusion-scheduler-master` 只提供调度框架能力，不依赖 manager。
- `datafusion-manager` 负责 Spring Boot 启动、配置、持久化、HTTP、鉴权和外部服务发现。
- manager 可以依赖并装配 master；master 不反向依赖 manager。

## 运行时职责

- 创建 `MasterStorage`，聚合 trigger、flow、task、event 的数据库适配。
- 创建 `WorkerStorageImpl` 和 `WorkerManager`，管理执行节点注册表。
- 创建 `HttpMasterTaskOperator`，通过 HTTP 调用 agent。
- 创建并启动 `MasterService`。
- 启动时恢复已启用调度和未完成实例 actor。
- 提供 agent 注册、心跳、下线和任务结果上报内部接口。
- 在流程发布、开始调度、取消调度、取消发布时同步 master 调度队列。

## Spring 装配

`SchedulerMasterConfig` 负责装配：

| Bean | 说明 |
|------|------|
| `MasterStorage` | 聚合 `TriggerStorageImpl`、`FlowStorageImpl`、`TaskStorageImpl`、`EventStorageImpl` |
| `Options` | 从 Spring `Environment` 读取 `master.*` 配置，缺省使用 master 默认值 |
| `WorkerStorage` | 使用 `WorkerStorageImpl` 适配 `scheduler_worker_registry` |
| `WorkerManager` | 执行节点注册、心跳和选择 |
| `MasterTaskOperator` | 默认 `HttpMasterTaskOperator` |
| `MasterService` | 调度核心入口，容器销毁时调用 `stop()` |

`SchedulerMasterLifecycle` 只负责启动生命周期编排：调用 `MasterService.reloadSchedules()` 后启动 master 触发线程。

## RPC 接口

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

`WorkerRpcProvider` 处理执行节点注册、心跳、下线；`SchedulerRpcProvider` 处理任务结果上报并调用 `MasterService.getTaskAction().asyncHandle(taskResult)`。

## 执行节点注册表

`scheduler_worker_registry` 是调度可用节点的事实来源。Nacos 或静态配置只负责寻址，不承载任务绑定、插件能力、负载、failover 或审计状态。

可调度节点必须满足：

- `is_active = 1`
- `status = STATUS_UP`
- 心跳未超时
- `plugins` 包含任务需要的 `pluginType`

manager 重启后可以从数据库恢复有效节点记录，但恢复出的节点必须等待 agent 注册或心跳刷新后再参与任务分配。

## 调度队列同步

流程定义域与 master 调度队列的同步规则：

| 场景 | DB 状态 | master 操作 |
|------|---------|-------------|
| 发布且勾选开始调度 | `publishState=true`、`enabled=true` | `addSchedule` |
| 发布但不开始调度 | `publishState=true`、`enabled=false` | 不加入队列 |
| 开始调度 | `enabled=true` | `addSchedule` |
| 取消调度 | `enabled=false` | `stopSchedule` |
| 取消发布 | `publishState=false`、`enabled=false` | 先 `stopSchedule` |

启用调度必须存在有效触发器，否则应失败并回滚数据库状态。`TriggerStorageImpl` 读取触发器时优先使用 `scheduler_flow_info.trigger_id`。

## 启动恢复

manager 启动恢复只调用 master 统一入口：

```text
SchedulerMasterLifecycle.run
    -> MasterService.reloadSchedules()
    -> MasterService.start()
```

`reloadSchedules()` 内部负责：

- 清理初始化阶段流程和任务实例。
- 恢复已启用 trigger 调度计划。
- 从实时表恢复未完成流程和任务 actor。

恢复细节由框架层文档维护，manager 不在生命周期类中直接查询 trigger 循环调用 `addSchedule`。

## 任务结果与容错

现有闭环：

- agent 上报 `RUNNING`、`RUN_SUCCESS`、`RUN_FAILURE`、`STOP_SUCCESS`、`STOP_FAILURE`、`KILLED`。
- manager 调用 `TaskAction.asyncHandle(taskResult)` 推进 master 状态机。
- `HttpMasterTaskOperator` 调用 agent 的 submit / stop / kill / finish。

后续能力：

- 任务超时检测。
- 执行节点心跳超时检测。
- failover 和失败重试。
- 内部接口鉴权、签名或网络隔离。

## 日志

任务实例日志由 agent 写入任务运行目录，并通过 `TaskResult.workDirPath` 供实例查询域只读获取。manager runtime 第一版不新增独立调度日志能力。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-manager -am
```

重点验证：agent 注册/心跳、执行节点选择、任务结果上报、manager 重启恢复调度、发布/启停/取消发布与 master 队列同步。
