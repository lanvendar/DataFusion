# scheduler-instance 数据定义文档

> 本文档是数据库、Java 后端、前端数据结构的唯一事实源。设计文档只说明数据流、行为和约束，不重复完整字段定义。

## 1. 数据库数据模型

### 1.1 表模型

| 表名 | 操作 | 主键 | 候选键 / 唯一业务键 | 分区策略 | 表注释 | 说明 |
|------------|-----------|-------------|-------------------------------------|--------------------|---------------|-------|
| `scheduler_flow_instance` | 复用 | `id uuid` | 无 | 无 | 流程运行实例表 | DDL 参照 `datafusion-manager/src/main/resources/init_db/init_ddl.sql` |
| `scheduler_flow_instance_his` | 新增 | `id uuid` | 无 | 无 | 流程运行实例历史表 | 结构镜像 `scheduler_flow_instance`，保存归档后的成功流程实例 |
| `scheduler_task_instance` | 复用 | `id uuid` | 无 | 无 | 任务运行实例表 | DDL 参照 `datafusion-manager/src/main/resources/init_db/init_ddl.sql` |
| `scheduler_task_instance_his` | 新增 | `id uuid` | 无 | 无 | 任务运行实例历史表 | 结构镜像 `scheduler_task_instance`，保存归档后的成功任务实例 |
| `scheduler_event_instance` | 复用 | `id uuid` | 无 | 无 | 事件实例表 | DDL 参照 `datafusion-manager/src/main/resources/init_db/init_ddl.sql` |

当前不新增 `scheduler_event_instance_his`。事件实例继续查询 `scheduler_event_instance`。

`TaskInstanceLog` 不新增表。日志能力基于实时或历史任务实例的 `worker_result.workDirPath` 定位 agent 标准任务日志，
基于 `worker_result.pluginLogUri` 定位插件日志。

### 1.2 DDL / 迁移

新增历史表，来源为 [init_ddl.sql](../../datafusion-manager/src/main/resources/init_db/init_ddl.sql):

| 表名 | 迁移规则 | 说明 |
|------|----------|-------|
| `scheduler_flow_instance_his` | 新增表，字段、类型、主键和注释镜像 `scheduler_flow_instance` | 归档时保留原 `id`、运行时间、状态、DAG 快照和审计字段 |
| `scheduler_task_instance_his` | 新增表，字段、类型、主键和注释镜像 `scheduler_task_instance` | 归档时保留原 `id`、worker 信息、`worker_result` 和审计字段 |

`viewType` 不是 DDL 字段，不写入任何实例表。它只作为前端和 API 查询参数，用于选择实时表或历史表。

### 1.3 字段 / 列模型

#### scheduler_flow_instance

| DB 列 | DB 类型 | Java 字段 | Java 类型 | 是否必填 | 默认值 | 约束 / 索引 | DB 注释 | 说明 |
|-----------|---------|------------|-----------|----------|---------|--------------------|------------|-------|
| `id` | `uuid` | `id` | `UUID` | 是 | 无 | primary key | 实例id | 流程实例 ID |
| `flow_id` | `uuid` | `flowId` | `UUID` | 是 | 无 | 无 | 流程ID | 流程定义 ID |
| `flow_name` | `varchar(64)` | `flowName` | `String` | 是 | 无 | 无 | 流程名称 | 流程名称快照 |
| `flow_code` | `varchar(50)` | `flowCode` | `String` | 否 | 无 | 无 | 流程编码 | 流程编码快照 |
| `flow_type` | `varchar` | `flowType` | `String` | 是 | 无 | 无 | 流程类型 | 流程类型 |
| `status` | `varchar(50)` | `status` | `String` | 是 | 无 | 无 | 流程实例状态 | 保存 `StatusEnum.stateType` |
| `trigger_id` | `varchar` | `triggerId` | `String` | 是 | 无 | 无 | 发布版本 | 触发器 ID，现有注释与字段语义不完全一致 |
| `publish_version` | `int8` | `publishVersion` | `Long` | 否 | 无 | 无 | 发布版本 | 发布版本 |
| `flow_param` | `json` | `flowParam` | `JsonNode` | 否 | 无 | 无 | 流程变量参数 | 流程变量参数快照，遵循 `ParamData.vars` |
| `dep_event_ids` | `varchar` | `depEventIds` | `String` | 否 | 无 | 无 | 全局依赖事件ID，英文逗号分割 | 依赖事件 ID |
| `event_id` | `uuid` | `eventId` | `UUID` | 否 | 无 | 无 | 事件ID | 流程产生事件 ID |
| `schedule_time` | `int8` | `scheduleTime` | `Long` | 否 | 无 | 无 | 调度时间 | 毫秒时间戳 |
| `start_time` | `int8` | `startTime` | `Long` | 否 | 无 | 无 | 开始时间 | 毫秒时间戳 |
| `end_time` | `int8` | `endTime` | `Long` | 否 | 无 | 无 | 结束时间 | 毫秒时间戳 |
| `creator` | `varchar(100)` | `creator` | `String` | 是 | 当前用户 | 无 | 创建人 | 继承自 `BaseEntity` |
| `updater` | `varchar(100)` | `updater` | `String` | 是 | 当前用户 | 无 | 修改人 | 继承自 `BaseEntity` |
| `create_time` | `timestamp(6)` | `createTime` | `Date` | 是 | 当前时间 | 无 | 创建时间 | 继承自 `BaseEntity` |
| `update_time` | `timestamp(6)` | `updateTime` | `Date` | 是 | 当前时间 | 无 | 修改时间 | 继承自 `BaseEntity` |
| `flow_dag_snapshot` | `json` | `flowDagSnapshot` | `JsonNode` | 否 | 无 | 无 | 流程DAG快照 | 流程 DAG 快照 |

