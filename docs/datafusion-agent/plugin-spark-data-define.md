# Spark 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 `SPARK + K8S_OPERATOR` 的数据结构事实源。设计流程见
> [plugin-spark-design.md](./plugin-spark-design.md)。

## 1. 数据库数据模型

无。Spark agent 插件不新增 Agent 侧数据库表。

Manager 侧继续复用 `scheduler_task_info.definition`、`scheduler_task_instance.task_data`、
`system_plugin_config.plugin_param` 和 `system_plugin_config.run_mode` 保存任务定义、任务实例数据和插件运行配置。

## 2. 数据所有权与生命周期

| 数据 | 创建来源 | 所有者 | 修改者 | 生命周期 | 退出 / 转换 |
|------|----------|--------|--------|----------|-------------|
| `TaskRequest.runMode` | Manager 插件配置 | Manager | Manager | 单次调度请求；agent 保存到 `.snap` | 与 `pluginType=SPARK` 共同路由执行器 |
| `TaskRequest.pluginParam` | Manager 插件配置 | Manager | Manager | 单次调度请求；agent 保存到 `.snap` | `SparkParamResolver` 解析为 `SparkExecutionParam` |
| `TaskRequest.taskData` | Manager 任务定义与实例上下文 | Manager | Manager | 单次调度请求；agent 保存到 `.snap` | 与 `defaultTaskData` 合并为 `effectiveTaskData` |
| `effectiveTaskData` | Agent 参数解析 | Agent | Agent | 单次任务运行期 | 写入 `spark-sql-job.json` 和 Kubernetes ConfigMap |
| `SparkExecutionParam` | Agent 参数解析 | Agent | Agent | 单次 submit / control / status 操作内存对象 | 执行器消费后转为 `.snap/.state` 与 Kubernetes 资源 |
| `SparkKubernetesRuntimeRef` | `.snap + .state` 重建 | Agent | Agent | 单次状态查询或控制动作 | 用于查询、停止、强杀、清理 Kubernetes 资源 |
| `.snap` | `SparkPluginTaskExecutor.submitTask` | Agent | Agent | 任务运行期 | agent finish / destroy 流程清理 |
| `.state` | 执行器与状态映射 | Agent | Agent | 任务运行期 | 终态上报后由 agent 流程清理 |

Agent 不回写 Manager 的 `pluginParam` 或 `taskData`。

## 3. Java 后端数据模型

| 对象 | 字段 | 生命周期 | 说明 |
|------|------|----------|------|
| `SparkRunMode` | `K8S_OPERATOR` | 请求解析后固定 | 当前唯一运行模式 |
| `SparkExecutionParam` | `runMode`, `flowInstanceId`, `taskInstanceId`, `workDir`, `effectiveTaskData`, `sparkVersion`, `mainClass`, `mainApplicationFile`, `sparkConf`, `hadoopConf`, `arguments`, `kubernetes` | 单次任务 | 执行器的归一化输入 |
| `SparkKubernetesParam` | `namespace`, `applicationName`, `configMapName`, `image`, `imagePullPolicy`, `serviceAccountName`, `pluginAppDir`, `sharedPvcName`, `sharedMountPath`, `pluginJarName`, `jarMountPath`, `jobConfigMountPath`, `podLabelSelector`, `logStorageUri`, `sparkWebUiUri`, `collectLogsOnFinish`, `labels`, `annotations`, `nodeSelector`, `driver`, `executor` | 单次任务 | 渲染 SparkApplication 和 ConfigMap |
| `SparkKubernetesRuntimeRef` | `namespace`, `applicationName`, `configMapName`, `podLabelSelector`, `logStorageUri`, `sparkWebUiUri`, `collectLogsOnFinish` | 状态查询 / 控制动作 | 从 `.snap + .state` 重建 |
| `SparkOperatorStatus` | `state`, `applicationExists`, `podExists`, `podRunning`, `serviceExists` | 单次状态查询 | Kubernetes 事实，不直接等于 DataFusion 状态 |
| `SparkTaskResult` | `status`, `appId`, `workDirPath`, `result` | 单次动作 | 转换为 `TaskResult.workerResult` |

## 4. `pluginParam`

