# Spark 插件设计

> 数据结构见 [plugin-spark-data-define.md](./plugin-spark-data-define.md)。Agent 总体设计见
> [agent-design.md](./agent-design.md)。Spark SQL 作业 jar 见
> [../datafusion-plugin/datafusion-plugin-spark-sql/spark-sql-design.md](../datafusion-plugin/datafusion-plugin-spark-sql/spark-sql-design.md)。

## 1. 定位

`SPARK` 插件在 agent 中提交、控制和刷新 Spark SQL 作业。当前可执行运行模式为 `K8S_OPERATOR`，通过
Spark Operator 创建 `SparkApplication`，并在 driver / executor Pod 中加载共享插件目录里的
`plugin-spark-sql.jar`。

| 项 | 当前设计 |
|----|----------|
| 模块 | `datafusion-agent` |
| 包 | `com.datafusion.agent.runtime.worker.plugin.spark` |
| 插件类型 | `SPARK` |
| 运行模式 | `K8S_OPERATOR` |
| 调度接口 | 复用 `/internal/scheduler/*` |
| Kubernetes 资源 | `SparkApplication`、job ConfigMap、driver / executor Pod |

## 2. 总体链路

```text
Manager scheduler
    -> TaskRequest(pluginType=SPARK, runMode=K8S_OPERATOR, pluginParam, taskData)
    -> AgentExecutorRpcProvider
    -> WorkerTaskService
    -> SparkPluginTaskExecutor
    -> SparkParamResolver
    -> spark-sql-job.json + ConfigMap
    -> SparkApplication
    -> WorkerTaskExecutionStore
    -> AgentTaskStateListenerRegistry
    -> ManagerTaskResultReporter
```

`WorkerTaskOperatorRouter` 按 `SPARK + K8S_OPERATOR` 直接选择 `SparkPluginTaskExecutor`。

## 3. 数据流

| 场景 | 来源 | 处理 | 输出 |
|------|------|------|------|
| 参数解析 | `TaskRequest.runMode`、`pluginParam`、`taskData` | `SparkParamResolver` 校验运行模式、合并任务数据、解析 Kubernetes 参数 | `SparkExecutionParam` |
| 任务配置 | `pluginParam.defaultTaskData + taskData` | 深度合并；移除 agent 专用 `kubernetes` | `effectiveTaskData` |
| 本地快照 | `effectiveTaskData` | 写入任务运行目录 | `spark-sql-job.json` |
| Kubernetes 配置 | `spark-sql-job.json` | 创建 ConfigMap | key 固定 `spark-sql-job.json` |
| 作业提交 | `SparkExecutionParam` | 渲染并提交 SparkApplication | `appId=applicationName` |
| 状态刷新 | `.snap + .state`、Kubernetes 状态 | `K8sOperatorRunModeStateMapping` 映射 | `StatusEnum` 与 `WorkerResult` |
| 终态处理 | driver pod log、SparkApplication 状态 | 终态上报前采集日志；finish 清理资源 | `pluginLogUri`、`sparkWebUiUri` |

状态映射器接收任务监听器同一次读取的 `.snap + .state`，不自行访问状态存储。终态日志结果由映射器准备，
再由监听器统一持久化、写后校验和上报。

## 4. 关键规则

- `TaskRequest.runMode` 必须为 `K8S_OPERATOR`。
- `sparkVersion` 固定为 `4.0.2`；默认 main class 为
  `com.datafusion.plugin.spark.sql.SparkSqlApplication`。
- 默认镜像为 `apache/spark:4.0.2-scala2.13-java17-ubuntu`；模板可配置内部镜像。
- `pluginAppDir` 默认 `/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql`。
- `pluginJarName` 默认 `plugin-spark-sql.jar`，通过 initContainer 复制到
  `/opt/spark/work-dir/datafusion-jars`。
- `jobConfigMountPath` 默认 `/opt/datafusion/spark/jobs`，driver 通过 `--job-file` 读取 job config。
- Kubernetes 资源名使用 `pluginParam.kubernetes.namePrefix`，默认 `df-spark`。
- `taskData.kubernetes` 可覆盖 namespace、image、serviceAccountName、sharedPvcName、资源规格、日志采集等运行时字段。
- 用户 label 不允许覆盖 `datafusion.*` / `datafusion.io/*` 保留前缀。
- `.snap.runMode` 是控制和状态恢复时的运行模式事实来源。

## 5. 控制语义

| 动作 | 行为 | 返回 |
|------|------|------|
| `submit` | 写本地 job 快照，创建 ConfigMap 和 SparkApplication，保存动作结果 `.state` | 成功返回 `SUBMIT_SUCCESS`；失败返回 `SUBMIT_FAILURE` |
| `stop` | patch `SparkApplication.spec.suspend=true` | 返回 `STOPPING`，由状态刷新推进终态 |
| `kill` | 删除 SparkApplication、Pod 和 ConfigMap | 删除请求成功返回 `KILLING`；状态刷新确认 Application、Pod、Service 均不存在后返回 `KILLED` |
| `finish` | master 确认终态后幂等清理 SparkApplication 和 ConfigMap | 清理成功返回 `true` |

清理 Pod 时先按任务标签查询，再按资源名称逐个删除，不依赖 Kubernetes `deletecollection` 权限。
任务提交 `.snap` 由 `WorkerTaskService` 在调用插件前整体保存，Spark 执行器不重复覆盖。

## 6. 集成边界

- Kubernetes API 通过 Fabric8 client 访问，连接信息复用 `AgentProperties.Kubernetes`。
- Spark Operator CRD 使用 `sparkoperator.k8s.io/v1beta2`。
- Spark SQL 语义、Paimon 配置和 SQL 执行由 `plugin-spark-sql.jar` 负责，agent 不解析 SQL。
- agent 不新增 HTTP API、数据库表、Mapper 或前端页面。

## 7. 验证

| 验证项 | 期望 |
|--------|------|
| 参数解析 | `runMode=K8S_OPERATOR`、Spark 版本、Kubernetes 必填项校验正确 |
| job config | `spark-sql-job.json` 内容为合并后的 `effectiveTaskData`，不包含 agent 专用 `kubernetes` |
| SparkApplication | main class、jar 挂载、ConfigMap 挂载、driver / executor 资源渲染正确 |
| 状态映射 | `SUBMITTED/RUNNING/COMPLETED/FAILED/SUSPENDED` 等状态映射为 DataFusion 状态 |
| 控制动作 | stop、kill、finish 幂等 |
| 重启恢复 | 基于 `.snap + .state` 可恢复查询和控制 |
