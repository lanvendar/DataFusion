# DataX LOCAL / K8S 运行模式设计

> 数据结构见 [datax-run-mode-data-define.md](./datax-run-mode-data-define.md)。Agent 总体设计见 [agent-design.md](./agent-design.md)，模板机制见 [plugin-run-mode-template-design.md](./plugin-run-mode-template-design.md)。

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
    -> WorkerTaskExecutionStateStore
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
- `taskData.jobJson`、`taskData.jobPath`、`taskData.jobFileName` 至少一个必填。
- K8S 模式不允许 `taskData.jobPath`，避免从 agent 本机任意读取文件。
- 通用参数由 `taskData` 覆盖 `pluginParam`。
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
    -> 如存在 jobJson，写入本地 job 文件
    -> 创建日志目录
    -> 渲染 templates/datax/datax-local.yml 得到 LocalProcessSpec
    -> ProcessBuilder 启动 DataX Engine
    -> appId = process.pid()
    -> 写 WorkerTaskExecutionState(status=RUNNING, runMode=LOCAL)
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
    -> 渲染 templates/datax/datax-k8s-job.yml
    -> Fabric8 load(renderedYaml).createOrReplace()
    -> appId = Kubernetes Job name
    -> 写 WorkerTaskExecutionState(status=RUNNING, runMode=K8S, pluginParam._runtime)
```

Job 约定：

- Job / Secret 结构固定在模板中，动态值来自 `pluginParam.kubernetes` 和 `taskData.kubernetes`。
- label 必须包含 `plugin-type`、`run-mode`、`task-instance-id`、`flow-instance-id` 等定位信息。
- DataX job JSON 放 Secret，不用 ConfigMap。
- `backoffLimit` 默认 0，避免 Kubernetes 自己重试改变调度语义。
- 镜像必须内置 DataX bundle，agent 不上传本地插件目录。
- 提交成功后必须把 namespace、jobName、secretName、podLabelSelector、containerName 等运行引用写入 `.state.pluginParam._runtime`。

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

- `TaskResult.logPath` 返回 agent 管理的本地日志目录或采集后的本地日志文件。
- `TaskResult.result.pluginLogUri` 返回三方日志入口，例如对象存储地址或 `k8s://{namespace}/jobs/{jobName}`。
- 未配置外部日志且 `collectLogsOnFinish=true` 时，agent 在终态 best-effort 拉取 Pod 主容器日志。
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

重点验证：LOCAL 成功/失败/stop/kill/日志路径；K8S Job 提交、状态映射、删除 Job 后状态、agent 重启后基于 `.state` 接管。
