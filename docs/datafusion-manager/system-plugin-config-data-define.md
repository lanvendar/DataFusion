# system-plugin-config 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 插件配置表信息

- 表名: `system_plugin_config`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 系统插件配置表，保存插件名称、插件类型、运行模式、模板标记、配置参数和租户隔离信息。

> 注意：原始 COMMENT 中 `name`、`type`、`project_id`、`create_user`、`update_user` 与真实列名不一致。本文按真实列名 `plugin_name`、`plugin_type`、`tenant_id`、`creator`、`updater` 设计。

### 1.2 插件配置表 DDL

```sql
CREATE TABLE system_plugin_config (
    id uuid NOT NULL,
    plugin_name varchar(255) NOT NULL,
    plugin_type varchar(255) NOT NULL,
    run_mode varchar(255) NOT NULL,
    description varchar(255) NULL,
    plugin_param jsonb NULL,
    is_template bool NOT NULL DEFAULT false,
    is_del int2 NOT NULL DEFAULT 0,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    tenant_id uuid NOT NULL,
    CONSTRAINT system_plugin_config_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE system_plugin_config IS '系统插件配置表';
COMMENT ON COLUMN system_plugin_config.id IS '主键';
COMMENT ON COLUMN system_plugin_config.plugin_name IS '插件名称';
COMMENT ON COLUMN system_plugin_config.plugin_type IS '插件类型';
COMMENT ON COLUMN system_plugin_config.run_mode IS '运行模式';
COMMENT ON COLUMN system_plugin_config.description IS '描述';
COMMENT ON COLUMN system_plugin_config.plugin_param IS '插件配置';
COMMENT ON COLUMN system_plugin_config.is_template IS '模板数据标记';
COMMENT ON COLUMN system_plugin_config.is_del IS '删除状态：0-正常; 1-删除';
COMMENT ON COLUMN system_plugin_config.creator IS '创建人';
COMMENT ON COLUMN system_plugin_config.updater IS '修改人';
COMMENT ON COLUMN system_plugin_config.create_time IS '创建时间';
COMMENT ON COLUMN system_plugin_config.update_time IS '修改时间';
COMMENT ON COLUMN system_plugin_config.tenant_id IS '租户ID';
```

当前 DDL 未对 `plugin_name`、`plugin_type`、`run_mode`、`tenant_id` 或模板标记建立唯一约束，名称唯一性由 Service 层校验；`is_template` 只作为内置初始化模板数据标记，不触发其他记录变更。

### 1.3 任务类型配置表信息

- 表名: `system_task_type_config`
- 操作: 提供分页查询、列表查询、新增、修改、详情查询和删除。
- 主键: `id uuid`
- 候选键: `task_type`
- 说明: 任务类型配置表，维护 `task_type` 到默认插件 `default_plugin_id` 的绑定关系，并记录该任务类型适用的 `plugin_type`。

> 注意：原始 COMMENT 中表名 `system_execution_component` 和 `task_module`、`project_id`、`model_status`、`create_user`、`update_user` 等列名与当前 DDL 不一致。本文按真实表名 `system_task_type_config` 和真实列名设计。

### 1.4 任务类型配置表 DDL

```sql
CREATE TABLE system_task_type_config (
    id uuid NOT NULL,
    task_type varchar(255) NOT NULL,
    default_plugin_id uuid NOT NULL,
    plugin_type varchar(255) NULL,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    tenant_id uuid NOT NULL,
    CONSTRAINT system_task_type_config_pkey PRIMARY KEY (id)
);

COMMENT ON TABLE system_task_type_config IS '任务类型配置表';
COMMENT ON COLUMN system_task_type_config.id IS '主键';
COMMENT ON COLUMN system_task_type_config.task_type IS '任务类型';
COMMENT ON COLUMN system_task_type_config.default_plugin_id IS '默认插件ID';
COMMENT ON COLUMN system_task_type_config.plugin_type IS '插件类型';
COMMENT ON COLUMN system_task_type_config.creator IS '创建人';
COMMENT ON COLUMN system_task_type_config.updater IS '修改人';
COMMENT ON COLUMN system_task_type_config.create_time IS '创建时间';
COMMENT ON COLUMN system_task_type_config.update_time IS '修改时间';
COMMENT ON COLUMN system_task_type_config.tenant_id IS '租户ID';
```

