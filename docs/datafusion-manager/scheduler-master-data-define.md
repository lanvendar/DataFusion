# scheduler-master 数据结构定义

> 本文档是调度 master 运行时数据结构的聚合事实源。既有流程、任务、触发器和事件字段继续以各自 `*-data-define.md` 为准；本文件只记录 master runtime 如何复用这些表，并完整定义 `scheduler_worker_registry`。

## 1. 数据库数据模型

### 1.1 表模型

| 表名 | 操作 | 主键 | 候选键 / 唯一业务键 | 分区策略 | 表注释 | 说明 |
|------|------|------|----------------------|----------|--------|------|
| `scheduler_flow_info` | 复用 | `id uuid` | 无 | 无 | 调度流程定义表 | `FlowStorageImpl` 读取流程定义；完整结构见 [scheduler-flow-data-define.md](./scheduler-flow-data-define.md) |
| `scheduler_flow_instance` | 复用 | `id uuid` | 无 | 无 | 流程运行实例表 | `FlowStorageImpl` 和 `TriggerStorageImpl` 保存、更新、查询流程实例；完整结构见 [scheduler-instance-data-define.md](./scheduler-instance-data-define.md) |
| `scheduler_task_info` | 复用 | `id uuid` | 无 | 无 | 调度任务表 | `TaskStorageImpl` 读取任务定义；完整结构见 [scheduler-task-data-define.md](./scheduler-task-data-define.md) |
| `scheduler_task_link` | 复用 | `id uuid` | 无 | 无 | 流程任务编排关系表 | `TaskStorageImpl` 读取流程 DAG 任务依赖；完整结构见 [scheduler-flow-data-define.md](./scheduler-flow-data-define.md) |
| `scheduler_task_instance` | 复用 | `id uuid` | 无 | 无 | 任务运行实例表 | `TaskStorageImpl` 保存、更新、查询任务实例；完整结构见 [scheduler-instance-data-define.md](./scheduler-instance-data-define.md) |
| `scheduler_trigger_info` | 复用 | `id uuid` | 无 | 无 | 调度触发器配置表 | `TriggerStorageImpl` 读取触发器定义；完整结构见 [scheduler-trigger-data-define.md](./scheduler-trigger-data-define.md) |
| `scheduler_event_instance` | 复用 | `id uuid` | 无 | 无 | 调度事件实例表 | `EventStorageImpl` 保存、加载运行期事件实例；完整结构见 [scheduler-event-data-define.md](./scheduler-event-data-define.md) |
| `scheduler_worker_registry` | 新增 | `id uuid` | `worker_code`、`host + port` | 无 | 调度 worker 注册表，记录 worker 的注册状态、心跳时间、插件能力和运行元信息 | manager 侧 `WorkerStorageImpl` 的持久化表；`id` 由 `workerCode` 稳定生成 |

`scheduler_event_info` 是事件定义域表，不由 `MasterStorage` 直接读写；master runtime 通过流程和任务定义中的 `event_id`、`dep_event_ids` 参与事件调度。

### 1.2 DDL / 迁移

`scheduler_worker_registry` 来源为 [init_ddl.sql](../../datafusion-manager/src/main/resources/init_db/init_ddl.sql):

```sql
-- DROP TABLE scheduler_worker_registry;

CREATE TABLE scheduler_worker_registry (
id uuid NOT NULL,
worker_code varchar(128) NOT NULL, -- worker编码
host_name varchar(128) NOT NULL, -- 主机名称
host varchar(45) NOT NULL, -- IP地址
port int4 NOT NULL, -- 端口
status int4 NOT NULL, -- 状态：0-下线 1-上线
"zone" varchar(64) NULL, -- 区域/分组，预留字段
plugins varchar(256) NULL, -- 组件类型列表，逗号分隔
register_time timestamp(6) NULL, -- 注册时间
last_heartbeat_time timestamp(6) NULL, -- 最近心跳时间
is_active int2 NOT NULL, -- 是否有效：1-有效 0-无效
remark varchar(255) NULL, -- 资源说明
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
tenant_id uuid NULL, -- 租户ID
CONSTRAINT scheduler_worker_registry_host_port_uk UNIQUE (host, port),
CONSTRAINT scheduler_worker_registry_pkey PRIMARY KEY (id),
CONSTRAINT scheduler_worker_registry_worker_code_uk UNIQUE (worker_code)
);
COMMENT ON TABLE scheduler_worker_registry IS '调度 worker 注册表，记录 worker 的注册状态、心跳时间、插件能力和运行元信息';
```

