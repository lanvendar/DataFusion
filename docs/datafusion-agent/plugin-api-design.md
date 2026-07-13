# API 插件设计

> 数据结构见 [plugin-api-data-define.md](./plugin-api-data-define.md)。Shell LOCAL 设计见
> [plugin-shell-design.md](./plugin-shell-design.md)。API 抽数程序设计见
> [../datafusion-plugin/datafusion-plugin-api/api-extract-design.md](../datafusion-plugin/datafusion-plugin-api/api-extract-design.md)。

## 定位

`API` 是 API 抽数任务执行插件入口，第一版只支持 `LOCAL` 运行模式。agent 侧不重新实现 API 抽数逻辑，
只负责把调度参数转换成本地 `java` 命令，并复用 `SHELL + LOCAL` 的进程启动、日志、状态文件和控制能力。

首版 `taskData` 直接承载 API job JSON；`bizRef` 和 `trigger` 属于调度属性，不进入插件配置。
agent 提交前合并 `pluginParam.defaultTaskData` 与 `taskData`，过滤调度属性后写入任务运行目录的 `api-job.json`，
再按内置模板 `plugins/api/templates/api-local-runtime.yml` 渲染本地命令：

```text
java [jvmOptions...] -jar {apiJar} -job api-job.json
java [jvmOptions...] -classpath {classpath} {mainClass} -job api-job.json
```

API jar 的工作目录是当前任务运行目录，executor 固定追加 `-job api-job.json`，程序启动后读取该 job 文件。

## 执行结构

```text
ApiLocalPluginTaskExecutor(API)
    -> 合并 defaultTaskData 和 taskData
    -> 根据 launchMode 生成 launchArgs 并渲染 api-local-runtime.yml
    -> 归一化为 Shell LOCAL command/args/env
    -> delegate ShellLocalPluginTaskExecutor

ApiLocalRunModeStateMapping(API + LOCAL)
    -> delegate ShellLocalRunModeStateMapping
```

API executor 的 `prepareTask` 只做参数和命令模板校验，仍返回原始 API 请求；`submitTask` 写入
`api-job.json` 后再归一化为 Shell 请求提交。Shell 保存的 `.snap.pluginType` 仍保持 `API`，
状态刷新按 `API + LOCAL` 路由。

## 提交流程

```text
TaskRequest(pluginType=API, runMode=LOCAL, taskData, pluginParam)
    -> WorkerTaskOperatorRouter.route("API")
    -> ApiLocalPluginTaskExecutor.prepareTask
    -> submitTask 时写入 effective job 到 api-job.json
    -> 按 launchMode 渲染 API LOCAL 命令
    -> 归一化为 Shell command/args/env
    -> ShellLocalPluginTaskExecutor.submitTask
    -> 写 WorkerTaskExecutionSnap(pluginType=API, runMode=LOCAL, 归一化后的 taskData/pluginParam)
    -> 写 WorkerTaskExecutionState(status=RUNNING, appId=pid, workDirPath=任务运行目录)
    -> watcher 等待退出码并更新 RUN_SUCCESS / RUN_FAILURE
```

## 部署方式

API 插件 jar 随 agent 插件资源发布到运行侧插件目录：

```text
/opt/datafusion/plugins/api/datafusion-plugin-api-1.0.0-executable.jar
```

通用 builder 位于：

```text
datafusion-plugin/build-plugin.sh
```

builder 负责把 `datafusion-plugin-api` 的 jar、`conf/`、`jobs/`、`plugin-api-commands.md` 同步到
`datafusion-agent/src/main/resources/plugins/api/`。API 是单包插件，builder 只覆盖 `plugins/api` 下自己的
jar、`lib/`、`conf/`、`jobs/` 和命令文档，不清理 agent 侧 `plugins/api/templates/`。

构建元数据放在 `datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json`。公共 builder 启动时读取
`modulePath`、`artifactId`、`runtimeResourceDir`、`agentPublishDir`、`resourceDirs`、`resourceFiles`，并校验
`pluginType=API`；该文件不复制到 `plugins/api/` 运行目录。

支持两种发布模式：

```bash
./datafusion-plugin/build-plugin.sh --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json --mode fat
./datafusion-plugin/build-plugin.sh --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json --mode thin
```

`fat` 模式发布 `datafusion-plugin-api-*-executable.jar`，并保持 `lib/` 为空目录；`thin` 模式发布普通 jar，
并把 runtime 依赖复制到 `lib/`。

worker 是否向 manager 上报 `API` 能力，由 `DATAFUSION_WORKER_PLUGIN_TYPES` 控制。API 专用 worker 部署时配置：

```text
DATAFUSION_WORKER_PLUGIN_TYPES=API
```

如果同一个 worker 需要同时承接 Shell、Spider、API 等本地进程任务，可以配置逗号分隔的插件类型。

## 状态映射

`API + LOCAL` 状态映射委托 `ShellLocalRunModeStateMapping`，映射规则与 `SHELL + LOCAL` 一致：

| 条件 | 状态 |
|------|------|
| `.state.status` 已是终态 | 返回该终态 |
| watcher 已写入 `exitCode=0` | `RUN_SUCCESS` |
| watcher 已写入 `exitCode!=0` | `RUN_FAILURE` |
| 顶层进程 PID 仍存活 | `RUNNING` |
| 顶层进程 PID 不存在且无 `exitCode` | `UNKNOWN` |

## Manager 侧约定

Manager 侧新增或配置系统插件入口：

```text
pluginType=API
runMode=LOCAL
```

插件级参数保存启动模式、jar/classpath、JVM、日志、环境变量和默认 job；任务级 `taskData`
直接传入本次 API 抽数 job JSON。`runMode=LOCAL` 是插件配置顶层字段，不放入 `pluginParam`。

## 非目标

- 不新增 API 专用表结构。
- 不在 agent 中解析 API 抽数业务配置。
- 不支持 K8S、YARN、Standalone 等运行模式。
- 不修改 master 的 worker 查找算法。