#### scheduler_flow_instance_his

字段、类型、Java 字段和约束镜像 `scheduler_flow_instance`。区别只在表名和持久化模型:

- 表名: `scheduler_flow_instance_his`。
- Java 持久化模型: `FlowInstanceHisEntity`。
- 数据来源: `SchedulerInstanceArchiveScheduleJob` 定时迁移 `StatusEnum.isSuccess()` 为 `true` 的实时流程实例。

#### scheduler_task_instance

| DB 列 | DB 类型 | Java 字段 | Java 类型 | 是否必填 | 默认值 | 约束 / 索引 | DB 注释 | 说明 |
|-----------|---------|------------|-----------|----------|---------|--------------------|------------|-------|
| `id` | `uuid` | `id` | `UUID` | 是 | 无 | primary key | 主键 | 任务实例 ID |
| `flow_id` | `uuid` | `flowId` | `UUID` | 是 | 无 | 无 | 流程ID | 流程定义 ID |
| `flow_instance_id` | `uuid` | `flowInstanceId` | `UUID` | 是 | 无 | 无 | 流程实例ID | 流程实例 ID |
| `task_id` | `uuid` | `taskId` | `UUID` | 是 | 无 | 无 | 任务ID | 任务定义 ID |
| `task_type` | `varchar` | `taskType` | `String` | 是 | 无 | 无 | 任务类型 | 任务类型 |
| `task_name` | `varchar` | `taskName` | `String` | 是 | 无 | 无 | 任务名称 | 任务名称快照 |
| `task_code` | `varchar` | `taskCode` | `String` | 是 | 无 | 无 | 任务编码 | 任务编码快照 |
| `description` | `varchar` | `description` | `String` | 否 | 无 | 无 | 任务描述 | 任务描述 |
| `task_param` | `json` | `taskParam` | `JsonNode` | 否 | 无 | 无 | 任务变量参数 | 运行期变量参数快照，遵循 `ParamData.vars` |
| `task_data` | `json` | `taskData` | `JsonNode` | 否 | 无 | 无 | 渲染后的任务定义 | 渲染后的任务定义 |
| `plugin_data` | `json` | `pluginData` | `JsonNode` | 否 | 无 | 无 | 组件数据 | 执行插件数据 |
| `view` | `json` | `view` | `JsonNode` | 否 | 无 | 无 | 任务视图 | 前端画布视图 |
| `dep_event_ids` | `text` | `depEventIds` | `String` | 否 | 无 | 无 | 依赖事件id | 依赖事件 ID |
| `event_id` | `uuid` | `eventId` | `UUID` | 否 | 无 | 无 | 产生事件id | 任务产生事件 ID |
| `status` | `varchar` | `status` | `String` | 否 | 无 | 无 | 任务实例状态 | 保存 `StatusEnum.stateType` |
| `start_time` | `int8` | `startTime` | `Long` | 否 | 无 | 无 | 任务实例开始时间 | 毫秒时间戳 |
| `end_time` | `int8` | `endTime` | `Long` | 否 | 无 | 无 | 任务实例结束时间 | 毫秒时间戳 |
| `cost_time` | `int4` | `costTime` | `Integer` | 否 | 无 | 无 | 耗时 | 现有表类型为 `int4` |
| `last_instance_id` | `text` | `lastInstanceId` | `String` | 否 | 无 | 无 | 上一个任务实例id | 上游任务实例 ID |
| `next_instance_id` | `text` | `nextInstanceId` | `String` | 否 | 无 | 无 | 下一个任务实例id | 下游任务实例 ID |
| `worker_id` | `uuid` | `workerId` | `UUID` | 否 | 无 | 无 | 执行节点id | worker ID |
| `worker_result` | `json` | `workerResult` | `WorkerResult` | 否 | 无 | 无 | 返回值 | worker 返回结果，只保存 `TaskResult.workerResult` |
| `creator` | `varchar(100)` | `creator` | `String` | 是 | 当前用户 | 无 | 创建人 | 继承自 `BaseEntity` |
| `updater` | `varchar(100)` | `updater` | `String` | 是 | 当前用户 | 无 | 更新人 | 继承自 `BaseEntity` |
| `create_time` | `timestamp(6)` | `createTime` | `Date` | 是 | 当前时间 | 无 | 创建时间 | 继承自 `BaseEntity` |
| `update_time` | `timestamp(6)` | `updateTime` | `Date` | 是 | 当前时间 | 无 | 更新时间 | 继承自 `BaseEntity` |

#### scheduler_task_instance_his

字段、类型、Java 字段和约束镜像 `scheduler_task_instance`。区别只在表名和持久化模型:

- 表名: `scheduler_task_instance_his`。
- Java 持久化模型: `TaskInstanceHisEntity`。
- 数据来源: `SchedulerInstanceArchiveScheduleJob` 定时迁移 `StatusEnum.isSuccess()` 为 `true` 的实时任务实例。

#### scheduler_event_instance