| 字段 | 类型 | 默认值 | 必填 | 说明 |
|------|------|--------|------|------|
| `sparkVersion` | `String` | `4.0.2` | 否 | 必须为 `4.0.2` |
| `mainClass` | `String` | `com.datafusion.plugin.spark.sql.SparkSqlApplication` | 否 | Spark SQL 应用入口 |
| `sparkConf` | `Object<String,String>` | `{}` | 否 | 插件级 Spark 配置 |
| `hadoopConf` | `Object<String,String>` | `{}` | 否 | 插件级 Hadoop 配置 |
| `defaultTaskData` | `Object` | `{}` | 否 | 与 `taskData` 深度合并 |
| `kubernetes` | `SparkKubernetesParam` | `{}` | 是 | Kubernetes 运行参数 |

## 5. `taskData`

| 字段 | 类型 | 说明 |
|------|------|------|
| `job` | `Object` | Spark SQL 任务元信息 |
| `sqlTargetType` | `String` | SQL 目标类型 |
| `catalogName` | `String` | catalog 名称 |
| `databaseName` | `String` | database 名称 |
| `statements` | `Array<String>` | SQL 语句列表 |
| `paimonConf` | `Object<String,String>` | Paimon 配置 |
| `sparkConf` | `Object<String,String>` | 任务级 Spark 配置，覆盖插件级同名项 |
| `hadoopConf` | `Object<String,String>` | 任务级 Hadoop 配置，覆盖插件级同名项 |
| `kubernetes` | `Object` | agent 运行参数覆盖项；不写入 `spark-sql-job.json` |

合并规则：对象深度合并，数组整体替换，普通值以 `taskData` 为准。`kubernetes` 在生成
`effectiveTaskData` 时移除。

## 6. Kubernetes 参数

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `namePrefix` | `String` | `df-spark` | 只从 `pluginParam.kubernetes` 读取 |
| `namespace` | `String` | `default` | 可由 `taskData.kubernetes` 覆盖 |
| `image` | `String` | `apache/spark:4.0.2-scala2.13-java17-ubuntu` | 可由任务级覆盖 |
| `imagePullPolicy` | `String` | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 无 | 必填 |
| `pluginAppDir` | `String` | `/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql` | 共享盘插件目录 |
| `sharedPvcName` | `String` | 无 | 必填 |
| `pluginJarName` | `String` | `plugin-spark-sql.jar` | Spark SQL fat jar |
| `jarMountPath` | `String` | `/opt/spark/work-dir/datafusion-jars` | Pod 内 jar 目录 |
| `jobConfigMountPath` | `String` | `/opt/datafusion/spark/jobs` | Pod 内 job config 目录 |
| `sparkWebUiUriTemplate` | `String` | `http://{{applicationName}}-ui-svc.{{namespace}}.svc:4040` | 只从任务级读取 |
| `collectLogsOnFinish` | `Boolean` | `true` | 终态是否采集 driver 日志 |
| `labels` / `annotations` | `Object<String,String>` | `{}` | 插件级与任务级合并 |
| `nodeSelector` | `Object<String,String>` | `{"kubernetes.io/arch":"amd64"}` | 插件级与任务级合并 |
| `driver` / `executor` | `Object` | `{}` | driver / executor 规格 |

## 7. 状态模型

| 字段 | 所属对象 | 值 / 类型 | 说明 |
|------|----------|-----------|------|
| `pluginType` | `WorkerTaskExecutionSnap` | `SPARK` | 插件路由键 |
| `runMode` | `WorkerTaskExecutionSnap` | `K8S_OPERATOR` | 状态映射和控制恢复键 |
| `appId` | `WorkerTaskExecutionState` | SparkApplication name | 执行器提交后写入 |
| `workDirPath` | `WorkerTaskExecutionState` | 本地任务运行目录 | 保存 job 快照和日志 |
| `status` | `WorkerTaskExecutionState` | `StatusEnum` | 由执行器和状态映射写入 |
| `result` | `WorkerTaskExecutionState` | JSON | 保存 `message`, `pluginLogUri`, `sparkWebUiUri`, `finalized` 等摘要 |

## 8. 前端数据模型

无。
