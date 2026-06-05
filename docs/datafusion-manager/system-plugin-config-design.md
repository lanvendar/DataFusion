# system-plugin-config 设计文档

> 数据结构定义见 [system-plugin-config-data-define.md](./system-plugin-config-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 系统插件配置的分页查询、列表查询、新增、复制、修改、详情查询和软删除；任务类型配置的分页查询、列表查询、新增、修改、详情查询和删除。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.system`
- 路径前缀: `/api/system/plugin`、`/api/system/task-type`
- 调用链: `PluginConfigController` -> `PluginConfigService` -> `PluginConfigServiceImpl` -> `PluginConfigMapper` -> `system_plugin_config`
- 调用链: `TaskTypeConfigController` -> `TaskTypeConfigService` -> `TaskTypeConfigServiceImpl` -> `TaskTypeConfigMapper` -> `system_task_type_config`

这个模块只负责插件配置和任务类型到默认插件的绑定管理，不负责插件 jar 上传、动态加载、代码执行或插件市场。

### 1.1 字段边界

| 分类 | 字段 | 说明 |
|------|------|------|
| 插件定义属性 | `pluginName`、`pluginType`、`runMode`、`description`、`env`、`isTemplate` | 描述插件身份、运行模式、适用范围和配置内容；`isTemplate` 仅允许查看和查询过滤，不通过新增、复制或更新接口修改 |
| 任务类型定义属性 | `taskType`、`defaultPluginId`、`pluginType` | 描述任务类型和默认执行插件绑定；`taskType` 为候选键，新增后不可修改 |
| 数据管理属性 | `isDel`、`tenantId` | 描述删除状态和租户隔离，默认由后端维护，不在普通表单中手工编辑 |
| 系统属性 | `id`、`creator`、`updater`、`createTime`、`updateTime` | 由系统生成或维护 |

`tenant_id` 是强约束隔离字段。当前阶段不从 `HttpUtils.getTenantId()` 读取租户，避免无认证令牌时阻断本地调试；后端临时使用固定默认租户 `00000000-0000-0000-0000-000000000001` 写入和过滤数据。

任务类型与默认插件 ID 的绑定由 `system_task_type_config` 维护。`task_type` 是候选键，新增时 `id` 由 `taskType` 通过 `UUID.nameUUIDFromBytes(...)` 稳定生成；`taskType` 新增后不通过修改接口变更。

`run_mode` 是 `plugin_type` 的第二维，用来选择 `env` 模板。比如 `FLINK + YARN` 和 `FLINK + K8S` 可以对应两套不同的配置结构。

新增插件配置时，`id` 由 `plugin_name + plugin_type + run_mode + tenant_id` 组成的字符串，按 `UUID.nameUUIDFromBytes(...)` 生成，保证同一业务维度下主键稳定。

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<PluginConfigQueryDto>` | `Result<PageResponse<PluginConfigDto>>` | 分页查询插件配置 |
| `POST` | `/list` | `PluginConfigQueryDto` | `Result<List<PluginConfigDto>>` | 查询插件配置列表 |
| `POST` | `/add` | `PluginConfigSaveDto` | `Result<UUID>` | 新增插件配置 |
| `POST` | `/copy` | `PluginConfigSaveDto` | `Result<UUID>` | 复制插件配置 |
| `POST` | `/update` | `PluginConfigUpdateDto` | `Result<Boolean>` | 修改插件配置 |
| `GET` | `/{id}` | path `UUID id` | `Result<PluginConfigDto>` | 根据 ID 查询插件配置 |
| `DELETE` | `/{id}` | path `UUID id` | `Result<Boolean>` | 软删除插件配置 |
| `POST` | `/api/system/task-type/page` | `PageQuery<TaskTypeConfigQueryDto>` | `Result<PageResponse<TaskTypeConfigDto>>` | 分页查询任务类型配置 |
| `POST` | `/api/system/task-type/list` | `TaskTypeConfigQueryDto` | `Result<List<TaskTypeConfigDto>>` | 查询任务类型配置列表 |
| `POST` | `/api/system/task-type/add` | `TaskTypeConfigSaveDto` | `Result<UUID>` | 新增任务类型配置 |
| `POST` | `/api/system/task-type/update` | `TaskTypeConfigUpdateDto` | `Result<Boolean>` | 修改任务类型配置 |
| `GET` | `/api/system/task-type/{id}` | path `UUID id` | `Result<TaskTypeConfigDto>` | 根据 ID 查询任务类型配置 |
| `DELETE` | `/api/system/task-type/{id}` | path `UUID id` | `Result<Boolean>` | 删除任务类型配置 |

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/system-plugin-config-data-define.md` | 系统插件配置字段、类型、校验和层间映射定义 |
| `docs/datafusion-manager/system-plugin-config-design.md` | 系统插件配置 API、Service、Mapper、集成和验证设计 |

### 3.2 修改

无。

### 3.3 后续实现建议新增

| 文件 | 说明 |
|------|------|
| `datafusion-manager/src/main/java/com/datafusion/manager/system/controller/PluginConfigController.java` | HTTP 入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/service/PluginConfigService.java` | Service 契约 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/service/impl/PluginConfigServiceImpl.java` | 业务实现 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dao/PluginConfigMapper.java` | MyBatis-Plus Mapper |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/po/PluginConfigEntity.java` | 表实体 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/PluginConfigQueryDto.java` | 查询 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/PluginConfigSaveDto.java` | 新增 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/PluginConfigUpdateDto.java` | 修改 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/PluginConfigDto.java` | 响应 DTO |
| `datafusion-manager/src/main/resources/mapper/PluginConfigMapper.xml` | 仅在需要自定义 SQL 时新增 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/controller/TaskTypeConfigController.java` | 任务类型配置 HTTP 入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/service/TaskTypeConfigService.java` | 任务类型配置 Service 契约 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/service/impl/TaskTypeConfigServiceImpl.java` | 任务类型配置业务实现 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dao/TaskTypeConfigMapper.java` | 任务类型配置 MyBatis-Plus Mapper |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/po/TaskTypeConfigEntity.java` | 任务类型配置表实体 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/TaskTypeConfigQueryDto.java` | 任务类型配置查询 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/TaskTypeConfigSaveDto.java` | 任务类型配置新增 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/TaskTypeConfigUpdateDto.java` | 任务类型配置修改 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/TaskTypeConfigDto.java` | 任务类型配置响应 DTO |
| `datafusion-web/src/modules/system-task-type-config/*` | 任务类型配置前端页面、API client、表格和表单 |
| `datafusion-web/src/router/routes.tsx` | 挂载“任务类型配置”菜单 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `pagePluginConfig` | `PageQuery<PluginConfigQueryDto>` | `PageResponse<PluginConfigDto>` | 按条件分页查询 |
| `listPluginConfig` | `PluginConfigQueryDto` | `List<PluginConfigDto>` | 按条件查询全量列表 |
| `getPluginConfigById` | `UUID id` | `PluginConfigDto` | 查询详情，不存在或越租户时抛业务异常 |
| `addPluginConfig` | `PluginConfigSaveDto` | `UUID` | 新增插件配置 |
| `copyPluginConfig` | `PluginConfigSaveDto` | `UUID` | 按请求体中的插件配置字段复制一条新插件配置 |
| `updatePluginConfig` | `PluginConfigUpdateDto` | `boolean` | 按非空字段合并更新 |
| `deletePluginConfig` | `UUID id` | `boolean` | 软删除插件配置 |
| `getPluginConfigByTypeAndMode` | `String pluginType`, `String runMode` | `PluginConfigEntity` | 按 `plugin_type + run_mode` 获取匹配配置，用于模板渲染或下游选择 |
| `pageTaskTypeConfig` | `PageQuery<TaskTypeConfigQueryDto>` | `PageResponse<TaskTypeConfigDto>` | 按条件分页查询任务类型配置 |
| `listTaskTypeConfig` | `TaskTypeConfigQueryDto` | `List<TaskTypeConfigDto>` | 按条件查询全量任务类型配置 |
| `getTaskTypeConfigById` | `UUID id` | `TaskTypeConfigDto` | 查询详情，不存在或越租户时抛业务异常 |
| `addTaskTypeConfig` | `TaskTypeConfigSaveDto` | `UUID` | 新增任务类型配置 |
| `updateTaskTypeConfig` | `TaskTypeConfigUpdateDto` | `boolean` | 修改默认插件和插件类型 |
| `deleteTaskTypeConfig` | `UUID id` | `boolean` | 删除任务类型配置 |
| `getDefaultPluginIdByTaskType` | `String taskType` | `UUID` | 按任务类型获取默认插件 ID，供任务定义新增时使用 |
### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `pluginName` 模糊匹配，`pluginType`、`runMode`、`isTemplate` 精确匹配，默认只查 `isDel=0` 且当前租户数据 | 返回 `PageResponse<PluginConfigDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<PluginConfigDto>` |
| 查询详情 | 根据 `id` 查询，必须命中当前租户且 `isDel=0` | 不存在时抛 `插件配置不存在` |
| 新增插件配置 | `pluginName`、`pluginType` 必填；`env` 可选；`tenantId` 当前阶段使用固定默认租户；`isDel=0` | 缺字段时失败 |
| 新增插件配置 | 同一租户下 `pluginType + runMode + pluginName` 应唯一 | 重复时抛 `插件名称已存在` |
| 新增插件配置 | 新记录的 `isTemplate` 固定为 `false`，接口不接收模板标记 | 不修改其他插件配置记录 |
| 复制插件配置 | 不通过 path `id` 查询源记录，前端提交当前行的 `pluginName`、`pluginType`、`runMode`、`description`、`env` 等字段 | 复制行为等同新增，由后端生成新主键 |
| 复制插件配置 | 复制请求体中的 `pluginType`、`runMode`、`description`、`env`，新记录的 `isTemplate` 固定为 `false` | 接口不接收模板标记 |
| 复制插件配置 | 新记录的 `pluginName` 默认使用请求体 `pluginName + "_" + 4位随机码` | 如果生成后与同租户同类型同运行模式插件名称重复，需要重新生成或抛 `插件名称已存在` |
| 复制插件配置 | 新记录的 `id` 仍按 `pluginName + pluginType + runMode + tenantId` 生成 | 复制后的主键随新 `pluginName` 稳定生成 |
| 修改插件配置 | 先查旧实体，再合并非空字段 | 不修改 `tenantId`、`isDel`、`creator`、`createTime` |
| 修改插件配置 | 若修改 `pluginName`、`pluginType` 或 `runMode`，需要重新做唯一性校验 | 重复时抛业务异常 |
| 修改插件配置 | 更新接口不接收、不修改 `isTemplate` | 模板标记由后台初始化数据维护 |
| 删除插件配置 | 采用软删除，把 `isDel` 更新为 `1` | 不做物理删除 |
| 删除插件配置 | 删除已是模板数据的记录时，不自动补选新模板数据 | 后续是否自动补位由业务再定 |
| 分页查询任务类型配置 | `taskType` 模糊匹配，`pluginType`、`defaultPluginId` 精确匹配，默认只查当前租户数据 | 返回 `PageResponse<TaskTypeConfigDto>` |
| 新增任务类型配置 | `taskType`、`defaultPluginId` 必填；写入前 `taskType` trim 后转大写；当前租户下 `taskType` 唯一 | 重复时抛 `任务类型已存在`；缺默认插件时抛 `默认插件ID不能为空` |
| 新增任务类型配置 | `id` 由标准化后的 `taskType` 通过 `UUID.nameUUIDFromBytes(...)` 生成 | 不由前端提交 |
| 修改任务类型配置 | `taskType` 是候选键，不通过修改接口变更；`defaultPluginId` 必填；仅修改 `defaultPluginId`、`pluginType` | 不修改 `tenantId`、`creator`、`createTime` |
| 查询默认插件 | 新增任务时优先使用请求体显式 `pluginId`；为空时按 `taskType` 查询 `system_task_type_config.default_plugin_id` | 不存在时抛明确业务异常 |
| 删除任务类型配置 | 采用物理删除，表结构没有 `is_del` 字段 | 删除后任务新增无法再按该类型解析默认插件 |

### 4.2 事务边界

- 是否需要事务: 需要。
- 事务方法: `addPluginConfig`、`copyPluginConfig`、`updatePluginConfig`、`deletePluginConfig`、`addTaskTypeConfig`、`updateTaskTypeConfig`、`deleteTaskTypeConfig` 建议放在事务内。
- 回滚条件: 建议对 `RuntimeException` 和 `CommonException` 回滚。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 列表查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除前检查 |
| `selectCount` | 查询 | MyBatis-Plus `BaseMapper` | `pluginName` 唯一性校验 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增插件配置 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 复制插件配置 |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改插件配置 |

本模块初版不要求自定义 SQL XML。

## 6. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD、分页和条件查询 |
| `HttpUtils` | 静态工具调用 | 获取当前用户姓名，写入 `creator` 和 `updater` |
| 临时默认租户 | Service 常量 | 写入 `tenantId`，并作为查询隔离条件；后续接入正式租户上下文后替换 |
| `TaskInfoServiceImpl` | Service 依赖 | 新建任务时不再使用静态映射解析 `taskType`，默认插件 ID 由任务类型配置表提供 |
| `TaskTypeConfigService` | Service 依赖 | `TaskInfoServiceImpl` 通过 `getDefaultPluginIdByTaskType` 获取默认插件 ID |
| `scheduler_task_info` | 下游消费表 | 任务定义表仍保留 `plugin_id`，本模块只管理插件配置本身 |

## 7. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户: 当前阶段使用 Service 内固定默认租户；后续接入正式租户上下文后恢复跨租户隔离。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除依赖外层统一鉴权。

## 8. 不实现的部分

- 不实现插件 jar 上传、解压、签名校验或动态热加载。
- 不实现插件代码执行器和 Worker 插件生命周期管理。
- 不实现插件市场、版本管理和灰度发布。
- 不实现跨租户共享插件。
- 不把 `system_plugin_config` 当作运行时任务实例表使用。
- 不提供任务类型配置软删除，当前表没有删除状态字段。

## 9. 风险和建议

- 当前阶段使用固定默认租户规避无认证令牌调试问题；正式多租户接入时，需要替换为统一租户上下文并完成历史数据迁移或兼容策略。
- 原始 DDL 的 COMMENT 与实际列名不一致，建议同步修正，否则后续建模和接口文档容易混乱。
- `is_del` 只有 1/0 语义，没有数据库唯一索引；并发新增需要 Service 层严控。
- 当前设计没有版本字段，如果后续需要插件升级、回滚或兼容多个版本，建议新增 `plugin_version`。
- `env` 是宽松 JSONB，前端和后端都需要约定字段结构，否则会出现“能存不能用”的问题。
- 任务类型与默认插件 ID 的关系由 `system_task_type_config` 维护；当前 DDL 没有数据库唯一索引，并发新增仍依赖 Service 层校验。

## 10. 验证

- 单元测试: 建议覆盖插件配置新增成功且 `isTemplate=false`、复制成功且 `isTemplate=false`、复制名称追加 4 位随机码、重复 `pluginName`、更新不改变 `isTemplate`、软删除、跨租户隔离和模板解析；任务类型配置新增成功、重复 `taskType`、更新默认插件、删除、按 `taskType` 解析默认插件。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`。
- 前端验证命令: `npm run build`。
- 手工检查: 用 `/api/system/plugin/page`、`/api/system/plugin/list`、`/api/system/plugin/add`、`/api/system/plugin/copy`、`/api/system/plugin/update`、`/api/system/plugin/{id}`、`DELETE /api/system/plugin/{id}`、`/api/system/task-type/page`、`/api/system/task-type/list`、`/api/system/task-type/add`、`/api/system/task-type/update`、`/api/system/task-type/{id}`、`DELETE /api/system/task-type/{id}` 验证接口契约和异常。
