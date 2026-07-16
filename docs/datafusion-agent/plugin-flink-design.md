# Flink 插件设计

> 数据结构见 [plugin-flink-data-define.md](./plugin-flink-data-define.md)。Agent 总体设计见
> [agent-design.md](./agent-design.md)。

## 1. 定位

`FLINK` 插件在 agent 中统一承接 Flink 作业提交、停止、强杀、状态映射和日志入口上报。
当前执行器注册为 `FLINK + K8S_OPERATOR`。

| 项 | 当前设计 |
|----|----------|
| 模块 | `datafusion-agent` |
| 包 | `com.datafusion.agent.runtime.worker.plugin.flink` |
| 插件类型 | `FLINK` |
| 可执行运行模式 | `K8S_OPERATOR` |
| 调度接口 | 复用 `/internal/scheduler/*` |
| Kubernetes 资源 | `FlinkDeployment`、JobManager / TaskManager Pod、REST Service |

## 2. 总体链路

```text
Manager scheduler
    -> TaskRequest(pluginType=FLINK, runMode, pluginParam, taskData)
    -> AgentExecutorRpcProvider
    -> WorkerTaskService
    -> FlinkPluginTaskExecutor
    -> FlinkParamResolver
    -> flink-job.json
    -> FlinkDeployment
    -> WorkerTaskExecutionStore
    -> AgentTaskStateListenerRegistry
    -> ManagerTaskResultReporter
```

`WorkerTaskOperatorRouter` 按 `FLINK + K8S_OPERATOR` 直接选择 `FlinkPluginTaskExecutor`。

## 3. K8S_OPERATOR 提交流程

```text
解析 pluginParam / taskData
    -> 生成 effectiveTaskData 与 flinkConfig
    -> 写任务运行目录 flink-job.json
    -> 校验 flinkAppDir、flinkAppJar、libDir、mainClass、flinkVersion、Kubernetes 参数
    -> 生成 FlinkDeployment 名称和 REST service 地址
    -> 渲染 plugins/flink/templates/flink-k8s-operator-deployment.yml
    -> Fabric8 createOrReplace
    -> 写入 .snap/.state
```

核心规则：

- `TaskRequest.runMode` 来自 Manager 插件配置，固定为 `K8S_OPERATOR`。
- `pluginParam.flinkVersion` 固定为 `2.2.0`，Operator `spec.flinkVersion` 渲染为 `v2_2`。
- 默认镜像为 `flink:2.2.0-scala_2.12-java17`；模板可配置内部镜像。
- `launchMode` 默认为 `JAR`，主 jar 由 `pluginParam.flinkAppJar` 指定。
- `flinkAppDir` 是 Pod 内共享盘 app 目录，例如 `/opt/datafusion/plugins/flink/datafusion-plugin-flink-table`。
- initContainer 只复制当前 app 目录下的主 jar 和 `lib/*.jar` 到 `/opt/flink/usrlib`。
- `flink-job.json` 只保存在 agent 本地任务运行目录，不创建 Kubernetes ConfigMap 或 Secret。
- 作业参数通过 `--job <base64(job-json)>` 传给业务 main class。
- `appId` 保存 `FlinkDeployment.metadata.name`。
- `.snap.runMode` 是状态刷新和控制恢复时的运行模式事实来源。
- `upgradeMode` 由插件或任务 Kubernetes 参数配置；`jobState` 不接受外部配置，由提交和停止动作决定。

## 4. 参数与合并规则

| 输入 | 用途 | 规则 |
|------|------|------|
| `pluginParam` | 插件级运行配置 | Agent 只读取，不回写 |
| `pluginParam.defaultTaskData` | 任务默认业务配置 | 与 `taskData` 深度合并 |
| `taskData` | 本次任务配置 | 覆盖默认业务配置 |
| `pluginParam.flinkConfig` | 插件级 Flink 配置 | 与 `effectiveTaskData.flinkConfig` 合并 |
| `effectiveTaskData.flinkConfig` | 任务级 Flink 配置 | 覆盖插件级同名项 |
| `taskData.kubernetes` | 单次 Kubernetes 覆盖 | 只覆盖允许的运行时字段，不写入 job JSON |

`FlinkParamResolver` 会把 `sink.options` 中的非密钥 S3 配置映射为 Flink S3A 配置，并按 `taskData.job.id`
派生 checkpoint / savepoint 路径。`state.backend.type` 会在渲染前归一为 `state.backend`。

## 5. 控制语义

| 动作 | 行为 | 返回 |
|------|------|------|
| `submit` | 将 job state 设为 `running`，校验共享盘依赖目录和主 jar，创建或更新 `FlinkDeployment`，写 `.snap/.state` | 成功返回 `SUBMIT_SUCCESS`；失败返回 `SUBMIT_FAILURE` |
| `stop` | 发起温和停止，将 `FlinkDeployment.spec.job.state` 更新为小写 `suspended` | 返回 `STOPPING`，由状态刷新推进终态 |
| `kill` | 强制清理 FlinkDeployment、Pod、Service 等运行资源 | 删除请求成功返回 `KILLING`，由状态刷新确认资源消失后推进 `KILLED` |
| `finish` | master 确认终态后按配置清理 `FlinkDeployment` | 返回清理结果 |