`system_task_type_config` 通过 Controller、Service、DTO、Mapper 提供 CRUD 能力。`default_plugin_id` 指向 `system_plugin_config.id`，当前 DDL 不强制外键，避免初始化顺序和跨环境迁移被数据库约束阻塞。

当前 DDL 未对 `task_type`、`tenant_id`、`plugin_type` 或 `default_plugin_id` 建立唯一约束；Service 层以 `task_type` 作为候选键校验，新增时 `id` 使用 `UUID.nameUUIDFromBytes(taskType.getBytes(StandardCharsets.UTF_8))` 稳定生成。

### 1.5 字段分层

| 分类 | 字段 | 前端处理方式 | 说明 |
|------|------|--------------|------|
| 插件定义属性 | `plugin_name`、`plugin_type`、`run_mode`、`description`、`plugin_param`、`is_template` | `is_template` 仅可查看和查询过滤；其他字段可新增、修改、查看 | 描述插件身份、运行模式、适用范围和配置内容 |
| 任务类型定义属性 | `task_type`、`default_plugin_id`、`plugin_type` | `task_type` 新增后不可修改；`default_plugin_id`、`plugin_type` 可修改 | 描述任务类型与默认执行插件绑定关系 |
| 数据管理属性 | `is_del`、`tenant_id` | 只读或不展示 | 软删除和租户隔离信息，由后端维护 |
| 系统属性 | `id`、`creator`、`updater`、`create_time`、`update_time` | 只读或不展示 | 由系统生成、维护或用于追踪 |

本文将 `plugin_type` 视为插件大类，`run_mode` 视为运行模式；`plugin_param` 模板由 `plugin_type + run_mode` 共同决定。`is_template` 表示这条记录是系统初始化模板数据。

### 1.6 插件配置表字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 新增时由 `pluginName + pluginType + runMode + tenantId` 拼接后通过 `UUID.nameUUIDFromBytes(...)` 生成 | 主键，继承自 `BaseIdEntity` |
| `plugin_name` | `pluginName` | `String` | 是 | 无 | 插件名称，同一租户同插件类型同运行模式下应保持唯一 |
| `plugin_type` | `pluginType` | `String` | 是 | 无 | 插件类型，建议作为字典值维护 |
| `run_mode` | `runMode` | `String` | 是 | `DEFAULT` | 运行模式，建议作为字典值维护，例如 `DEFAULT`、`YARN`、`K8S` |
| `description` | `description` | `String` | 否 | 无 | 描述 |
| `plugin_param` | `pluginParam` | `JsonNode` | 否 | 无 | 插件配置，使用 JSONB 存储 |
| `is_template` | `isTemplate` | `Boolean` | 否 | `false` | 是否模板数据 |
| `is_del` | `isDel` | `Short` | 是 | `0` | 删除状态：`0`-正常，`1`-删除 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |
| `tenant_id` | `tenantId` | `UUID` | 是 | 当前阶段固定为 `00000000-0000-0000-0000-000000000001` | 租户 ID，作为数据隔离边界 |

### 1.7 任务类型配置表字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 新增时由 `taskType` 通过 `UUID.nameUUIDFromBytes(...)` 生成 | 主键 |
| `task_type` | `taskType` | `String` | 是 | 无 | 任务类型 |
| `default_plugin_id` | `defaultPluginId` | `UUID` | 是 | 无 | 默认插件 ID，对应 `system_plugin_config.id` |
| `plugin_type` | `pluginType` | `String` | 否 | 无 | 插件类型，用于约束任务类型可选插件大类 |
| `creator` | `creator` | `String` | 是 | 初始化用户 | 创建人 |
| `updater` | `updater` | `String` | 是 | 初始化用户 | 修改人 |
| `create_time` | `createTime` | `Date` | 是 | 初始化时间 | 创建时间 |
| `update_time` | `updateTime` | `Date` | 是 | 初始化时间 | 修改时间 |
| `tenant_id` | `tenantId` | `UUID` | 是 | 初始化租户 | 租户 ID，作为数据隔离边界 |

