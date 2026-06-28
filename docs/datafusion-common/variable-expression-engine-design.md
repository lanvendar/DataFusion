# 变量与表达式引擎设计

> 数据结构见 [variable-expression-engine-data-define.md](./variable-expression-engine-data-define.md)。本文只描述当前语法、渲染链路和错误策略。

`datafusion-common` 提供变量 token 扫描、函数参数解析和字符串渲染能力；`datafusion-scheduler-master` 在提交任务前补充调度内置变量。

## 能力边界

当前支持：

- `#(变量名)`：从 `VariableRenderContext.variables` 读取变量值。
- `#day(...)`：按 base、offset、suffix、pattern 计算并格式化时间。
- `#timestamp(base)`：把变量值或字面量解析为毫秒时间戳字符串。
- `#expr(...)`：识别入口存在，但当前直接抛出 `UnsupportedOperationException`。

当前语法只支持 `#(...)` 和 `#name(...)`，不支持 `#{var}`、`#[...]` 或在变量 token 中执行属性访问。

## 渲染链路

```text
DefaultVariableRenderer.render
    -> PlaceholderTokenizer.scan
    -> VariableRenderEngine
    -> BuiltinFunctionEngine
    -> AviatorExpressionEngine
```

调度侧入口：

```text
SchedulerVariableFacade.replacePlaceholders
    -> SchedulerVariableResolver.resolveBuiltinVariables
    -> DefaultVariableRenderer.render
```

`PlaceholderTokenizer` 支持同一字符串中的多个 token、函数括号嵌套、单引号和反斜杠转义。未闭合括号会抛出异常。函数名只能由字母、数字和下划线组成，首字符必须是字母或下划线。

## 函数规则

`FunctionArgumentParser` 用英文逗号拆分参数，单引号字符串内的逗号不拆分。函数实现会去掉单引号参数的首尾引号，并把 `\'` 还原为 `'`。

`#day(...)` 支持 0 到 4 个参数：

```text
#day()
#day(base)
#day(base, pattern)
#day(base, offset)
#day(base, offset, pattern)
#day(base, offset, suffix)
#day(base, offset, suffix, pattern)
```

两参数时第二个参数可识别为 offset 则作为 `offset`，否则作为 `pattern`。三参数时第二个参数固定为 `offset`，第三个参数可识别为 suffix 则作为 `suffix`，否则作为 `pattern`。计算顺序为 `base -> offset -> suffix -> pattern`，默认格式为 `yyyyMMddHHmmss`。

`#timestamp(base)` 至少需要一个参数。`base` 先按变量名读取，未命中时按字面量解析为 `Long`；解析失败返回 `null`。

## 调度内置变量

`SchedulerVariableResolver` 每次渲染前写入当前时间、调度时间、业务时间和事件时间变量。`_schedule_time_` 优先来自 `PlaceholderContext.scheduleTime`，否则读取变量环境中已有的 `_schedule_time_`。`_biz_align_` 和 `_event_align_` 为空时使用 `original`，非法值会抛出异常。

## 错误策略

| 场景 | 策略 |
|------|------|
| `#(unknown_var)` | 保留原 token |
| `#unknown(...)` | 没有支持的 engine 时抛出异常 |
| 未闭合 token | 抛出异常 |
| `#day` 参数超过 4 个 | 抛出异常 |
| `#timestamp` 无参数 | 抛出异常 |
| 非法时间对齐值 | 抛出异常 |
| `#expr(...)` | 抛出 `UnsupportedOperationException` |

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-common,datafusion-scheduler-master -am
```