| DB 列 | DB 类型 | Java 字段 | Java 类型 | 是否必填 | 默认值 | 约束 / 索引 | DB 注释 | 说明 |
|-----------|---------|------------|-----------|----------|---------|--------------------|------------|-------|
| `id` | `uuid` | `id` | `UUID` | 是 | 无 | primary key | 主键 | 事件实例 ID |
| `event_id` | `uuid` | `eventId` | `UUID` | 是 | 无 | 无 | 事件id | 事件定义 ID |
| `event_name` | `varchar` | `eventName` | `String` | 是 | 无 | 无 | 事件名称 | 事件名称快照 |
| `event_type` | `varchar` | `eventType` | `String` | 是 | 无 | 无 | 事件类型:TASK,FLOW | 实例表按现状保存 `EventTypeEnum.getType()` 的字符串值，`TASK=1`、`FLOW=2` |
| `flow_instance_id` | `uuid` | `flowInstanceId` | `UUID` | 否 | 无 | 无 | 流程实例id | 关联流程实例 ID |
| `task_instance_id` | `uuid` | `taskInstanceId` | `UUID` | 否 | 无 | 无 | 任务实例id | 关联任务实例 ID |
| `effect_time` | `int8` | `effectTime` | `Long` | 否 | 无 | 无 | 事件生效时间 | 事件生效时间 |
| `effect_begin_time` | `int8` | `effectBeginTime` | `Long` | 否 | 无 | 无 | 事件开始生效时间 | 事件开始生效时间 |
| `effect_end_time` | `int8` | `effectEndTime` | `Long` | 否 | 无 | 无 | 事件结束生效时间 | 事件结束生效时间 |

### 1.4 种子 / 初始化数据

无。

## 2. Java 后端数据模型

### 2.1 持久化模型

| Java 字段 | DB 列 | Java 类型 | 注解 / 类型处理器 | 说明 |
|------------|-----------|-----------|---------------------------|-------|
| `FlowInstanceEntity.*` | `scheduler_flow_instance.*` | `UUID/String/Long/JsonNode/Date` | `@TableName("scheduler_flow_instance")`、`@TableField` | 实时流程实例，继承 `BaseIdEntity` |
| `FlowInstanceHisEntity.*` | `scheduler_flow_instance_his.*` | `UUID/String/Long/JsonNode/Date` | `@TableName("scheduler_flow_instance_his")`、`@TableField` | 历史流程实例，字段镜像 `FlowInstanceEntity` |
| `TaskInstanceEntity.*` | `scheduler_task_instance.*` | `UUID/String/Long/Integer/JsonNode/WorkerResult/Date` | `@TableName("scheduler_task_instance")`、`@TableField` | 实时任务实例，继承 `BaseIdEntity`，`workerResult` 按 `WorkerResult` 结构读写 |
| `TaskInstanceHisEntity.*` | `scheduler_task_instance_his.*` | `UUID/String/Long/Integer/JsonNode/WorkerResult/Date` | `@TableName("scheduler_task_instance_his")`、`@TableField` | 历史任务实例，字段镜像 `TaskInstanceEntity` |
| `EventInstanceEntity.*` | `scheduler_event_instance.*` | `UUID/String/Long` | `@TableName("scheduler_event_instance")`、`@TableId`、`@TableField` | 表无审计字段，不继承 `BaseEntity` |

### 2.2 API 输入模型

