# Flink 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 Flink 插件运行模式、任务参数、Kubernetes 参数和状态持久化的事实源。
> 设计流程见 [plugin-flink-design.md](./plugin-flink-design.md)。

## 1. 数据库数据模型

无。Flink agent 插件不新增 Agent 侧数据库表。

Manager 侧继续复用 `scheduler_task_info.definition`、`scheduler_task_instance.task_data`、
`system_plugin_config.plugin_param` 和 `system_plugin_config.run_mode` 保存任务定义、任务实例数据和插件运行配置。

## 2. 数据所有权与生命周期

| 数据 | 创建来源 | 所有者 | 修改者 | 生命周期 | 退出 / 转换 |
|------|----------|--------|--------|----------|-------------|
| `TaskRequest.pluginParam` | Manager 插件配置 | Manager | Manager | 单次调度请求；agent 保存到 `.snap` | `FlinkParamResolver` 解析为 `FlinkExecutionParam` |
| `TaskRequest.taskData` | Manager 任务定义与实例上下文 | Manager | Manager | 单次调度请求；agent 保存到 `.snap` | 与 `defaultTaskData` 合并为 `effectiveTaskData` |
| `effectiveTaskData` | Agent 参数解析 | Agent | Agent | 单次任务运行期 | 写入本地 `flink-job.json` |
| `flinkConfig` | Agent 参数解析 | Agent | Agent | 单次任务运行期 | 写回 `effectiveTaskData.flinkConfig` 并渲染到 `FlinkDeployment` |
| `FlinkExecutionParam` | Agent 参数解析 | Agent | Agent | 单次 submit / control / status 操作内存对象 | runner 消费后转为 `.snap/.state` 与 Kubernetes 资源 |
| `FlinkKubernetesRuntimeRef` | `.snap + .state` 重建 | Agent | Agent | 单次状态查询或控制动作 | 用于查询、停止、强杀、清理 Kubernetes 资源 |
| `.snap` | `FlinkPluginTaskExecutor.submitTask` | Agent | Agent | 任务运行期 | agent finish / destroy 流程清理 |
| `.state` | runner 与状态映射 | Agent | Agent | 任务运行期 | 终态上报后由 agent 流程清理 |

Agent 不回写 Manager 的 `pluginParam` 或 `taskData`。

## 3. Java 后端数据模型

| 对象 | 字段 | 生命周期 | 说明 |
|------|------|----------|------|
| `FlinkRunMode` | `LOCAL`, `STANDALONE`, `YARN`, `K8S`, `K8S_OPERATOR` | 请求解析后固定 | 当前已注册 runner 为 `K8S_OPERATOR` |
| `FlinkExecutionParam` | `runMode`, `flowInstanceId`, `taskInstanceId`, `jobJson`, `effectiveTaskData`, `workDir`, `flinkConfig`, `flinkAppDir`, `launchMode`, `flinkAppJar`, `classpath`, `mainClass`, `flinkVersion`, `libDir`, `args`, `kubernetes` | 单次任务 | runner 的归一化输入 |
| `FlinkKubernetesParam` | `namespace`, `deploymentName`, `image`, `imagePullPolicy`, `serviceAccountName`, `sharedPvcName`, `sharedMountPath`, `flinkAppDir`, `flinkAppJar`, `jarUri`, `mainClass`, `flinkVersion`, `libDir`, `jobParallelism`, `upgradeMode`, `flinkWebUiUri`, `collectLogsOnFinish`, `deleteDeploymentOnFinish`, `labels`, `annotations`, `env`, `envFrom`, `jobManager`, `taskManager`, `nodeSelector` | 单次任务 | 渲染 `FlinkDeployment` |
| `FlinkKubernetesRuntimeRef` | `namespace`, `deploymentName`, `podLabelSelector`, `logStorageUri`, `flinkWebUiUri`, `collectLogsOnFinish`, `deleteDeploymentOnFinish` | 状态查询 / 控制动作 | 从 `.snap + .state` 重建 |
| `FlinkOperatorStatus` | `state`, `deploymentExists`, `podExists`, `serviceExists` | 单次状态查询 | Kubernetes / Operator 事实 |
| `FlinkTaskResult` | `status`, `appId`, `workDirPath`, `result`, `kubernetesRuntimeRef` | 单次 runner 返回 | 转换为 `TaskResult.workerResult` |

## 4. `pluginParam`

| 字段 | 类型 | 默认值 | 必填 | 说明 |
|------|------|--------|------|------|
| `runMode` | `String` | 无 | 是 | 当前可执行值为 `K8S_OPERATOR` |
| `flinkAppDir` | `String` | 无 | 是 | Pod 内共享盘 app 目录 |
| `launchMode` | `String` | `JAR` | 否 | 当前执行路径按 JAR 模式渲染 |
| `flinkAppJar` | `String` | 无 | 是 | app 主 jar 文件名 |
| `classpath` | `String` | `""` | 否 | 额外 classpath 表达式 |
| `mainClass` | `String` | 无 | 是 | Flink application main class |
| `flinkVersion` | `String` | `2.2.0` | 否 | 必须为 `2.2.0` |
| `libDir` | `String` | `lib` | 否 | app 目录下依赖目录 |
| `flinkCheckpointRootDir` | `String` | `s3a://data-lake-warehouse/flink` | 否 | 派生 checkpoint / savepoint 路径根目录 |
| `flinkConfig` | `Object<String,String>` | `{}` | 否 | 插件级 Flink 配置 |
| `defaultTaskData` | `Object` | `{}` | 否 | 与 `taskData` 深度合并 |
| `kubernetes` | `FlinkKubernetesParam` | `{}` | 是 | Kubernetes 运行参数 |

