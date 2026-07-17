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

运行模式来自 `TaskRequest.runMode`，执行参数来自 `TaskRequest.pluginParam` 和 `TaskRequest.taskData`。
`application.yml` 不定义 `datafusion.agent.datax.*`。

## 总体链路

```text
Manager scheduler
    -> TaskRequest(pluginType=DATAX, runMode, taskData, pluginParam)
    -> AgentExecutorRpcProvider
    -> WorkerService
    -> WorkerPluginRouter(DATAX + runMode)
    -> LocalDataxPluginTaskExecutor / K8sDataxPluginTaskExecutor
    -> DataxExecutionParam
    -> YAML template
    -> ExecutionSpec
    -> WorkerResult
    -> WorkerTaskStateCoordinator / FileWorkerTaskExecutionStore
    -> AgentTaskStateListenerRegistry
    -> AgentTaskResultReporter
```

`LocalDataxPluginTaskExecutor` 与 `K8sDataxPluginTaskExecutor` 分别注册 `DATAX + LOCAL` 和
`DATAX + K8S`。公共 `DataxPluginTaskExecutor` 只共享参数解析和第三方动作辅助，不再负责状态持久化、
`TaskResult` 组装或 Store 访问。完整提交快照由 `WorkerService` 保存；插件动作统一返回原有 `WorkerResult`，
`DataxTaskResult` 删除。

状态映射按 `pluginType + runMode` 注册：

- `DATAX + LOCAL`
- `DATAX + K8S`

重新提交覆盖新 `.snap` 前，`WorkerService` 把旧 `.snap/.state` 放入
`RunningTaskContext.previousSnapshot/previousState`。K8S 执行器必须先用旧配置和旧 appId 清理旧资源，
不能继续用“新配置 + 旧 appId”拼接 RuntimeRef。

## 参数契约

核心规则：

- `TaskRequest.runMode` 必填，只能为 `LOCAL` 或 `K8S`。
- Agent 不改写 `pluginParam`；提交后的运行模式事实来源是 `.snap.runMode`。
- LOCAL 模式下，`pluginParam.jobFile` 非空时复制该文件为本次任务的 `job.json`。
- LOCAL 模式下，`pluginParam.jobFile` 为空时，使用 `taskData.jobJson` 或 `deepMerge(pluginParam.defaultTaskData, taskData)` 生成 `job.json`。
- `taskData` 只覆盖 `pluginParam.defaultTaskData`，不覆盖 `pluginParam` 外层启动参数。
- K8S 运行参数由 `taskData.kubernetes` 覆盖 `pluginParam.kubernetes`；资源名称前缀
  `pluginParam.kubernetes.namePrefix` 除外，只允许插件配置覆盖代码默认值 `df-datax`。
- K8S 镜像必须由 `pluginParam.kubernetes.image` 或 `taskData.kubernetes.image` 提供。
- 缺省值只来自代码协议，例如 `javaBin=java`、`namespace=default`、`backoffLimit=0`。

示例：

```json
{
  "runMode": "K8S",
  "pluginParam": {
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
    -> 立即注册 watcher(actionRevision + appId)
    -> 返回 WorkerResult(appId=pid, workDirPath=任务运行目录, pluginLogUri=local-datax.log)
    -> WorkerTaskStateCoordinator CAS 写 SUBMIT_SUCCESS
    -> watcher 通过 Coordinator 提交运行终态或控制终态
```

进程 watcher 在进程创建并取得 appId 后立即注册，再返回 `WorkerResult`；不使用 `afterSubmitStateSaved` 一类扩展钩子。

控制规则：

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | `ProcessHandle.destroy()` | 目标语义为保持 `STOPPING`；watcher/映射器确认退出后写 `STOP_SUCCESS` |
| kill | `ProcessHandle.destroyForcibly()` | 目标语义为保持 `KILLING`；watcher/映射器确认退出后写 `KILLED` |
| finish | 清理插件侧资源 | 成功返回 `true`；失败保留监听和 `.snap/.state` |

LOCAL 状态映射优先使用 `.state.status` 终态；`STOPPING/KILLING` 且 pid 不存在时分别映射为
`STOP_SUCCESS/KILLED`；其他状态其次使用 `exitCode`，最后用 pid 存活判断 `RUNNING` 或 `UNKNOWN`。

## K8S 模式

提交流程：

```text
解析参数
    -> 生成 Job / Secret 名称
    -> 用 previousSnapshot/previousState 构造旧 RuntimeRef
    -> 如果旧 appId 存在, 按 BEFORE_SUBMIT 清理旧 Job、匹配 Pod 和 Secret；清理失败抛异常
    -> 在任务运行目录生成 job.json 快照
    -> 读取任务运行目录/job.json 生成 Secret data.job.json
    -> 渲染 plugins/datax/templates/datax-k8s-runtime.yml
    -> Fabric8 load(renderedYaml).createOrReplace()
    -> appId = Kubernetes Job name
    -> 返回 WorkerResult(appId=jobName, workDirPath=任务运行目录, pluginLogUri=插件日志入口)
    -> WorkerTaskStateCoordinator CAS 写 SUBMIT_SUCCESS
```

