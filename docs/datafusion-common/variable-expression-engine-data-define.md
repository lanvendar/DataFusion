# 变量与表达式引擎数据定义

本文定义 `datafusion-common` 变量渲染引擎和 `datafusion-scheduler-master` 调度内置变量使用的数据结构。行为和渲染链路见 [variable-expression-engine-design.md](./variable-expression-engine-design.md)。

## 数据库数据模型

无。

## Java 数据模型

### Token 与上下文

| 类 | 字段 | 说明 |
|----|------|------|
| `PlaceholderToken` | `type` | token 类型，见 `PlaceholderTokenType` |
| `PlaceholderToken` | `name` | 变量名或函数名 |
| `PlaceholderToken` | `rawText` | 原始 token 文本 |
| `PlaceholderToken` | `argumentsText` | 函数参数原文；变量 token 为空 |
| `PlaceholderToken` | `startIndex` / `endIndex` | token 在原始字符串中的起止下标 |
| `VariableRenderContext` | `variables` | 变量映射，key 为变量编码，value 为 `Variable` |
| `SqlVariableRenderContext` | `templateSqlTime` | SQL 模板默认时间，覆盖 `getDefaultTimeMillis()` |
| `PlaceholderContext` | `scheduleTime` | 调度默认时间，覆盖 `getDefaultTimeMillis()` |
| `Variable` | `name` / `type` / `value` | 共享变量对象，来自 `datafusion-common-data` |

### 端口与注册模型

| 接口 / 类 | 方法 / 字段 | 说明 |
|-----------|-------------|------|
| `VariableRenderer` | `render(value, context)` | 渲染字符串中的变量 token |
| `VariableResolver` | `resolve(context)` | 向上下文写入或补全变量 |
| `PlaceholderEngine` | `name` / `supports` / `render` | 单类 token 渲染引擎 |
| `VariableFunction` | `name` / `call` | 内置函数或扩展函数 |
| `VariableFunctionRegistry` | `register` / `getFunction` | 函数注册表 |
| `DefaultVariableFunctionRegistry` | `functions` | 函数名到 `VariableFunction` 的映射，函数名统一小写 |

### 枚举和值域

| 枚举 / 值域 | 值 | 说明 |
|-------------|----|------|
| `PlaceholderTokenType` | `VARIABLE` / `FUNCTION` | `#(...)` 与 `#name(...)` 两类 token |
| `VarType` | `IN` / `OUT` | 共享变量输入/输出类型，JSON 值为 `in` / `out` |
| 默认函数 | `day` / `timestamp` | `DefaultVariableFunctionRegistry` 默认注册 |
| 扩展函数 | `ServiceLoader<VariableFunction>` | 启动时加载并注册 |

### 调度内置变量

| 枚举 | 变量编码 | 说明 |
|------|----------|------|
| `NOW_TIME` | `_now_time_` | 当前系统毫秒时间戳 |
| `NOW_DATE` | `_now_date_` | 当前系统时间，格式 `yyyyMMddHHmmss` |
| `SCHEDULE_TIME` | `_schedule_time_` | 调度时间，优先使用上下文默认时间 |
| `BIZ_ALIGN` | `_biz_align_` | 业务时间对齐方式，默认 `original` |
| `BIZ_TIME` | `_biz_time_` | 调度时间按 `_biz_align_` 对齐后的毫秒时间戳 |
| `BIZ_DATE` | `_biz_date_` | `_biz_time_` 格式化结果 |
| `EVENT_ALIGN` | `_event_align_` | 事件时间对齐方式，默认 `original` |
| `EVENT_TIME` | `_event_time_` | 调度时间按 `_event_align_` 对齐后的毫秒时间戳 |
| `EVENT_DATE` | `_event_date_` | `_event_time_` 格式化结果 |

## 前端数据模型

无。

## 数据映射

1. `PlaceholderTokenizer.scan` 将原始字符串拆成 `PlaceholderToken` 列表。
2. `DefaultVariableRenderer` 按 token 顺序选择 `PlaceholderEngine` 渲染，并保留普通文本。
3. `VariableRenderEngine` 从 `VariableRenderContext.variables` 读取 `Variable.value`。
4. `BuiltinFunctionEngine` 通过 `FunctionArgumentParser` 拆分参数，并从 `VariableFunctionRegistry` 查找函数。
5. `SchedulerVariableResolver` 在调度渲染前写入调度内置变量。

## 复用结构

- `Variable`、`VarType` 来自 `datafusion-common-data`。
- `TimeAlignmentEnum`、`DateCalUtil`、`DateTimeStamp` 来自 `datafusion-common`，用于时间对齐和格式化。
