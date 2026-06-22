# 变量与表达式引擎设计

> 本文档先收敛 DataFusion 变量、函数、表达式的语法和业务语义，再定义模块分层与迁移路线。实现代码应在本文档确认后再推进，避免在设计未稳定前修改 `datafusion-scheduler-master` 或 `datafusion-common/template` 的使用模块。

## 1. 背景

当前调度参数模块已经具备三类能力：

- 变量渲染：将运行期变量替换到 SQL、JSON 或普通文本中。
- 内置时间函数：基于 `_schedule_time_`、`_biz_align_`、`_event_align_` 派生业务时间、事件时间、日期字符串和时间戳。
- 表达式扩展：后续计划接入 Java Aviator，用于承载更复杂的用户表达式。

旧实现中，变量、函数和表达式分别散落在 `#{var}`、`#[DAY(...)]`、`#[TIMESTAMP(...)]` 等语法中，内置时间逻辑也分布在 `BuiltinParamResolver`、`ExpPlaceholderUtils`、`AbstractDateBuiltinFunc`、`FlowInstance` 和 `TaskInstance`。这会导致：

- 同一类时间语义在不同类里重复计算。
- `biz` 与 `event` 的解耦规则容易被局部 fallback 破坏。
- `parseLong`、空值处理、非法 align 处理到处重复。
- 后续接入 Aviator 时，变量、函数、表达式边界不够清楚。

本设计只先定义目标形态和迁移路线，不要求立即修改使用模块。

## 2. 设计原则

- 保留现有业务能力，不保留旧语法兼容包袱。
- 变量、函数、表达式使用统一的 `#...` 风格，但语义边界必须清楚。
- 运行期时间语义只在一个地方计算，调用方只读取结果。
- `biz` 与 `event` 时间彻底解耦，`_event_time_` 不再依赖 `_biz_align_`。
- 先完成设计文档，再按阶段迁移代码；迁移前不修改 `datafusion-common/template` 或调度参数使用方。

## 3. 改造边界

- 当前系统尚未正式上线，不要求兼容旧调度参数语法。
- 旧语法 `#{var}`、`#[DAY(...)]`、`#[TIMESTAMP(...)]` 不作为新设计的正式语法。
- 本设计不直接替代 JFinal SQL 模板引擎。
- 本设计不要求第一阶段接入 Aviator，只预留边界。
- 本设计不定义前端交互细节。
- SQL 模板中的旧 `#day` 用法后续可在单独阶段迁移，不在设计文档阶段直接改实现。

## 4. 语法总览

新语法只保留两种外形：

```text
#(变量名)
#函数名(参数...)
```

其中：

- `#(变量名)` 表示变量渲染。
- `#day(...)`、`#timestamp(...)` 表示平台内置函数。
- `#expr(...)` 是 Aviator 表达式入口，本质上也是一个保留函数名。

## 5. 变量

变量表示“从变量环境中读取一个已存在的值”，不做计算。

示例：

```text
#(_biz_date_)
#(_event_time_)
#(table_name)
```

规则：

- `#(...)` 仅表示变量输出。
- 括号内第一版只支持普通变量名，不支持 `#(task.name)` 这类属性访问。
- 不支持 `#(_biz_date_, "-2M", "MS", "yyyyMMdd")` 这类隐式日期函数写法。
- 日期计算和格式化必须使用 `#day(...)`。
- 变量不存在时保留原 token 透传，例如 `#(unknown_var)` 输出仍为 `#(unknown_var)`。
- `_变量_` 形式的变量均视为系统内置变量，由平台定义和维护。

## 6. 内置函数

函数表示“调用 DataFusion 平台提供的受控能力”。函数名固定，参数规则固定，由白名单引擎解释。

示例：

```text
#day(_biz_date_)
#day(_event_date_, "-1D", "yyyyMMdd")
#timestamp(_event_time_)
```

规则：