| 对象 | 类型 | 场景 | 字段 | 字段类型 | 校验 / 查询行为 | 说明 |
|--------|------|----------|-------|------------|-----------------------------|-------|
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `flowKeyword` | `String` | `flow_name like` 或 `id eq` | 流程名称/ID 搜索框 |
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `taskKeyword` | `String` | `task_name like` 或 `id eq`，流程分页时通过任务实例关联过滤 | 任务名称/ID 搜索框 |
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `status` | `String` | `status eq` | `StatusEnum.stateType` |
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `viewType` | `String` | `REALTIME` 查实时表，`HISTORY` 查历史表；缺省为 `REALTIME` | 页面实时/历史切换，不落库 |
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `scheduleStartTime` / `scheduleEndTime` | `Long` | `schedule_time between` | 流程实例字段 |
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `startTime` / `endTime` | `Long` | `start_time between` | 流程/任务实例字段 |
| `SchedulerInstanceQueryDto` | `Query` | 流程/任务实例查询 | `finishStartTime` / `finishEndTime` | `Long` | `end_time between` | 流程/任务实例字段 |
| `FlowInstanceTaskQueryDto` | `Query` | 展开流程实例任务 | `flowInstanceId` | `UUID` | 必填，`flow_instance_id eq` | 流程实例 ID |
| `FlowInstanceTaskQueryDto` | `Query` | 展开流程实例任务 | `viewType` | `String` | `REALTIME` 查 `scheduler_task_instance`，`HISTORY` 查 `scheduler_task_instance_his`；缺省为 `REALTIME` | 与流程实例主列表保持一致 |
| `EventInstanceQueryDto` | `Query` | 事件实例查询 | `eventKeyword` | `String` | `event_name like` 或 `event_id eq` | 事件名称/ID 搜索 |
| `EventInstanceQueryDto` | `Query` | 事件实例查询 | `eventType` | `String` | `event_type eq` | 实例表查询值为 `1` / `2`，前端展示映射为任务 / 流程 |
| `EventInstanceQueryDto` | `Query` | 事件实例查询 | `flowInstanceId` / `taskInstanceId` | `UUID` | `eq` | 关联实例 |
| `EventInstanceQueryDto` | `Query` | 事件实例查询 | `effectStartTime` / `effectEndTime` | `Long` | `effect_time between` | 事件生效时间范围 |
| `TaskInstanceLogQueryDto` | `Query` | 日志读取 | `flowInstanceId` | `UUID` | 必填 | 流程实例 ID |
| `TaskInstanceLogQueryDto` | `Query` | 日志读取 | `taskInstanceId` | `UUID` | 必填 | 任务实例 ID |
| `TaskInstanceLogQueryDto` | `Query` | 日志读取 | `logType` | `String` | 必填，`LOG` / `ERROR` / `STATUS` / `PLUGIN` | 日志类型 |
| `TaskInstanceLogQueryDto` | `Query` | 日志读取 | `offset` / `limit` | `Long` / `Integer` | 可选；刷新时 `offset=0` | 分段读取；刷新重读当前日志，加载更多按 `nextOffset` 追加 |
| `SchedulerInstanceActionDto` | `Action` | 流程实例操作 | `flowInstanceId` | `UUID` | 必填 | 流程实例 ID |
| `SchedulerInstanceActionDto` | `Action` | 流程实例操作 | `actionType` | `String` | 必填，允许 `SUBMIT` / `STOP`；不开放 `REINIT` | 映射 `MasterService.getFlowAction()` 公开能力 |
| `SchedulerInstanceActionDto` | `Action` | 任务实例操作 | `taskInstanceId` | `UUID` | 必填 | 任务实例 ID |
| `SchedulerInstanceActionDto` | `Action` | 任务实例操作 | `actionType` | `String` | 必填，允许 `SUBMIT` / `STOP` / `KILL` / `RESTART` / `ENFORCE_SUCCESS`；任务实例行不开放 `REINIT` | 映射 `MasterService.getTaskAction()` 公开能力；`TaskAction.taskReInit(FlowInstance)` 是 `flowReInit` 联动重建任务实例的内部入口，不是单任务实例操作 |

### 2.3 API 输出模型

| 对象 | 类型 | 场景 | 字段 | 字段类型 | 来源 | 说明 |
|--------|------|----------|-------|------------|--------|-------|
| `FlowInstanceDto` | `Page/Detail` | 流程实例列表和详情 | `id`、`flowId`、`flowName`、`flowCode`、`flowType`、`status`、`triggerId`、`publishVersion`、`scheduleTime`、`startTime`、`endTime`、`duration`、`flowDagSnapshot`、`availableActions` | `UUID/String/Long/JsonNode/List` | `FlowInstanceEntity` / `FlowInstanceHisEntity` | `duration` 为派生字段；`availableActions` 由后端按实时实例状态计算 |
| `TaskInstanceDto` | `Page/ListItem/Detail` | 任务实例列表和详情 | `id`、`flowInstanceId`、`taskId`、`taskType`、`taskName`、`taskCode`、`status`、`startTime`、`endTime`、`costTime`、`lastInstanceId`、`nextInstanceId`、`workerId`、`workerResult`、`workerResultText`、`workDirPath`、`availableActions` | `UUID/String/Long/Integer/WorkerResult/List` | `TaskInstanceEntity` / `TaskInstanceHisEntity` | `workerResultText` 和 `workDirPath` 由 `workerResult.message/workDirPath` 派生；上下游实例 ID 支持依赖图只读展示；`availableActions` 由后端按实时实例状态计算 |
| `EventInstanceDto` | `Page/Detail` | 事件实例列表和详情 | `id`、`eventId`、`eventName`、`eventType`、`flowInstanceId`、`taskInstanceId`、`effectTime`、`effectBeginTime`、`effectEndTime` | `UUID/String/Long` | `EventInstanceEntity` | 无 |
| `TaskInstanceLogDto` | `Detail` | 日志内容 | `logType`、`path`、`content`、`nextOffset`、`hasMore` | `String/Long/Boolean` | 共享文件系统 | 不落库 |
| `TaskInstanceDependencyDto` | `ViewModel` | 任务依赖图只读展示 | `flowInstanceId`、`taskInstanceId`、`flowDagSnapshot`、`lastInstanceId`、`nextInstanceId` | `UUID/JsonNode/String` | `FlowInstanceDto` + `TaskInstanceDto` | 前端可直接由详情数据组装，不要求新增后端接口 |
| `SchedulerInstanceAvailableActionDto` | `ViewModel` | 流程/任务实例可用操作 | `actionType`、`label`、`confirmRequired` | `String/String/Boolean` | `SchedulerInstanceActionPolicy` | 前端仅渲染后端返回的可用操作；所有操作均需要二次确认 |
| `Boolean` | `ActionResult` | 流程/任务实例操作 | `true/false` | `Boolean` | `FlowInstanceService` / `TaskInstanceService` | 操作成功只表示指令已提交给 master action，不代表异步执行最终成功 |