### 1.3 字段 / 列模型

#### scheduler_worker_registry

| DB 列 | DB 类型 | Java 字段 | Java 类型 | 必填 | 默认值 | 约束 / 索引 | DB 注释 | 说明 |
|-------|---------|-----------|-----------|------|--------|-------------|---------|------|
| `id` | `uuid` | `id` | `UUID` | 是 | 由 `workerCode` 字节生成 | primary key | 主键 | worker 注册记录主键，对应调度侧 `Worker.id` 和任务链路 `workerId`；生成规则为 `UUID.nameUUIDFromBytes(workerCode.getBytes(UTF_8))` |
| `worker_code` | `varchar(128)` | `workerCode` | `String` | 是 | agent 上报 | unique | worker编码 | agent 稳定编码，对应 `Worker.workerCode` |
| `host_name` | `varchar(128)` | `hostName` | `String` | 是 | agent 上报 | 无 | 主机名称 | worker 主机名称，用于展示和运行信息记录 |
| `host` | `varchar(45)` | `host` | `String` | 是 | agent 上报 | unique(`host`,`port`) | IP地址 | agent HTTP 地址 IP 或 hostname |
| `port` | `int4` | `port` | `Integer` | 是 | agent 上报 | unique(`host`,`port`) | 端口 | agent HTTP 端口 |
| `status` | `int4` | `status` | `Integer` | 是 | `1` | 无 | 状态：0-下线 1-上线 | 对应 `Worker.STATUS_DOWN/STATUS_UP` |
| `zone` | `varchar(64)` | `zone` | `String` | 否 | 无 | 无 | 区域/分组，预留字段 | 预留 worker 分组能力，第一版不参与调度选择 |
| `plugins` | `varchar(256)` | `plugins` | `String` | 否 | 无 | 无 | 组件类型列表，逗号分隔 | 对应 `Worker.pluginTypes`，按英文逗号保存 |
| `register_time` | `timestamp(6)` | `registerTime` | `Date` | 否 | 注册时当前时间 | 无 | 注册时间 | agent 注册或首次恢复上线时间 |
| `last_heartbeat_time` | `timestamp(6)` | `lastHeartbeatTime` | `Date` | 否 | 心跳时当前时间 | 无 | 最近心跳时间 | agent 心跳更新，不代表界面修改 |
| `is_active` | `int2` | `isActive` | `Integer` | 是 | `1` | 无 | 是否有效：1-有效 0-无效 | 调度查找 worker 时必须过滤 `is_active = 1` |
| `remark` | `varchar(255)` | `remark` | `String` | 否 | 无 | 无 | 资源说明 | 界面维护字段 |
| `creator` | `varchar(100)` | `creator` | `String` | 是 | 当前用户 / `system` | 无 | 创建人 | 界面或系统创建记录的审计字段，agent 心跳不更新 |
| `updater` | `varchar(100)` | `updater` | `String` | 是 | 当前用户 / `system` | 无 | 修改人 | 界面或系统修改记录的审计字段，agent 心跳不更新 |
| `create_time` | `timestamp(6)` | `createTime` | `Date` | 是 | 当前时间 | 无 | 创建时间 | 记录创建时间 |
| `update_time` | `timestamp(6)` | `updateTime` | `Date` | 是 | 当前时间 | 无 | 修改时间 | 记录更新时间；注册、心跳、下线都会更新 |
| `tenant_id` | `uuid` | `tenantId` | `UUID` | 否 | 无 | 无 | 租户ID | 预留字段，第一版不做租户隔离调度 |

### 1.4 种子 / 初始化数据

无。

## 2. Java 后端数据模型

### 2.1 持久化模型