## 5. `taskData`

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobJson` | `String` 或 `Object` | 完整 Flink 作业配置；存在时作为 `effectiveTaskData` |
| `job` | `Object` | 作业元信息 |
| `source` | `Object` | source 配置 |
| `flinkConfig` | `Object<String,String>` | 任务级 Flink 配置 |
| `sink` | `Object` | sink 配置；非密钥 S3 选项会映射为 Flink S3A 配置 |
| `args` | `Array<String>` | 单次任务附加参数 |
| `kubernetes` | `Object` | 单次 Kubernetes 覆盖项；不写入 `flink-job.json` |

合并规则：`jobJson` 存在时优先使用；否则对象深度合并，数组整体替换，普通值以 `taskData` 为准。
`args` 和 `kubernetes` 是 agent 运行元数据，不写入最终 job JSON。

## 6. Flink 配置规则

| 来源 | 优先级 | 说明 |
|------|--------|------|
| `pluginParam.flinkConfig` | 低 | 插件级默认 Flink 配置 |
| `effectiveTaskData.flinkConfig` | 高 | 任务级 Flink 配置 |
| `sink.options` 非密钥 S3 配置 | 补充 | 映射为 `fs.s3a.*` |
| `taskData.job.id` | 派生 | 生成 checkpoint / savepoint 路径 |

归一后的配置写回 `effectiveTaskData.flinkConfig`，同时渲染到 `FlinkDeployment.spec.flinkConfiguration`。
`state.backend.type` 在渲染前归一为 `state.backend`。

## 7. Kubernetes 参数

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `namePrefix` | `String` | `df-flink` | 只从 `pluginParam.kubernetes` 读取 |
| `namespace` | `String` | `default` | 可由任务级覆盖 |
| `image` | `String` | `flink:2.2.0-scala_2.12-java17` | 可由任务级覆盖 |
| `imagePullPolicy` | `String` | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 无 | Flink Pod 使用的 ServiceAccount |
| `sharedPvcName` | `String` | 无 | 共享 PVC 名称 |
| `flinkWebUiUriTemplate` | `String` | `http://{{serviceName}}.{{namespace}}.svc:8081` | 任务结果中的 Web UI 地址 |
| `labels` / `annotations` | `Object<String,String>` | `{}` | 插件级与任务级合并，保留前缀不可覆盖 |
| `env` / `envFrom` | `Object` / `Array` | 空 | Flink container 环境 |
| `jobManager` / `taskManager` | `Object` | `{}` | JM / TM 资源和副本 |
| `nodeSelector` | `Object<String,String>` | `{"kubernetes.io/arch":"amd64"}` | 插件级与任务级合并 |
| `collectLogsOnFinish` | `Boolean` | `true` | 终态是否采集 Pod 日志 |
| `deleteDeploymentOnFinish` | `Boolean` | `false` | finish 时是否删除 FlinkDeployment |
| `upgradeMode` | `String` | `stateless` | `FlinkDeployment.spec.job.upgradeMode` |
| `podTemplate` | `Object` | 空 | 受限覆盖项 |

派生字段：

| 字段 | 来源 | 说明 |
|------|------|------|
| `deploymentName` | `{namePrefix}-{taskInstanceId}` | FlinkDeployment name |
| `jarUri` | `flinkAppJar` | `local:///opt/flink/usrlib/{flinkAppJar}` |
| `sharedMountPath` | `flinkAppDir` | 从 `/plugins/` 前缀推导共享盘挂载点 |
| `jobArg` | `flink-job.json` | `--job <base64(job-json)>` |
| `operatorFlinkVersion` | `flinkVersion` | `2.2.0 -> v2_2` |

## 8. 插件目录

```text
plugins/flink/{appDirName}/
plugins/flink/{appDirName}/{flinkAppJar}
plugins/flink/{appDirName}/{libDir}/
```

运行侧目录由 `pluginParam.flinkAppDir` 指定。initContainer 只复制当前 app 目录中的 jar 和依赖到
`/opt/flink/usrlib`。

## 9. 状态模型

| 字段 | 所属对象 | 值 / 类型 | 说明 |
|------|----------|-----------|------|
| `pluginType` | `WorkerTaskExecutionSnap` | `FLINK` | 插件路由键 |
| `runMode` | `WorkerTaskExecutionSnap` | `LOCAL`, `STANDALONE`, `YARN`, `K8S`, `K8S_OPERATOR` | 状态映射和控制恢复键 |
| `appId` | `WorkerTaskExecutionState` | `FlinkDeployment` name | runner 提交后写入 |
| `workDirPath` | `WorkerTaskExecutionState` | 本地任务运行目录 | 保存 job 快照和日志 |
| `status` | `WorkerTaskExecutionState` | `StatusEnum` | 由 runner 和状态映射写入 |
| `result` | `WorkerTaskExecutionState` | JSON | 可包含 `pluginLogUri`, `flinkWebUiUri`, `flinkJobId`, `savepointLocation` |

## 10. 前端数据模型

无。
