# Shell 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 Shell LOCAL 运行模式的字段、任务参数和状态持久化事实源。
> Agent 总体运行时结构见 [agent-data-define.md](./agent-data-define.md)。

## 1. 表结构

无。Shell 插件不新增 agent 侧数据库表。

Manager 侧 `system_plugin_config.plugin_param` 保存插件级默认参数，`scheduler_task_instance.task_data`
保存单次任务参数。

## 2. TaskRequest.pluginParam

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 否 | `LOCAL` | Shell 当前固定 `LOCAL` |
| `command` | `String` | 是 | 无 | 本地可执行命令，例如 `sh` |
| `args` | `List<String>` | 否 | 空 | 命令参数，例如 `["-c"]` |
| `env` | `Object<String,String>` | 否 | 空 | 插件级环境变量 |
| `pluginLogUri` | `String` | 否 | 空 | 插件日志入口；为空时不写，`stdout.log` 作为标准日志由 manager 读取 |

默认模板位于：

```text
datafusion-agent/src/main/resources/plugins/shell/templates/shell-local-plugin-config.json
```

模板结构使用 `pluginParam` 字段，与 `system_plugin_config.plugin_param` 对齐。

## 3. TaskRequest.taskData

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `command` | `String` | 否 | `pluginParam.command` | 单任务命令；仅在插件级未配置时使用 |
| `args` | `List<String>` | 否 | 空 | 单任务参数，追加在 `pluginParam.args` 后 |
| `env` | `Object<String,String>` | 否 | 空 | 单任务环境变量，同名覆盖插件级变量 |
| `pluginLogUri` | `String` | 否 | 空 | 单任务插件日志入口 |

## 4. Runtime 模型

| 对象 | 场景 | 字段 | 生命周期 | 说明 |
|------|------|------|----------|------|
| `LocalShellProcess` | LOCAL 模板渲染产物 | `kind`, `command` | 单次提交 | 模板只描述启动命令 |
| `WorkerTaskExecutionSnap` | 提交快照 | `pluginType=SHELL`, `runMode=LOCAL`, `taskData`, `pluginParam` | 提交后到 finish | 控制请求恢复上下文 |
| `WorkerTaskExecutionState` | 运行态 | `appId`, `workDirPath`, `status`, `exitCode`, `result` | 提交后到 finish | `appId` 为顶层 shell PID |

## 5. TaskResult.result

```json
{
  "message": "LOCAL shell task submitted",
  "pluginType": "SHELL",
  "runMode": "LOCAL",
  "exitCode": 0
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | `String` | 是 | 简短执行说明 |
| `pluginType` | `String` | 是 | 固定 `SHELL` |
| `runMode` | `String` | 是 | 固定 `LOCAL` |
| `pluginLogUri` | `String` | 否 | 用户显式配置的插件日志入口；默认不写 |
| `agentLogPath` | `String` | 否 | Shell 插件不主动设置 |
| `exitCode` | `Integer` | 否 | 顶层 shell 退出码 |