| Java 字段 | DB 列 | Java 类型 | 注解 / 类型处理器 | 说明 |
|-----------|-------|-----------|-------------------|------|
| `WorkerRegistryEntity.id` | `id` | `UUID` | `@TableId("id")` | 持久化记录主键 |
| `WorkerRegistryEntity.workerCode` | `worker_code` | `String` | `@TableField("worker_code")` | 对应 `Worker.workerCode` |
| `WorkerRegistryEntity.hostName` | `host_name` | `String` | `@TableField("host_name")` | 主机名称 |
| `WorkerRegistryEntity.host` | `host` | `String` | `@TableField("host")` | IP 地址 |
| `WorkerRegistryEntity.port` | `port` | `Integer` | `@TableField("port")` | 端口 |
| `WorkerRegistryEntity.status` | `status` | `Integer` | `@TableField("status")` | worker 状态 |
| `WorkerRegistryEntity.zone` | `zone` | `String` | `@TableField("zone")` | 预留分组 |
| `WorkerRegistryEntity.plugins` | `plugins` | `String` | `@TableField("plugins")` | 插件类型逗号分隔字符串 |
| `WorkerRegistryEntity.registerTime` | `register_time` | `Date` | `@TableField("register_time")` | 注册时间 |
| `WorkerRegistryEntity.lastHeartbeatTime` | `last_heartbeat_time` | `Date` | `@TableField("last_heartbeat_time")` | 最近心跳时间 |
| `WorkerRegistryEntity.isActive` | `is_active` | `Integer` | `@TableField("is_active")` | 是否有效 |
| `WorkerRegistryEntity.remark` | `remark` | `String` | `@TableField("remark")` | 说明 |
| `WorkerRegistryEntity.tenantId` | `tenant_id` | `UUID` | `@TableField("tenant_id")` | 预留租户 ID |
| `WorkerRegistryEntity.creator/updater/createTime/updateTime` | 审计列 | `String/Date` | 继承字段或显式字段 | 界面和系统审计字段 |

### 2.2 API 输入模型

| 对象 | 类型 | 场景 | 字段 | 字段类型 | 校验 / 查询行为 | 说明 |
|------|------|------|------|----------|------------------|------|
| `Worker` | `Action` | agent 注册、心跳、下线 | `id` | `String` | manager 返回后必填 | 对应 `scheduler_worker_registry.id` |
| `Worker` | `Action` | agent 注册、心跳、下线 | `workerCode` | `String` | 注册必填；生成规则由 agent 端定义 | 对应 `worker_code` |
| `Worker` | `Action` | agent 注册、心跳、下线 | `ip` | `String` | 必填 | 对应 `host` |
| `Worker` | `Action` | agent 注册、心跳、下线 | `port` | `Integer` | 必填 | 对应 `port` |
| `Worker` | `Action` | agent 注册、心跳、下线 | `hostName` | `String` | 必填 | 对应 `host_name` |
| `Worker` | `Action` | agent 注册、心跳、下线 | `pluginTypes` | `List<String>` | 可选 | 对应 `plugins` |
| `TaskResult` | `Action` | agent 上报任务结果 | `taskInstanceId`、`flowInstanceId`、`taskName`、`taskState`、`workerResult` | 多类型 | `taskState` 必填 | 由 `MasterService.getTaskAction().asyncHandle` 推进状态机；worker 侧运行信息只放入 `workerResult` |
| `WorkerResult` | `ActionPart` | `TaskResult.workerResult` | `outputVars`、`workerId`、`appId`、`workDirPath`、`message`、`pluginLogUri` | 多类型 | 可选 | manager 保存到任务实例 `worker_result`，不包含 `exitCode` |

### 2.3 API 输出模型

| 对象 | 类型 | 场景 | 字段 | 字段类型 | 来源 | 说明 |
|------|------|------|------|----------|------|------|
| `Result<Worker>` | `ActionResult` | agent 注册、心跳、下线 | `data` | `Worker` | controller 执行结果 | 返回 manager 侧 `Worker.id` |
| `Result<Boolean>` | `ActionResult` | 任务结果上报 | `data` | `Boolean` | controller 执行结果 | 内部接口统一返回 |
| `Result<TaskResult>` | `ActionResult` | manager 调用 agent submit/stop/kill/finish | `data` | `TaskResult` | agent 响应 | manager 解析后返回给 master task operator |