`kill` 和 `finish` 的清理逻辑必须幂等。状态刷新只查询状态和采集终态日志，不触发 stop、kill 或重新提交。
清理 Pod 和 Service 时先按任务标签查询，再按资源名称逐个删除，不依赖 Kubernetes `deletecollection` 权限。
`kill` 发出删除请求后保持 `KILLING`，直到状态刷新确认 FlinkDeployment 和运行 Pod 都已不存在；Service 残留只影响清理结果，不阻塞任务终态。

## 6. 状态映射

`K8sOperatorClient` 只返回 Kubernetes / Operator 状态事实；`K8sOperatorRunModeStateMapping` 结合任务状态映射
为 DataFusion `StatusEnum`。状态查询优先读取单个 FlinkDeployment 的 `spec.job.state`、
`status.jobStatus.state`、`status.jobManagerDeploymentStatus`、`metadata.generation` 和
`status.observedGeneration`。Operator 状态足够时不查询 Pod 或 Service；仅在 `KILLING` 或停止阶段 CR 已不存在时查询运行 Pod 是否残留。

映射器接收任务监听器同一次读取的 `.snap + .state`，不自行读取或写入 `WorkerTaskExecutionStore`。终态日志和
结果只在 `prepareFinalReport` 中写入传入的内存状态，随后由监听器统一持久化、写后校验和上报。

`.state.status` 是 DataFusion 控制意图的事实来源，`STOPPING` 和 `KILLING` 不重复写入 Operator 状态快照。
当 `metadata.generation` 已推进但 `status.observedGeneration` 尚未追平时，Operator 状态仍属于旧版本，保持当前中间态。

| 任务状态 | Operator 状态 | DataFusion 状态 |
|----------|---------------|-----------------|
| 正常执行 | 当前 generation 尚未被 Operator 观察 | 保持当前状态 |
| 正常执行 | `NONE`, `CREATED`, `INITIALIZING`, `RECONCILING` 且 JobManager 非 `ERROR` | 保持当前状态 |
| 提交阶段 | JobManager `ERROR` | `SUBMIT_FAILURE` |
| 运行阶段 | JobManager `ERROR` | `RUN_FAILURE` |
| 正常执行 | `RUNNING`, `RESTARTING`, `FAILING`, `CANCELLING` | `RUNNING` |
| 正常执行 | `FINISHED` | `RUN_SUCCESS` |
| 正常执行 | `FAILED`, `CANCELED`, `SUSPENDED` | `RUN_FAILURE` |
| `STOPPING` | 期望状态不是 `suspended`，或当前 generation 尚未被 Operator 观察 | `STOPPING` |
| `STOPPING` | 当前 generation 已观察，且为 `FINISHED`, `CANCELED`, `SUSPENDED` | `STOP_SUCCESS` |
| `STOPPING` | `FAILED` | `STOP_FAILURE` |
| `STOPPING` | JobManager `ERROR` | `STOP_FAILURE` |
| `STOPPING` | 其他状态 | `STOPPING` |
| `KILLING` | 运行资源不存在 | `KILLED` |
| 非终态 | 状态缺失或无法识别 | `UNKNOWN` |

终态上报前可采集 JobManager / TaskManager Pod 日志，写入本地 `k8s-flink.log` 或返回外部日志 URI。

## 7. 集成边界

- Kubernetes API 通过 Fabric8 client 访问，连接信息复用 `AgentProperties.Kubernetes`。
- Flink Operator CRD 使用 `flink.apache.org/v1beta1` `FlinkDeployment`。
- agent 不新增 HTTP API、数据库表、Mapper 或前端页面。
- agent 不解析 Kafka、Paimon 或业务 SQL 语义，只把 job config 传给 Flink 插件应用。
- 共享盘目录只承载 jar、依赖和静态资源，不保存本次任务的 job JSON 或密钥。
- ServiceAccount 需要目标 namespace 内 Pod、Pod log、Service 和 `flinkdeployments.flink.apache.org` 的必要权限。

## 8. 验证

| 验证项 | 期望 |
|--------|------|
| 参数解析 | `runMode=K8S_OPERATOR`、Flink 版本、main class、主 jar、共享盘目录、Kubernetes 必填项校验正确 |
| job config | `flink-job.json` 内容为合并后的 `effectiveTaskData`，不包含 agent 专用覆盖项 |
| FlinkDeployment | image、entryClass、jarURI、`--job` 参数、initContainer、volume、nodeSelector 渲染正确 |
| 状态映射 | Operator 状态和本地阶段能映射为 DataFusion 状态 |
| 控制动作 | stop、kill、finish 幂等 |
| 重启恢复 | 基于 `.snap + .state` 可恢复查询和控制 |
