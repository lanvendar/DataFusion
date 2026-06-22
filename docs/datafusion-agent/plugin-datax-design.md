# DataX 插件设计

> 数据结构见 [plugin-datax-data-define.md](./plugin-datax-data-define.md)。Agent 总体设计见
> [agent-design.md](./agent-design.md)。

## 定位

`DATAX` 插件在 agent 中支持两种运行模式：

- `LOCAL`：本地 Java 进程运行 DataX Engine。
- `K8S`：提交 Kubernetes Secret + Job 运行 DataX。

不新增 HTTP API，复用 agent 任务控制接口：

```text
POST /internal/scheduler/submitTask
POST /internal/scheduler/stopTask
POST /internal/scheduler/killTask
POST /internal/scheduler/finishTask
```

参数只来自 `TaskRequest.pluginParam` 和 `TaskRequest.taskData`。`application.yml` 不定义 `datafusion.agent.datax.*`。

## 总体链路

```text
Manager scheduler
    -> TaskRequest(pluginType=DATAX, taskData, pluginParam)
    -> AgentExecutorRpcProvider
    -> WorkerTaskService
    -> DataxPluginTaskExecutor
    -> DataxTaskRunner(LOCAL or K8S)
    -> YAML template
    -> ExecutionSpec
    -> WorkerTaskExecutionStore
    -> AgentTaskStateReportScheduler
    -> ManagerTaskResultReporter
```

`WorkerTaskOperatorRouter` 是 `pluginType -> PluginTaskExecutor` 的一对一映射，因此 DataX 使用一个 `DataxPluginTaskExecutor` 注册 `pluginType=DATAX`，执行器内部按 `pluginParam.runMode` 分派到不同 runner。

状态映射按 `pluginType + runMode` 注册：

- `DATAX + LOCAL`
- `DATAX + K8S`

## 参数契约

核心规则：

- `pluginParam.runMode` 必填，且只能由 manager 插件配置注入。
- LOCAL 模式下，`pluginParam.jobFile` 非空时复制该文件为本次任务的 `job.json`。
- LOCAL 模式下，`pluginParam.jobFile` 为空时，使用 `taskData.jobJson` 或 `deepMerge(pluginParam.defaultTaskData, taskData)` 生成 `job.json`。
- `taskData` 只覆盖 `pluginParam.defaultTaskData`，不覆盖 `pluginParam` 外层启动参数。
- K8S 参数由 `taskData.kubernetes` 覆盖 `pluginParam.kubernetes`。
- K8S 镜像必须由 `pluginParam.kubernetes.image` 或 `taskData.kubernetes.image` 提供。
- 缺省值只来自代码协议，例如 `javaBin=java`、`namespace=default`、`backoffLimit=0`。

示例：

```json
{
  "pluginParam": {
    "runMode": "K8S",
    "logLevel": "INFO",
    "kubernetes": {
      "namespace": "datafusion",
      "image": "registry.example.com/datafusion/datax-runtime:1.0.0"
    }
  },
  "taskData": {
    "jobName": "ods_customer.json",
    "jobJson": {
      "job": {
        "content": []
      }
    }
  }
}
```

## LOCAL 模式

提交流程：

```text
解析参数
    -> 创建任务运行目录
    -> 如 pluginParam.jobFile 非空，复制为任务运行目录/job.json
    -> 否则把 taskData.jobJson 或合并后的 effectiveTaskData 写入任务运行目录/job.json
    -> 渲染 plugins/datax/templates/datax-local-runtime.yml 得到 LocalShellProcess
    -> ProcessBuilder 启动 DataX Engine
    -> appId = process.pid()
    -> 写 WorkerTaskExecutionSnap(...)
    -> 写 WorkerTaskExecutionState(status=RUNNING, appId=pid, workDirPath=任务运行目录, result.pluginLogUri=local-datax.log)
    -> watcher 等待退出码并更新 RUN_SUCCESS / RUN_FAILURE
```

控制规则：

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | `ProcessHandle.destroy()` | `STOP_SUCCESS`；进程不存在也返回成功，保证幂等 |
| kill | `ProcessHandle.destroyForcibly()` | `KILLED`；进程不存在也返回已强杀 |
| finish | 终态后删除状态文件 | 当前终态 |

LOCAL 状态映射优先使用 `.state.status` 终态，其次使用 `exitCode`，最后用 pid 存活判断 `RUNNING` 或 `UNKNOWN`。