### 2.4 Service 模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|------|------|------|----------|----------|------|
| `WorkerStorageImpl` | worker 注册表持久化 | `Worker` / `WorkerRegistryEntity` | 多类型 | manager 运行期 | 实现 `datafusion-scheduler-master` 的 `WorkerStorage` |
| `WorkerOperator` | UI 手工操作端口 | `workerId` | `String` | manager 运行期 | 定义有效、无效、删除等非 agent 生命周期操作 |
| `MasterStorage` | 调度核心存储聚合 | `TriggerStorageImpl`、`FlowStorageImpl`、`TaskStorageImpl`、`EventStorageImpl` | Storage | Spring Bean 生命周期 | manager 将已有定义、实例和事件表适配给 master |

### 2.5 集成模型

| 对象 | 集成目标 | 方向 | 字段 | 字段类型 | 转换规则 | 说明 |
|------|----------|------|------|----------|----------|------|
| `Worker` | `scheduler_worker_registry` | agent -> manager DB -> master | `id/workerCode/ip/port/hostName/pluginTypes/status/registerTime/lastHeartbeatTime/updateTime` | 多类型 | `id` 映射 registry 主键 `id`；`workerCode` 映射 `worker_code`；`pluginTypes` 与 `plugins` 逗号字符串互转；毫秒时间戳与 `timestamp(6)` 互转 | worker 注册、心跳和下线 |
| `FlowInfoEntity` | `FlowInfo` | manager DB -> master | 流程定义字段 | 多类型 | 见 [scheduler-flow-data-define.md](./scheduler-flow-data-define.md) | `FlowStorageImpl` 适配 |
| `FlowInstanceEntity` | `FlowInstance` / `TriggerInstance` | manager DB <-> master | 流程实例字段 | 多类型 | 见 [scheduler-instance-data-define.md](./scheduler-instance-data-define.md) | 流程实例和触发实例共用表 |
| `TaskInfoEntity` / `TaskLinkEntity` | `TaskInfo` / `TaskLink` | manager DB -> master | 任务定义和依赖字段 | 多类型 | 见 [scheduler-task-data-define.md](./scheduler-task-data-define.md) | `TaskStorageImpl` 适配 |
| `TaskInstanceEntity` | `TaskInstance` | manager DB <-> master | 任务实例字段 | 多类型 | 见 [scheduler-instance-data-define.md](./scheduler-instance-data-define.md) | 保存任务实例状态和 worker 结果 |
| `TriggerInfoEntity` + `FlowInfoEntity` | `TriggerInfo` | manager DB <-> master | 触发器和流程调度字段 | 多类型 | 见 [scheduler-trigger-data-define.md](./scheduler-trigger-data-define.md) | `TriggerInfo` 是跨表复合视图 |
| `EventInstanceEntity` | `GlobalEvent` | manager DB <-> master | 事件实例字段 | 多类型 | 见 [scheduler-event-data-define.md](./scheduler-event-data-define.md) | `EventStorageImpl` 适配 |

### 2.6 状态 / 枚举模型

| 字段 / 枚举 | Owner object | Values | Storage type | Display label | Conversion rule | Notes |
|-------------|--------------|--------|--------------|---------------|-----------------|-------|
| `status` | `Worker` / `scheduler_worker_registry` | `0` / `1` | `int4` | 下线 / 上线 | 直接映射 `Worker.STATUS_DOWN`、`Worker.STATUS_UP` | 调度查找仅使用 `status = 1` |
| `is_active` | `scheduler_worker_registry` | `0` / `1` | `int2` | 无效 / 有效 | manager 查询过滤 | 调度查找必须同时满足 `is_active = 1` |
| `tenant_id` | `scheduler_worker_registry` | UUID | `uuid` | 租户 ID | 第一版不参与过滤 | 暂无租户隔离调度 |

## 3. 前端数据模型

### 3.1 API Client 模型

无。

### 3.2 页面查询模型

无。