- `#day(...)`、`#timestamp(...)` 属于内置时间函数。
- 第一个参数可以是内置时间变量编码，例如 `_biz_date_`、`_event_date_`、`_schedule_time_`。
- 普通内置函数参数按逗号分隔。
- 普通内置函数参数只支持变量名、字符串字面量和数字字面量。
- 字符串参数只支持双引号。
- 双引号字符串内部只支持双引号转义。
- 逗号出现在字符串内时不拆分参数。
- 普通内置函数参数中不嵌套 Aviator 表达式。
- 未注册函数直接抛错，例如 `#unknown(...)`。
- 函数参数数量错误、参数语义错误、非法 `align` 均按函数使用错误抛错。

## 7. `#day(...)` 日期函数

`#day(...)` 统一承载 SQL 模板和调度参数场景中的日期规则。新规范不兼容旧四参顺序。

支持写法：

```text
#day()
#day(base)
#day(base, pattern)
#day(base, offset)
#day(base, offset, pattern)
#day(base, offset, suffix)
#day(base, offset, suffix, pattern)
```

参数含义：

| 参数 | 说明 |
|------|------|
| `base` | 变量名、时间戳、日期字符串或内置时间变量语义 |
| `offset` | 日期偏移规则，例如 `-2M`、`1D`、`0`，识别规则复用 `DateCalUtil.isOffsetExp(...)` |
| `suffix` | 日期边界规则，例如 `MS`、`MD`，识别规则复用 `DateCalUtil.isSuffixExp(...)` |
| `pattern` | 输出日期格式，例如 `yyyyMMdd` |

简写识别规则：

- 两参数版本中，第二个参数能识别为 `offset` 时解释为 `#day(base, offset)`，否则解释为 `#day(base, pattern)`。
- 三参数版本中，第二个参数固定为 `offset`；第三个参数能识别为 `suffix` 时解释为 `#day(base, offset, suffix)`，否则解释为 `#day(base, offset, pattern)`。
- 四参数版本只支持 `#day(base, offset, suffix, pattern)`。
- 不支持旧 SQL 模板中的 `#day(base, offset, pattern, suffix)` 顺序。
- `suffix` 枚举只以 `DateCalUtil` 中定义的原始枚举为准：`WS`、`WD`、`MS`、`MD`、`SS`、`SD`、`YS`、`YD`。

计算顺序固定为：

```text
base -> offset -> suffix -> pattern
```

即先做日期偏移，再做边界取值，最后格式化输出。该顺序与当前 `DateCalUtil.calDateExp(...)` 的业务行为一致。

不支持先 `suffix` 后 `offset` 的计算顺序。例如“月初后再加 3 天”暂不作为函数能力支持；简单固定日场景可通过 `pattern` 表达，例如 `yyyyMM03`。

覆盖范围：

- `#day(base, "yyyyMMdd")` 覆盖仅格式化。
- `#day(base, "-1D")` 覆盖仅偏移。
- `#day(base, "-1D", "yyyyMMdd")` 覆盖偏移后格式化。
- `#day(base, "-1D", "MS")` 覆盖偏移后取边界，使用默认格式。
- `#day(base, "-1D", "MS", "yyyyMMdd")` 覆盖偏移、取边界、格式化。

## 8. 表达式

表达式表示“交给表达式引擎执行的一段用户逻辑”。后续默认由 Aviator 引擎处理。

示例：

```text
#expr(_biz_time_ > _event_time_ ? _biz_time_ : _event_time_)
#expr(score > 90 ? "high" : "normal")
#expr(string.startsWith(task_name, "ods_"))
```

规则：

- `#expr(...)` 是唯一表达式入口。
- 不支持 `#([表达式])`。
- 不支持 `#(score > 90 ? "high" : "normal")`。
- `#expr(...)` 括号内整体交给 Aviator 表达式引擎。
- 外层 tokenizer 只负责找到完整 `#expr(...)` token 边界。
- `#expr(...)` 内部逗号和括号不由外层解析器拆分。
- `LONG` 类型变量应转为 `Long`。
- `STRING` 类型变量应转为 `String`。
- `EXPRESSION` 类型变量第一版不递归执行，按普通字符串变量处理。

如果需要在表达式中调用日期能力，后续通过 Aviator 自定义函数承载，例如：

```text
#expr(df_day(score > 90 ? _biz_time_ : _event_time_, "yyyyMMdd"))
```

## 9. 内置时间变量

