# scheduler-flow 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `scheduler_flow_info`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 调度流程定义表，保存流程基础信息、调度窗口、触发器引用、事件依赖、发布状态和前端视图信息。

### 1.2 DDL

```sql
CREATE TABLE scheduler_flow_info (
    id uuid NOT NULL,
    flow_name varchar(255) NOT NULL,
    flow_code varchar(255) NOT NULL,
    group_id uuid NULL,
    trigger_id uuid NOT NULL,
    description varchar(1000) NULL,
    flow_type varchar NOT NULL,
    flow_param json NULL,
    start_time int8 NULL,
    end_time int8 NULL,
    enabled bool DEFAULT false NOT NULL,
    dep_event_ids varchar NULL,
    event_id uuid NULL,
    publish_state bool DEFAULT false NOT NULL,
    publish_version int8 DEFAULT 0 NOT NULL,
    "view" json NULL,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    CONSTRAINT flow_info_pkey PRIMARY KEY (id)
);
```

当前 DDL 未对 `flow_code` 建唯一索引，唯一性由 Service 层校验。

### 1.3 关联表

流程 DAG 编排同时使用以下表：

| 表名 | 说明 | 关系 |
|------|------|------|
| `scheduler_task_info` | 任务定义表 | `flow_id` 绑定流程，`view` 保存节点视图 |
| `scheduler_task_link` | 流程任务编排关系表 | `flow_id` 关联流程，`start_id/end_id` 关联上下游任务 |
| `scheduler_trigger_info` | 触发器定义表 | `scheduler_flow_info.trigger_id` 引用触发器 |

`scheduler_task_link` 当前 DDL：

```sql
CREATE TABLE scheduler_task_link (
    id uuid NOT NULL,
    flow_id uuid NOT NULL,
    start_id uuid NOT NULL,
    end_id uuid NOT NULL,
    "view" json NULL,
    CONSTRAINT task_link_pkey PRIMARY KEY (id)
);
```

### 1.4 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 新增时 `UUID.nameUUIDFromBytes(flowCode)` | 主键，继承自 `BaseIdEntity` |
| `flow_name` | `flowName` | `String` | 是 | 无 | 流程名称 |
| `flow_code` | `flowCode` | `String` | 是 | 无 | 流程编码，全局唯一性由 Service 校验 |
| `group_id` | `groupId` | `UUID` | 否 | 无 | 流程分组 |
| `trigger_id` | `triggerId` | `UUID` | 是 | 无 | 触发器 ID |
| `description` | `description` | `String` | 否 | 无 | 流程描述 |
| `flow_type` | `flowType` | `String` | 是 | 无 | 流程类型 |
| `flow_param` | `flowParam` | `JsonNode` | 否 | 无 | 流程参数 JSON |
| `start_time` | `startTime` | `Long` | 否 | 无 | 调度开始时间 |
| `end_time` | `endTime` | `Long` | 否 | 无 | 调度结束时间 |
| `enabled` | `enabled` | `Boolean` | 是 | `false` | 是否启用调度 |
| `dep_event_ids` | `depEventIds` | `String` | 否 | 无 | 依赖事件 ID，逗号分隔 |
| `event_id` | `eventId` | `UUID` | 否 | 无 | 流程完成后产生的事件 ID |
| `publish_state` | `publishState` | `Boolean` | 是 | `false` | 发布状态 |
| `publish_version` | `publishVersion` | `Long` | 是 | `0` | 发布版本，当前使用发布时间戳 |
| `view` | `view` | `JsonNode` | 否 | 无 | 流程前端视图 JSON；当前 Controller 未直接维护 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |

## 2. Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `flowName` | `flow_name` | `String` | `@TableField("flow_name")` | 流程名称 |
| `flowCode` | `flow_code` | `String` | `@TableField("flow_code")` | 流程编码 |
| `groupId` | `group_id` | `UUID` | `@TableField("group_id")` | 流程分组 |
| `description` | `description` | `String` | `@TableField("description")` | 流程描述 |
| `flowType` | `flow_type` | `String` | `@TableField("flow_type")` | 流程类型 |
| `flowParam` | `flow_param` | `JsonNode` | `@TableField("flow_param")` | 流程参数 JSON |
| `startTime` | `start_time` | `Long` | `@TableField("start_time")` | 调度开始时间 |
| `endTime` | `end_time` | `Long` | `@TableField("end_time")` | 调度结束时间 |
| `enabled` | `enabled` | `Boolean` | `@TableField("enabled")` | 是否启用调度 |
| `depEventIds` | `dep_event_ids` | `String` | `@TableField("dep_event_ids")` | 依赖事件 ID |
| `eventId` | `event_id` | `UUID` | `@TableField("event_id")` | 事件 ID |
| `publishState` | `publish_state` | `Boolean` | `@TableField("publish_state")` | 发布状态 |
| `publishVersion` | `publish_version` | `Long` | `@TableField("publish_version")` | 发布版本 |
| `triggerId` | `trigger_id` | `UUID` | `@TableField("trigger_id")` | 触发器 ID |
| `view` | `view` | `JsonNode` | `@TableField("view")` | 流程前端视图 JSON |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `FlowInfoQueryDto` | `Query` | 分页和列表查询 | `flowName` | `String` | `like` | 流程名称模糊查询 |
| `FlowInfoQueryDto` | `Query` | 分页和列表查询 | `flowType` | `String` | `eq` | 流程类型过滤 |
| `FlowInfoQueryDto` | `Query` | 分页和列表查询 | `enabled` | `Boolean` | `eq` | 调度启用状态过滤 |
| `FlowInfoQueryDto` | `Query` | 分页和列表查询 | `publishState` | `Boolean` | `eq` | 发布状态过滤 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `flowName` | `String` | `@NotBlank` | 流程名称 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `flowCode` | `String` | `@NotBlank` + 唯一性校验 | 流程编码 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `groupId` | `UUID` | 可选 | 流程分组 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `description` | `String` | 可选 | 流程描述 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `flowType` | `String` | `@NotBlank` | 流程类型 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `flowParam` | `String` | 可选，JSON 字符串 | 流程参数 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `startTime` | `Long` | 可选 | 调度开始时间 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `endTime` | `Long` | 可选 | 调度结束时间 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `depEventIds` | `List<String>` | 可选 | 依赖事件 ID 列表 |
| `FlowInfoSaveDto` | `Request` | 新增流程 | `triggerId` | `UUID` | `@NotNull` | 触发器 ID，流程新增时必须选择触发器 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `id` | `UUID` | `@NotNull` | 流程 ID |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `flowName` | `String` | 非空时更新 | 流程名称 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `flowCode` | `String` | 非空时唯一性校验后更新 | 流程编码 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `groupId` | `UUID` | 非 `null` 时更新 | 流程分组 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `description` | `String` | 非 `null` 时更新 | 流程描述 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `flowType` | `String` | 非空时更新 | 流程类型 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `flowParam` | `String` | 非 `null` 时按 JSON 更新 | 流程参数 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `startTime` | `Long` | 非 `null` 时更新 | 调度开始时间 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `endTime` | `Long` | 非 `null` 时更新 | 调度结束时间 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `depEventIds` | `List<String>` | 非 `null` 时覆盖 | 依赖事件 ID 列表 |
| `FlowInfoUpdateDto` | `Request` | 修改流程 | `triggerId` | `UUID` | 非 `null` 时更新 | 触发器 ID |
| `FlowPublishDto` | `Request` | 发布流程 | `id` | `UUID` | `@NotNull` | 流程 ID |
| `FlowPublishDto` | `Request` | 发布流程 | `enableSchedule` | `Boolean` | 可选 | 发布后是否同时启用调度 |
| `FlowInfoDto` | `Response` | 查询响应 | `id/flowName/flowCode/groupId/description/flowType` | 多类型 | 无 | 流程基础信息 |
| `FlowInfoDto` | `Response` | 查询响应 | `flowParam` | `String` | `JsonNode` -> JSON 字符串 | 流程参数 |
| `FlowInfoDto` | `Response` | 查询响应 | `startTime/endTime/enabled` | 多类型 | 无 | 调度窗口和启用状态 |
| `FlowInfoDto` | `Response` | 查询响应 | `depEventIds` | `List<String>` | 逗号字符串拆分 | 依赖事件 ID |
| `FlowInfoDto` | `Response` | 查询响应 | `eventId/triggerId/publishState/publishVersion` | 多类型 | 无 | 事件、触发器和发布信息 |
| `FlowDagDto` | `Response` | 查询流程 DAG | `flowId` | `UUID` | 无 | 流程 ID |
| `FlowDagDto` | `Response` | 查询流程 DAG | `nodes` | `List<NodeDto>` | 无 | 节点列表 |
| `FlowDagDto` | `Response` | 查询流程 DAG | `edges` | `List<EdgeDto>` | 无 | 连线列表 |
| `DagSaveDto` | `Request` | 保存流程 DAG | `flowId` | `UUID` | `@NotNull` | 流程 ID |
| `DagSaveDto` | `Request` | 保存流程 DAG | `nodes` | `List<NodeDto>` | 可选 | 节点列表 |
| `DagSaveDto` | `Request` | 保存流程 DAG | `edges` | `List<EdgeDto>` | 可选 | 连线列表 |
| `NodeDto` | `Request/Response` | DAG 节点 | `id` | `String` | `@NotBlank` | 对应 `scheduler_task_info.id` |
| `NodeDto` | `Response` | DAG 节点 | `data` | `NodeDataDto` | 响应时填充 | 任务基本信息，供右侧【基本信息】直接展示 |
| `NodeDto` | `Request/Response` | DAG 节点 | `nodeView` | `NodeViewDto` | 可选 | 节点前端视图 |
| `EdgeDto` | `Request/Response` | DAG 连线 | `id` | `String` | 可选 | 前端生成 ID，保存时不入库 |
| `EdgeDto` | `Request/Response` | DAG 连线 | `source` | `String` | `@NotBlank` | 上游任务 ID |
| `EdgeDto` | `Request/Response` | DAG 连线 | `target` | `String` | `@NotBlank` | 下游任务 ID |
| `EdgeDto` | `Request/Response` | DAG 连线 | `edgeView` | `EdgeViewDto` | 可选 | 连线前端视图 |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `taskId` | `String` | 响应时填充 | 任务 ID |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `taskName` | `String` | 响应时填充 | 任务名称 |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `taskCode` | `String` | 响应时填充 | 任务编码 |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `taskType` | `String` | 响应时填充 | 任务类型 |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `description` | `String` | 响应时填充 | 任务描述 |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `syncFlag` | `Boolean` | 响应时填充 | 任务同步状态 |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `taskParam` | `String` | 响应时填充，JSON 字符串 | 任务参数，用于前端解析 `ParamData.params` |
| `NodeDataDto` | `Response` | DAG 节点业务数据 | `definition` | `String` | 响应时填充，JSON 字符串 | 任务定义 |
| `TaskInfoQueryDto` | `Query` | 流程编排任务池查询 | `keyword` | `String` | `ILIKE` OR 查询 | 同时模糊匹配 `taskName` 和 `taskCode` |
| `TaskInfoQueryDto` | `Query` | 流程编排任务池查询 | `isBound` | `Boolean` | `eq false` | 任务池固定查询未绑定任务 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/flow/page` | `PageQuery<FlowInfoQueryDto>` | `PageResponse<FlowInfoDto>` | `Result<T>` | 分页查询 |
| `POST /api/scheduler/flow/list` | `FlowInfoQueryDto` | `List<FlowInfoDto>` | `Result<T>` | 列表查询 |
| `GET /api/scheduler/flow/detail/{id}` | path `UUID id` | `FlowInfoDto` | `Result<T>` | 查询详情 |
| `POST /api/scheduler/flow/add` | `FlowInfoSaveDto` | `UUID` | `Result<T>` | 新增流程 |
| `POST /api/scheduler/flow/update` | `FlowInfoUpdateDto` | `Boolean` | `Result<T>` | 修改流程 |
| `POST /api/scheduler/flow/delete/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除流程 |
| `GET /api/scheduler/flow/dag/detail/{id}` | path `UUID id` | `FlowDagDto` | `Result<T>` | 查询流程 DAG |
| `POST /api/scheduler/flow/dag/save` | `DagSaveDto` | `Boolean` | `Result<T>` | 保存流程 DAG |
| `POST /api/scheduler/flow/publish` | `FlowPublishDto` | `Boolean` | `Result<T>` | 发布流程 |
| `POST /api/scheduler/flow/unpublish/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 取消发布 |
| `POST /api/scheduler/flow/enable/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 启用调度 |
| `POST /api/scheduler/flow/disable/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 停用调度 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `FlowInfoSaveDto` -> `FlowInfoEntity` | 复制基础字段和调度字段 | `id` 使用 `UUID.nameUUIDFromBytes(flowCode)`；`triggerId` 必填；`flowParam` JSON 字符串转 `JsonNode`；`depEventIds` 列表转逗号字符串；默认 `enabled=false/publishState=false/publishVersion=0` |
| `FlowInfoUpdateDto` -> existing `FlowInfoEntity` | 仅允许未发布且未启用流程合并非空字段 | `flowCode` 非空时重新校验唯一；`depEventIds` 非 `null` 时整体覆盖 |
| `FlowInfoEntity` -> `FlowInfoDto` | 字段逐一复制 | `flowParam` 转 JSON 字符串；`depEventIds` 逗号字符串拆成列表 |
| `FlowInfoQueryDto` -> `LambdaQueryWrapper` | `flowName` 模糊匹配，`flowType/enabled/publishState` 精确匹配 | 默认按 `createTime desc` 排序 |
| `TaskInfoEntity` -> `NodeDto` | `task_info.id` 作为 `node.id`，任务基本信息填充 `node.data` | `task_info.view` 解析为 `NodeViewDto`；`node.data` 需覆盖右侧【基本信息】展示字段 |
| `EdgeDto` -> `TaskLinkEntity` | `source/target` 转 `startId/endId`，`flowId` 来自 `DagSaveDto` | 入库 `id` 使用 `UUID.randomUUID()`，不使用前端 edge ID |
| `TaskLinkEntity` -> `EdgeDto` | `id/startId/endId` 转字符串 | `task_link.view` 解析为 `EdgeViewDto` |
| `TaskInfoQueryDto` -> 任务池查询 | `isBound=false` 且 `keyword` 对 `taskName/taskCode` 做 OR 模糊匹配 | 默认按 `updateTime desc` 排序；返回 `id/taskName/taskCode/taskType/syncFlag` 等任务池卡片字段 |
| `FlowInfoEntity` -> scheduler `FlowInfo` | `FlowStorageImpl` 转换为调度框架模型 | `flowType` 转 `FlowTypeEnum`；`flowParam` 转 `ParamData`；`depEventIds` 转集合；`publishVersion` 转版本字符串 |
| `FlowInfoEntity` + `TriggerInfoEntity` -> scheduler `TriggerInfo` | `TriggerStorageImpl` 组合流程调度窗口和触发器配置 | 当前触发器查询字段需确认，详见设计风险 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `flowType` | `varchar` | `String` | `FlowStorageImpl` 中转 `FlowTypeEnum.fromString` | API 当前使用字符串，不做枚举校验 |
| `flowParam` | `json` | `JsonNode` | API 使用 JSON 字符串，Entity 使用 `JsonNode` | 流程参数，遵循 `ParamData` 结构 |
| `taskParam` | `json` | `JsonNode` | API 使用 JSON 字符串，Entity 使用 `JsonNode` | 任务参数，遵循 `ParamData` 结构；`params` 用于基本信息展示，`vars` 用于调度信息展示 |
| `depEventIds` | `varchar` | `String` | API 使用 `List<String>`，DB 使用逗号分隔字符串 | 后续建议下沉为关联表或 JSON 数组 |
| `view` | `json` | `JsonNode` | 当前流程接口不直接写入 | 流程画布级视图 |
| `task_info.view` | `json` | `JsonNode` | `NodeViewDto` 与 `JsonNode` 互转 | DAG 节点视图；前端需要持久化的节点位置、样式和展示信息都可放入 `nodeView` |
| `task_link.view` | `json` | `JsonNode` | `EdgeViewDto` 与 `JsonNode` 互转 | DAG 连线视图；前端需要持久化的边样式、展示信息和 `sourceHandle/targetHandle` 等连线锚点都可放入 `edgeView` |
| `publishVersion` | `int8` | `Long` | 发布时使用 `System.currentTimeMillis()` | 调度框架版本字符串来源 |
| `enabled` | `bool` | `Boolean` | 发布后可启用，取消发布时置 `false` | 调度开关 |
| `publishState` | `bool` | `Boolean` | 发布置 `true`，取消发布置 `false` | 发布状态 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id` 和审计字段 |
| `CommonException` | `datafusion-common` | 业务异常 | 流程不存在、编码重复、状态不允许等 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
| `JacksonUtils` | `datafusion-common` | JSON 转换 | 字符串、`JsonNode`、DTO 和调度模型之间转换 |
| `FlowInfo` | `datafusion-scheduler-master` | 调度框架模型 | `FlowStorageImpl` 从 `FlowInfoEntity` 转换 |
| `TriggerInfo` | `datafusion-scheduler-master` | 调度触发模型 | `TriggerStorageImpl` 从流程和触发器表组合 |
