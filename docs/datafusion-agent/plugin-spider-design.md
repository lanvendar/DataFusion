# Spider 插件设计

> 数据结构见 [plugin-spider-data-define.md](./plugin-spider-data-define.md)。Shell LOCAL 设计见
> [plugin-shell-design.md](./plugin-shell-design.md)。

## 定位

`SPIDER` 是爬虫任务执行插件入口，第一版只支持 `LOCAL` 运行模式。它用于把爬虫任务和普通 `SHELL`
任务在调度层分成两个 `pluginType`，从而通过 worker 插件能力自然路由到 spider 专用节点。

`SPIDER + LOCAL` 第一版不重新实现进程执行逻辑，而是用组合委托复用 `SHELL + LOCAL`：

```text
SpiderLocalPluginTaskExecutor(SPIDER)
    -> delegate ShellLocalPluginTaskExecutor

SpiderLocalRunModeStateMapping(SPIDER + LOCAL)
    -> delegate ShellLocalRunModeStateMapping
```

## 部署方式

Spider 插件入口随 agent 启动加载。worker 是否向 manager 上报 `SPIDER` 能力，由
`DATAFUSION_WORKER_PLUGIN_TYPES` 控制。

spider 专用 worker 部署时配置：

```text
DATAFUSION_WORKER_PLUGIN_TYPES=SPIDER
```

普通 Shell worker 继续配置：

```text
DATAFUSION_WORKER_PLUGIN_TYPES=SHELL
```

## 提交流程

```text
TaskRequest(pluginType=SPIDER, runMode=LOCAL, taskData, pluginParam)
    -> WorkerTaskOperatorRouter.route("SPIDER")
    -> SpiderLocalPluginTaskExecutor
    -> ShellLocalPluginTaskExecutor.prepareTask / submitTask
    -> 写 WorkerTaskExecutionSnap(pluginType=SPIDER, runMode=LOCAL)
    -> 写 WorkerTaskExecutionState(status=RUNNING, appId=pid, workDirPath=任务运行目录)
    -> watcher 等待退出码并更新 RUN_SUCCESS / RUN_FAILURE
```

Shell LOCAL 执行器必须使用 `TaskRequest.pluginType` 写入 `.snap.pluginType` 和结果摘要，不能硬编码为 `SHELL`。
这样 SPIDER 委托 Shell 执行时，状态刷新仍能按 `SPIDER + LOCAL` 找到 Spider 状态映射。

## 状态映射

`SPIDER + LOCAL` 状态映射委托 `ShellLocalRunModeStateMapping`，映射规则与 `SHELL + LOCAL` 一致：

| 条件 | 状态 |
|------|------|
| `.state.status` 已是终态 | 返回该终态 |
| watcher 已写入 `exitCode=0` | `RUN_SUCCESS` |
| watcher 已写入 `exitCode!=0` | `RUN_FAILURE` |
| 顶层进程 PID 仍存活 | `RUNNING` |
| 顶层进程 PID 不存在且无 `exitCode` | `UNKNOWN` |

## Manager 侧约定

Manager 需要新增或配置一个系统插件入口：

```text
pluginType=SPIDER
runMode=LOCAL
pluginParam 结构复用 Shell LOCAL
```

任务定义选择 `SPIDER` 插件后，master 会按 `pluginType=SPIDER` 查找 worker；只有注册了 `SPIDER` 的 spider
worker 会被调度到。

## 非目标

- 不新增 Spider 专用表结构。
- 不在第一版实现 Spider 专用参数解析。
- 不新增独立 Spider 插件启用开关。
- 不修改 master 的 worker 查找算法。
