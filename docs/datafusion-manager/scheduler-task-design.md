# scheduler-task 设计文档

> 数据结构定义见 [scheduler-task-data-define.md](./scheduler-task-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 调度任务定义的分页查询、列表查询、新增、修改、详情查询和删除。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.scheduler`
- 路径前缀: `/api/scheduler/task`
- 调用链: `TaskController` -> `TaskInfoService` -> `TaskInfoServiceImpl` -> `TaskInfoMapper` -> `scheduler_task_info`

`TaskController` 只管理任务定义，不直接提交 worker 任务，也不维护任务实例状态。任务实例执行链由 `TaskStorageImpl`、`FlowInfoServiceImpl` 和 `datafusion-scheduler-master` 处理。

### 1.1 字段边界

`scheduler_task_info` 当前同时保存任务定义属性和调度编排属性。任务定义页面和 `TaskController` 的新增/修改入参只处理任务定义属性。

| 分类 | 字段 | 说明 |
|------|------|------|
| 任务定义属性 | `taskName`、`taskCode`、`description`、`taskTypeId`、`taskType`、`taskParam`、`definition` | 描述任务自身，允许在任务定义页面新增、修改和查看 |
| 调度编排属性 | `isBound`、`flowId`、`pluginId`、`view`、`depEventIds`、`eventId`、`enabled` | 描述任务进入流程后的绑定、执行、画布、事件和启停信息，不在任务定义页面展示或编辑 |
| 系统属性 | `id`、`syncFlag`、`sourceRoute`、`creator`、`updater`、`createTime`、`updateTime` | 由系统生成或维护，任务定义页面只读展示必要审计信息 |

`pluginId` 是调度执行适配属性。短期由于表结构要求 `plugin_id uuid NOT NULL`，新增任务时由后端根据 `taskType` 解析默认执行插件并写入；任务定义页面不展示、不提交 `pluginId`。如果同一 `taskType` 后续支持多个执行插件，应在任务拖入流程后的节点配置中选择或覆盖，而不是在任务定义阶段暴露。

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<TaskInfoQueryDto>` | `Result<PageResponse<TaskInfoDto>>` | 分页查询任务 |
| `POST` | `/list` | `TaskInfoQueryDto` | `Result<List<TaskInfoDto>>` | 查询任务列表 |
| `POST` | `/add` | `TaskInfoSaveDto` | `Result<UUID>` | 新增任务 |
| `POST` | `/update` | `TaskInfoUpdateDto` | `Result<Boolean>` | 修改任务 |
| `GET` | `/detail/{id}` | path `UUID id` | `Result<TaskInfoDto>` | 根据 ID 查询任务 |
| `POST` | `/delete/{id}` | path `UUID id` | `Result<Boolean>` | 删除任务 |

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/scheduler-task-data-define.md` | 调度任务字段、类型、校验和层间映射定义 |
| `docs/datafusion-manager/scheduler-task-design.md` | 调度任务 API、Service、Mapper、集成和验证设计 |

### 3.2 修改

无。

### 3.3 复用

| 对象 | 路径 | 说明 |
|------|------|------|
| `TaskController` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/TaskController.java` | HTTP 入口 |
| `TaskInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/TaskInfoService.java` | Service 契约 |
| `TaskInfoServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/TaskInfoServiceImpl.java` | 业务实现 |
| `TaskInfoMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/TaskInfoMapper.java` | MyBatis-Plus Mapper |
| `TaskInfoMapper.xml` | `datafusion-manager/src/main/resources/mapper/TaskInfoMapper.xml` | `getTaskInfo` 和 `listByFlowId` 自定义查询 |
| `TaskInfoEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/TaskInfoEntity.java` | 表实体 |
| `TaskStorageImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/TaskStorageImpl.java` | master `TaskStorage` 适配 |
| `FlowInfoServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/FlowInfoServiceImpl.java` | 流程 DAG 读取任务定义 |
| `Result<T>` | `datafusion-common-spring` | API 响应包装 |
| `PageQuery<T>` / `PageResponse<T>` | `datafusion-common-spring` | 分页请求和响应 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `getTaskInfo` | `UUID taskId` | `TaskInfoEntity` | 按任务 ID 查询实体，供 `TaskStorageImpl` 使用 |
| `listByFlowId` | `UUID flowId` | `List<TaskInfoEntity>` | 按流程 ID 查询任务定义，供流程 DAG 组装使用 |
| `pageTaskInfo` | `PageQuery<TaskInfoQueryDto>` | `PageResponse<TaskInfoDto>` | 按条件分页查询 |
| `listTaskInfo` | `TaskInfoQueryDto` | `List<TaskInfoDto>` | 按条件查询列表 |
| `getTaskInfoById` | `UUID id` | `TaskInfoDto` | 查询详情，不存在时抛业务异常 |
| `addTaskInfo` | `TaskInfoSaveDto` | `UUID` | 新增任务定义，校验 `taskCode` 唯一 |
| `updateTaskInfo` | `TaskInfoUpdateDto` | `boolean` | 按非空字段合并更新 |
| `deleteTaskInfo` | `UUID id` | `boolean` | 删除任务定义，删除前检查是否已绑定流程 |

### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `taskName`、`taskCode` 模糊匹配，`taskType` 精确匹配；任务定义页面不提供 `flowId/enabled/isBound` 等调度属性筛选 | 返回 `PageResponse<TaskInfoDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<TaskInfoDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "任务不存在")` |
| 新增任务 | `taskName/taskCode/taskTypeId/taskType` 必填；`taskCode` 唯一；`pluginId` 由后端按 `taskType` 填默认执行插件 | 重复编码、缺字段或无法解析默认执行插件时失败 |
| 新增任务 | `id` 由 `UUID.nameUUIDFromBytes(taskCode)` 生成 | `taskCode` 变化会导致 ID 语义变化，新增前必须先校验唯一 |
| 新增任务 | `isBound=false`、`flowId=null`、`view=null`、`depEventIds=null`、`eventId=null`、`enabled=false`、`syncFlag=false` | 调度编排属性由后端置默认值，不由任务定义页面提交 |
| 修改任务 | 先查询旧实体，再合并非空字段 | 任务定义页面不提交 `pluginId/view/depEventIds/eventId/enabled`；流程编排阶段可维护这些字段；`clearEventId=true` 时清空 `eventId`；合并后置 `syncFlag=false` |
| 删除任务 | 仅允许删除未绑定流程的任务 | 已绑定流程时抛 `任务已绑定流程, 无法删除` |

### 4.2 事务边界

- 是否需要事务: 当前代码未显式声明事务。
- 事务方法: `addTaskInfo`、`updateTaskInfo`、`deleteTaskInfo` 建议后续补充事务。
- 回滚条件: 建议对 `RuntimeException` 和 `CommonException` 回滚。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 列表查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除前检查 |
| `selectCount` | 查询 | MyBatis-Plus `BaseMapper` | `taskCode` 唯一性校验 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增任务 |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改任务 |
| `deleteById` / `removeById` | 写入 | MyBatis-Plus `ServiceImpl` | 删除任务 |
| `getTaskInfo` | 查询 | `TaskInfoMapper.xml` | `select * from scheduler_task_info where id = #{taskId}` |
| `listByFlowId` | 查询 | `TaskInfoMapper.xml` | `select * from scheduler_task_info where flow_id = #{flowId} order by create_time asc` |

自定义 SQL 使用 `#{}` 参数绑定。

## 6. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD、分页和条件查询 |
| `HttpUtils` | 静态工具调用 | 获取当前用户名，写入 `creator` 和 `updater` |
| `TaskStorageImpl` | scheduler master 适配 | 将任务定义转换为调度框架 `TaskInfo` |
| `FlowInfoServiceImpl` | Service 注入 | 根据 `flowId` 组装流程 DAG 时读取任务定义 |
| `TaskLinkService` | Service 注入 | 作为流程 DAG 的连线来源 |
| 默认执行插件解析 | Service 内部规则 | 根据 `taskType` 获取默认 `pluginId`，用于满足 `scheduler_task_info.plugin_id` 非空约束 |

## 7. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户/项目/App Header: 当前链路未显式处理。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除依赖外层统一鉴权。

## 8. 不实现的部分

- 不在 `TaskController` 中处理 worker 提交、任务执行或实例状态变更。
- 不修改 `scheduler_task_info` 表结构。
- 不把 `TaskInfoEntity` 与 `TaskInstanceEntity` 混用。
- 不在本设计中新增任务执行插件协议。
- 不在任务定义页面展示或编辑 `isBound`、`flowId`、`pluginId`、`view`、`depEventIds`、`eventId`、`enabled`。
- 不在本设计中实现流程节点级插件覆盖；该能力属于流程编排/节点配置设计。

## 9. 后端实现说明

本次前端迁移不提交 `pluginId`。后端新增任务时采用以下兼容逻辑：

- `TaskInfoSaveDto.pluginId` 为非前端必填字段，保留字段只用于兼容旧调用方显式传值。
- `TaskInfoServiceImpl.addTaskInfo` 新增时优先使用入参 `pluginId`；为空时按 `taskType` 解析默认执行插件，并写入 `TaskInfoEntity.pluginId`。
- 第一版默认映射支持 `DATAX`、`SHELL`、`SQL`、`HTTP`、`SPARK`。
- 如果无法根据 `taskType` 找到默认执行插件，后端返回明确业务错误，不能写入空 `pluginId`。

该点只影响任务定义新增链路；任务修改链路仍不应从任务定义页面修改 `pluginId`。

## 10. 验证

- 单元测试: 建议覆盖新增任务、重复 `taskCode`、修改后唯一性检查、删除已绑定流程任务、按 `flowId` 查询任务。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`
- Checkstyle: 本次只新增文档，不涉及 Java 代码。
- 手工检查: 使用 `/page`、`/list`、`/add`、`/update`、`/detail/{id}`、`/delete/{id}` 验证返回和异常。