## 2. Entity / PO 映射

本节定义 `system_plugin_config` 与 `system_task_type_config` 的 Entity / PO 映射。

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `pluginName` | `plugin_name` | `String` | `@TableField("plugin_name")` | 插件名称 |
| `pluginType` | `plugin_type` | `String` | `@TableField("plugin_type")` | 插件类型 |
| `runMode` | `run_mode` | `String` | `@TableField("run_mode")` | 运行模式 |
| `description` | `description` | `String` | `@TableField("description")` | 描述 |
| `pluginParam` | `plugin_param` | `JsonNode` | `@TableField(value = "plugin_param", typeHandler = JsonNodeTypeHandler.class)` | JSONB 配置 |
| `isTemplate` | `is_template` | `Boolean` | `@TableField("is_template")` | 是否模板数据 |
| `isDel` | `is_del` | `Short` | `@TableField("is_del")` | 删除状态 |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |
| `tenantId` | `tenant_id` | `UUID` | `@TableField("tenant_id")` | 租户 ID |

### 2.1 任务类型配置 Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity`，新增时由 `taskType` 稳定生成 |
| `taskType` | `task_type` | `String` | `@TableField("task_type")` | 任务类型，候选键 |
| `defaultPluginId` | `default_plugin_id` | `UUID` | `@TableField("default_plugin_id")` | 默认插件 ID |
| `pluginType` | `plugin_type` | `String` | `@TableField("plugin_type")` | 插件类型 |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |
| `tenantId` | `tenant_id` | `UUID` | `@TableField("tenant_id")` | 租户 ID |

## 3. DTO 定义

