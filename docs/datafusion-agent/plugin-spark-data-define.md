# Spark 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 Spark `K8S_OPERATOR` 运行模式的字段、任务参数、Kubernetes 资源和状态持久化事实源。
> Agent 总体运行时结构见 [agent-data-define.md](./agent-data-define.md)。

## 1. Database Data Model

### 1.1 Table Model

- Table name: 无
- Operation: not involved
- Primary key: 无
- Candidate key / unique business key: 无
- Partition strategy: 无
- Table comment: 无
- Notes: Spark agent 插件不新增 Agent 侧数据库表。Manager 侧继续复用 `scheduler_task_info.definition`、`scheduler_task_instance.task_data`、`system_plugin_config.plugin_param` 和 `system_plugin_config.run_mode` 承载任务定义与插件运行配置。

### 1.2 DDL / Migration

无

### 1.3 Field / Column Model

无

### 1.4 Seed / Initial Data

| Data | Scenario | Initialization method | Notes |
|------|----------|-----------------------|-------|
| `Spark K8S_OPERATOR` 插件配置模板 | 系统内置插件模板 | 后续通过 agent 资源 `plugins/spark/templates/spark-k8s-operator-plugin-config.json` 或 manager 初始化数据导入 | `pluginType=SPARK`，`runMode=K8S_OPERATOR` |

## 2. Java Backend Data Model

### 2.1 Persistence Model

无

### 2.2 API Input Model

无。Spark 插件不新增 agent HTTP API，复用 `TaskRequest`。

### 2.3 API Output Model

无。Spark 插件不新增 agent HTTP API，复用 `TaskResult` / `WorkerResult`。

### 2.4 Service Model

| Object | Scenario | Field | Field type | Lifecycle | Notes |
|--------|----------|-------|------------|-----------|-------|
| `SparkRunMode` | 运行模式枚举 | `K8S_OPERATOR` | enum | 请求解析后固定 | 首版只实现 `K8S_OPERATOR`；后续再扩展 `LOCAL`、`THRIFT` |
| `SparkExecutionParam` | 归一化执行参数 | `runMode`, `flowInstanceId`, `taskInstanceId`, `effectiveTaskData`, `workDir`, `sparkVersion`, `mainClass`, `mainApplicationFile`, `sparkConf`, `hadoopConf`, `arguments`, `kubernetes` | Java object | 单次任务 | 从 `TaskRequest` 归一化；runner 只消费该对象 |
| `SparkKubernetesParam` | `K8S_OPERATOR` 提交参数 | `namespace`, `applicationName`, `image`, `serviceAccountName`, `pluginAppDir`, `sharedPvcName`, `sharedMountPath`, `pluginJarName`, `jarMountPath`, `jobConfigMountPath`, `driver`, `executor`, `labels`, `annotations` | Java object | 单次任务 | `serviceAccountName` 必填；`sharedMountPath` 由 `pluginAppDir` 推导，用于渲染 `SparkApplication`、SQL job ConfigMap 和运行引用 |
| `SparkKubernetesRuntimeRef` | `K8S_OPERATOR` 接管参数 | `namespace`, `applicationName`, `configMapName`, `podLabelSelector`, `logStorageUri`, `sparkWebUiUri`, `collectLogsOnFinish` | Java object | 单次状态查询或控制命令 | 由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建 |
| `SparkOperatorStatus` | `K8S_OPERATOR` 状态事实 | `state`, `applicationExists`, `podExists`, `podRunning`, `serviceExists` | Java object | 单次状态查询 | Client 只返回 Kubernetes 事实，DataFusion 状态由 `K8sOperatorRunModeStateMapping` 映射 |
| `SparkTaskRunner` | 运行模式分派 | `runMode()`, `submit(param)`, `stop(param, state)`, `kill(param, state)`, `finish(param, state)` | interface | Spring Bean | 首版只注册 `K8S_OPERATOR` runner |
| `SparkTaskResult` | runner 返回 | `status`, `appId`, `workDirPath`, `result` | Java object | 单次动作 | 由 `SparkPluginTaskExecutor` 转换为 `TaskResult.workerResult` |

### 2.5 Integration Model

