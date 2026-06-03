# scheduler-event 设计文档

> 数据结构定义见 [scheduler-event-data-define.md](./scheduler-event-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 调度事件配置的分页查询、列表查询、新增、修改、详情查询和删除；同时通过 `EventStorageImpl` 适配 scheduler master 的全局事件实例存储。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.scheduler`
- 路径前缀: `/api/scheduler/event`
- 调用链: `EventController` -> `EventInfoService` -> `EventInfoServiceImpl` -> `EventInfoMapper` -> `scheduler_event_info`
- 调度集成链: `MasterStorage` -> `EventStorageImpl` -> `EventInstanceService` -> `EventInstanceMapper` -> `scheduler_event_instance`

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<EventInfoQueryDto>` | `Result<PageResponse<EventInfoDto>>` | 分页查询事件 |
| `POST` | `/list` | `EventInfoQueryDto` | `Result<List<EventInfoDto>>` | 查询事件列表 |
| `POST` | `/add` | `EventInfoSaveDto` | `Result<UUID>` | 新增事件 |
| `POST` | `/update` | `EventInfoUpdateDto` | `Result<Boolean>` | 修改事件 |
| `GET` | `/detail/{id}` | path `UUID id` | `Result<EventInfoDto>` | 根据 ID 查询事件 |
| `POST` | `/delete/{id}` | path `UUID id` | `Result<Boolean>` | 删除事件 |

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/scheduler-event-data-define.md` | 调度事件字段、类型、校验、表结构和层间映射定义 |
| `docs/datafusion-manager/scheduler-event-design.md` | 调度事件 API、Service、Mapper、集成和验证设计 |

### 3.2 修改

无。

### 3.3 复用

| 对象 | 路径 | 说明 |
|------|------|------|
| `EventController` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/EventController.java` | HTTP 入口 |
| `EventInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/EventInfoService.java` | 事件配置 Service 契约 |
| `EventInfoServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/EventInfoServiceImpl.java` | 事件配置业务实现 |
| `EventInfoMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/EventInfoMapper.java` | 事件配置 MyBatis-Plus Mapper |
| `EventInfoEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/EventInfoEntity.java` | `scheduler_event_info` 表实体 |
| `EventInstanceService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/EventInstanceService.java` | 事件实例 Service 契约 |
| `EventInstanceMapper.xml` | `datafusion-manager/src/main/resources/mapper/EventInstanceMapper.xml` | 事件实例自定义 SQL |
| `EventInstanceEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/EventInstanceEntity.java` | `scheduler_event_instance` 表实体 |
| `EventStorageImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/EventStorageImpl.java` | scheduler master `EventStorage` 适配 |
| `FlowInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/FlowInfoService.java` | 删除事件前检查流程引用 |
| `TaskInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/TaskInfoService.java` | 删除事件前检查任务引用 |
| `Result<T>` | `datafusion-common-spring` | API 响应包装 |
| `PageQuery<T>` / `PageResponse<T>` | `datafusion-common-spring` | 分页请求和响应 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `pageEventInfo` | `PageQuery<EventInfoQueryDto>` | `PageResponse<EventInfoDto>` | 按条件分页查询，按 `createTime desc` 排序 |
| `listEventInfo` | `EventInfoQueryDto` | `List<EventInfoDto>` | 按条件查询全量列表，按 `createTime desc` 排序 |
| `getEventInfoById` | `UUID id` | `EventInfoDto` | 查询事件详情，不存在时抛业务异常 |
| `addEventInfo` | `EventInfoSaveDto` | `UUID` | 新增事件，校验事件类型与关联对象字段一致性 |
| `updateEventInfo` | `EventInfoUpdateDto` | `boolean` | 按非空字段合并更新，合并后重新校验一致性 |
| `deleteEventInfo` | `UUID id` | `boolean` | 删除事件，删除前检查流程和任务引用 |

### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `eventName` 模糊匹配，`eventType/flowId/taskId` 精确匹配 | 返回 `PageResponse<EventInfoDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<EventInfoDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "事件不存在")` |
| 新增事件 | `eventName` 和 `eventType` 必填 | Bean Validation 失败时由全局异常处理返回 |
| 新增 `TASK` 事件 | `eventType="1"` 时 `taskId` 必填 | 缺失时抛 `TASK类型事件必须关联任务` |
| 新增 `FLOW` 事件 | `eventType="2"` 时 `flowId` 必填 | 缺失时抛 `FLOW类型事件必须关联流程` |
| 修改事件 | 先查询旧实体，再合并非空字段，最后校验合并后的 `eventType/flowId/taskId` | 不存在或关联不完整时抛业务异常 |
| 删除事件 | 删除前检查 `flow_info.event_id`、`task_info.event_id`、`flow_info.dep_event_ids`、`task_info.dep_event_ids` | 被引用时抛 `事件已被...引用, 无法删除` |
| 依赖事件检查 | 对 `dep_event_ids` 先 `LIKE` 预筛选，再按英文逗号拆分精确确认 | 避免 UUID 子串误判 |

### 4.2 事务边界

- 是否需要事务: 当前代码未显式声明事务。
- 事务方法: `addEventInfo`、`updateEventInfo`、`deleteEventInfo` 建议后续按写操作补充事务。
- 回滚条件: 建议对 `RuntimeException` 和 `CommonException` 回滚。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 事件配置分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 事件配置列表查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除前检查 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增事件配置或事件实例 |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改事件配置 |
| `deleteById` / `removeById` | 写入 | MyBatis-Plus `ServiceImpl` | 删除事件配置 |
| `count` | 查询 | MyBatis-Plus `IService` | 删除事件前检查流程和任务引用 |
| `loadByEventId` | 查询 | `EventInstanceMapper.xml` | 按事件 ID、事件时间、事件类型查询事件实例列表 |
| `loadByEventKey` | 查询 | `EventInstanceMapper.xml` | 按事件 ID 和事件时间查询最新一条事件实例 |

自定义 SQL 使用 `#{}` 参数绑定。`EventStorageImpl.loadByEventId` 当前直接使用 MyBatis-Plus wrapper 查询 `scheduler_event_instance`，未调用 `EventInstanceService.loadByEventId` 的自定义 SQL。

## 6. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD、分页、条件查询和引用计数 |
| `HttpUtils` | 静态工具调用 | 获取当前用户名，写入 `creator` 和 `updater` |
| `FlowInfoService` | Service 注入 | 删除事件前检查流程产生事件和流程依赖事件 |
| `TaskInfoService` | Service 注入 | 删除事件前检查任务产生事件和任务依赖事件 |
| `EventStorage` | 接口适配 | `EventStorageImpl` 将 `GlobalEvent` 持久化为 `EventInstanceEntity` |
| `MasterStorage` | Spring Bean 装配 | `SchedulerConfig` 将 `EventStorageImpl` 注入 scheduler master 存储聚合对象 |
| `CachedEventStorage` | scheduler master 内部装饰器 | 对 `loadByEventKey` 做 Caffeine 缓存 |
| `GlobalEventOperator` | scheduler master 事件运行组件 | 任务或流程运行成功时产生全局事件，等待节点监听事件发生 |

## 7. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户/项目/App Header: 当前链路未显式处理。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除依赖外层统一鉴权。

## 8. 不实现的部分

- 不新增事件类型枚举 DTO 或数据库枚举类型。
- 不修改 `eventType` 的字符串编码方式。
- 不修改数据库 DDL，不补事件名称唯一索引和外键约束。
- 不补流程 ID、任务 ID 是否真实存在的校验。
- 不调整 `/detail/{id}`、`/delete/{id}` 的现有路径风格。
- 不修复 `EventStorageImpl` 和 `scheduler_event_instance` 的字段不一致问题，只记录当前风险。

## 9. 风险和建议

- `eventType` 当前只对 `"1"` 和 `"2"` 做关联字段校验，非法值不会被拒绝，应补合法值校验或统一枚举。
- `TASK` 事件允许同时带 `flowId`，`FLOW` 事件允许同时带 `taskId`，当前没有互斥校验，后续应明确是否允许。
- 新增和修改只校验关联 ID 是否传入，没有校验对应流程或任务是否存在。
- 修改时 `flowId`、`taskId` 为 `null` 表示不更新，因此无法通过现有接口清空旧关联。
- `eventName` 没有唯一性校验和数据库唯一索引，业务上若要求事件名唯一，需要补 Service 校验和 DB 约束。
- `pageEventInfo` 未防御 `PageQuery` 为 `null` 的情况。
- `EventStorageImpl.toEventInstanceEntity` 当前未设置 `eventName`，但 `scheduler_event_instance.event_name` DDL 为 `NOT NULL`，保存 `GlobalEvent` 时可能插入失败。
- `EventStorageImpl.loadByEventId` 使用 `.last("LIMIT " + retainNum)` 拼接 SQL 片段，虽然 `retainNum` 是 `int`，仍建议限制下限和上限。
- `EventInstanceMapper.loadByEventId` 自定义 SQL 的第三个参数是 `type`，与 `EventStorage.loadByEventId` 的第三个参数 `retainNum` 语义不同，后续维护时容易误用。
- `EventStorageImpl.loadByEventId` 未按 `effect_end_time` 过滤有效窗口，只按 `effect_time >= retainTime` 和 `LIMIT retainNum` 查询，与 `EventInstanceMapper.loadByEventId` 的时间窗口语义不同。

## 10. 验证

- 单元测试: 建议覆盖新增 `TASK`、新增 `FLOW`、非法 `eventType`、修改后关联校验、删除被流程或任务引用事件、删除被依赖事件引用事件。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`
- Checkstyle: 本次只新增文档，不涉及 Java 代码。
- 手工检查: 使用 `/page`、`/list`、`/add`、`/update`、`/detail/{id}`、`/delete/{id}` 验证返回和异常。
