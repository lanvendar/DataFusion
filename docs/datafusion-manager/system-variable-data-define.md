# system-variable 数据结构定义

> 本文档是字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `system_variable_info`
- 操作: 修改表名为 `system_variable_info`
- 主键: `id uuid`
- 说明: 系统变量配置表，保存系统变量和用户自定义变量。

### 1.2 DDL

```sql
CREATE TABLE system_variable_info (
id uuid NOT NULL,
code varchar(255) NOT NULL, -- 变量编码
"name" text NULL, -- 变量名称
"type" varchar(50) NOT NULL, -- 变量类型:CUSTOM(自定义);SYSTEM(系统全局)
value_type varchar(255) NOT NULL, -- 变量值类型
value text NULL, -- 值
remark text NULL, -- 参数备注
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
CONSTRAINT system_variable_info_pkey PRIMARY KEY (id)
);

-- Column comments

COMMENT ON COLUMN system_variable_info.code IS '变量编码';
COMMENT ON COLUMN system_variable_info."name" IS '变量名称';
COMMENT ON COLUMN system_variable_info."type" IS '变量类型:CUSTOM(自定义);SYSTEM(系统全局)';
COMMENT ON COLUMN system_variable_info.value_type IS '变量值类型';
COMMENT ON COLUMN system_variable_info.value IS '值';
COMMENT ON COLUMN system_variable_info.remark IS '参数备注';
COMMENT ON COLUMN system_variable_info.creator IS '创建人';
COMMENT ON COLUMN system_variable_info.updater IS '修改人';
COMMENT ON COLUMN system_variable_info.create_time IS '创建时间';
COMMENT ON COLUMN system_variable_info.update_time IS '修改时间';
```

### 1.3 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明                               |
|-------|-----------|-----------|------|--------|----------------------------------|
| `id` | `id` | `UUID` | 是 | 无 | 主键，继承自 `BaseIdEntity`            |
| `code` | `code` | `String` | 是 | 无 | 变量编码，当前由 Service 层保证唯一           |
| `name` | `name` | `String` | 否 | 无 | 变量名称                             |
| `type` | `type` | `String` | 是 | 新增时固定为 `CUSTOM` | 变量类型，当前取值为 `CUSTOM` / `SYSTEM`   |
| `value_type` | `valueType` | `String` | 是 | 无 | 值类型，当前取值为 `STRING` / `LONG` / `EXPRESSION` |
| `value` | `value` | `String` | 否 | 无 | 变量值                              |
| `remark` | `remark` | `String` | 否 | 无 | 参数备注，可用于说明内置变量含义或枚举可选值 |
| `creator` | `creator` | `String` | 是 | 当前用户 | 创建人，继承自 `BaseEntity`             |
| `updater` | `updater` | `String` | 是 | 当前用户 | 修改人，继承自 `BaseEntity`             |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity`            |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity`            |