| Object | Integration target | Direction | Field | Field type | Conversion rule | Notes |
|--------|--------------------|-----------|-------|------------|-----------------|-------|
| `TaskRequest.pluginParam` | Manager `system_plugin_config.plugin_param` | Inbound | `runMode` | `String` | Manager 注入 `PluginConfigEntity.runMode` | 必须为 `K8S_OPERATOR` |
| `TaskRequest.taskData` | Manager scheduler task data | Inbound | Spark SQL job config、`sparkConf`、`hadoopConf`、`kubernetes` | `JsonNode` | 业务参数与 `pluginParam.defaultTaskData` 深度合并；`kubernetes` 单独解析为 `SparkKubernetesParam` | 业务来源定位不进入 Agent；`kubernetes` 不写入 `effectiveTaskData` |
| `SparkKubernetesParam` | Kubernetes API | Outbound | `applicationName`, `namespace`, `image`, `driver`, `executor`, `sparkConf`, `hadoopConf` | Java object | `applicationName` 按 `{namePrefix}-{taskInstanceId}` 生成并渲染为 `SparkApplication` | `pluginParam.kubernetes.namePrefix` 默认 `df-spark`，API version 固定 `sparkoperator.k8s.io/v1beta2` |
| `SparkKubernetesParam` | Kubernetes API | Outbound | `jobConfigConfigMapName` | `String` | 渲染为 ConfigMap 并挂载到 driver pod | key 固定为 `spark-sql-job.json` |
| `SparkKubernetesRuntimeRef` | Kubernetes API | Inbound/Control | `applicationName`, `podLabelSelector` | `String` | 查询 `SparkApplication` 状态、driver pod 日志和资源清理 | 支持 agent 重启后接管 |

### 2.6 State / Enum Model

| Field / enum | Owner object | Values | Storage type | Display label | Conversion rule | Notes |
|--------------|--------------|--------|--------------|---------------|-----------------|-------|
| `pluginType` | `WorkerTaskExecutionSnap` | `SPARK` | String | Spark 插件 | 固定大写 | worker 注册和路由键 |
| `runMode` | `WorkerTaskExecutionSnap` | `K8S_OPERATOR` | String | Spark Operator | 由 `SparkExecutionParam.runMode` 写入 | 状态映射和控制请求恢复按 `SPARK + K8S_OPERATOR` 选择 |
| `appId` | `WorkerTaskExecutionState` | SparkApplication name | String | SparkApplication 名称 | runner 提交后写入 | 终端任务 ID |
| `status` | `WorkerTaskExecutionState` | `SUBMIT_SUCCESS`, `SUBMIT_FAILURE`, `RUNNING`, `RUN_SUCCESS`, `RUN_FAILURE`, `STOPPING`, `STOP_SUCCESS`, `KILLING`, `KILLED`, `UNKNOWN` | `StatusEnum` | 调度状态 | 由 `SparkOperatorStatus` 结合本地状态映射 | 不新增 agent 私有状态 |
| `result` | `WorkerTaskExecutionState` | `JsonNode` | Java object | 执行摘要 | 写入 `message`, `pluginLogUri`, `sparkWebUiUri`, `finalized` | 不保存完整 SQL 或敏感参数 |
| `SparkApplicationState` | Kubernetes CRD | `SUBMITTED`, `RUNNING`, `COMPLETED`, `FAILED`, `SUBMISSION_FAILED`, `PENDING_RERUN`, `INVALIDATING`, `SUCCEEDING`, `FAILING`, `SUSPENDING`, `SUSPENDED`, `RESUMING`, `UNKNOWN` | String | Operator 状态 | 映射到 `StatusEnum` | 按 kubeflow spark-operator 2.5.0 v1beta2 状态处理 |

## 3. Frontend Data Model

### 3.1 API Client Model

无。首版不新增前端 API client。

### 3.2 Page Query Model

无

### 3.3 Page Display Model

无

## 4. Data Mapping Rules

### 4.1 API Mapping