Job 约定：

- Job / Secret 结构固定在模板中，动态值来自 `pluginParam.kubernetes` 和 `taskData.kubernetes`。
- Job 名称为 `{namePrefix}-{taskInstanceId}`，Secret 名称为
  `{namePrefix}-job-config-{taskInstanceId}`；角色由 `DataxK8sNameGenerator` 决定。
- label 必须包含 `plugin-type`、`run-mode`、`task-instance-id`、`flow-instance-id` 等定位信息。
- DataX job JSON 先落盘到 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/job.json`，
  再读取该快照写入 Secret，不用 ConfigMap。
- `backoffLimit` 默认 0，避免 Kubernetes 自己重试改变调度语义。
- 镜像必须内置 DataX bundle，agent 不上传本地插件目录。
- 镜像内 DataX home 固定为 `/opt/datafusion/plugins/datax`，需包含 `lib/datax-bundle-0.0.1.jar`、`conf/logback.xml`、`plugin`、`job` 和 `logs` 目录。
- K8S Secret 挂载到 `/opt/datafusion/plugins/datax/job`，容器内 DataX job 文件固定为 `/opt/datafusion/plugins/datax/job/job.json`。
- K8S 容器内 DataX 日志文件固定为 `/opt/datafusion/plugins/datax/logs/datax.log`；agent 终态采集的是 Pod 主容器日志，不直接读取容器文件系统。
- 提交成功后只把 Job name 写入 `.state.appId`；runMode 使用 `.snap.runMode`，namespace、secretName、podLabelSelector、containerName 等运行引用
  由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建，stop / kill / finish 和周期状态映射都不能依赖当前控制请求携带完整参数。
- 重新提交前如果 `previousState.appId` 存在，agent 使用 `previousSnapshot + previousState` 按
  `BEFORE_SUBMIT` 幂等清理旧 Job、匹配 Pod 和 Secret，不能用新 Kubernetes 配置拼接旧 appId；
  只有清理完成才创建新 Job，清理异常由 Coordinator 提交 `SUBMIT_FAILURE`。
- 终态上报前只采集日志并写入最终结果；master 确认终态后，`finishTask` 按 `AFTER_FINISH` 清理资源。
- `AFTER_FINISH` 会主动删除 Secret；`deleteJobOnFinish=true` 时删除 Job，否则 Job/Pod 由
  `ttlSecondsAfterFinished` 兜底清理。
- 清理 Pod 时先按任务标签查询，再按资源名称逐个删除，不依赖 Kubernetes `deletecollection` 权限。

状态映射：

`DataxKubernetesClient` 只查询 `DataxKubernetesStatus`，包含 Job 状态和 Pod 存活事实。
`DataxK8sRunModeStateMapping` 结合本地状态转换为 DataFusion 状态。
映射器接收任务监听器同一次读取的 `.snap + .state`，不自行访问状态存储；终态日志结果由映射器准备，
再由监听器统一持久化、写后校验和上报。

| Kubernetes Job 状态 | DataFusion 状态 |
|---------------------|-----------------|
| `Complete=True` | `RUN_SUCCESS` |
| `Failed=True` | `RUN_FAILURE` |
| `active > 0` | `RUNNING` |
| 本地 `STOPPING` 且 Pod 全部退出 | `STOP_SUCCESS` |
| 本地 `KILLING` 且 Job、Pod 均不存在 | `KILLED` |
| Job 不存在、Job status 为空或 Job 未激活 | `UNKNOWN` |

控制规则：

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | 删除 Job 和本次任务 Secret，Job 使用默认 grace period | `STOPPING`，待状态映射转终态 |
| kill | 删除 Job/Secret，使用 `gracePeriodSeconds=0`；无 Job 引用视为无残留 | 有运行引用且删除请求成功返回 `KILLING`，Job、Pod 均不存在后映射为 `KILLED`；无运行引用直接返回 `KILLED`；失败返回 `UNKNOWN` |
| finish | master 确认终态后清理 Secret / Job；状态文件清理由 agent finish 流程处理 | 当前终态 |

## 日志

- `TaskResult.workerResult.workDirPath` 返回任务运行目录，manager 只用该目录识别 `stdout.log`、`stderr.log`、`state.log`。
- `TaskResult.workerResult.pluginLogUri` 返回插件日志入口，可以是 `${taskRuntimeDir}/...` 下的本地日志文件、对象存储 URI 或 `k8s://{namespace}/jobs/{jobName}`。
- agent 自身服务日志不进入 `TaskResult.workerResult`；由 `Worker.workerLogDir` 在注册时上报。
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