### 2.4 Service 模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|--------|----------|-------|------------|-----------|-------|
| `SchedulerInstanceQueryDto` | 后端分页过滤 | 查询条件字段 | `String/Long` | 请求内 | 被 Flow/Task 实例查询复用 |
| `FlowInstanceTaskQueryDto` | 展开流程实例任务 | `flowInstanceId`、`viewType` | `UUID/String` | 请求内 | `viewType` 只决定任务实例表路由 |
| `SchedulerInstanceArchiveBatch` | 成功实例归档 | `batchSize`、成功状态集合、流程定义版本存在性 | `Integer/List<String>/Boolean` | 定时任务内 | 成功状态集合来自 `StatusEnum.isSuccess()`；当 `scheduler_flow_info(id, publish_version)` 不存在时，对应成功实例不再保留实时记录 |
| `TaskInstanceLogDto` | 日志读取响应 | 日志内容和偏移 | `String/Long/Boolean` | 请求内 | 不持久化 |
| `SchedulerInstanceActionDto` | 流程/任务实例操作 | `flowInstanceId`、`taskInstanceId`、`actionType` | `UUID/String` | 请求内 | 操作入口复用 `ActionType`，Service 根据流程或任务实例加载实时表实体并转换为 scheduler model |
| `SchedulerInstanceAvailableActionDto` | 流程/任务实例可用操作 | `actionType`、`label`、`confirmRequired` | `String/String/Boolean` | 响应内 | 后端根据实例类型和 `StatusEnum` 统一计算；前端不自行维护状态矩阵 |

### 2.5 集成模型

| 对象 | 集成目标 | 方向 | 字段 | 字段类型 | 转换规则 | 说明 |
|--------|--------------------|-----------|-------|------------|-----------------|-------|
| `FlowInstanceEntity` | `datafusion-scheduler-master` | master -> manager DB | `status`、`flowDagSnapshot`、运行时间 | `String/JsonNode/Long` | `FlowStorageImpl` 转换并保存 | 运行态落库 |
| `TaskInstanceEntity` | `datafusion-scheduler-master` / worker result | master/worker -> manager DB | `status`、`workerId`、`workerResult` | `String/UUID/WorkerResult` | `TaskStorageImpl` 转换并保存；`status` 使用 `StatusEnum.stateType`；`workerResult` 保存 `TaskResult.workerResult` | 任务运行态、返回值和日志路径 |
| `EventInstanceEntity` | `datafusion-scheduler-master` | master -> manager DB | `eventId`、`effectTime`、`eventType` | `UUID/Long/String` | `EventStorageImpl` 转换并保存；`eventType` 使用 `EventTypeEnum.getType()` 的字符串值 | 事件实例 |
| `FlowInstanceHisEntity` / `TaskInstanceHisEntity` | 归档定时任务 | realtime DB -> his DB | 与实时表一致 | 与实时表一致 | 实时表记录状态满足 `StatusEnum.isSuccess()` 后复制到对应历史表，再删除实时表记录；仍存在流程定义版本时保留最新实时实例，流程定义版本不存在时不保留 | 成功实例归档 |
| `TaskInstanceLogDto` | agent / 共享日志目录 | file -> API | `content`、`nextOffset` | `String/Long` | 标准日志按 `TaskResult.workerResult.workDirPath` 和 `TaskRuntimeFiles` 拼接；插件日志读取 `workerResult.pluginLogUri` | 日志读取 |

### 2.6 状态 / 枚举模型

| 字段 / 枚举 | 所属对象 | 取值 | 存储类型 | 展示标签 | 转换规则 | 说明 |
|--------------|--------------|--------|--------------|---------------|-----------------|-------|
| `status` | `FlowInstanceDto` / `TaskInstanceDto` | `StatusEnum` | `varchar` | 前端按枚举映射展示 | 保存和查询均使用 `StatusEnum.stateType`，后端通过 `StatusEnum.fromString()` 转换 | 成功归档判断使用 `StatusEnum.isSuccess()`，即 `RUN_SUCCESS` / `ENFORCE_SUCCESS` |
| `eventType` | `EventInstanceDto` | `EventTypeEnum` | `varchar` | `1=任务`、`2=流程` | 实例表保存 `EventTypeEnum.getType()` 的字符串值，前端展示时映射为业务标签 | 保持现有实例表存储方式 |
| `viewType` | `SchedulerInstanceQueryDto` / `FlowInstanceTaskQueryDto` | `REALTIME`、`HISTORY` | 不落库 | 实时、历史 | `REALTIME` 路由实时表，`HISTORY` 路由历史表 | 页面 tab，不参与状态判断 |
| `logType` | `TaskInstanceLogQueryDto` | `LOG`、`ERROR`、`STATUS`、`PLUGIN` | 不落库 | 标准日志、错误日志、状态日志、插件日志 | 映射到不同日志文件 | 日志读取类型 |
| `flowActionType` | `SchedulerInstanceActionDto` | `SUBMIT`、`STOP` | 不落库 | 提交、停止 | `FlowInstanceService` 映射到 `FlowAction.flowSubmit`、`flowStop` | 仅操作实时流程实例；不开放 `REINIT` |
| `taskActionType` | `SchedulerInstanceActionDto` | `SUBMIT`、`STOP`、`KILL`、`RESTART`、`ENFORCE_SUCCESS` | 不落库 | 提交、停止、强制停止、重启、强制成功 | `TaskInstanceService` 映射到 `TaskAction.taskSubmit`、`taskStop`、`taskKill`、`taskRestart`、`taskEnforceSuccess` | 不开放任务实例 `REINIT`；`TaskAction.taskReInit(FlowInstance)` 仅作为流程重新初始化的联动入口 |
| `flowAvailableActions` | `FlowInstanceDto.availableActions` | `SUBMIT`、`STOP` | 不落库 | 提交、停止 | `SchedulerInstanceActionPolicy` 根据流程状态计算 | `INIT_SUCCESS` / `WAIT_DEPENDENT` 显示 `SUBMIT`；`RUNNING` 显示 `STOP`；不显示 `REINIT` |
| `taskAvailableActions` | `TaskInstanceDto.availableActions` | `SUBMIT`、`STOP`、`KILL`、`RESTART`、`ENFORCE_SUCCESS` | 不落库 | 提交、停止、强制停止、重启、强制成功 | `SchedulerInstanceActionPolicy` 根据任务状态计算 | 状态矩阵见设计文档；所有操作 `confirmRequired=true` |