内置时间变量由平台统一派生，调用方不应各自计算。

统一规则：

- `_schedule_time_` 优先使用 `PlaceholderContext.scheduleTime`，为空时从变量环境读取。
- `_biz_align_` 和 `_event_align_` 默认值均为 `original`。
- `_biz_align_` 与 `_event_align_` 独立生效。
- `_event_time_` 不依赖 `_biz_align_`。
- `xxx_time` 变量统一为毫秒时间戳 `Long` 语义。
- `xxx_date` 变量统一为字符串格式，默认 Java 日期格式为 `yyyyMMddHHmmss`。
- `_biz_time_`、`_biz_date_`、`_event_time_`、`_event_date_`、`_now_time_`、`_now_date_` 是运行期派生内置变量，由系统覆盖生成。
- 非法 `align` 按函数使用错误抛错。

业务语义不变清单：

- `_schedule_time_` 可以来自上下文，也可以来自变量环境，上下文优先。
- `_biz_time_` 由 `_schedule_time_ + _biz_align_` 派生。
- `_event_time_` 由 `_schedule_time_ + _event_align_` 派生。
- `_biz_date_` 由 `_biz_time_` 格式化得到。
- `_event_date_` 由 `_event_time_` 格式化得到。
- `_now_time_` 为当前系统毫秒时间戳。
- `_now_date_` 为当前系统时间格式化结果。
- `#day(...)` 和 `#timestamp(...)` 必须覆盖旧 `DAY` / `TIMESTAMP` 的日期偏移、边界取值和格式化能力。

## 10. 错误策略

| 场景 | 策略 |
|------|------|
| `#(unknown_var)` | 保留原 token 透传 |
| `#unknown(...)` | 抛错 |
| 未闭合 token | 抛错 |
| 函数参数数量错误 | 抛错 |
| 函数参数语义错误 | 抛错 |
| 非法 `align` | 抛错 |
| `#expr(...)` 执行失败 | 抛错 |

## 11. 模块归属决策

调度参数包统一改名为调度变量包：

```text
datafusion-scheduler-master/src/main/java/com/datafusion/scheduler/master/variable/
```

原有包迁移关系：

| 旧包 | 新包 |
|------|------|
| `com.datafusion.scheduler.master.param` | `com.datafusion.scheduler.master.variable` |
| `com.datafusion.scheduler.master.param.builtin` | `com.datafusion.scheduler.master.variable` |

`datafusion-common` 只承载与调度领域无关的通用协议和解析能力：

- token 扫描。
- 函数参数解析。
- 变量渲染上下文接口或数据结构。
- 渲染引擎接口。
- 函数接口。
- `#day(...)`、`#timestamp(...)` 的无调度语义函数实现。
- 通用编排接口。

`datafusion-common` 不负责生成 `_schedule_time_`、`_biz_align_`、`_event_align_`、`_biz_time_`、`_event_time_` 等调度运行期变量，也不维护调度内置变量目录。原因是这些变量不是表达式引擎的通用能力，而是 Scheduler 运行时契约。

依赖方向应保持：

```text
datafusion-scheduler-master -> datafusion-common
datafusion-scheduler-master -> datafusion-common-data
datafusion-manager -> datafusion-common-data
```

不能出现：

```text
datafusion-common -> datafusion-scheduler-master
```

通用变量对象直接使用 `datafusion-common-data` 中已有类型：

```text
datafusion-common-data/src/main/java/com/datafusion/scheduler/model/Variable.java
```

`common.variable` 不再另起一套变量值模型，避免 `Variable`、`VariableValue`、`VariableDefinition` 多套对象互转。

建议包结构：

| 包 | 职责 |
|----|------|
| `common.variable` | token 扫描、函数参数解析、渲染上下文、渲染器、引擎接口和通用引擎实现 |
| `common.variable.function` | 定义函数接口、函数注册表接口，以及 `day` / `timestamp` 的通用实现 |

调度模块提供具体实现：

```text
datafusion-scheduler-master/src/main/java/com/datafusion/scheduler/master/variable/
```

调度变量层职责：

