# datafusion-plugin-spider 设计

> 本文档只描述 `datafusion-plugin-spider` 外部运行时资源引入和交付边界。Agent 侧执行模型见
> [../../datafusion-agent/plugin-spider-design.md](../../datafusion-agent/plugin-spider-design.md)。

## 1. 定位

`datafusion-plugin-spider` 负责把 `SPIDER` 运行时资源打包到插件资源目录，并提供 spider agent 镜像与部署脚本。
任务执行不在本模块实现，当前执行链路由 `datafusion-agent` 的 `SPIDER + LOCAL` 插件承载。

当前代码库中的职责边界：

| 模块 | 职责 |
|------|------|
| `datafusion-plugin/datafusion-plugin-spider` | 保存 spider 运行包、插件构建 manifest、运行时解压脚本、docker 部署资源 |
| `datafusion-agent` | 注册 `pluginType=SPIDER`，复用 Shell LOCAL 执行器提交和控制本地进程 |
| Manager / Scheduler | 生成 `TaskRequest(pluginType=SPIDER, runMode=LOCAL)` 并接收 agent 状态上报 |

## 2. 资源交付

插件资源源目录为：

```text
datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/spider/
```

当前保存两个运行包：

| 资源 | 目录 | 用途 |
|------|------|------|
| `browser-agent-linux-amd64-runtime.tar.gz` | `browser-agent/` | browser-agent 运行时 |
| `sh-web-spider-linux-amd64-runtime.tar.gz` | `sh-web-spider/` | sh-web-spider 运行时 |

`src/main/resources/docker/sync-spider-runtime.sh` 负责把外部构建产物同步到上述目录。
`src/main/resources/plugins/init-runtime-unpack.sh` 在 spider agent 启动阶段解压 `*.tar.gz`，不解析任务参数，也不参与调度决策。

资源所有权与生命周期：

| 数据 | 所有者 | 创建来源 | 生命周期 | 变更边界 |
|------|--------|----------|----------|----------|
| spider 运行包 | `datafusion-plugin-spider` | 外部 `browser-agent` / `sh-web-spider` 打包产物，经 `sync-spider-runtime.sh` 同步 | 随插件资源发布；agent 启动时解压到本地插件目录 | 只由插件构建流程替换；任务执行期不修改 |
| `plugin-build-manifest.json` | `datafusion-plugin-spider` | 模块资源文件 | 随插件构建读取 | 定义资源同步来源和发布目标，不进入任务请求 |
| `init-runtime-unpack.sh` | `datafusion-plugin-spider` | 模块资源文件 | spider agent 启动阶段执行 | 只解压本地归档，不访问远端，不解析调度参数 |
| docker 部署资源 | `datafusion-plugin-spider` | 模块资源文件 | 部署 spider agent 时使用 | 负责镜像和 Kubernetes 部署形态，不参与任务状态流转 |

## 3. 执行链路

```text
Manager scheduler
    -> TaskRequest(pluginType=SPIDER, runMode=LOCAL, pluginParam, taskData)
    -> AgentExecutorRpcProvider
    -> WorkerTaskService
    -> SpiderLocalPluginTaskExecutor
    -> ShellLocalPluginTaskExecutor
    -> 本地 shell 进程
    -> WorkerTaskExecutionStore
    -> AgentTaskStateReportScheduler
    -> ManagerTaskResultReporter
```

`SpiderLocalPluginTaskExecutor` 只提供独立插件注册名，提交、停止、强杀、完成和销毁均委托
`ShellLocalPluginTaskExecutor`。状态映射由 `SpiderLocalRunModeStateMapping` 委托
`ShellLocalRunModeStateMapping`。

## 4. 配置与参数

Agent 内置模板位于：

```text
datafusion-agent/src/main/resources/plugins/spider/templates/spider-local-plugin-config.json
```

模板固定：

| 字段 | 当前值 |
|------|--------|
| `pluginType` | `SPIDER` |
| `runMode` | `LOCAL` |
| `pluginParam.command` | `sh` |
| `pluginParam.args` | `["-c"]` |
| `pluginParam.env` | `{}` |
| `pluginParam.pluginLogUri` | `""` |

实际命令文本由 Shell LOCAL 规则从 `pluginParam` 与 `taskData.args` 组合。Spider 运行脚本依赖的 `.env`、工作目录、
`MCP_URL`、Kafka 等环境由运行包自身和部署环境提供，调度请求只表达一次任务要执行的命令。

## 5. 运行边界

- 本模块不新增 Java 执行器、Controller、Service、Mapper 或数据库表。
- 本模块不直接写入 Manager 或 Agent 持久化数据。
- 本模块不解析 `taskData`，也不生成 shell 命令。
- 本模块不在任务运行期下载远端运行包。
- `DATAFUSION_WORKER_PLUGIN_TYPES` 需要包含 `SPIDER`，否则 agent 不接收 SPIDER 任务。
- spider agent 镜像和部署脚本只负责运行时资源就绪与 agent 启动。

与任务数据的关系：

| 对象 | 来源 | 使用方 | 生命周期 |
|------|------|--------|----------|
| `TaskRequest.pluginType=SPIDER` | Manager / Scheduler | `datafusion-agent` | 单次任务请求 |
| `TaskRequest.runMode=LOCAL` | Manager 插件配置 | `SpiderLocalPluginTaskExecutor` / Shell LOCAL delegate | 单次任务请求和 `.snap` |
| `TaskRequest.taskData.args` | 任务定义或实例上下文 | Shell LOCAL delegate | 单次任务请求 |
| `.snap` / `.state` | `WorkerTaskExecutionStore` | 状态刷新、停止、强杀、完成 | 任务运行期，终态确认后按 agent 流程清理 |

`datafusion-plugin-spider` 不持有以上任务数据，只提供本地运行时资源。

## 6. 验证

| 验证项 | 期望 |
|--------|------|
| 插件资源目录 | `browser-agent/` 与 `sh-web-spider/` 下存在对应 `.tar.gz` |
| 启动初始化 | `init-runtime-unpack.sh` 能把运行包解压到归档所在目录 |
| Agent 注册 | worker 支持 `SPIDER` 插件类型 |
| 任务提交 | `SPIDER + LOCAL` 任务进入 Shell LOCAL 执行链路 |
| 状态上报 | 状态与 Shell LOCAL 映射一致，返回 `workDirPath` 和插件日志入口 |