## 3. 前端数据模型

### 3.1 API Client 模型

| API | 请求对象 | 响应对象 | 响应包装 | Query key / 缓存 | 说明 |
|-----|----------------|-----------------|------------------|-------------------|-------|
| `POST /api/scheduler/flow/instance/page` | `PageQuery<SchedulerInstanceQueryDto>` | `PageResponse<FlowInstanceDto>` | `Result<T>` | `scheduler-instance-flow` | 主列表 |
| `GET /api/scheduler/flow/instance/{id}` | 路径参数 `UUID id` | `FlowInstanceDto` | `Result<T>` | `scheduler-instance-flow-detail` | 详情 |
| `POST /api/scheduler/task/instance/page` | `PageQuery<SchedulerInstanceQueryDto>` | `PageResponse<TaskInstanceDto>` | `Result<T>` | `scheduler-instance-task` | 任务实例独立分页 |
| `POST /api/scheduler/task/instance/listByFlowInstance` | `FlowInstanceTaskQueryDto` | `TaskInstanceDto[]` | `Result<T>` | `scheduler-instance-task-by-flow` | 展开行懒加载，按 `viewType` 查询实时或历史任务表 |
| `GET /api/scheduler/task/instance/{id}` | 路径参数 `UUID id` | `TaskInstanceDto` | `Result<T>` | `scheduler-instance-task-detail` | 详情 |
| `POST /api/scheduler/event/instance/page` | `PageQuery<EventInstanceQueryDto>` | `PageResponse<EventInstanceDto>` | `Result<T>` | `scheduler-instance-event` | 事件实例 |
| `GET /api/scheduler/event/instance/{id}` | 路径参数 `UUID id` | `EventInstanceDto` | `Result<T>` | `scheduler-instance-event-detail` | 详情 |
| `POST /api/scheduler/task/instance/log/content` | `TaskInstanceLogQueryDto` | `TaskInstanceLogDto` | `Result<T>` | `scheduler-instance-log` | 日志内容 |
| `GET /api/scheduler/task/instance/log/filebrowser/{taskInstanceId}` | 路径参数 `UUID taskInstanceId` | 重定向响应 | HTTP Redirect / Error | `scheduler-instance-filebrowser` | 后端按任务运行目录跳转 FileBrowser |
| `POST /api/scheduler/flow/instance/action` | `SchedulerInstanceActionDto` | `Boolean` | `Result<T>` | `scheduler-instance-flow-action` | 流程实例操作 |
| `POST /api/scheduler/task/instance/action` | `SchedulerInstanceActionDto` | `Boolean` | `Result<T>` | `scheduler-instance-task-action` | 任务实例操作 |

### 3.2 页面查询模型

| 字段 | 类型 | 默认值 | 来源 | 映射到 API | 说明 |
|-------|------|---------|--------|-------------|-------|
| `flowKeyword` | `string` | `""` | 输入框 | `SchedulerInstanceQueryDto.flowKeyword` | 流程名称/ID |
| `taskKeyword` | `string` | `""` | 输入框 | `SchedulerInstanceQueryDto.taskKeyword` | 任务名称/ID |
| `status` | `string` | `undefined` | Select | `SchedulerInstanceQueryDto.status` | 实例状态 |
| `viewType` | `"REALTIME" | "HISTORY"` | `"REALTIME"` | Segmented/Tab | `SchedulerInstanceQueryDto.viewType` / `FlowInstanceTaskQueryDto.viewType` | 表路由: 实时表 / 历史表 |
| `scheduleRange` | `[number, number]` | `undefined` | DateRangePicker | `scheduleStartTime/scheduleEndTime` | 调度时间范围 |
| `startRange` | `[number, number]` | `undefined` | DateRangePicker | `startTime/endTime` | 开始时间范围 |
| `finishRange` | `[number, number]` | `undefined` | DateRangePicker | `finishStartTime/finishEndTime` | 结束时间范围 |

### 3.3 页面展示模型