- 将调度上下文转换为 `common.variable` 的渲染上下文。
- 注入 `_schedule_time_`、`_biz_align_`、`_event_align_` 等调度内置变量。
- 维护调度领域变量目录枚举。
- 调用 `common.variable` 提供的通用 renderer 和 `day` / `timestamp` 函数。
- 保证 `FlowInstance`、`TaskInstance` 只读取统一求值结果，不再各自实现时间解析或兜底计算。

## 12. 推荐分层

### 12.1 变量目录层

变量目录层只描述“有哪些变量”，不负责运行期计算。

| 对象 | 所属模块 | 职责 |
|------|----------|------|
| `SchedulerBuiltinVariableEnum` | `datafusion-scheduler-master` | 定义调度内置变量 `paramKeyCode` |
| `system_variable_info` 初始化数据 | `datafusion-manager` | 提供系统变量目录、默认值和说明 |
| `system-variable-data-define.md` | `docs/datafusion-manager` | 记录系统变量数据结构和初始化约定 |

约束：

- `system_variable_info.code` 必须严格等于内置变量枚举的 `paramKeyCode`，也是表达式渲染使用的稳定编码。
- `system_variable_info.name` 是前端展示名称，可使用中文，不参与变量渲染。
- `value_type`、默认 `value` 和 `remark` 由 `system-variable-data-define.md` 与 `init_data.sql` 维护，不放入调度枚举。
- 变量目录不表达运行期值，运行期值由变量求值层生成。
- 复制任务参数时，是否过滤解析器生成变量通过 `SchedulerBuiltinVariableEnum.getByParamKeyCode(...)` 和变量类型共同判断，不额外引入 `generated` 字段。

### 12.2 变量求值层

变量求值层负责把上下文解析成完整变量环境。

建议对象：

| 对象 | 所属模块 | 职责 |
|------|----------|------|
| `VariableRenderContext` | `datafusion-common` | 通用渲染上下文，只持有 `Map<String, Variable>` |
| `SqlVariableRenderContext` | `datafusion-common` | SQL 模板变量渲染上下文，持有 `templateSqlTime` |
| `VariableResolver` | `datafusion-common` | 定义变量环境预处理接口，不包含调度实现 |
| `SchedulerBuiltinTimeResolver` | `datafusion-scheduler-master` | 统一解析 `_schedule_time_`、`_biz_align_`、`_event_align_` 和派生时间 |
| `SchedulerVariableResolver` | `datafusion-scheduler-master` | 编排调度内置变量解析并写回变量环境 |
| `PlaceholderContext` | `datafusion-scheduler-master` | 继承 `VariableRenderContext`，承载 `scheduleTime`，不再放 `biz/event` 定制字段 |

约束：

- `datafusion-common` 只定义 `VariableResolver` 接口，不内置调度变量解析实现。
- `SchedulerVariableResolver` 不直接散写 `biz/event` 两套时间计算逻辑，应委托 `SchedulerBuiltinTimeResolver`。
- `FlowInstance` 和 `TaskInstance` 只读取统一求值结果，不在缺少 `_event_time_` 时基于 `_schedule_time_` 和 `_event_align_` 兜底计算。
- `FlowInstance` 和 `TaskInstance` 不持有 `SchedulerBuiltinTimeResolver`。
- 派生变量由解析器生成并覆盖同名输入。
- 变量字段统一使用 `Map<String, Variable>`，其中 `Variable` 来自 `datafusion-common-data`。
- `VariableRenderContext` 基类只保留变量环境，默认时间由子类提供。
- `PlaceholderContext` 通过继承关系直接作为通用渲染上下文使用，并将 `scheduleTime` 暴露为默认时间语义。
- `SqlVariableRenderContext.templateSqlTime` 的类型是毫秒时间戳 `Long`，不是格式化字符串。
- common 日期函数默认格式化输出使用 Java 24 小时制格式 `yyyyMMddHHmmss`；文档或 SQL 语境中的 `yyyyMMddHH24mmss` 语义等价，落到 Java 实现时使用 `yyyyMMddHHmmss`。

### 12.3 Token 扫描层

Token 扫描层负责从普通 SQL、JSON、文本中识别 `#...` 片段。

建议对象：