本节定义 `system_plugin_config` 与 `system_task_type_config` 的 DTO。

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `PluginConfigQueryDto` | `Query` | 分页和列表查询 | `pluginName` | `String` | `like` | 插件名称模糊查询 |
| `PluginConfigQueryDto` | `Query` | 分页和列表查询 | `pluginType` | `String` | `eq` | 插件类型精确查询 |
| `PluginConfigQueryDto` | `Query` | 分页和列表查询 | `runMode` | `String` | `eq` | 运行模式精确查询 |
| `PluginConfigQueryDto` | `Query` | 分页和列表查询 | `isTemplate` | `Boolean` | `eq` | 是否模板数据 |
| `PluginConfigQueryDto` | `Query` | 分页和列表查询 | `isDel` | `Short` | `eq`，默认 `0` | 删除状态，默认只查正常数据 |
| `PluginConfigSaveDto` | `Request` | 新增和复制插件配置 | `pluginName` | `String` | `@NotBlank` | 插件名称；复制时后端基于该值追加 `_` 和 4 位随机码 |
| `PluginConfigSaveDto` | `Request` | 新增和复制插件配置 | `pluginType` | `String` | `@NotBlank` | 插件类型 |
| `PluginConfigSaveDto` | `Request` | 新增和复制插件配置 | `runMode` | `String` | `@NotBlank`，默认 `DEFAULT` | 运行模式 |
| `PluginConfigSaveDto` | `Request` | 新增和复制插件配置 | `description` | `String` | 可选 | 描述 |
| `PluginConfigSaveDto` | `Request` | 新增和复制插件配置 | `pluginParam` | `JsonNode` | 可选 | JSONB 配置 |
| `PluginConfigUpdateDto` | `Request` | 修改插件配置 | `id` | `UUID` | `@NotNull` | 插件配置 ID |
| `PluginConfigUpdateDto` | `Request` | 修改插件配置 | `pluginName` | `String` | 非空时合并 | 插件名称 |
| `PluginConfigUpdateDto` | `Request` | 修改插件配置 | `pluginType` | `String` | 非空时合并 | 插件类型 |
| `PluginConfigUpdateDto` | `Request` | 修改插件配置 | `runMode` | `String` | 非空时合并 | 运行模式 |
| `PluginConfigUpdateDto` | `Request` | 修改插件配置 | `description` | `String` | 非 `null` 时合并 | 描述 |
| `PluginConfigUpdateDto` | `Request` | 修改插件配置 | `pluginParam` | `JsonNode` | 非 `null` 时合并 | JSONB 配置 |
| `PluginConfigDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `PluginConfigDto` | `Response` | 查询响应 | `pluginName` | `String` | 无 | 插件名称 |
| `PluginConfigDto` | `Response` | 查询响应 | `pluginType` | `String` | 无 | 插件类型 |
| `PluginConfigDto` | `Response` | 查询响应 | `runMode` | `String` | 无 | 运行模式 |
| `PluginConfigDto` | `Response` | 查询响应 | `description` | `String` | 无 | 描述 |
| `PluginConfigDto` | `Response` | 查询响应 | `pluginParam` | `JsonNode` | 无 | JSONB 配置 |
| `PluginConfigDto` | `Response` | 查询响应 | `isTemplate` | `Boolean` | 无 | 是否模板数据 |
| `PluginConfigDto` | `Response` | 查询响应 | `isDel` | `Short` | 无 | 删除状态 |
| `PluginConfigDto` | `Response` | 查询响应 | `tenantId` | `UUID` | 无 | 租户 ID |
| `PluginConfigDto` | `Response` | 查询响应 | `creator` | `String` | 无 | 创建人 |
| `PluginConfigDto` | `Response` | 查询响应 | `updater` | `String` | 无 | 修改人 |
| `PluginConfigDto` | `Response` | 查询响应 | `createTime` | `Date` | 无 | 创建时间 |
| `PluginConfigDto` | `Response` | 查询响应 | `updateTime` | `Date` | 无 | 修改时间 |
| `TaskTypeConfigQueryDto` | `Query` | 分页和列表查询 | `taskType` | `String` | `like` | 任务类型模糊查询 |
| `TaskTypeConfigQueryDto` | `Query` | 分页和列表查询 | `defaultPluginId` | `UUID` | `eq` | 默认插件 ID 精确查询 |
| `TaskTypeConfigQueryDto` | `Query` | 分页和列表查询 | `pluginType` | `String` | `eq` | 插件类型精确查询 |
| `TaskTypeConfigSaveDto` | `Request` | 新增任务类型配置 | `taskType` | `String` | `@NotBlank` | 任务类型，新增后不可修改 |
| `TaskTypeConfigSaveDto` | `Request` | 新增任务类型配置 | `defaultPluginId` | `UUID` | `@NotNull` | 默认插件 ID |
| `TaskTypeConfigSaveDto` | `Request` | 新增任务类型配置 | `pluginType` | `String` | 可选 | 插件类型 |
| `TaskTypeConfigUpdateDto` | `Request` | 修改任务类型配置 | `id` | `UUID` | `@NotNull` | 任务类型配置 ID |
| `TaskTypeConfigUpdateDto` | `Request` | 修改任务类型配置 | `defaultPluginId` | `UUID` | `@NotNull` | 默认插件 ID |
| `TaskTypeConfigUpdateDto` | `Request` | 修改任务类型配置 | `pluginType` | `String` | 可选 | 插件类型 |
| `TaskTypeConfigDto` | `Response` | 查询响应 | `id`、`taskType`、`defaultPluginId`、`pluginType`、`tenantId`、`creator`、`updater`、`createTime`、`updateTime` | `UUID/String/Date` | 无 | 任务类型配置响应 |

## 4. API 数据映射

本节定义 `system_plugin_config` 与 `system_task_type_config` 的 API。

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/system/plugin/page` | `PageQuery<PluginConfigQueryDto>` | `PageResponse<PluginConfigDto>` | `Result<T>` | 分页查询 |
| `POST /api/system/plugin/list` | `PluginConfigQueryDto` | `List<PluginConfigDto>` | `Result<T>` | 列表查询 |
| `POST /api/system/plugin/add` | `PluginConfigSaveDto` | `UUID` | `Result<T>` | 新增插件配置 |
| `POST /api/system/plugin/copy` | `PluginConfigSaveDto` | `UUID` | `Result<T>` | 复制插件配置 |
| `POST /api/system/plugin/update` | `PluginConfigUpdateDto` | `Boolean` | `Result<T>` | 修改插件配置 |
| `GET /api/system/plugin/{id}` | path `UUID id` | `PluginConfigDto` | `Result<T>` | 查询详情 |
| `DELETE /api/system/plugin/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 软删除插件配置 |
| `POST /api/system/task-type/page` | `PageQuery<TaskTypeConfigQueryDto>` | `PageResponse<TaskTypeConfigDto>` | `Result<T>` | 分页查询任务类型配置 |
| `POST /api/system/task-type/list` | `TaskTypeConfigQueryDto` | `List<TaskTypeConfigDto>` | `Result<T>` | 列表查询任务类型配置 |
| `POST /api/system/task-type/add` | `TaskTypeConfigSaveDto` | `UUID` | `Result<T>` | 新增任务类型配置 |
| `POST /api/system/task-type/update` | `TaskTypeConfigUpdateDto` | `Boolean` | `Result<T>` | 修改任务类型配置 |
| `GET /api/system/task-type/{id}` | path `UUID id` | `TaskTypeConfigDto` | `Result<T>` | 查询详情 |
| `DELETE /api/system/task-type/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除任务类型配置 |

