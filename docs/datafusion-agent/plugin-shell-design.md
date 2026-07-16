# Shell 插件设计

> 数据结构见 [plugin-shell-data-define.md](./plugin-shell-data-define.md)。Agent 总体设计见
> [agent-design.md](./agent-design.md)。

## 定位

`SHELL` 插件当前只支持 `LOCAL` 运行模式，用于在 agent 节点本地启动 shell 进程。它不提供独立 HTTP API，
复用 agent 任务控制接口：

```text
POST /internal/scheduler/submitTask
POST /internal/scheduler/stopTask
POST /internal/scheduler/killTask
POST /internal/scheduler/finishTask
```

## 提交流程

```text
TaskRequest(pluginType=SHELL, runMode=LOCAL, taskData, pluginParam)
    -> WorkerTaskOperatorRouter.route("SHELL", "LOCAL")
    -> ShellLocalPluginTaskExecutor.validateTaskRequest
    -> 渲染 plugins/shell/templates/shell-local-runtime.yml 得到 LocalShellProcess
    -> 创建 ${taskRuntimeDir}/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/
    -> ProcessBuilder 启动本地进程
    -> stdout 写入 stdout.log，stderr 写入 stderr.log
    -> 写 WorkerTaskExecutionSnap(...)
    -> 写 WorkerTaskExecutionState(status=RUNNING, appId=pid, workDirPath=任务运行目录)
    -> watcher 等待退出码并更新 RUN_SUCCESS / RUN_FAILURE
```

模板只描述 `kind + command`。工作目录、环境变量、stdout 和 stderr 都由执行器生成。

## 参数规则

- `pluginParam.command` 是默认命令；缺失时可使用 `taskData.command`。
- `pluginParam.args` 和 `taskData.args` 按顺序追加。
- `pluginParam.env` 和 `taskData.env` 合并，任务级同名变量覆盖插件级变量。
- `pluginParam.pluginLogUri` 或 `taskData.pluginLogUri` 非空时透传为插件日志入口；为空时不写 `pluginLogUri`。
- 不支持外部传入 `workDir`，任务工作目录必须由 agent 统一生成。

## 控制规则

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | `ProcessHandle.destroy()` | `STOP_SUCCESS`；进程不存在也返回成功，保证幂等 |
| kill | `ProcessHandle.destroyForcibly()` | `KILLED`；进程不存在也返回已强杀 |
| finish | 读取当前终态；状态文件清理由 agent 终态上报规则决定 | 当前终态 |

stop / kill / finish 支持最小控制请求。只要请求携带 `taskInstanceId`，agent 会通过 `.snap + .state`
恢复 `pluginType`、`taskData`、`pluginParam`、`appId` 和任务运行目录。

## 状态映射

`SHELL + LOCAL` 状态以顶层 shell 进程为主：

| 条件 | 状态 |
|------|------|
| `.state.status` 已是终态 | 返回该终态 |
| watcher 已写入 `exitCode=0` | `RUN_SUCCESS` |
| watcher 已写入 `exitCode!=0` | `RUN_FAILURE` |
| 顶层 shell PID 仍存活 | `RUNNING` |
| 顶层 shell PID 不存在且无 `exitCode` | `UNKNOWN` |

watcher 在顶层 shell 退出时会 best-effort 检查 `ProcessHandle.descendants()`。如果顶层 shell 已退出但仍能观察到
存活后代进程，状态先保持 `RUNNING`。agent 不持久化后代 PID，重启后只能依赖顶层 PID 和已记录退出码判断状态。

## 日志

- `stdout.log` 和 `stderr.log` 固定写入任务工作目录。
- `TaskResult.workerResult.workDirPath` 返回任务运行目录。
- `TaskResult.workerResult.pluginLogUri` 只表示用户显式配置的 Shell 插件日志入口；默认不把 `stdout.log` 当插件日志返回。
- Shell 插件不写 agent 服务日志入口；agent 服务日志目录由 `Worker.workerLogDir` 在注册时上报。

## 非目标

- 不实现跨 agent 接管本地进程。
- 不支持用户自定义工作目录。
- 不持久化 shell 后代进程 PID。