| 对象 | 职责 |
|------|------|
| `PlaceholderTokenizer` | 扫描 `#(var)` 和 `#name(...)` token |
| `PlaceholderToken` | 保存 token 类型、函数名、原始内容、参数文本和原始区间 |

约束：

- 支持字符串中的 token，例如 JSON 字符串和 SQL 字符串。
- 正确处理括号嵌套和双引号。
- 字符串内逗号不拆分参数。
- `#expr(...)` 参数体不由外层拆分。
- 未闭合 token 直接抛错。

### 12.4 引擎执行层

引擎执行层负责将 token 渲染为最终字符串。

建议接口与实现：

| 对象 | 所属模块 | 职责 |
|------|----------|------|
| `PlaceholderEngine` | `datafusion-common` / `common.variable.engine` | 渲染引擎接口 |
| `VariableRenderEngine` | `datafusion-common` / `common.variable.engine` | 渲染 `#(var)`，按 `Map<String, Variable>` 查值 |
| `BuiltinFunctionEngine` | `datafusion-common` / `common.variable.engine` | 渲染 `#day(...)`、`#timestamp(...)` 等函数 |
| `DefaultVariableRenderer` | `datafusion-common` | 编排 tokenizer 和通用 engine，支持构造期追加领域 engine |
| `AviatorExpressionEngine` | `datafusion-common` / `common.variable.engine` | 渲染 `#expr(...)` |
| `VariableFunction` | `datafusion-common` | 函数接口 |
| `VariableFunctionRegistry` | `datafusion-common` | 函数注册表接口 |
| `DayVariableFunction` | `datafusion-common` | `day` 通用函数实现，只处理入参值、offset、suffix、pattern |
| `TimestampVariableFunction` | `datafusion-common` | `timestamp` 通用函数实现，只处理入参值到毫秒时间戳的转换 |

约束：

- 引擎由门面类在构造期固定。
- 不暴露运行期全局可变 `addHandler`。
- `BuiltinFunctionEngine` 只负责函数 token 分发，具体函数逻辑由 `VariableFunction` 实现。
- `DefaultVariableRenderer` 默认包含 `VariableRenderEngine`、`BuiltinFunctionEngine` 和 `AviatorExpressionEngine`。
- `DayVariableFunction`、`TimestampVariableFunction` 不依赖 `SchedulerBuiltinVariableEnum`。
- `DayVariableFunction`、`TimestampVariableFunction` 可以被 scheduler variable 和 JFinal SQL 模板共同复用。
- `AviatorExpressionEngine` 使用独立变量环境，不直接暴露 `Variable` 对象。
- `datafusion-common` 可以提供默认函数注册表，但只注册无调度语义的 `day`、`timestamp`。
- `_now_time_`、`_now_date_`、`_biz_time_`、`_biz_date_`、`_event_time_`、`_event_date_`、`_schedule_time_`、`_biz_align_`、`_event_align_` 均不作为 SQL 模板通用变量复用。

### 12.5 编排层

编排层对外提供统一渲染入口。

建议流程：

```text
SchedulerVariableFacade.render(text, placeholderContext)
  -> SchedulerVariableResolver.resolve(placeholderContext)
  -> VariableRenderer.render(text, placeholderContext)
  -> PlaceholderTokenizer.scan(text)
  -> for each token:
       find engine
       render token
  -> merge rendered text
```

## 13. 迁移阶段

### 13.1 第零阶段：设计收敛

目标：

- 只重构本文档。
- 不修改 `param` / `variable` 代码。
- 不修改 `datafusion-common/template` 代码。
- 不修改 SQL 模板样例。

验收：

- 语法规则明确。
- 错误策略明确。
- 内置时间变量业务语义明确。
- 模块迁移方向明确。

### 13.2 第一阶段：抽取通用模型与解析器

目标：

- 在 `datafusion-common/src/main/java/com/datafusion/common/variable/` 下建立通用 token、参数解析、上下文和接口。
- 通用上下文变量字段使用 `Map<String, Variable>`，`Variable` 复用 `datafusion-common-data`。
- 只定义接口，不在 `datafusion-common` 实现调度内置变量解析。
- 实现无调度语义的 `day`、`timestamp` 函数，供 scheduler variable 和 SQL 模板复用。
- 不接入 scheduler 使用链路。
- 不改变现有调度业务行为。

