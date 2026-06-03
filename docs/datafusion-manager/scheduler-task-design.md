# scheduler-task 设计文档

> 数据结构定义见 [scheduler-task-data-define.md](./scheduler-task-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 调度任务定义的分页查询、列表查询、新增、修改、详情查询和删除。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.scheduler`
- 路径前缀: `/api/scheduler/task`
- 调用链: `TaskController` -> `TaskInfoService` -> `TaskInfoServiceImpl` -> `TaskInfoMapper` -> `scheduler_task_info`

`TaskController` 只管理任务定义，不直接提交 worker 任务，也不维护任务实例状态。任务实例执行链由 `TaskStorageImpl`、`FlowInfoServiceImpl` 和 `datafusion-scheduler-master` 处理。

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
| 分页查询 | `taskName`、`taskCode` 模糊匹配，`taskType/flowId/enabled/isBound` 精确匹配 | 返回 `PageResponse<TaskInfoDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<TaskInfoDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "任务不存在")` |
| 新增任务 | `taskName/taskCode/taskTypeId/taskType/pluginId` 必填；`taskCode` 唯一 | 重复编码或缺字段时失败 |
| 新增任务 | `id` 由 `UUID.nameUUIDFromBytes(taskCode)` 生成 | `taskCode` 变化会导致 ID 语义变化，新增前必须先校验唯一 |
| 新增任务 | `isBound=false`、`enabled=false`、`syncFlag=false` | 作为未绑定、未启用的任务定义 |
| 修改任务 | 先查询旧实体，再合并非空字段 | 合并后置 `syncFlag=false` |
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

## 9. 验证

- 单元测试: 建议覆盖新增任务、重复 `taskCode`、修改后唯一性检查、删除已绑定流程任务、按 `flowId` 查询任务。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`
- Checkstyle: 本次只新增文档，不涉及 Java 代码。
- 手工检查: 使用 `/page`、`/list`、`/add`、`/update`、`/detail/{id}`、`/delete/{id}` 验证返回和异常。
