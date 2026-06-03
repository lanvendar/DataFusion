# scheduler-trigger 设计文档

> 数据结构定义见 [scheduler-trigger-data-define.md](./scheduler-trigger-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 调度触发器的分页查询、列表查询、新增、修改、详情查询和删除。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.scheduler`
- 路径前缀: `/api/scheduler/trigger`
- 调用链: `TriggerController` -> `TriggerInfoService` -> `TriggerInfoServiceImpl` -> `TriggerInfoMapper` -> `scheduler_trigger_info`

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<TriggerInfoQueryDto>` | `Result<PageResponse<TriggerInfoDto>>` | 分页查询触发器 |
| `POST` | `/list` | `TriggerInfoQueryDto` | `Result<List<TriggerInfoDto>>` | 查询触发器列表 |
| `POST` | `/add` | `TriggerInfoSaveDto` | `Result<UUID>` | 新增触发器 |
| `POST` | `/update` | `TriggerInfoUpdateDto` | `Result<Boolean>` | 修改触发器 |
| `GET` | `/{id}` | path `UUID id` | `Result<TriggerInfoDto>` | 根据 ID 查询触发器 |
| `DELETE` | `/{id}` | path `UUID id` | `Result<Boolean>` | 删除触发器 |

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/scheduler-trigger-data-define.md` | 调度触发器字段、类型、校验和层间映射定义 |
| `docs/datafusion-manager/scheduler-trigger-design.md` | 调度触发器 API、Service、Mapper、集成和验证设计 |

### 3.2 修改

无。

### 3.3 复用

| 对象 | 路径 | 说明 |
|------|------|------|
| `TriggerController` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/TriggerController.java` | HTTP 入口 |
| `TriggerInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/TriggerInfoService.java` | Service 契约 |
| `TriggerInfoServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/TriggerInfoServiceImpl.java` | 业务实现 |
| `TriggerInfoMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/TriggerInfoMapper.java` | MyBatis-Plus Mapper |
| `TriggerInfoEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/TriggerInfoEntity.java` | 表实体 |
| `TriggerInfoMapper.xml` | `datafusion-manager/src/main/resources/mapper/TriggerInfoMapper.xml` | 自定义 `getByTriggerId` 查询 |
| `TriggerStorageImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/TriggerStorageImpl.java` | master `TriggerStorage` 适配 |
| `Result<T>` | `datafusion-common-spring` | API 响应包装 |
| `PageQuery<T>` / `PageResponse<T>` | `datafusion-common-spring` | 分页请求和响应 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `getByTriggerId` | `UUID triggerId` | `TriggerInfoEntity` | 根据触发器 ID 查询实体，供 `TriggerStorageImpl` 使用 |
| `pageTriggerInfo` | `PageQuery<TriggerInfoQueryDto>` | `PageResponse<TriggerInfoDto>` | 按条件分页查询，按 `createTime desc` 排序 |
| `listTriggerInfo` | `TriggerInfoQueryDto` | `List<TriggerInfoDto>` | 按条件查询全量列表，按 `createTime desc` 排序 |
| `getTriggerInfoById` | `UUID id` | `TriggerInfoDto` | 查询触发器详情，不存在时抛业务异常 |
| `addTriggerInfo` | `TriggerInfoSaveDto` | `UUID` | 新增触发器，校验名称唯一和类型参数一致性 |
| `updateTriggerInfo` | `TriggerInfoUpdateDto` | `boolean` | 按非空字段合并更新，合并后重新校验一致性 |
| `deleteTriggerInfo` | `UUID id` | `boolean` | 删除触发器，删除前检查流程引用 |

### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `name` 模糊匹配，`type/policy` 转 ordinal 后精确匹配 | 返回 `PageResponse<TriggerInfoDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<TriggerInfoDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "触发器不存在")` |
| 新增触发器 | `name` 唯一；`type/policy` 必须能解析为枚举名 | 名称重复或枚举名非法时失败 |
| 新增 `CRON` | `cron` 必填 | 缺失时抛 `CRON类型触发器必须填写cron表达式` |
| 新增 `INTERVAL` | `interval` 必须大于 0 | 缺失或小于等于 0 时抛 `INTERVAL类型触发器必须填写大于0的周期间隔` |
| 修改触发器 | 先查询旧实体，再合并非空字段，最后校验合并后的 `type/cron/interval` | 不存在、名称重复或类型参数不完整时抛业务异常 |
| 删除触发器 | 删除前检查流程引用 | 已关联流程时抛 `该触发器已关联流程, 无法删除` |

### 4.2 事务边界

- 是否需要事务: 当前代码未显式声明事务。
- 事务方法: `addTriggerInfo`、`updateTriggerInfo`、`deleteTriggerInfo` 建议后续按写操作补充事务。
- 回滚条件: 建议对 `RuntimeException` 和 `CommonException` 回滚。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 列表查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除前检查 |
| `selectCount` | 查询 | MyBatis-Plus `BaseMapper` | `name` 唯一性校验 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增触发器 |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改触发器 |
| `deleteById` / `removeById` | 写入 | MyBatis-Plus `ServiceImpl` | 删除触发器 |
| `getByTriggerId` | 查询 | `TriggerInfoMapper.xml` | `select * from scheduler_trigger_info where id = #{triggerId}` |

自定义 SQL 使用 `#{triggerId}` 参数绑定。

## 6. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD、分页和条件查询 |
| `HttpUtils` | 静态工具调用 | 获取当前用户名，写入 `creator` 和 `updater` |
| `FlowInfoService` | Service 注入 | 删除触发器前做流程引用检查 |
| `TriggerTypeEnum` | 枚举转换 | API 传 enum name，DB 保存 ordinal 字符串 |
| `TriggerPolicyEnum` | 枚举转换 | API 传 enum name，DB 保存 ordinal 字符串 |
| `TriggerStorageImpl` | scheduler master 适配 | 将 manager 表数据转换为 scheduler master 的 `TriggerInfo` 和 `TriggerInstance` |

## 7. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户/项目/App Header: 当前链路未显式处理。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除依赖外层统一鉴权。

## 8. 不实现的部分

- 不新增触发器枚举 DTO 或数据库枚举类型。
- 不修改 `type/policy` 的 ordinal 存储方式。
- 不修改数据库 DDL，不补 `name` 唯一索引。
- 不实现 cron 表达式合法性解析，只检查 `CRON` 类型时非空。
- 不修复删除引用检查逻辑，只记录当前风险。

## 9. 风险和建议

- `name` 唯一性只靠 Service 层查询校验，并发新增时可能绕过，应补数据库唯一索引。
- `type/policy` 以 enum ordinal 字符串入库，枚举顺序变化会导致历史数据语义变化。
- `TriggerTypeEnum.valueOf(typeName)` 和 `TriggerPolicyEnum.valueOf(policyName)` 对非法字符串会抛 `IllegalArgumentException`，当前未转换为业务错误信息。
- `CRON` 只校验非空，没有校验 cron 表达式语法。
- `INTERVAL` 在 manager API 中单位是分钟，`TriggerStorageImpl` 转给 scheduler master 时变成毫秒字符串，需要保持边界语义一致。
- `deleteTriggerInfo` 当前使用 `flowInfoService.getFlowInfo(id)` 做引用检查，但 `scheduler_flow_info` 表中引用字段是 `trigger_id`，该逻辑可能无法发现已被流程引用的触发器。
- `pageTriggerInfo` 未防御 `PageQuery` 为 `null` 的情况。

## 10. 验证

- 单元测试: 建议覆盖新增 `CRON`、新增 `INTERVAL`、重复 `name`、非法 `type/policy`、修改后校验、删除被流程引用触发器。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`
- Checkstyle: 本次只新增文档，不涉及 Java 代码。
- 手工检查: 使用 `/page`、`/list`、`/add`、`/update`、`/{id}`、`DELETE /{id}` 验证返回和异常。
