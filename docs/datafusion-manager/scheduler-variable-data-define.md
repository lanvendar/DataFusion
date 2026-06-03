# scheduler-variable 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `scheduler_variable_info`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 调度变量配置表，保存系统变量和用户自定义变量。

### 1.2 DDL

```sql
CREATE TABLE scheduler_variable_info (
    id uuid NOT NULL,
    code varchar(255) NOT NULL,
    "name" text NULL,
    "type" varchar(50) NOT NULL,
    value_type varchar(255) NOT NULL,
    value text NULL,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    CONSTRAINT variable_info_pkey PRIMARY KEY (id)
);
```

当前 DDL 未对 `code` 建唯一索引，唯一性由 Service 层校验。

### 1.3 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 无 | 主键，继承自 `BaseIdEntity` |
| `code` | `code` | `String` | 是 | 无 | 变量编码，当前由 Service 层保证唯一 |
| `name` | `name` | `String` | 否 | 无 | 变量名称 |
| `type` | `type` | `String` | 是 | 新增时固定为 `CUSTOM` | 变量类型，当前取值为 `CUSTOM` / `SYSTEM` |
| `value_type` | `valueType` | `String` | 是 | 无 | 值类型，当前取值为 `STRING` / `EXPRESSION` |
| `value` | `value` | `String` | 否 | 无 | 变量值 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |

## 2. Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `code` | `code` | `String` | `@TableField("code")` | 变量编码 |
| `name` | `name` | `String` | `@TableField("name")` | 变量名称 |
| `type` | `type` | `String` | `@TableField("type")` | 变量类型 |
| `valueType` | `value_type` | `String` | `@TableField("value_type")` | 值类型 |
| `value` | `value` | `String` | `@TableField("value")` | 变量值 |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `VariableInfoQueryDto` | `Query` | 分页和列表查询 | `name` | `String` | `like` | 变量名称模糊查询 |
| `VariableInfoQueryDto` | `Query` | 分页和列表查询 | `code` | `String` | `like` | 变量编码模糊查询 |
| `VariableInfoQueryDto` | `Query` | 分页和列表查询 | `type` | `String` | `eq` | 变量类型精确查询 |
| `VariableInfoQueryDto` | `Query` | 分页和列表查询 | `valueType` | `String` | `eq` | 值类型精确查询 |
| `VariableInfoSaveDto` | `Request` | 新增变量 | `code` | `String` | `@NotBlank` | 变量编码，全局唯一 |
| `VariableInfoSaveDto` | `Request` | 新增变量 | `name` | `String` | `@NotBlank` | 变量名称 |
| `VariableInfoSaveDto` | `Request` | 新增变量 | `valueType` | `String` | `@NotBlank` | 值类型 |
| `VariableInfoSaveDto` | `Request` | 新增变量 | `value` | `String` | 无 | 变量值 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `id` | `UUID` | `@NotNull` | 变量 ID |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `code` | `String` | 非空时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `name` | `String` | 非空时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `valueType` | `String` | 非空时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `value` | `String` | 非 `null` 时合并 | `SYSTEM` 和 `CUSTOM` 均可修改 |
| `VariableInfoDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `VariableInfoDto` | `Response` | 查询响应 | `code` | `String` | 无 | 变量编码 |
| `VariableInfoDto` | `Response` | 查询响应 | `name` | `String` | 无 | 变量名称 |
| `VariableInfoDto` | `Response` | 查询响应 | `type` | `String` | 无 | 变量类型 |
| `VariableInfoDto` | `Response` | 查询响应 | `valueType` | `String` | 无 | 值类型 |
| `VariableInfoDto` | `Response` | 查询响应 | `value` | `String` | 无 | 变量值 |
| `VariableInfoDto` | `Response` | 查询响应 | `creator` | `String` | 无 | 创建人 |
| `VariableInfoDto` | `Response` | 查询响应 | `updater` | `String` | 无 | 修改人 |
| `VariableInfoDto` | `Response` | 查询响应 | `createTime` | `Date` | 无 | 创建时间 |
| `VariableInfoDto` | `Response` | 查询响应 | `updateTime` | `Date` | 无 | 修改时间 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/variable/page` | `PageQuery<VariableInfoQueryDto>` | `PageResponse<VariableInfoDto>` | `Result<T>` | 分页查询 |
| `POST /api/scheduler/variable/list` | `VariableInfoQueryDto` | `List<VariableInfoDto>` | `Result<T>` | 列表查询 |
| `POST /api/scheduler/variable/add` | `VariableInfoSaveDto` | `UUID` | `Result<T>` | 新增变量 |
| `POST /api/scheduler/variable/update` | `VariableInfoUpdateDto` | `Boolean` | `Result<T>` | 修改变量 |
| `GET /api/scheduler/variable/{id}` | path `UUID id` | `VariableInfoDto` | `Result<T>` | 查询详情 |
| `DELETE /api/scheduler/variable/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除变量 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `VariableInfoSaveDto` -> `VariableInfoEntity` | 复制 `code`、`name`、`valueType`、`value` | `id` 使用 `UUID.nameUUIDFromBytes(code.getBytes())`；`type` 固定 `CUSTOM`；审计字段由 Service 设置 |
| `VariableInfoUpdateDto` -> existing `VariableInfoEntity` | 按变量类型合并字段 | `SYSTEM` 仅合并非 `null` 的 `value`；`CUSTOM` 合并非空字符串字段和非 `null` 的 `value` |
| `VariableInfoEntity` -> `VariableInfoDto` | 字段逐一复制 | 无 |
| `VariableInfoQueryDto` -> `LambdaQueryWrapper` | `name/code` 使用 `like`，`type/valueType` 使用 `eq` | 默认 `createTime desc` |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `type` | `varchar(50)` | `String` | 无转换 | 当前约定取值 `CUSTOM` / `SYSTEM`，未定义枚举 |
| `valueType` | `varchar(255)` | `String` | 无转换 | 当前约定取值 `STRING` / `EXPRESSION`，未定义枚举 |
| `id` | `uuid` | `UUID` | 新增时由 `code` 生成 | 修改 `code` 后 `id` 不重新生成 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id` |
| `BaseEntity` | `datafusion-common-spring` | Entity 基类 | 提供审计字段 |
| `CommonException` | `datafusion-common` | 业务异常 | 变量不存在、编码重复、系统变量删除 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
