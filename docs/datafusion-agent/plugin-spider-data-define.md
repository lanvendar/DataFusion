# Spider 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 Spider LOCAL 运行模式的字段、任务参数和状态持久化事实源。
> Shell LOCAL 字段见 [plugin-shell-data-define.md](./plugin-shell-data-define.md)。

## 1. 表结构

无。Spider 插件不新增 agent 侧数据库表。

Manager 侧新增插件配置时使用 `pluginType=SPIDER`、`runMode=LOCAL`，插件参数结构复用 Shell LOCAL。

默认模板位于：

```text
datafusion-agent/src/main/resources/plugins/spider/templates/spider-local-plugin-config.json
```

## 2. Agent 配置

Spider 插件入口随 agent 启动加载，不提供独立启用开关。worker 是否上报 `SPIDER` 能力，由
`AgentProperties.Worker.pluginTypes` 控制。

部署环境变量：

```text
DATAFUSION_WORKER_PLUGIN_TYPES=SPIDER
```

## 3. TaskRequest.pluginParam

复用 Shell LOCAL 插件参数结构：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 否 | `LOCAL` | Spider 第一版固定 `LOCAL` |
| `command` | `String` | 是 | 无 | 本地可执行命令，例如 `sh` |
| `args` | `List<String>` | 否 | 空 | 命令参数，例如 `["-c"]` |
| `env` | `Object<String,String>` | 否 | 空 | 插件级环境变量 |
| `pluginLogUri` | `String` | 否 | 空 | 插件日志入口；为空时不写 |

## 4. TaskRequest.taskData

复用 Shell LOCAL 单任务参数结构：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `command` | `String` | 否 | `pluginParam.command` | 单任务命令；仅在插件级未配置时使用 |
| `args` | `List<String>` | 否 | 空 | 单任务参数，追加在 `pluginParam.args` 后 |
| `env` | `Object<String,String>` | 否 | 空 | 单任务环境变量，同名覆盖插件级变量 |
| `pluginLogUri` | `String` | 否 | 空 | 单任务插件日志入口 |

## 5. Runtime 模型

| 对象 | 场景 | 字段 | 生命周期 | 说明 |
|------|------|------|----------|------|
| `WorkerTaskExecutionSnap` | 提交快照 | `pluginType=SPIDER`, `runMode=LOCAL`, `taskData`, `pluginParam` | 提交后到 finish | 控制请求恢复上下文 |
| `WorkerTaskExecutionState` | 运行态 | `appId`, `workDirPath`, `status`, `exitCode`, `result` | 提交后到 finish | 与 Shell LOCAL 一致，`appId` 为顶层进程 PID |

## 6. WorkerResult

复用 Shell LOCAL `WorkerResult` 结构。`pluginType` 和 `runMode` 不写入 `WorkerResult`，只保存在 `.snap` 和结果摘要 JSON 中。