建议新增：

| 文件 | 说明 |
|------|------|
| `common/variable/PlaceholderTokenizer.java` | 扫描 token |
| `common/variable/PlaceholderToken.java` | token 数据对象 |
| `common/variable/VariableRenderContext.java` | 通用上下文 |
| `common/variable/SqlVariableRenderContext.java` | SQL 模板变量渲染上下文 |
| `common/variable/VariableResolver.java` | 变量环境预处理接口 |
| `common/variable/DefaultVariableRenderer.java` | 默认渲染器 |
| `common/variable/engine/FunctionArgumentParser.java` | 函数参数解析 |
| `common/variable/engine/PlaceholderEngine.java` | 引擎接口 |
| `common/variable/engine/VariableRenderEngine.java` | 变量渲染引擎 |
| `common/variable/engine/BuiltinFunctionEngine.java` | 函数分发引擎 |
| `common/variable/engine/AviatorExpressionEngine.java` | Aviator 表达式引擎 |
| `common/variable/function/VariableFunction.java` | 函数接口 |
| `common/variable/function/VariableFunctionRegistry.java` | 函数注册表接口 |
| `common/variable/function/DayVariableFunction.java` | `day` 通用函数 |
| `common/variable/function/TimestampVariableFunction.java` | `timestamp` 通用函数 |
| `common/variable/VariableRenderer.java` | 渲染门面接口 |

验收：

- `datafusion-common` 不依赖 `datafusion-scheduler-master`。
- `datafusion-common` 仅依赖 `datafusion-common-data` 中的 `Variable`。
- `datafusion-scheduler-master` 可以依赖 `datafusion-common` 的接口和 tokenizer/parser。
- 覆盖变量 token。
- 覆盖函数 token。
- 覆盖 `#expr(...)` 的边界扫描。
- 覆盖双引号、转义双引号、字符串内逗号。
- 未闭合 token 抛错。

### 13.3 第二阶段：统一内置时间求值

目标：

- 在 `com.datafusion.scheduler.master.variable` 下抽取调度变量实现。
- 消除重复 `parseLong`、空判断和 align 处理。
- 保持调度业务功能不变。

改造内容：

- `com.datafusion.scheduler.master.param` 迁移为 `com.datafusion.scheduler.master.variable`。
- `com.datafusion.scheduler.master.param.builtin` 合并到 `com.datafusion.scheduler.master.variable`。
- `BuiltinVariableEnum` 下沉并改名为 `SchedulerBuiltinVariableEnum`。
- `SchedulerBuiltinVariableEnum` 仅保留 `paramKeyCode`，不维护展示名称。
- `SchedulerVariableResolver` 使用 `SchedulerBuiltinTimeResolver`。
- `FlowInstance` 和 `TaskInstance` 使用统一时间求值结果。
- 非法 align 改为抛错。
- `_event_time_` 只依赖 `_event_align_`。

验收：

- `_schedule_time_` 来自 `context.scheduleTime`。
- `_schedule_time_` 来自变量环境。
- `_biz_align_` 默认 `original`。
- `_event_align_` 默认 `original`。
- 非法 align 抛错。
- `_event_time_` 不依赖 `_biz_align_`。
- `xxx_time` 是毫秒时间戳。
- `xxx_date` 是 `yyyyMMddHHmmss` 字符串。

### 13.4 第三阶段：调度参数切换新语法

目标：

- 建立 `#(var)` 和 `#函数名(...)` 两种外形。
- 移除旧 `#{}` / `#[]` 语法。
- 不缺失旧 `DAY` / `TIMESTAMP` 的业务能力。

改造内容：

- `SchedulerVariableFacade` 改为 token 扫描和 engine 分发。
- 调度侧 `SchedulerVariableFacade` 使用 common 的 `DefaultVariableRenderer`。
- common 的 `DefaultVariableRenderer` 默认注册 `VariableRenderEngine`、`BuiltinFunctionEngine`
  和 `AviatorExpressionEngine`。