## 2. Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `code` | `code` | `String` | `@TableField("code")` | 变量编码 |
| `name` | `name` | `String` | `@TableField("name")` | 变量名称 |
| `type` | `type` | `String` | `@TableField("type")` | 变量类型 |
| `valueType` | `value_type` | `String` | `@TableField("value_type")` | 值类型 |
| `value` | `value` | `String` | `@TableField("value")` | 变量值 |
| `remark` | `remark` | `String` | `@TableField("remark")` | 参数备注 |
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
| `VariableInfoSaveDto` | `Request` | 新增变量 | `remark` | `String` | 无 | 参数备注 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `id` | `UUID` | `@NotNull` | 变量 ID |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `code` | `String` | 非空时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `name` | `String` | 非空时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `valueType` | `String` | 非空时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `value` | `String` | 非 `null` 时合并 | `SYSTEM` 和 `CUSTOM` 均可修改 |
| `VariableInfoUpdateDto` | `Request` | 修改变量 | `remark` | `String` | 非 `null` 时合并 | `SYSTEM` 类型忽略 |
| `VariableInfoDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `VariableInfoDto` | `Response` | 查询响应 | `code` | `String` | 无 | 变量编码 |
| `VariableInfoDto` | `Response` | 查询响应 | `name` | `String` | 无 | 变量名称 |
| `VariableInfoDto` | `Response` | 查询响应 | `type` | `String` | 无 | 变量类型 |
| `VariableInfoDto` | `Response` | 查询响应 | `valueType` | `String` | 无 | 值类型 |
| `VariableInfoDto` | `Response` | 查询响应 | `value` | `String` | 无 | 变量值 |
| `VariableInfoDto` | `Response` | 查询响应 | `remark` | `String` | 无 | 参数备注 |
| `VariableInfoDto` | `Response` | 查询响应 | `creator` | `String` | 无 | 创建人 |
| `VariableInfoDto` | `Response` | 查询响应 | `updater` | `String` | 无 | 修改人 |
| `VariableInfoDto` | `Response` | 查询响应 | `createTime` | `Date` | 无 | 创建时间 |
| `VariableInfoDto` | `Response` | 查询响应 | `updateTime` | `Date` | 无 | 修改时间 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/system/variable/page` | `PageQuery<VariableInfoQueryDto>` | `PageResponse<VariableInfoDto>` | `Result<T>` | 分页查询 |
| `POST /api/system/variable/list` | `VariableInfoQueryDto` | `List<VariableInfoDto>` | `Result<T>` | 列表查询 |
| `POST /api/system/variable/add` | `VariableInfoSaveDto` | `UUID` | `Result<T>` | 新增变量 |
| `POST /api/system/variable/update` | `VariableInfoUpdateDto` | `Boolean` | `Result<T>` | 修改变量 |
| `GET /api/system/variable/{id}` | path `UUID id` | `VariableInfoDto` | `Result<T>` | 查询详情 |
| `DELETE /api/system/variable/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 删除变量 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `VariableInfoSaveDto` -> `VariableInfoEntity` | 复制 `code`、`name`、`valueType`、`value`、`remark` | `id` 使用 `UUID.nameUUIDFromBytes(code.getBytes())`；`type` 固定 `CUSTOM`；审计字段由 Service 设置 |
| `VariableInfoUpdateDto` -> existing `VariableInfoEntity` | 按变量类型合并字段 | `SYSTEM` 仅合并非 `null` 的 `value`；`CUSTOM` 合并非空字符串字段和非 `null` 的 `value`、`remark` |
| `VariableInfoEntity` -> `VariableInfoDto` | 字段逐一复制 | 无 |
| `VariableInfoQueryDto` -> `LambdaQueryWrapper` | `name/code` 使用 `like`，`type/valueType` 使用 `eq` | 默认 `createTime desc` |

## 6. 枚举 / JSON / 特殊字段

| 字段 | 存储类型 | Java 类型 | 转换规则 | 说明 |
|------|----------|-----------|----------|------|
| `type` | `varchar(50)` | `String` | 无转换 | 当前约定取值 `CUSTOM` / `SYSTEM`，未定义枚举 |
| `valueType` | `varchar(255)` | `String` | 无转换 | 当前约定取值 `STRING` / `LONG` / `EXPRESSION`，未定义枚举 |
| `id` | `uuid` | `UUID` | 新增时由 `code` 生成 | 修改 `code` 后 `id` 不重新生成 |

### 6.1 内置时间变量备注约定

内置时间变量在 `system_variable_info` 中作为变量目录和前端提示使用。`code` 是表达式渲染使用的稳定编码，必须严格等于调度内置变量枚举的 `paramKeyCode`；`name` 是前端展示名称，可使用中文；运行期真实值由调度上下文派生。

| code | name | valueType | 默认 value | remark 要求 |
|------|------|-----------|------------|-------------|
| `_now_time_` | 当前时间戳 | `LONG` | 无 | 当前系统时间，格式为毫秒时间戳，例如 `1772012833904`。 |
| `_now_date_` | 当前日期 | `STRING` | 无 | 当前系统日期，格式为 `yyyyMMddHHmmss`，例如 `20260620100353`。 |
| `_schedule_time_` | 调度时间戳 | `LONG` | 无 | 原始调度时间，格式为毫秒时间戳，例如 `1772012833904`。 |
| `_biz_align_` | 业务时间对齐枚举 | `STRING` | `original` | 业务时间对齐方式，格式为小写下划线编码。枚举格式见本节后续说明。 |
| `_biz_time_` | 业务时间戳 | `LONG` | 无 | 业务时间，格式为毫秒时间戳；由 `_schedule_time_` 按 `_biz_align_` 对齐后生成。 |
| `_biz_date_` | 业务日期 | `STRING` | 无 | 业务日期，格式为 `yyyyMMddHHmmss`；由 `_biz_time_` 格式化后生成。 |
| `_event_align_` | 事件时间对齐枚举 | `STRING` | `original` | 事件时间对齐方式，格式为小写下划线编码。枚举格式见本节后续说明。 |
| `_event_time_` | 事件时间戳 | `LONG` | 无 | 事件匹配时间，格式为毫秒时间戳；由 `_schedule_time_` 按 `_event_align_` 对齐后生成。 |
| `_event_date_` | 事件日期 | `STRING` | 无 | 事件日期，格式为 `yyyyMMddHHmmss`；由 `_event_time_` 格式化后生成。 |

`_biz_align_` 和 `_event_align_` 复用同一套 `TimeAlignmentEnum` 枚举。基础枚举值包括：

```text
original,
minute_5, minute_10, minute_15, minute_30,
hour_1,
day_1,
month_1, month_3,
year_1,
month_end, year_end
```

后缀规则：

- `_next`: 取下一周期边界，例如 `day_1_next` 表示下一天零点。
- `_add_8`: 在对齐结果上增加 8 小时时区偏移，例如 `hour_1_add_8`。
- `_next_add_8`: 先取下一周期边界，再增加 8 小时时区偏移，例如 `month_1_next_add_8`。

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
