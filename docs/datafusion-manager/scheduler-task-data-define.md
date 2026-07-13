# scheduler-task 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `scheduler_task_info`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 调度任务表，当前同时保存任务定义属性、调度编排属性和系统属性。任务定义页面只新增、修改和展示任务定义属性。

### 1.2 DDL

```sql
CREATE TABLE scheduler_task_info (
    id uuid NOT NULL,
    task_name varchar(255) NOT NULL,
    task_code varchar(255) NOT NULL,
    description varchar(1000) NULL,
    task_type_id varchar(255) NOT NULL,
    task_type varchar(255) NOT NULL,
    task_param json NULL,
    definition json NULL,
    is_bound bool DEFAULT false NOT NULL,
    flow_id uuid NULL,
    plugin_id uuid NOT NULL,
    "view" json NULL,
    dep_event_ids varchar NULL,
    event_id uuid NULL,
    enabled bool DEFAULT true NOT NULL,
    sync_flag bool DEFAULT false NOT NULL,
    source_route text NULL,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    CONSTRAINT task_info_pkey PRIMARY KEY (id)
);

```

`task_code` 和 `source_route` 业务身份暂由 Service 校验；数据库暂不增加唯一索引。

### 1.3 字段分层

| 分类 | 字段 | 前端任务定义页处理方式 | 说明 |
|------|------|------------------------|------|
| 任务定义属性 | `task_name`、`task_code`、`description`、`task_type_id`、`task_type`、`task_param`、`definition` | 可新增、修改、查看 | 描述任务自身配置 |
| 调度编排属性 | `is_bound`、`flow_id`、`plugin_id`、`view`、`dep_event_ids`、`event_id`、`enabled` | 不展示、不编辑、不提交 | 描述任务进入流程后的绑定、执行插件、画布、事件和启停信息 |
| 系统属性 | `id`、`sync_flag`、`source_route`、`creator`、`updater`、`create_time`、`update_time` | 只读或不展示 | 由系统生成、维护或用于追踪 |

`plugin_id` 虽然当前位于 `scheduler_task_info` 且非空，但它属于调度执行适配属性。新增任务定义时，后端根据 `taskType` 解析默认 `pluginId` 并写入；前端任务定义页不展示、不提交。流程节点配置可以覆盖执行插件。

### 1.4 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 新增时 `UUID.nameUUIDFromBytes(taskCode)` | 主键，继承自 `BaseIdEntity` |
| `task_name` | `taskName` | `String` | 是 | 无 | 任务名称 |
| `task_code` | `taskCode` | `String` | 是 | 无 | 任务编码，全局唯一性由 Service 校验 |
| `description` | `description` | `String` | 否 | 无 | 任务描述 |
| `task_type_id` | `taskTypeId` | `String` | 是 | 无 | 任务类型 ID |
| `task_type` | `taskType` | `String` | 是 | 无 | 任务类型，用于查询过滤和调度模型转换 |
| `task_param` | `taskParam` | `JsonNode` | 否 | 无 | 任务变量参数 JSON，遵循 `ParamData.vars` |
| `definition` | `definition` | `JsonNode` | 否 | 无 | 任务定义 JSON |
| `is_bound` | `isBound` | `Boolean` | 是 | `false` | 是否已绑定流程 |
| `flow_id` | `flowId` | `UUID` | 否 | 无 | 绑定的流程 ID |
| `plugin_id` | `pluginId` | `UUID` | 是 | 后端按 `taskType` 解析默认值 | 执行组件 ID，属于调度编排属性，任务定义页不展示、不提交 |
| `view` | `view` | `JsonNode` | 否 | 无 | 前端流程画布视图 JSON，属于调度编排属性 |
| `dep_event_ids` | `depEventIds` | `String` | 否 | 无 | 依赖事件 ID，属于调度编排属性，当前按逗号分隔字符串保存 |
| `event_id` | `eventId` | `UUID` | 否 | 无 | 本任务产生的事件 ID，属于调度编排属性 |
| `enabled` | `enabled` | `Boolean` | 是 | `true` | 是否启用调度，属于调度编排属性 |
| `sync_flag` | `syncFlag` | `Boolean` | 是 | `false` | 任务同步标识，修改业务任务时置为 `false` |
| `source_route` | `sourceRoute` | `String` | 否 | 无 | 原始业务定位路由 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |

## 2. Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `taskName` | `task_name` | `String` | `@TableField("task_name")` | 任务名称 |
| `taskCode` | `task_code` | `String` | `@TableField("task_code")` | 任务编码 |
| `description` | `description` | `String` | `@TableField("description")` | 任务描述 |
| `taskTypeId` | `task_type_id` | `String` | `@TableField("task_type_id")` | 任务类型 ID |
| `taskType` | `task_type` | `String` | `@TableField("task_type")` | 任务类型 |
| `taskParam` | `task_param` | `JsonNode` | `@TableField("task_param")` | 任务变量参数 JSON，遵循 `ParamData.vars` |
| `definition` | `definition` | `JsonNode` | `@TableField("definition")` | 任务定义 JSON |
| `isBound` | `is_bound` | `Boolean` | `@TableField("is_bound")` | 是否绑定流程 |
| `flowId` | `flow_id` | `UUID` | `@TableField("flow_id")` | 流程 ID |
| `pluginId` | `plugin_id` | `UUID` | `@TableField("plugin_id")` | 执行组件 ID |
| `view` | `view` | `JsonNode` | `@TableField("view")` | 前端视图 JSON |
| `depEventIds` | `dep_event_ids` | `String` | `@TableField("dep_event_ids")` | 依赖事件 ID |
| `eventId` | `event_id` | `UUID` | `@TableField("event_id")` | 事件 ID |
| `enabled` | `enabled` | `Boolean` | `@TableField("enabled")` | 是否启用 |
| `syncFlag` | `sync_flag` | `Boolean` | `@TableField("sync_flag")` | 同步标识 |
| `sourceRoute` | `source_route` | `String` | `@TableField("source_route")` | 原始业务定位路由 |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `taskName` | `String` | `ILIKE` | 任务名称模糊查询 |
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `taskCode` | `String` | `ILIKE` | 任务编码模糊查询 |
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `taskType` | `String` | `eq` | 任务类型过滤 |
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `flowId` | `UUID` | 调度编排查询保留；任务定义页面不使用 | 所属流程 ID 过滤 |
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `enabled` | `Boolean` | `eq` | 启用状态过滤 |
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `isBound` | `Boolean` | `eq` | 绑定状态过滤 |
| `TaskInfoQueryDto` | `Query` | 分页和列表查询 | `syncFlag` | `Boolean` | `eq` | 同步状态过滤 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `taskName` | `String` | `@NotBlank` + `@Size(max=235)` | 任务名称，预留复制后缀长度 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `taskCode` | `String` | `@NotBlank` + `@Size(max=235)` + 唯一性校验 | 任务编码，预留复制后缀长度 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `description` | `String` | 可选 | 任务描述 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `taskTypeId` | `String` | `@NotBlank` | 任务类型 ID |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `taskType` | `String` | `@NotBlank` | 任务类型 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `taskParam` | `String` | 可选，JSON 字符串 | 任务变量参数 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `definition` | `String` | 可选，JSON 字符串 | 任务定义 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `pluginId` | `UUID` | 不由前端提交；后端按 `taskType` 填默认值 | 执行组件 ID |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `view` | `String` | 不由任务定义页面提交 | 前端流程画布视图 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `depEventIds` | `String` | 不由任务定义页面提交 | 依赖事件 ID，逗号分隔 |
| `TaskInfoSaveDto` | `Request` | 新增任务 | `eventId` | `UUID` | 不由任务定义页面提交 | 事件 ID |
| `TaskInfoCopyDto` | `Request` | 复制单条任务 | `sourceId` | `UUID` | `@NotNull` | 被复制的原任务 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `id` | `UUID` | `@NotNull` | 任务 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `taskName` | `String` | 非空时更新，`@Size(max=235)` | 任务名称，预留复制后缀长度 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `taskCode` | `String` | 非空时唯一性校验后更新，`@Size(max=235)` | 任务编码，预留复制后缀长度 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `description` | `String` | 非 `null` 时更新 | 任务描述 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `taskTypeId` | `String` | 非空时更新 | 任务类型 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `taskType` | `String` | 非空时更新 | 任务类型 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `taskParam` | `String` | 非 `null` 时按 JSON 更新 | 任务变量参数 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `definition` | `String` | 非 `null` 时按 JSON 更新 | 任务定义 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `pluginId` | `UUID` | 不由任务定义页面提交；流程节点配置阶段另行处理 | 执行组件 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `view` | `String` | 不由任务定义页面提交；流程编排阶段另行处理 | 前端流程画布视图 |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `depEventIds` | `String` | 不由任务定义页面提交；流程编排阶段另行处理 | 依赖事件 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `eventId` | `UUID` | 不由任务定义页面提交；流程编排阶段另行处理 | 事件 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `clearEventId` | `Boolean` | 不由任务定义页面提交；流程编排关闭生成事件时传 `true` | 是否清空事件 ID |
| `TaskInfoUpdateDto` | `Request` | 修改任务 | `enabled` | `Boolean` | 不由任务定义页面提交；调度启停阶段另行处理 | 是否启用 |
| `TaskInfoDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `TaskInfoDto` | `Response` | 查询响应 | `taskName` | `String` | 无 | 任务名称 |
| `TaskInfoDto` | `Response` | 查询响应 | `taskCode` | `String` | 无 | 任务编码 |
| `TaskInfoDto` | `Response` | 查询响应 | `description` | `String` | 无 | 任务描述 |
| `TaskInfoDto` | `Response` | 查询响应 | `taskTypeId` | `String` | 无 | 任务类型 ID |
| `TaskInfoDto` | `Response` | 查询响应 | `taskType` | `String` | 无 | 任务类型 |
| `TaskInfoDto` | `Response` | 查询响应 | `taskParam` | `String` | `JsonNode` -> JSON 字符串 | 任务变量参数 |
| `TaskInfoDto` | `Response` | 查询响应 | `definition` | `String` | `JsonNode` -> JSON 字符串 | 任务定义 |
| `TaskInfoDto` | `Response` | 查询响应 | `isBound` | `Boolean` | 无 | 是否绑定流程 |
| `TaskInfoDto` | `Response` | 查询响应 | `flowId` | `UUID` | 无 | 流程 ID |
| `TaskInfoDto` | `Response` | 查询响应 | `pluginId` | `UUID` | 无 | 执行组件 ID |
| `TaskInfoDto` | `Response` | 查询响应 | `view` | `String` | `JsonNode` -> JSON 字符串 | 前端视图 |
| `TaskInfoDto` | `Response` | 查询响应 | `depEventIds` | `String` | 无 | 依赖事件 ID |
| `TaskInfoDto` | `Response` | 查询响应 | `eventId` | `UUID` | 无 | 事件 ID |
| `TaskInfoDto` | `Response` | 查询响应 | `enabled` | `Boolean` | 无 | 是否启用 |
| `TaskInfoDto` | `Response` | 查询响应 | `syncFlag` | `Boolean` | 无 | 同步标识 |
| `TaskInfoDto` | `Response` | 查询响应 | `sourceRoute` | `BusinessSourceRoute` | 协议串解码 | 业务来源定位信息 |
| `TaskInfoDto` | `Response` | 查询响应 | `creator` | `String` | 无 | 创建人 |
| `TaskInfoDto` | `Response` | 查询响应 | `updater` | `String` | 无 | 修改人 |
| `TaskInfoDto` | `Response` | 查询响应 | `createTime` | `Date` | 无 | 创建时间 |
| `TaskInfoDto` | `Response` | 查询响应 | `updateTime` | `Date` | 无 | 修改时间 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/task/page` | `PageQuery<TaskInfoQueryDto>` | `PageResponse<TaskInfoDto>` | `Result<T>` | 分页查询 |
| `POST /api/scheduler/task/list` | `TaskInfoQueryDto` | `List<TaskInfoDto>` | `Result<T>` | 列表查询 |
| `POST /api/scheduler/task/add` | `TaskInfoSaveDto` | `UUID` | `Result<T>` | 新增任务 |
| `POST /api/scheduler/task/copy` | `TaskInfoCopyDto` | `UUID` | `Result<T>` | 复制单条任务 |
| `POST /api/scheduler/task/update` | `TaskInfoUpdateDto` | `Boolean` | `Result<T>` | 修改任务 |
| `GET /api/scheduler/task/detail/{id}` | path `UUID id` | `TaskInfoDto` | `Result<T>` | 查询详情 |
| `POST /api/scheduler/task/delete/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除任务 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TaskInfoSaveDto` -> `TaskInfoEntity` | 复制任务定义属性 | `id` 使用 `UUID.nameUUIDFromBytes(taskCode)`；`taskParam/definition` JSON 字符串转 `JsonNode`；`pluginId` 由后端按 `taskType` 解析默认值；默认 `isBound=false/enabled=true/syncFlag=false` |
| `TaskInfoCopyDto` + source `TaskInfoEntity` -> new `TaskInfoEntity` | 后端基于原任务生成新 `taskName/taskCode/id` | 复制任务执行定义，清空业务身份并设置 `syncFlag=false/sourceRoute=null`；重置流程编排属性，`pluginId` 按 `taskType` 重新解析 |
| `TaskInfoUpdateDto` -> existing `TaskInfoEntity` | 合并任务定义属性和流程编排属性的非空字段 | 不更新 `isBound/flowId`；`clearEventId=true` 时清空 `eventId`，否则 `eventId` 非空时更新；更新后置 `syncFlag=false`；`taskCode` 非空时重新校验唯一 |
| `TaskInfoEntity` -> `TaskInfoDto` | 字段逐一复制 | `taskParam/definition/view` 转 JSON 字符串；`sourceRoute` 解码为 `BusinessSourceRoute` |
| `TaskInfoQueryDto` -> `LambdaQueryWrapper` | `taskName/taskCode` 使用 `ILIKE`，`taskType/flowId/enabled/isBound/syncFlag` 精确匹配 | 默认 `createTime desc` |
| `TaskInfoEntity` -> scheduler `TaskInfo` | `TaskStorageImpl` 转换为调度框架模型 | `taskParam` 转 `ParamData.vars`；`definition` 透传为 `JsonNode`；`depEventIds` 按逗号转集合；`enabled` 转 `isAble` |
| `TaskInstanceEntity` -> scheduler `TaskInstance` | `TaskStorageImpl` 转换任务实例模型 | `task_param` 转运行期 `ParamData.vars`；`task_data` 转渲染后的任务定义 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `taskParam` | `json` | `JsonNode` | API 使用 JSON 字符串，Entity 使用 `JsonNode` | 任务变量参数，遵循 `ParamData.vars` |
| `definition` | `json` | `JsonNode` | API 使用 JSON 字符串，Entity 使用 `JsonNode` | 纯插件执行定义，禁止顶层 `bizRef/sourceRoute` |
| `view` | `json` | `JsonNode` | 流程编排阶段维护，任务定义页面不提交 | 前端画布视图 |
| `depEventIds` | `varchar` | `String` | 流程编排阶段维护，当前按逗号分隔字符串保存 | `TaskStorageImpl` 转调度模型时解析为集合 |
| `taskCode` | `varchar(255)` | `String` | Service 层唯一性校验；任务定义 API 最大 235 | 当前无数据库唯一索引；预留 20 位给复制后缀 |
| `syncFlag` | `bool` | `Boolean` | 新增、修改和复制任务时置 `false`；统一登记成功后置 `true` | 表示业务任务是否已同步到调度配置 |
| `sourceRoute` | `text` | `String` | Entity 存协议串，响应 DTO 返回结构化对象 | 业务来源身份只由 `bizSystem + bizKey` 决定 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id` |
| `BaseEntity` | `datafusion-common-spring` | Entity 基类 | 提供审计字段 |
| `CommonException` | `datafusion-common` | 业务异常 | 任务不存在、任务编码重复、已绑定流程不可删除 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
| `JacksonUtils` | `datafusion-common` | JSON 转换 | 字符串、`JsonNode` 和调度模型之间转换 |
| `TaskInfo` | `datafusion-scheduler-master` | 调度框架模型 | `TaskStorageImpl` 从 `TaskInfoEntity` 转换 |