## K8S 模式

提交流程：

```text
解析参数
    -> 生成 Job / Secret 名称
    -> 在任务运行目录生成 job.json 快照
    -> 读取任务运行目录/job.json 生成 Secret data.job.json
    -> 渲染 plugins/datax/templates/datax-k8s-runtime.yml
    -> Fabric8 load(renderedYaml).createOrReplace()
    -> appId = Kubernetes Job name
    -> 写 WorkerTaskExecutionSnap(...)
    -> 写 WorkerTaskExecutionState(status=RUNNING, appId=jobName, workDirPath=任务运行目录, result.pluginLogUri=插件日志入口)
```

Job 约定：

- Job / Secret 结构固定在模板中，动态值来自 `pluginParam.kubernetes` 和 `taskData.kubernetes`。
- label 必须包含 `plugin-type`、`run-mode`、`task-instance-id`、`flow-instance-id` 等定位信息。
- DataX job JSON 先落盘到 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/job.json`，
  再读取该快照写入 Secret，不用 ConfigMap。
- `backoffLimit` 默认 0，避免 Kubernetes 自己重试改变调度语义。
- 镜像必须内置 DataX bundle，agent 不上传本地插件目录。
- 提交成功后只把 Job name 写入 `.state.appId`；namespace、secretName、podLabelSelector、containerName 等运行引用
  由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建，stop / kill / finish 和周期状态映射都不能依赖当前控制请求携带完整参数。

状态映射：

| Kubernetes Job 状态 | DataFusion 状态 |
|---------------------|-----------------|
| `Complete=True` | `RUN_SUCCESS` |
| `Failed=True` | `RUN_FAILURE` |
| `active > 0` | `RUNNING` |
| 本地 `STOPPING` 且 Pod 全部退出 | `STOP_SUCCESS` |
| 本地 `KILLING` 且 Pod 全部退出 | `KILLED` |
| Job 不存在且本地非终态 | `UNKNOWN` |

控制规则：

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | 删除 Job，使用默认 grace period | `STOPPING`，待状态映射转终态 |
| kill | 删除 Job，`gracePeriodSeconds=0` | `KILLING`，待状态映射转终态 |
| finish | 终态后采集日志并清理 Secret / Job | 当前终态 |

## 日志

- `TaskResult.workDirPath` 返回任务运行目录，manager 只用该目录识别 `stdout.log`、`stderr.log`、`state.log`。
- `TaskResult.result.pluginLogUri` 返回插件日志入口，可以是 `${taskRuntimeDir}/...` 下的本地日志文件、对象存储 URI 或 `k8s://{namespace}/jobs/{jobName}`。
- agent 自身服务日志不进入 `TaskResult.result`；由 `Worker.workerLogDir` 在注册和心跳时上报。
- LOCAL 模式只暴露一个主插件日志文件 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/local-datax.log`，DataX logback 和进程 stdout / stderr 都写入该文件。
- 未配置外部日志且 `collectLogsOnFinish=true` 时，agent 在终态 best-effort 拉取 Pod 主容器日志，并写入
  `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/k8s-datax.log`。
- K8S 模式配置 `logStorageUri` 时，`pluginLogUri=logStorageUri`；未配置时，提交后先返回 `k8s://{namespace}/jobs/{jobName}`，终态采集成功后返回本地 `k8s-datax.log`。
- 日志采集失败不改变任务终态。
- 不在日志、状态文件、label 或 annotation 中输出完整 job JSON。

## 安全

- DataX job JSON 可能包含数据库密码。LOCAL 文件应只允许当前用户读写；K8S 使用 Secret。
- K8S ServiceAccount 只授予目标 namespace 内 Job、Pod、Pod log、Secret 的必要权限。
- 用户自定义 label 不允许覆盖 `datafusion.*` / `datafusion.io/*` 保留 label。

## 非目标

- 不在 agent 侧重新生成 DataX reader / writer JSON。
- 不实现 DataX 分布式模式，仍按 standalone 运行。
- 不实现 K8S 镜像构建流水线。
- 不实现跨 agent 迁移接管。
- 不把 DataX 账号密码从 job JSON 拆成独立 Secret key。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```

重点验证：LOCAL 成功/失败/stop/kill/日志路径；K8S Job 提交、状态映射、删除 Job 后状态、agent 重启后基于 `.snap + .state` 接管。
