# scheduler-trigger 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `scheduler_trigger_info`
- 操作: 不涉及
- 主键: `id uuid`
- 说明: 调度触发器配置表，保存触发器类型、调度策略、cron 表达式或间隔配置。

### 1.2 DDL

```sql
CREATE TABLE scheduler_trigger_info (
    id uuid NOT NULL,
    "name" varchar(255) NOT NULL,
    "policy" varchar(255) NOT NULL,
    "type" varchar(255) NOT NULL,
    cron varchar(255) NULL,
    "interval" int4 NULL,
    creator varchar(100) NOT NULL,
    updater varchar(100) NOT NULL,
    create_time timestamp(6) NOT NULL,
    update_time timestamp(6) NOT NULL,
    CONSTRAINT trigger_info_pkey PRIMARY KEY (id)
);
```

当前 DDL 未对 `name` 建唯一索引，唯一性由 Service 层校验。

### 1.3 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 新增时 `UUID.randomUUID()` | 主键，继承自 `BaseIdEntity` |
| `name` | `name` | `String` | 是 | 无 | 触发器名称，当前由 Service 层保证唯一 |
| `policy` | `policy` | `String` | 是 | 无 | 调度策略，DB 保存 `TriggerPolicyEnum.ordinal()` 字符串 |
| `type` | `type` | `String` | 是 | 无 | 触发器类型，DB 保存 `TriggerTypeEnum.ordinal()` 字符串 |
| `cron` | `cron` | `String` | 否 | 无 | cron 表达式，`type=CRON` 时必填 |
| `interval` | `interval` | `Integer` | 否 | 无 | 周期间隔，单位分钟，`type=INTERVAL` 时必须大于 0 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |

## 2. Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `name` | `name` | `String` | `@TableField("name")` | 触发器名称 |
| `policy` | `policy` | `String` | `@TableField("policy")` | 调度策略 ordinal 字符串 |
| `type` | `type` | `String` | `@TableField("type")` | 触发器类型 ordinal 字符串 |
| `cron` | `cron` | `String` | `@TableField("cron")` | cron 表达式 |
| `interval` | `interval` | `Integer` | `@TableField("interval")` | 周期间隔，单位分钟 |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `TriggerInfoQueryDto` | `Query` | 分页和列表查询 | `name` | `String` | `like` | 触发器名称模糊查询 |
| `TriggerInfoQueryDto` | `Query` | 分页和列表查询 | `type` | `String` | enum name -> ordinal 后 `eq` | 触发器类型 |
| `TriggerInfoQueryDto` | `Query` | 分页和列表查询 | `policy` | `String` | enum name -> ordinal 后 `eq` | 调度策略 |
| `TriggerInfoSaveDto` | `Request` | 新增触发器 | `name` | `String` | `@NotBlank` | 触发器名称 |
| `TriggerInfoSaveDto` | `Request` | 新增触发器 | `type` | `String` | `@NotBlank` + enum name 解析 | `CRON` / `INTERVAL` |
| `TriggerInfoSaveDto` | `Request` | 新增触发器 | `policy` | `String` | `@NotBlank` + enum name 解析 | 调度策略 |
| `TriggerInfoSaveDto` | `Request` | 新增触发器 | `cron` | `String` | `type=CRON` 时必填 | cron 表达式 |
| `TriggerInfoSaveDto` | `Request` | 新增触发器 | `interval` | `Integer` | `type=INTERVAL` 时必须大于 0 | 周期间隔，单位分钟 |
| `TriggerCronPreviewDto` | `Request` | cron 运行查看 | `cron` | `String` | 必填 | cron 表达式 |
| `TriggerCronPreviewDto` | `Request` | cron 运行查看 | `count` | `Integer` | 可选，默认 5，最大 20 | 预览数量 |
| `TriggerCronPreviewResultDto` | `Response` | cron 运行查看 | `cron` | `String` | 无 | cron 表达式 |
| `TriggerCronPreviewResultDto` | `Response` | cron 运行查看 | `timeZone` | `String` | 无 | 服务端时区 |
| `TriggerCronPreviewResultDto` | `Response` | cron 运行查看 | `nextTimes` | `List<Long>` | 无 | 后续运行时间戳 |
| `TriggerInfoUpdateDto` | `Request` | 修改触发器 | `id` | `UUID` | `@NotNull` | 触发器 ID |
| `TriggerInfoUpdateDto` | `Request` | 修改触发器 | `name` | `String` | 非空时合并 | 触发器名称 |
| `TriggerInfoUpdateDto` | `Request` | 修改触发器 | `type` | `String` | 非空时合并并解析 enum name | 触发器类型 |
| `TriggerInfoUpdateDto` | `Request` | 修改触发器 | `policy` | `String` | 非空时合并并解析 enum name | 调度策略 |
| `TriggerInfoUpdateDto` | `Request` | 修改触发器 | `cron` | `String` | 非 `null` 时合并 | cron 表达式 |
| `TriggerInfoUpdateDto` | `Request` | 修改触发器 | `interval` | `Integer` | 非 `null` 时合并 | 周期间隔，单位分钟 |
| `TriggerInfoDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `TriggerInfoDto` | `Response` | 查询响应 | `name` | `String` | 无 | 触发器名称 |
| `TriggerInfoDto` | `Response` | 查询响应 | `type` | `String` | ordinal -> enum name | 触发器类型 |
| `TriggerInfoDto` | `Response` | 查询响应 | `policy` | `String` | ordinal -> enum name | 调度策略 |
| `TriggerInfoDto` | `Response` | 查询响应 | `cron` | `String` | 无 | cron 表达式 |
| `TriggerInfoDto` | `Response` | 查询响应 | `interval` | `Integer` | 无 | 周期间隔，单位分钟 |
| `TriggerInfoDto` | `Response` | 查询响应 | `creator` | `String` | 无 | 创建人 |
| `TriggerInfoDto` | `Response` | 查询响应 | `updater` | `String` | 无 | 修改人 |
| `TriggerInfoDto` | `Response` | 查询响应 | `createTime` | `Date` | 无 | 创建时间 |
| `TriggerInfoDto` | `Response` | 查询响应 | `updateTime` | `Date` | 无 | 修改时间 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/trigger/page` | `PageQuery<TriggerInfoQueryDto>` | `PageResponse<TriggerInfoDto>` | `Result<T>` | 分页查询 |
| `POST /api/scheduler/trigger/list` | `TriggerInfoQueryDto` | `List<TriggerInfoDto>` | `Result<T>` | 列表查询 |
| `POST /api/scheduler/trigger/add` | `TriggerInfoSaveDto` | `UUID` | `Result<T>` | 新增触发器 |
| `POST /api/scheduler/trigger/update` | `TriggerInfoUpdateDto` | `Boolean` | `Result<T>` | 修改触发器 |
| `POST /api/scheduler/trigger/cron/preview` | `TriggerCronPreviewDto` | `TriggerCronPreviewResultDto` | `Result<T>` | Java cron 后续运行时间预览 |
| `GET /api/scheduler/trigger/{id}` | path `UUID id` | `TriggerInfoDto` | `Result<T>` | 查询详情 |
| `DELETE /api/scheduler/trigger/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除触发器 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TriggerInfoSaveDto` -> `TriggerInfoEntity` | 复制 `name`、`cron`、`interval` | `id` 使用 `UUID.randomUUID()`；`type/policy` 由 enum name 转 ordinal 字符串；审计字段由 Service 设置 |
| `TriggerInfoUpdateDto` -> existing `TriggerInfoEntity` | 非空字符串字段和非 `null` 数值字段合并 | 合并后重新校验 `type` 与 `cron/interval` 一致性 |
| `TriggerInfoEntity` -> `TriggerInfoDto` | 字段逐一复制 | `type/policy` 由 ordinal 字符串转 enum name |
| `TriggerCronPreviewDto` -> `TriggerCronPreviewResultDto` | 使用 `com.datafusion.common.cron.CronUtil` 计算后续运行时间 | 前端只在 `type=CRON` 时显示“运行查看”；返回时间戳由前端格式化展示 |
| `TriggerInfoQueryDto` -> `LambdaQueryWrapper` | `name` 使用 `like`；`type/policy` 转 ordinal 后 `eq` | 默认 `createTime desc` |
| `TriggerInfoEntity` -> scheduler `TriggerInfo` | `TriggerStorageImpl` 组合 `FlowInfoEntity` 和 `TriggerInfoEntity` | `INTERVAL` 从分钟转换为毫秒字符串 |
| scheduler `TriggerInfo` -> `TriggerInfoEntity` | `TriggerStorageImpl` 从调度模型转换实体 | `INTERVAL` 从毫秒字符串转换为分钟 |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `type` | `varchar(255)` | `String` | API 使用 enum name，DB 保存 ordinal 字符串 | `CRON=0`，`INTERVAL=1` |
| `policy` | `varchar(255)` | `String` | API 使用 enum name，DB 保存 ordinal 字符串 | `EXECUTE_ONCE=0`，`SERIAL_WAIT=1`，`PARALLEL=2`，`DISCARD_NEW=3`，`DISCARD_OLD=4` |
| `cron` | `varchar(255)` | `String` | 无转换 | `type=CRON` 时必填 |
| `interval` | `int4` | `Integer` | Manager API 使用分钟；调度框架表达式使用毫秒字符串 | `type=INTERVAL` 时必须大于 0 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id` |
| `BaseEntity` | `datafusion-common-spring` | Entity 基类 | 提供审计字段 |
| `CommonException` | `datafusion-common` | 业务异常 | 触发器不存在、名称重复、类型参数不完整 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
| `TriggerTypeEnum` | `datafusion-scheduler-master` | 类型转换 | API 名称和 DB ordinal 互转 |
| `TriggerPolicyEnum` | `datafusion-scheduler-master` | 策略转换 | API 名称和 DB ordinal 互转 |
