# scheduler-event 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `scheduler_event_info`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 调度事件配置表，保存事件名称、事件类型，以及事件关联的流程或任务定义。

- 表名: `scheduler_event_instance`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 调度事件实例表，保存 scheduler master 运行过程中产生的全局事件实例，供事件等待、事件匹配和缓存加载使用。

### 1.2 DDL

```sql
CREATE TABLE scheduler_event_info (
    id uuid NOT NULL,
    event_name varchar NOT NULL,
    event_type varchar NOT NULL,
    flow_id uuid NULL,
    task_id uuid NULL,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    CONSTRAINT event_info_pkey PRIMARY KEY (id)
);

CREATE TABLE scheduler_event_instance (
    id uuid NOT NULL,
    event_id uuid NOT NULL,
    event_name varchar NOT NULL,
    event_type varchar NOT NULL,
    flow_instance_id uuid NULL,
    task_instance_id uuid NULL,
    effect_time int8 NULL,
    effect_begin_time int8 NULL,
    effect_end_time int8 NULL,
    CONSTRAINT event_instance_pkey PRIMARY KEY (id)
);
```

当前 DDL 未对 `scheduler_event_info.event_name` 建唯一索引，事件名称当前允许重复。

### 1.3 字段定义

#### scheduler_event_info

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 新增时 `UUID.randomUUID()` | 主键，继承自 `BaseIdEntity` |
| `event_name` | `eventName` | `String` | 是 | 无 | 事件名称 |
| `event_type` | `eventType` | `String` | 是 | 无 | 事件类型编码，当前使用 `"1"` 表示 `TASK`，`"2"` 表示 `FLOW` |
| `flow_id` | `flowId` | `UUID` | 否 | 无 | 关联流程 ID，`eventType="2"` 时必填 |
| `task_id` | `taskId` | `UUID` | 否 | 无 | 关联任务 ID，`eventType="1"` 时必填 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |

#### scheduler_event_instance

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 保存事件实例时 `UUID.randomUUID()` | 主键 |
| `event_id` | `eventId` | `UUID` | 是 | 无 | 事件 ID，对应 `GlobalEvent.id` |
| `event_name` | `eventName` | `String` | 是 | 无 | 事件名称 |
| `event_type` | `eventType` | `String` | 是 | 无 | 事件类型编码，对应 `EventTypeEnum.getType()` |
| `flow_instance_id` | `flowInstanceId` | `UUID` | 否 | 无 | 流程实例 ID |
| `task_instance_id` | `taskInstanceId` | `UUID` | 否 | 无 | 任务实例 ID |
| `effect_time` | `effectTime` | `Long` | 否 | 无 | 事件生效时间，对应 `GlobalEvent.eventTime` |
| `effect_begin_time` | `effectBeginTime` | `Long` | 否 | 无 | 事件开始生效时间，对应 `GlobalEvent.beginTime` |
| `effect_end_time` | `effectEndTime` | `Long` | 否 | 无 | 事件结束生效时间，对应 `GlobalEvent.endTime` |

## 2. Entity / PO 映射

### 2.1 EventInfoEntity

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `eventName` | `event_name` | `String` | `@TableField("event_name")` | 事件名称 |
| `eventType` | `event_type` | `String` | `@TableField("event_type")` | 事件类型编码 |
| `flowId` | `flow_id` | `UUID` | `@TableField("flow_id")` | 关联流程 ID |
| `taskId` | `task_id` | `UUID` | `@TableField("task_id")` | 关联任务 ID |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |

### 2.2 EventInstanceEntity

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` / `@TableField("id")` | 主键 |
| `eventId` | `event_id` | `UUID` | `@TableField("event_id")` | 事件 ID |
| `eventName` | `event_name` | `String` | `@TableField("event_name")` | 事件名称 |
| `eventType` | `event_type` | `String` | `@TableField("event_type")` | 事件类型编码 |
| `flowInstanceId` | `flow_instance_id` | `UUID` | `@TableField("flow_instance_id")` | 流程实例 ID |
| `taskInstanceId` | `task_instance_id` | `UUID` | `@TableField("task_instance_id")` | 任务实例 ID |
| `effectTime` | `effect_time` | `Long` | `@TableField("effect_time")` | 事件生效时间 |
| `effectBeginTime` | `effect_begin_time` | `Long` | `@TableField("effect_begin_time")` | 事件开始生效时间 |
| `effectEndTime` | `effect_end_time` | `Long` | `@TableField("effect_end_time")` | 事件结束生效时间 |

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `EventInfoQueryDto` | `Query` | 分页和列表查询 | `eventName` | `String` | `like` | 事件名称模糊查询 |
| `EventInfoQueryDto` | `Query` | 分页和列表查询 | `eventType` | `String` | `eq` | 事件类型编码精确查询 |
| `EventInfoQueryDto` | `Query` | 分页和列表查询 | `flowId` | `UUID` | `eq` | 关联流程 ID 精确查询 |
| `EventInfoQueryDto` | `Query` | 分页和列表查询 | `taskId` | `UUID` | `eq` | 关联任务 ID 精确查询 |
| `EventInfoSaveDto` | `Request` | 新增事件 | `eventName` | `String` | `@NotBlank` | 事件名称 |
| `EventInfoSaveDto` | `Request` | 新增事件 | `eventType` | `String` | `@NotBlank` | 事件类型编码 |
| `EventInfoSaveDto` | `Request` | 新增事件 | `flowId` | `UUID` | `eventType="2"` 时必填 | 关联流程 ID |
| `EventInfoSaveDto` | `Request` | 新增事件 | `taskId` | `UUID` | `eventType="1"` 时必填 | 关联任务 ID |
| `EventInfoUpdateDto` | `Request` | 修改事件 | `id` | `UUID` | `@NotNull` | 事件 ID |
| `EventInfoUpdateDto` | `Request` | 修改事件 | `eventName` | `String` | 非空时合并 | 事件名称 |
| `EventInfoUpdateDto` | `Request` | 修改事件 | `eventType` | `String` | 非空时合并 | 事件类型编码 |
| `EventInfoUpdateDto` | `Request` | 修改事件 | `flowId` | `UUID` | 非 `null` 时合并 | 关联流程 ID |
| `EventInfoUpdateDto` | `Request` | 修改事件 | `taskId` | `UUID` | 非 `null` 时合并 | 关联任务 ID |
| `EventInfoDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `EventInfoDto` | `Response` | 查询响应 | `eventName` | `String` | 无 | 事件名称 |
| `EventInfoDto` | `Response` | 查询响应 | `eventType` | `String` | 无 | 事件类型编码 |
| `EventInfoDto` | `Response` | 查询响应 | `flowId` | `UUID` | 无 | 关联流程 ID |
| `EventInfoDto` | `Response` | 查询响应 | `taskId` | `UUID` | 无 | 关联任务 ID |
| `EventInfoDto` | `Response` | 查询响应 | `creator` | `String` | 无 | 创建人 |
| `EventInfoDto` | `Response` | 查询响应 | `updater` | `String` | 无 | 修改人 |
| `EventInfoDto` | `Response` | 查询响应 | `createTime` | `Date` | 无 | 创建时间 |
| `EventInfoDto` | `Response` | 查询响应 | `updateTime` | `Date` | 无 | 修改时间 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/event/page` | `PageQuery<EventInfoQueryDto>` | `PageResponse<EventInfoDto>` | `Result<T>` | 分页查询 |
| `POST /api/scheduler/event/list` | `EventInfoQueryDto` | `List<EventInfoDto>` | `Result<T>` | 列表查询 |
| `POST /api/scheduler/event/add` | `EventInfoSaveDto` | `UUID` | `Result<T>` | 新增事件 |
| `POST /api/scheduler/event/update` | `EventInfoUpdateDto` | `Boolean` | `Result<T>` | 修改事件 |
| `GET /api/scheduler/event/detail/{id}` | path `UUID id` | `EventInfoDto` | `Result<T>` | 查询详情 |
| `POST /api/scheduler/event/delete/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除事件 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `EventInfoSaveDto` -> `EventInfoEntity` | 复制 `eventName`、`eventType`、`flowId`、`taskId` | `id` 使用 `UUID.randomUUID()`；审计字段由 Service 设置 |
| `EventInfoUpdateDto` -> existing `EventInfoEntity` | 非空字符串字段和非 `null` UUID 字段合并 | 合并后重新校验 `eventType` 与 `flowId/taskId` 一致性 |
| `EventInfoEntity` -> `EventInfoDto` | 字段逐一复制 | 不做事件类型编码转换 |
| `EventInfoQueryDto` -> `LambdaQueryWrapper` | `eventName` 使用 `like`；`eventType/flowId/taskId` 使用 `eq` | 默认 `createTime desc` |
| scheduler `GlobalEvent` -> `EventInstanceEntity` | `id/eventType/flowInstanceId/taskInstanceId/effectTime/effectBeginTime/effectEndTime` 映射入库 | `eventType` 使用 `EventTypeEnum.getType()` 转字符串；`eventName` 当前未赋值 |
| `EventInstanceEntity` -> scheduler `GlobalEvent` | `eventId/eventType/flowInstanceId/taskInstanceId/effectTime/effectBeginTime/effectEndTime` 映射回调度模型 | `eventType` 由数字字符串解析为 `EventTypeEnum` |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `event_type` | `varchar` | `String` | manager API 当前直接使用编码字符串；scheduler master 使用 `EventTypeEnum` | `"1"` / `TASK`，`"2"` / `FLOW` |
| `dep_event_ids` | `varchar` | `String` | 英文逗号分割事件 ID | 删除事件时通过 `LIKE` 预筛选，再应用层精确拆分确认 |
| `effect_time` | `int8` | `Long` | 对应 `GlobalEvent.eventTime` | 事件唯一键的一部分 |
| `effect_begin_time` | `int8` | `Long` | 对应 `GlobalEvent.beginTime` | 事件有效窗口开始时间 |
| `effect_end_time` | `int8` | `Long` | 对应 `GlobalEvent.endTime` | 事件有效窗口结束时间 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id`，并继承 `BaseEntity` 审计字段 |
| `BaseEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `creator/updater/createTime/updateTime` |
| `CommonException` | `datafusion-common` | 业务异常 | 事件不存在、事件类型关联不完整、事件被引用 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
| `FlowInfoService` | `datafusion-manager` | 引用检查 | 检查流程产生事件和依赖事件 |
| `TaskInfoService` | `datafusion-manager` | 引用检查 | 检查任务产生事件和依赖事件 |
| `EventStorage` | `datafusion-scheduler-master` | 调度事件存储契约 | manager 使用 `EventStorageImpl` 适配 |
| `GlobalEvent` | `datafusion-scheduler-master` | 调度事件模型 | 运行时事件实例模型 |
| `EventTypeEnum` | `datafusion-scheduler-master` | 事件类型转换 | `TASK=1`，`FLOW=2` |