| 对象 | 场景 | 字段 | 类型 | 来源 | 格式化 / 展示规则 | 说明 |
|--------|----------|-------|------|--------|---------------------------|-------|
| `FlowInstanceRow` | 主表行 | `flowName`、`id`、`status`、`scheduleTime`、`startTime`、`endTime`、`duration`、`availableActions` | `string/number/list` | `FlowInstanceDto` | 时间戳格式化为日期时间；状态用 Tag；起止时间按开始/结束两行展示；操作按钮按 `availableActions` 渲染 | 可展开 |
| `TaskInstanceRow` | 展开行 | `taskName`、`id`、`status`、`workerResultText`、`workerResult.message`、`workerResult.appId`、`workerResult.workerId`、`workDirPath`、`workerResult.pluginLogUri`、`startTime`、`endTime`、`costTime`、`availableActions` | `string/number/list` | `TaskInstanceDto` | 返回结果列按文本行展示后端返回字段，不承载跳转操作；`workDirPath` 存在时操作列渲染“工作目录”并调用后端 FileBrowser 重定向接口；插件日志通过“查看日志”抽屉的“插件日志”tab 查看；起止时间按开始/结束/耗时展示；操作按钮按 `availableActions` 渲染 | 行操作含查看依赖图、查看日志和已开放的 `taskAction` 操作；前端不拼接 FileBrowser 地址或展示账号密码 |
| `EventInstanceRow` | 事件页 | `eventName`、`eventType`、`flowInstanceId`、`taskInstanceId`、`effectTime` | `string/number` | `EventInstanceDto` | 时间戳格式化 | 可独立页面或详情页 |
| `TaskInstanceDependencyPanel` | 依赖图抽屉 | `flowDagSnapshot`、`lastInstanceId`、`nextInstanceId`、当前任务实例 ID | `JsonNode/string` | `TaskInstanceDependencyDto` | 只读展示流程 DAG 和当前任务上下游关系 | 不修改定义 |
| `TaskInstanceLogPanel` | 日志弹窗/抽屉 | `content`、`hasMore` | `string/boolean` | `TaskInstanceLogDto` | 等宽字体、按偏移加载更多 | 不展示完整路径给普通用户也可 |

### 3.4 表单模型

无。

### 3.5 操作模型

| 操作 | 触发位置 | 载荷 | 成功行为 | 失败行为 | 说明 |
|--------|------------------|---------|------------------|------------------|-------|
| `query` | 查询按钮 | `SchedulerInstanceQueryDto` | 刷新流程实例分页 | 展示错误提示 | 主查询 |
| `reset` | 重置按钮 | 默认查询条件 | 回到默认 `REALTIME` 查询 | 无 | 主查询 |
| `expandFlow` | 流程实例展开 | `FlowInstanceTaskQueryDto` | 加载任务实例列表 | 展开区展示错误 | 懒加载，携带当前 `viewType` |
| `refreshFlowRow` | 流程实例行 | `flowInstanceId`、`viewType` | 刷新当前行和已展开任务列表 | 展示错误提示 | 读操作，不改变调度状态 |
| `openDependency` | 任务实例行 | `TaskInstanceDependencyDto` | 打开依赖图抽屉 | 展示错误提示 | 只读展示 |
| `openLog` | 任务实例行 | `TaskInstanceLogQueryDto` | 打开日志抽屉并读取日志 | 展示错误提示 | 只读 |
| `flowAction` | 流程实例行 | `SchedulerInstanceActionDto` | 打印操作日志并提交 master `FlowAction`，刷新当前行 | 展示错误提示 | 操作只针对实时实例 |
| `taskAction` | 任务实例行 | `SchedulerInstanceActionDto` | 打印操作日志并提交 master `TaskAction`，刷新当前行和所在流程 | 展示错误提示 | 操作只针对实时实例；不开放任务实例 `REINIT` |
| `confirmAction` | 流程/任务实例行 | `SchedulerInstanceAvailableActionDto` | 弹出二次确认，确认后提交对应 action API | 关闭确认框并展示错误提示 | 前端只展示后端返回的可用操作；所有操作均二次确认 |

### 3.6 状态模型

| 状态 | 类型 | 视图 / 组件 | 默认值 | 变更来源 | 说明 |
|-------|------|------------------|---------|---------------|-------|
| `viewType` | `"REALTIME" | "HISTORY"` | 主页面 | `"REALTIME"` | 实时/历史切换 | 影响流程和任务实例查询表路由 |
| `expandedFlowInstanceIds` | `string[]` | 主表 | `[]` | 展开/收起 | 控制任务懒加载 |
| `logDrawerOpen` | `boolean` | 日志抽屉 | `false` | 打开日志/关闭日志 | 控制日志读取 |
| `currentLogQuery` | `TaskInstanceLogQueryDto` | 日志抽屉 | `undefined` | 点击任务日志 | 记录当前日志 |
| `dependencyDrawerOpen` | `boolean` | 依赖图抽屉 | `false` | 打开依赖图/关闭依赖图 | 控制依赖图展示 |
| `currentDependency` | `TaskInstanceDependencyDto` | 依赖图抽屉 | `undefined` | 点击查看依赖图 | 记录当前流程和任务实例关系 |

## 4. 数据映射规则

### 4.1 API 映射

| API | 请求对象 | 响应数据 | 响应包装 | 说明 |
|-----|----------------|---------------|------------------|-------|
| `POST /api/scheduler/flow/instance/page` | `PageQuery<SchedulerInstanceQueryDto>` | `PageResponse<FlowInstanceDto>` | `Result<T>` | 主列表 |
| `POST /api/scheduler/task/instance/listByFlowInstance` | `FlowInstanceTaskQueryDto` | `List<TaskInstanceDto>` | `Result<T>` | 展开行任务，按 `viewType` 查实时或历史任务表 |
| `POST /api/scheduler/task/instance/log/content` | `TaskInstanceLogQueryDto` | `TaskInstanceLogDto` | `Result<T>` | 日志读取 |
| `POST /api/scheduler/flow/instance/action` | `SchedulerInstanceActionDto` | `Boolean` | `Result<T>` | 流程实例操作 |
| `POST /api/scheduler/task/instance/action` | `SchedulerInstanceActionDto` | `Boolean` | `Result<T>` | 任务实例操作 |