### 3.3 页面展示模型

无。

## 4. 数据映射规则

### 4.1 API Mapping

| API | Request object | Response data | Response wrapper | Notes |
|-----|----------------|---------------|------------------|-------|
| `POST /internal/schedule/worker/register` | `Worker` | `Worker` | `Result<T>` | agent 注册 worker，按 `workerCode` 匹配，返回稳定生成的 registry 主键 `id` |
| `POST /internal/schedule/worker/heartbeat` | `Worker` | `Worker` | `Result<T>` | agent 心跳，只按 `id` 更新状态和心跳时间 |
| `POST /internal/schedule/worker/offline` | `Worker` | `Worker` | `Result<T>` | agent 主动下线，只按 `id` 更新状态 |
| `POST /internal/schedule/worker/tasks` | `Worker` | `List<TaskRequest>` | `Result<T>` | agent 注册成功后按 `id` 拉取属于自己的未完成任务清单 |
| `POST /internal/schedule/reportTaskResult` | `TaskResult` | `Boolean` | `Result<T>` | agent 上报任务结果 |

### 4.2 Layer Conversion

| Direction | Conversion rule | Special handling |
|-----------|-----------------|------------------|
| `Worker` -> `WorkerRegistryEntity` | `id` 由 `workerCode` 稳定生成，`workerCode` -> `workerCode`，`ip` -> `host`，`hostName` -> `hostName`，`port` -> `port`，`status` -> `status`，`pluginTypes` -> `plugins` | 注册写入 `register_time`；心跳只按 `id` 更新 `last_heartbeat_time`、`status`、`update_time` |
| `WorkerRegistryEntity` -> `Worker` | `id` -> `id`，`workerCode` -> `workerCode`，`host` -> `ip`，`hostName` -> `hostName`，`port` -> `port`，`status` -> `status`，`plugins` -> `pluginTypes` | DB 恢复时仅加载 `is_active = 1` 的记录；启动后可先恢复已有 worker，再等待 agent 心跳刷新状态和插件能力 |
| `WorkerStorage.getWorker` | `workerId` 只查 `scheduler_worker_registry.id` | 任务链路使用 `workerId`，scheduler 框架层不按 `workerCode` 查询 |
| `WorkerStorageImpl.getWorkers` -> `WorkerManager.lookupWorker` | 查询 `is_active = 1` 且状态可用的 worker，再由 `WorkerManager` 按插件类型过滤 | 第一版不按 `tenant_id`、`zone` 过滤 |
| agent 注册 -> existing worker | 只按 `worker_code` 定位；找不到时使用 `workerCode` 派生 UUID 新增记录 | heartbeat/offline 只按 `id` 定位；注册不覆盖已有 `is_active` |
| agent 启动恢复任务 | 注册成功后调用 `WorkerListener.getTaskInsByWorkerId(workerId)` | 只返回属于当前 worker 且未完成的任务清单，agent 再结合本地状态文件恢复监听 |

## 5. 复用结构

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Worker` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/Worker.java` | 内部接口和调度模型复用 | worker 注册、心跳、选择和状态判断 |
| `TaskRequest` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/TaskRequest.java` | manager 调用 agent | 提交、停止、杀死和完成任务请求 |
| `TaskResult` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/TaskResult.java` | agent 上报和 manager 调用 agent 响应 | 推进 master 状态机 |
| `WorkerResult` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/WorkerResult.java` | `TaskResult.workerResult` 子结构 | worker 执行结果和日志入口 |
| `Result<T>` | `datafusion-common-spring` | 内部接口响应包装 | 统一 API 返回 |
| `MasterStorage` | `datafusion-scheduler-master` | 调度核心存储聚合 | 聚合 trigger、flow、task、event storage |
| `WorkerStorage` | `datafusion-scheduler-master` | worker 注册表契约 | manager 实现 DB 持久化适配 |
| `WorkerListener` | `datafusion-scheduler-master` | worker 生命周期契约 | agent 注册、心跳、下线、超时下线和任务清单恢复 |
| `WorkerOperator` | `datafusion-scheduler-master` | worker 手工操作契约 | UI 有效、无效和删除 |