| API | Request object | Response data | Response wrapper | Notes |
|-----|----------------|---------------|------------------|-------|
| `POST /internal/scheduler/submitTask` | `TaskRequest` | `TaskResult` | `Result<TaskResult>` | 复用 agent 调度 RPC |
| `POST /internal/scheduler/stopTask` | `TaskRequest` | `TaskResult` | `Result<TaskResult>` | patch `SparkApplication.spec.suspend=true` |
| `POST /internal/scheduler/killTask` | `TaskRequest` | `TaskResult` | `Result<TaskResult>` | 强制删除 `SparkApplication`、Pod 和 ConfigMap |
| `POST /internal/scheduler/finishTask` | `TaskRequest` | `Boolean` | `Result<Boolean>` | master 确认终态后执行资源清理 |

### 4.2 Layer Conversion

| Direction | Conversion rule | Special handling |
|-----------|-----------------|------------------|
| `PluginConfigEntity.runMode` -> `TaskRequest.pluginParam.runMode` | Manager 在 `TaskStorageImpl.toPluginData` 中注入 | Agent 收不到 `runMode` 时提交失败 |
| `TaskRequest.pluginParam + taskData` -> `SparkExecutionParam` | `pluginParam` 提供插件级默认值，`taskData` 提供任务级覆盖；Agent 不回写 `pluginParam` | 数组整体替换，对象深度合并，任务级优先 |
| `TaskRequest` -> `SparkExecutionParam.effectiveTaskData` | resolver 深度合并 `pluginParam.defaultTaskData` 与 `taskData`，排除 Agent 专用 `kubernetes` | 数组整体替换，对象深度合并，任务级优先 |
| `SparkExecutionParam.effectiveTaskData` -> job config file | 直接写入 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/spark-sql-job.json` | 本地快照用于排查，不写敏感凭据 |
| job config file -> Kubernetes ConfigMap | 创建 `{namePrefix}-job-config-{taskInstanceId}`，`namePrefix` 默认 `df-spark`，key 固定 `spark-sql-job.json` | SQL 内容不进入 command line |
| `SparkExecutionParam` -> `SparkApplication` | 渲染 `sparkoperator.k8s.io/v1beta2` `SparkApplication` | `mainApplicationFile=local://{jarMountPath}/{pluginJarName}`，`restartPolicy.type=Never` |
| plugin directory -> Spark pod runtime | driver / executor `initContainers` 从共享插件目录复制 fat jar `plugin-spark-sql.jar` 到 `emptyDir` | jar 内含 Paimon 1.4.1；官方 Spark 镜像保持不变 |
| submit result -> `WorkerTaskExecutionState` | `appId=SparkApplication.metadata.name`，`workDirPath=任务运行目录`，`result.pluginLogUri` 写初始日志入口 | `.snap.runMode` 保存运行模式，`.snap` 保存重建参数，状态查询不依赖控制请求携带完整参数 |
| `SparkOperatorStatus` -> `StatusEnum` | `SUBMISSION_FAILED -> SUBMIT_FAILURE`，`COMPLETED -> RUN_SUCCESS`，`FAILED -> RUN_FAILURE`，`RUNNING/SUBMITTED -> RUNNING`，`SUSPENDED after STOPPING -> STOP_SUCCESS` | `SparkApplication` 不存在或状态无法识别时返回 `UNKNOWN` 并打印精简 warn |
| final state -> `WorkerTaskExecutionState.result` | 终态上报前采集 driver pod 日志并写入 `pluginLogUri/sparkWebUiUri/finalized` | 不做资源清理 |
| finish -> Kubernetes resources | 幂等删除 `SparkApplication` 和 ConfigMap | 清理失败返回 `false` |

## 5. Reused Structures

| Object | Path | Reuse method | Notes |
|--------|------|--------------|-------|
| `TaskRequest` / `TaskResult` / `WorkerResult` | `datafusion-common-data` | 复用 | manager / agent 调度通信模型 |
| `PluginTaskExecutor` | `datafusion-scheduler-worker` | 实现 | `SparkPluginTaskExecutor` 注册 `pluginType=SPARK` |
| `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 实现 | `K8sOperatorRunModeStateMapping` 注册 `SPARK + K8S_OPERATOR` |
| `WorkerTaskExecutionSnap` / `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | 复用 | 提交快照、运行态和状态持久化 |
| `AgentProperties.Kubernetes` | `datafusion-agent` | 复用 | Fabric8 Kubernetes client 连接目标集群 |
| `PluginResultJson` | `datafusion-agent` | 复用 | 构造 `message/pluginType/runMode/pluginLogUri` 等轻量结果 |
