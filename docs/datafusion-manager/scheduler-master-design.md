# 调度 Manager 运行时设计

`datafusion-manager` 是 master 的运行时应用层，负责承载 `datafusion-scheduler-master`，提供 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、worker 注册心跳、任务结果上报、任务监控和容错处理。

## 边界

`datafusion-scheduler-master` / `datafusion-scheduler-worker` 是调度框架层，仅作为 jar 包提供核心能力、接口契约、状态机、模型和默认实现。

`datafusion-manager` / `datafusion-agent` 是运行时应用层，负责 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、部署和外部系统集成。

禁止反向依赖：框架层不得依赖运行时应用层；运行时应用层可以依赖并装配框架层。

## 职责

- 装配并启动 `MasterService`。
- 提供 `MasterStorage` 和 `WorkerStorage` 的持久化实现。
- 提供 worker 注册、心跳、下线和任务结果上报 HTTP 接口。
- 通过 HTTP RESTful 接口调用 agent。
- 通过 Nacos 或静态配置发现 agent 服务地址。
- 维护 manager 侧 Worker Registry，作为任务分配的权威视图。
- 监控任务执行状态，处理超时、失败重试和 worker 失联容错。
- 保存 manager 端调度日志。

## 模型归属

- `TaskRequest`、`TaskResult`、worker 注册/心跳相关 DTO 来自 `datafusion-common-data`。
- `WorkerManager`、`WorkerStorage`、worker 选择策略和 master 状态机来自 `datafusion-scheduler-master`。
- `datafusion-manager` 负责将 HTTP 入参、持久化记录和框架模型进行装配转换，不反向暴露 manager 专属模型给框架层。

## 注册与发现

第一版采用“服务发现 + 调度注册表”的混合方案：

- Nacos 负责发现 agent HTTP 服务地址。
- manager Worker Registry 负责保存 worker 调度状态、插件能力、心跳时间和更新时间。
- 任务分配以 Worker Registry 为准，Nacos 只作为寻址和实例存活参考。
- 静态 agent 地址作为本地开发和无注册中心部署的兜底方案。

不建议只依赖 Nacos 元数据承载调度状态。Nacos 适合服务寻址，不适合作为任务绑定关系、插件能力、负载水位、failover 状态和审计信息的唯一事实来源。

可调度判断规则：

- Nacos 或静态配置中存在可访问的 agent 地址。
- Worker Registry 中存在对应 `workerId`。
- worker 状态为 `STATUS_UP`。
- `lastHeartbeatTime` 未超过心跳超时阈值。
- worker 的 `pluginTypes` 包含任务需要的 `pluginType`。

运行时可扩展 `WorkerDiscovery` 适配 Nacos、静态配置、Kubernetes Endpoint、Redis TTL 或数据库注册表，但这些实现不应进入 `datafusion-scheduler-master`。

## HTTP 接口

manager 调用 agent：

```text
POST /internal/schedule/submitTask
POST /internal/schedule/stopTask
POST /internal/schedule/killTask
POST /internal/schedule/finishTask
```

agent 调用 manager：

```text
POST /internal/schedule/worker/register
POST /internal/schedule/worker/heartbeat
POST /internal/schedule/worker/offline
POST /internal/schedule/reportTaskResult
```

内部接口使用 `datafusion-common-spring` 的公共响应结构，例如 `Result<T>`。接口应具备内部鉴权、签名或网络隔离策略。

## Worker Registry

manager 保存的 worker 信息至少包含：

- `workerId`
- `ip`
- `port`
- `hostName`
- `pluginTypes`
- `status`
- `registerTime`
- `lastHeartbeatTime`
- `updateTime`

处理规则：

- 注册：新增或更新 worker，状态置为 `STATUS_UP`。
- 心跳：更新 `lastHeartbeatTime`、`updateTime`，同步插件能力变化。
- 下线：状态置为 `STATUS_DOWN`，更新 `updateTime`。
- 超时：由 manager 定时检测 `lastHeartbeatTime` 并触发失联处理。

## 任务监控与容错

manager 负责监控任务状态并调用 `TaskResultHandler` 推进 master 状态机。

状态来源：

- agent 异步上报 `RUNNING`、`RUN_SUCCESS`、`RUN_FAILURE`、`STOP_SUCCESS`、`STOP_FAILURE`、`KILLED`。
- manager 定时检测任务超时。
- manager 定时检测 worker 心跳超时。

超时处理：

```text
任务超过超时时间
    -> 调用 stopTask
    -> stop 超时或失败后调用 killTask
    -> 等待 agent 上报结果
    -> 必要时由 manager 写入兜底失败状态
```

失败重试和 failover 必须幂等。manager 重试同一任务时应持久化 `attemptNo`，并在请求中传给 agent。agent 收到重复 `submitTask`、`stopTask` 或 `killTask` 时应返回当前状态，不重复执行同一 attempt。

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

## 实现清单

- 新增 worker 注册、心跳、下线接口。
- 新增任务结果上报接口并调用 `TaskResultHandler`。
- 实现 `WorkerStorageImpl`。
- 装配 `WorkerManager`、`MasterService`、`HttpMasterTaskOperator`。
- 实现 worker 心跳超时检测。
- 实现任务超时检测。
- 实现失败重试和 failover 幂等处理。
- 实现 manager 端按流程实例 ID 分割日志。

## 验证

- agent 注册后 manager 能保存 worker 能力。
- 心跳更新 `lastHeartbeatTime` 和插件能力。
- manager 能按 `pluginTypes` 选择 worker。
- agent 上报任务结果后 master 状态机正确推进。
- 任务超时后 manager 发起 stop / kill。
- worker 失联后未完成任务进入 failover 或失败处理。

```powershell
mvn -DskipTests compile -pl datafusion-manager -am
```