## 5. 层间转换规则

本节定义 `system_plugin_config` 与 `system_task_type_config` 的层间转换规则。

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `PluginConfigSaveDto` -> `PluginConfigEntity` | 复制 `pluginName`、`pluginType`、`runMode`、`description`、`pluginParam` | `id` 使用 `UUID.nameUUIDFromBytes((pluginName + pluginType + runMode + tenantId).getBytes(StandardCharsets.UTF_8))` 生成；`isTemplate=false`；`isDel=0`；`tenantId` 当前阶段使用固定默认租户；审计字段由 Service 设置 |
| `PluginConfigSaveDto` -> copied `PluginConfigEntity` | 复制 `pluginType`、`runMode`、`description`、`pluginParam` | `pluginName` 使用请求体 `pluginName + "_" + 4位随机码`；不接收 `id`；`isTemplate` 强制为 `false`；其余主键、租户和审计规则与新增一致 |
| `PluginConfigUpdateDto` -> existing `PluginConfigEntity` | 按非空字段合并 | 不修改 `tenantId`、`isDel`、`creator`、`createTime`、`isTemplate` |
| `PluginConfigEntity` -> `PluginConfigDto` | 字段逐一复制 | `pluginParam` 以 `JsonNode` 直接返回 |
| `PluginConfigQueryDto` -> `LambdaQueryWrapper` | `pluginName` 使用 `like`，`pluginType`、`runMode`、`isTemplate`、`isDel` 使用 `eq` | 默认只查 `isDel=0` 且当前租户数据 |
| `TaskTypeConfigSaveDto` -> `TaskTypeConfigEntity` | 复制 `taskType`、`defaultPluginId`、`pluginType` | `defaultPluginId` 必填；`taskType` trim 后转大写；`id` 使用 `UUID.nameUUIDFromBytes(taskType.getBytes(StandardCharsets.UTF_8))` 生成；`tenantId` 当前阶段使用固定默认租户；审计字段由 Service 设置 |
| `TaskTypeConfigUpdateDto` -> existing `TaskTypeConfigEntity` | 按字段合并 `defaultPluginId`、`pluginType` | `defaultPluginId` 必填；不修改 `taskType`、`tenantId`、`creator`、`createTime` |
| `TaskTypeConfigEntity` -> `TaskTypeConfigDto` | 字段逐一复制 | 无 |
| `TaskTypeConfigQueryDto` -> `LambdaQueryWrapper` | `taskType` 使用 `like`，`defaultPluginId`、`pluginType` 使用 `eq` | 默认只查当前租户数据 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `plugin_param` | `jsonb` | `JsonNode` | API 直接使用 JSON 对象，Entity 通过 `JsonNodeTypeHandler` 持久化 | 插件配置 |
| `is_template` | `bool` | `Boolean` | `true` 表示模板数据，`false` 表示普通用户配置 | 模板标记 |
| `is_del` | `int2` | `Short` | `0` 表示正常，`1` 表示删除 | 软删除状态 |
| `run_mode` | `varchar(255)` | `String` | 作为模板选择维度，与 `plugin_type` 共同决定 `pluginParam` 结构 | 运行模式 |
| `tenant_id` | `uuid` | `UUID` | 当前阶段固定默认租户，后续接入正式租户上下文 | 临时默认值为 `00000000-0000-0000-0000-000000000001` |