- scheduler variable 包直接使用 common 的 `DefaultVariableRenderer`，并复用 common 的 `DayVariableFunction`、`TimestampVariableFunction`。
- 删除旧 `VarPlaceholderHandler`、`ExpPlaceholderHandler`、`PropertyPlaceholderHelper` 和旧 `exp/func` 函数体系。
- 将后续内置函数扩展入口抽象为 `datafusion-common` 下的 `VariableFunction` / `VariableFunctionRegistry` 接口；无领域函数可放 common，调度专属函数放 scheduler variable 包。

验收：

- `#(_biz_date_)` 渲染变量值。
- `#(unknown_var)` 保留原 token。
- `#unknown(...)` 抛错。
- `#day(_biz_date_, "yyyyMMdd")` 渲染日期。
- `#day(_biz_date_, "-2M")` 支持 offset 简写。
- `#day(_biz_date_, "-2M", "MS")` 支持 offset + suffix。
- `#day(_biz_date_, "-2M", "MS", "yyyyMMdd")` 支持完整日期规则。
- `#timestamp(_event_time_)` 渲染时间戳。
- `#(_biz_date_, "-2M", "MS", "yyyyMMdd")` 不被支持。
- `#day(score > 90 ? _biz_time_ : _event_time_, "yyyyMMdd")` 不被支持。

### 13.5 第四阶段：SQL 模板用法统一

目标：

- `datafusion-common/template` 的 `#day` 用法与变量表达式引擎使用同一参数顺序。
- `DateCalDirective` 复用 common 的 `DayVariableFunction` 或同一底层日期函数计算逻辑。
- SQL 模板不再保留旧四参顺序。
- SQL 模板不复用 scheduler 内置变量，不自动注入 `_now_time_`、`_now_date_`、`_biz_time_`、`_biz_date_`、`_event_time_`、`_event_date_`、`_schedule_time_`、`_biz_align_`、`_event_align_`。
- SQL 模板调用 common 日期函数时，无参 `#day()` 的默认时间使用 `SqlVariableRenderContext.templateSqlTime`。
- Scheduler 调用 common 日期函数时，无参 `#day()` 的默认时间使用 `PlaceholderContext.scheduleTime`。

统一规则：

```text
#day(base, offset, suffix, pattern)
```

改造内容：

- `DateCalDirective` 按 `base, offset, suffix, pattern` 读取四参。
- 三参版本按第三个参数是否为 `DateCalUtil.isSuffixExp(...)` 区分 `suffix` 与 `pattern`。
- 两参版本按第二个参数是否为 `DateCalUtil.isOffsetExp(...)` 区分 `offset` 与 `pattern`。
- `DateCalDirective` 负责从 JFinal `Scope` 取参数值，并把值传给 common 日期函数。
- common 日期函数默认输出格式统一为 `yyyyMMddHHmmss`。
- SQL 示例资源统一替换旧顺序。
- jar 内测试 SQL 资源在后续重新生成测试 jar 时应同步采用新顺序。

验收：

- `#day(day, '-2M', 'MD', 'yyyy-MM-dd')` 先减两个月，再取月末，最后格式化。
- `#day(day, 'yyyy-MM-dd')` 作为格式化简写。
- `#day(day, '-1D')` 作为 offset 简写。

### 13.6 第五阶段：接入 Aviator

目标：

- 通过 `#expr(...)` 支持 Aviator 表达式。

改造内容：

- 增加 Aviator 依赖。
- 实现 `AviatorExpressionEngine`。
- 构建类型化变量环境。
- 注册 DataFusion 自定义函数，例如 `df_day`、`df_timestamp`、`df_align`。
- 定义安全模式和函数白名单。

验收：

- `#expr(score > 90 ? "high" : "normal")`。
- `#expr(_biz_time_ > _event_time_ ? _biz_time_ : _event_time_)`。
- `#expr(df_day(_biz_time_, "yyyyMMdd"))`。
- 表达式中字符串包含逗号时不被外层拆分。

## 14. 待确认问题

- `#expr(...)` 中是否允许调用全部 Aviator 内置函数，还是只开放白名单。
- 是否需要为 SQL 场景提供安全模式，禁止变量直接拼接敏感 SQL 片段。
