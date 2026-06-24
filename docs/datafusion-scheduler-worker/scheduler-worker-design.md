# 调度 Worker 框架设计

> 数据结构定义见 [scheduler-worker-data-define.md](./scheduler-worker-data-define.md)。本文档只描述边界、流程、类目录和实现规则。

## 1. 定位

`datafusion-scheduler-worker` 是 worker 侧调度框架 jar，提供任务操作、插件路由、运行上下文、执行状态、状态映射和结果上报的通用契约。

worker 不依赖 Spring、HTTP/RPC Provider、Nacos、manager 或 agent 运行时实现。`datafusion-agent` 是 worker 的运行时承载应用，负责装配这些契约并提供具体文件、Redis、HTTP、Kubernetes、Yarn 等实现。

## 2. 目标类目录

```text
com.datafusion.scheduler.worker
    WorkerTaskOperator
    WorkerTaskService

com.datafusion.scheduler.worker.context
    RunningTaskContext
    WorkerTaskContextStorage
    CachedWorkerTaskContextStorage

com.datafusion.scheduler.worker.plugin
    PluginTaskExecutor
    PluginRunModeStateMapping
    WorkerPluginLoader
    WorkerTaskOperatorRouter

com.datafusion.scheduler.worker.state
    WorkerTaskExecutionSnap
    WorkerTaskExecutionState
    WorkerTaskExecutionStore

com.datafusion.scheduler.worker.reporter
    TaskResultReporter
    NoopTaskResultReporter
```

## 3. 核心职责

- `WorkerTaskOperator`：worker 侧任务控制入口，定义 `submitTask`、`stopTask`、`killTask`、`finishTask`。
- `WorkerTaskService`：默认实现，负责参数校验、插件路由、提交语义、上下文幂等和结果上报。
- `PluginTaskExecutor`：插件执行器，负责某一 `pluginType` 的 prepare、submit、stop、kill、finish/destroy。
- `PluginRunModeStateMapping`：插件状态映射器，按 `pluginType + runMode` 把终端状态映射为 `StatusEnum`。
- `RunningTaskContext`：进程内运行上下文，直接组合 `WorkerTaskExecutionSnap` 和
  `WorkerTaskExecutionState`，不重复声明二者已有属性，也不内嵌完整 `TaskRequest` / `TaskResult`。
- `WorkerTaskExecutionSnap`：可持久化的任务提交快照，保存恢复上下文、提交语义和插件参数。
- `WorkerTaskExecutionState`：可持久化的任务运行态 envelope，只保存状态刷新、监听和结果上报字段。
- `WorkerTaskExecutionStore`：任务执行快照和运行态存储 SPI。worker 只定义契约，文件或 Redis 实现由 agent 提供。
- `TaskResultReporter`：任务结果上报端口。HTTP 调用 manager 的实现由 agent 提供。

## 4. 任务流程

```text
TaskRequest
    -> WorkerTaskService
    -> WorkerTaskOperatorRouter.route(pluginType)
    -> PluginTaskExecutor.prepareTask
    -> PluginTaskExecutor.submitTask / stopTask / killTask / finishTask
    -> RunningTaskContext 更新
    -> TaskResultReporter.report
```

状态刷新由运行时应用发起，但使用 worker SPI：

```text
WorkerTaskExecutionStore.listListeningStates
    -> 按 taskInstanceId 读取 WorkerTaskExecutionSnap
    -> 按 snap.pluginType + snap.runMode 找 PluginRunModeStateMapping
    -> mapState(state) 得到 StatusEnum
    -> WorkerTaskExecutionStore.saveState
    -> TaskResultReporter.report
```

## 5. 提交语义

`SubmitModeEnum` 只表达提交方式，不表达任务本体同步运行。

- `SYNC`：worker 等待插件确认任务提交成功或进入运行态，返回 `RUNNING`。
- `ASYNC`：worker 接收请求后返回 `SUBMIT_SUCCESS`，后台执行提交并上报后续状态。

提交响应不应等待任务执行完成，也不应把最终成功/失败作为同步提交响应返回。最终状态必须通过 `TaskResultReporter` 异步上报。

## 6. 状态映射规则

`runMode` 是运行形态大类，不是全局状态查询实现。相同 `LOCAL`、`K8S`、`YARN` 在不同插件下可能有不同状态来源：

- `SHELL + LOCAL`：本地进程、退出码、包装脚本状态。
- `FLINK + K8S`：Flink CRD、Kubernetes Job、Pod 或插件自定义状态。
- `SPARK + YARN`：Yarn application 状态。

因此状态映射必须按 `pluginType + runMode` 路由。后续同一组合存在多种状态源时，可通过 `pluginParam.stateSource` 或 `supports(WorkerTaskExecutionState)` 扩展。

## 7. 幂等与恢复边界

- 幂等键以 `taskInstanceId` 为核心。
- 重复 `submitTask` 不重复启动同一任务实例。
- 重复 `stopTask` / `killTask` 返回当前控制结果或当前终态。
- `WorkerTaskContextStorage` 只保证运行时上下文存储和单进程幂等。
- 跨进程恢复、状态文件、Redis 存储、manager 查询未完成任务等由 agent 实现。

## 8. 不实现

- 不实现 HTTP/RPC Provider 或 Controller。
- 不实现 Nacos、Kubernetes、Yarn、Redis、数据库或 manager client。
- 不实现插件目录扫描，只定义 `WorkerPluginLoader`。
- 不实现具体 Shell、DataX、Flink、Spark 插件逻辑。

## 9. 验证

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-worker -am
```