## 7. 初始化数据

`init_data.sql` 初始化当前 agent 已实现的插件运行组合，初始化记录均使用固定默认租户
`00000000-0000-0000-0000-000000000001`、`creator=system`、`updater=system`、`is_template=true`、`is_del=0`。

### 7.1 `system_plugin_config`

| `id` | `plugin_name` | `plugin_type` | `run_mode` | `plugin_param` 来源 |
|------|---------------|---------------|------------|------------|
| `81deb2e9-2c69-33d0-917a-dded2e73ce6d` | `DataX LOCAL 模板` | `DATAX` | `LOCAL` | `datafusion-agent/src/main/resources/plugins/datax/templates/datax-local-plugin-config.json` 中的插件配置 JSON |
| `e9f668d7-7d7c-30e3-9143-3c5ab6019eb1` | `DataX K8S 模板` | `DATAX` | `K8S` | `datafusion-agent/src/main/resources/plugins/datax/templates/datax-k8s-plugin-config.json` 中的插件配置 JSON |
| `82a2e64f-47cb-3545-96f1-be547a1f5253` | `Shell LOCAL 模板` | `SHELL` | `LOCAL` | `datafusion-agent/src/main/resources/plugins/shell/templates/shell-local-plugin-config.json` 中的插件配置 JSON |

插件配置初始化主键按 Service 规则生成：
`UUID.nameUUIDFromBytes((pluginName + pluginType + runMode + tenantId).getBytes(StandardCharsets.UTF_8))`。

### 7.2 `system_task_type_config`

| `id` | `task_type` | `default_plugin_id` | `plugin_type` |
|------|-------------|---------------------|---------------|
| `d2f6659e-562a-350e-b926-d7812852e23d` | `DATAX` | `81deb2e9-2c69-33d0-917a-dded2e73ce6d` | `DATAX` |
| `28d568b3-892d-3e36-b283-3542693a1062` | `SHELL` | `82a2e64f-47cb-3545-96f1-be547a1f5253` | `SHELL` |

任务类型初始化主键按 Service 规则生成：
`UUID.nameUUIDFromBytes(taskType.getBytes(StandardCharsets.UTF_8))`。

`DATAX` 默认绑定 `DataX LOCAL 模板`。`DataX K8S 模板` 需要环境提供 `kubernetes.image`，因此只初始化为可复制/可选择的模板，不作为默认任务类型绑定。

## 8. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id` |
| `BaseEntity` | `datafusion-common-spring` | Entity 基类 | 提供审计字段 |
| `CommonException` | `datafusion-common` | 业务异常 | 插件不存在、名称重复、租户不匹配 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
| `JsonNodeTypeHandler` | `datafusion-common-spring` | JSONB 类型处理器 | `pluginParam` 持久化 |
