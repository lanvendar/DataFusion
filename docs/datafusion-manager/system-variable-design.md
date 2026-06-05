# system-variable 设计文档

> 数据结构定义见 [system-variable-data-define.md](./system-variable-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 系统变量配置的分页查询、列表查询、新增、修改、详情查询和删除。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.system`
- 路径前缀: `/api/system/variable`
- 调用链: `VariableController` -> `VariableInfoService` -> `VariableInfoServiceImpl` -> `VariableInfoMapper` -> `system_variable_info`

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<VariableInfoQueryDto>` | `Result<PageResponse<VariableInfoDto>>` | 分页查询变量 |
| `POST` | `/list` | `VariableInfoQueryDto` | `Result<List<VariableInfoDto>>` | 查询变量列表 |
| `POST` | `/add` | `VariableInfoSaveDto` | `Result<UUID>` | 新增变量，服务端固定创建 `CUSTOM` 类型 |
| `POST` | `/update` | `VariableInfoUpdateDto` | `Result<Boolean>` | 修改变量，`SYSTEM` 类型仅允许修改 `value` |
| `GET` | `/{id}` | path `UUID id` | `Result<VariableInfoDto>` | 根据 ID 查询变量 |
| `DELETE` | `/{id}` | path `UUID id` | `Result<Boolean>` | 删除变量，`SYSTEM` 类型不允许删除 |

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/system-variable-data-define.md` | 系统变量字段、类型、校验和层间映射定义 |
| `docs/datafusion-manager/system-variable-design.md` | 系统变量 API、Service、Mapper、集成和验证设计 |

### 3.2 修改

| 文件 | 说明 |
|------|------|
| `datafusion-manager/src/main/java/com/datafusion/manager/system/controller/VariableController.java` | 从 scheduler 包迁移到 system 包，路径前缀改为 `/api/system/variable` |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/service/VariableInfoService.java` | 从 scheduler 包迁移到 system 包 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/service/impl/VariableInfoServiceImpl.java` | 从 scheduler 包迁移到 system 包 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dao/VariableInfoMapper.java` | 从 scheduler 包迁移到 system 包 |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/po/VariableInfoEntity.java` | 从 scheduler 包迁移到 system 包，表名改为 `system_variable_info` |
| `datafusion-manager/src/main/java/com/datafusion/manager/system/dto/*.java` | 从 scheduler 包迁移到 system 包 |
| `datafusion-manager/src/main/java/com/datafusion/manager/ManagerApplication.java` | 增加 `com.datafusion.manager.system.dao` Mapper 扫描 |
| `datafusion-manager/src/main/resources/mapper/VariableInfoMapper.xml` | Mapper namespace 改为 `com.datafusion.manager.system.dao.VariableInfoMapper` |
| `datafusion-manager/src/main/resources/init_db/init_ddl.sql` | 表结构改为 `system_variable_info` |

### 3.3 复用

| 对象 | 路径 | 说明 |
|------|------|------|
| `VariableController` | `datafusion-manager/src/main/java/com/datafusion/manager/system/controller/VariableController.java` | HTTP 入口 |
| `VariableInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/system/service/VariableInfoService.java` | Service 契约 |
| `VariableInfoServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/system/service/impl/VariableInfoServiceImpl.java` | 业务实现 |
| `VariableInfoMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/system/dao/VariableInfoMapper.java` | MyBatis-Plus Mapper |
| `VariableInfoEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/system/po/VariableInfoEntity.java` | 表实体 |
| `VariableInfoMapper.xml` | `datafusion-manager/src/main/resources/mapper/VariableInfoMapper.xml` | Mapper XML，目前无自定义 SQL |
| `Result<T>` | `datafusion-common-spring` | API 响应包装 |
| `PageQuery<T>` / `PageResponse<T>` | `datafusion-common-spring` | 分页请求和响应 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `pageVariableInfo` | `PageQuery<VariableInfoQueryDto>` | `PageResponse<VariableInfoDto>` | 按条件分页查询，按 `createTime desc` 排序 |
| `listVariableInfo` | `VariableInfoQueryDto` | `List<VariableInfoDto>` | 按条件查询全量列表，按 `createTime desc` 排序 |
| `getVariableInfoById` | `UUID id` | `VariableInfoDto` | 查询变量详情，不存在时抛业务异常 |
| `addVariableInfo` | `VariableInfoSaveDto` | `UUID` | 新增 `CUSTOM` 变量，校验 `code` 唯一 |
| `updateVariableInfo` | `VariableInfoUpdateDto` | `boolean` | 按变量类型合并更新字段 |
| `deleteVariableInfo` | `UUID id` | `boolean` | 删除 `CUSTOM` 变量，禁止删除 `SYSTEM` |

### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `name/code` 模糊匹配，`type/valueType` 精确匹配 | 返回 `PageResponse<VariableInfoDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<VariableInfoDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "变量不存在")` |
| 新增变量 | `code` 全局唯一；`type` 固定为 `CUSTOM`；写入审计字段 | 重复时抛 `变量编码已存在` |
| 修改 `SYSTEM` | 仅允许修改非 `null` 的 `value` | 其他字段忽略 |
| 修改 `CUSTOM` | 非空字符串字段合并；`value` 非 `null` 时合并；修改 `code` 时校验唯一 | 变量不存在或编码重复时抛业务异常 |
| 删除变量 | 仅允许删除 `CUSTOM` | `SYSTEM` 抛 `系统变量不允许删除` |

### 4.2 事务边界

- 是否需要事务: 当前代码未显式声明事务。
- 事务方法: `addVariableInfo`、`updateVariableInfo`、`deleteVariableInfo` 建议后续按写操作补充事务。
- 回滚条件: 建议对 `RuntimeException` 和 `CommonException` 回滚。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 列表查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除前检查 |
| `selectCount` | 查询 | MyBatis-Plus `BaseMapper` | `code` 唯一性校验 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增变量 |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改变量 |
| `deleteById` / `removeById` | 写入 | MyBatis-Plus `ServiceImpl` | 删除变量 |

无自定义 SQL。`VariableInfoMapper.xml` 仅声明 mapper namespace。

## 6. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD 和分页查询 |
| `HttpUtils` | 静态工具调用 | 获取当前用户名，写入 `creator` 和 `updater` |
| Scheduler runtime | 数据配置 | 当前接口只维护系统变量配置，未实现变量解析或任务引用检查 |

## 7. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户/项目/App Header: 当前链路未显式处理。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除依赖外层统一鉴权。

## 8. 不实现的部分

- 不实现变量解析和表达式求值。
- 不实现变量被任务、脚本、数据同步配置引用时的删除保护。
- 不新增 `CUSTOM` / `SYSTEM` / `STRING` / `EXPRESSION` 枚举。
- 不补 `code` 唯一索引。
- 不调整 `code` 修改策略。

## 9. 风险和建议

- `code` 唯一性只靠 Service 层查询校验，并发新增时可能绕过，应补数据库唯一索引。
- `id` 由 `UUID.nameUUIDFromBytes(code.getBytes())` 生成，修改 `code` 后 `id` 不再和新 `code` 可推导。
- `type` 和 `valueType` 是字符串，缺少枚举和白名单校验。
- `pageVariableInfo` 未防御 `PageQuery` 为 `null` 的情况。
- 删除变量前未检查引用关系，可能造成任务配置引用悬空。

## 10. 验证

- 单元测试: 建议覆盖新增成功、重复 `code`、修改 `CUSTOM`、修改 `SYSTEM`、删除 `SYSTEM`、条件查询。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`
- Checkstyle: 迁移 Java 包路径时按项目 Checkstyle 规则检查。
- 手工检查: 使用 `/page`、`/list`、`/add`、`/update`、`/{id}`、`DELETE /{id}` 验证返回和异常。