### 4.2 分层转换

| 方向 | 转换规则 | 特殊处理 |
|-----------|-----------------|------------------|
| 页面查询模型 -> `SchedulerInstanceQueryDto` | 将输入框、选择器和时间范围转换为查询 DTO | 时间范围转毫秒时间戳 |
| `SchedulerInstanceQueryDto` -> Mapper 条件 | 先按 `viewType` 选择实时表或历史表，再在目标表上应用 `flowKeyword/taskKeyword/status/time range` | UUID 解析失败时只按名称模糊匹配；`status` 使用 `StatusEnum.stateType` |
| `FlowInstanceTaskQueryDto` -> Mapper 条件 | 先按 `viewType` 选择 `scheduler_task_instance` 或 `scheduler_task_instance_his`，再按 `flow_instance_id` 查询 | `viewType` 缺省为 `REALTIME` |
| `FlowInstanceEntity` / `FlowInstanceHisEntity` -> `FlowInstanceDto` | 字段复制 | `duration=endTime-startTime`，缺失时间则为空 |
| `TaskInstanceEntity` / `TaskInstanceHisEntity` -> `TaskInstanceDto` | 字段复制 | `workerResult` 按 `WorkerResult` 结构返回，另派生摘要文本和 `workDirPath`；前端在返回结果文本中展示 `workerResult.pluginLogUri` |
| `FlowInstanceDto` + `TaskInstanceDto` -> `TaskInstanceDependencyDto` | 前端组合当前流程 DAG 快照和任务上下游实例 ID | 只读展示，不回写定义 |
| `FlowInstanceEntity` / `TaskInstanceEntity` -> 历史 Entity | 归档时字段镜像复制，保留原主键和审计字段 | 插入历史表后删除实时表；同批次事务提交 |
| `EventInstanceEntity` -> `EventInstanceDto` | 字段复制 | `eventType` 保存值为 `1/2`，展示时映射任务/流程 |
| `TaskInstanceLogQueryDto` -> 文件路径 | `LOG -> {workDirPath}/stdout.log`、`ERROR -> {workDirPath}/stderr.log`、`STATUS -> {workDirPath}/state.log`、`PLUGIN -> workerResult.pluginLogUri` | 路径缺失或文件不存在时返回空内容和尝试路径；`PLUGIN` 必须位于当前任务 `workDirPath` 下 |
| `SchedulerInstanceActionDto` -> scheduler action | 根据 `actionType` 调用 `MasterService.getFlowAction()` 或 `MasterService.getTaskAction()` 中已公开的方法 | 仅允许实时实例；操作前查询实例是否存在；操作后只记录日志，不新增审计表 |
| `FlowInstanceEntity` / `TaskInstanceEntity` -> `availableActions` | 后端按实例状态调用 `SchedulerInstanceActionPolicy` 计算 | 历史实例返回空列表；action 接口提交时再次按同一策略校验，避免页面数据过期 |

## 5. 复用结构

| 对象 | 路径 | 复用方式 | 说明 |
|--------|------|--------------|-------|
| `Result<T>` | `datafusion-common-spring` | API 响应包装 | 统一返回 |
| `PageQuery<T>` / `PageResponse<T>` | `datafusion-common-spring` | 分页请求和响应 | 分页 API |
| `FlowInstanceEntity` / `TaskInstanceEntity` / `EventInstanceEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po` | 实时实例表 PO | 复用现有 PO |
| `FlowInstanceHisEntity` / `TaskInstanceHisEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po` | 历史实例表 PO | 新增，字段镜像实时表 |
| `FlowInstanceMapper` / `TaskInstanceMapper` / `EventInstanceMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao` | 实时表持久化访问 | 可补充分页查询 |
| `FlowInstanceHisMapper` / `TaskInstanceHisMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao` | 历史表持久化访问 | 新增历史分页、展开和归档查询 |
| `FlowInstanceService` / `TaskInstanceService` / `EventInstanceService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service` | 基础查询能力 | 可扩展页面查询 |
| `StatusEnum` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/enums/StatusEnum.java` | 状态存储、展示和成功归档判断 | `stateType` 落库，`fromString()` 读取，`isSuccess()` 触发归档 |
| `TaskResult` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/TaskResult.java` | worker 返回结果 | 只承载任务身份、状态和 `workerResult` |
| `WorkerResult` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/WorkerResult.java` | worker 执行结果 | 通过 `workDirPath` 承载 agent 任务运行目录，通过 `pluginLogUri` 承载插件日志路径 |
| `TaskRuntimeFiles` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/TaskRuntimeFiles.java` | 标准日志路径 | manager 根据 `WorkerResult.workDirPath` 拼接 `stdout.log`、`stderr.log`、`state.log` |
| `ActionType` | `datafusion-common-data/src/main/java/com/datafusion/scheduler/enums/ActionType.java` | 操作类型 | 实例操作 DTO 使用其名称作为 `actionType` |
| `MasterService` | `datafusion-scheduler-master` | 实例操作入口 | `getFlowAction()` / `getTaskAction()` |
| `SchedulerInstanceActionPolicy` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/model` | 可用操作策略 | 统一维护实例状态到操作按钮的映射，并供查询响应和 action 校验复用 |
