# Flink 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 Flink 插件运行模式、任务参数、K8S / K8S_OPERATOR 提交参数和状态持久化的事实源。
> Agent 总体运行时结构见 [agent-data-define.md](./agent-data-define.md)。

## 1. 表结构

无。Flink agent 插件不新增 Agent 侧数据库表。

Manager 侧继续复用 `scheduler_task_info.definition`、`scheduler_task_instance.task_data`、
`system_plugin_config.plugin_param` 和 `system_plugin_config.run_mode` 承载任务定义与插件运行配置。

## 2. 配置边界

Flink 运行参数不定义在 `datafusion-agent/src/main/resources/application.yml`，也不通过
`datafusion.agent.flink.*` 读取。Agent 只复用已有基础运行配置：

| 对象 | 字段 | 说明 |
|------|------|------|
| `AgentProperties.Storage` | `taskRuntimeDir` | 任务运行态绝对根目录，默认 `/opt/datafusion/task-runtime` |
| `AgentProperties.Kubernetes` | `apiServer`, `token`, `tokenFile`, `caCertFile` | Fabric8 Kubernetes client 连接集群；不是 Flink 作业参数 |

静态结构由模板管理：

| runMode | 模板 | 渲染产物 | 首版状态 |
|---------|------|----------|----------|
| `LOCAL` | `plugins/flink/templates/flink-local-runtime.yml` | `LocalShellProcess` | 只定义契约，暂不实现 |
| `STANDALONE` | `plugins/flink/templates/flink-standalone-runtime.yml` | Flink CLI / REST submit spec | 只定义契约，暂不实现 |
| `YARN` | `plugins/flink/templates/flink-yarn-runtime.yml` | Flink CLI submit spec | 只定义契约，暂不实现 |
| `K8S` | `plugins/flink/templates/flink-k8s-runtime.yml` | Flink Native Kubernetes Application submit spec | 只定义契约，暂不实现 |
| `K8S_OPERATOR` | `plugins/flink/templates/flink-k8s-operator-deployment.yml` | Secret + `FlinkDeployment` | 优先实现；集群 Operator 版本固定 `flink-kubernetes-operator:1.14.0` |

## 3. TaskRequest.pluginParam

`pluginParam` 是插件级运行配置，来源于 Manager 侧 `system_plugin_config.plugin_param`，并由 Manager 把
`PluginConfigEntity.runMode` 注入到 `pluginParam.runMode`。`runMode` 与现有规范一致，使用大写枚举值。
Agent 不从 `taskData.runMode` 或配置文件推断运行模式。

### 3.1 FlinkPluginParam

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 是 | 无 | 支持 `LOCAL`、`STANDALONE`、`YARN`、`K8S`、`K8S_OPERATOR` |
| `jobName` | `String` | 否 | `taskName` 或 `flink-task-{taskInstanceId}` | Flink 作业名 |
| `entryClass` | `String` | 条件必填 | 无 | Flink application main class；K8S / K8S_OPERATOR 必填 |
| `args` | `List<String>` | 否 | `["--config", "{{jobJsonMountPath}}"]` | 作业启动参数；Agent 自动替换配置文件路径占位 |
| `defaultTaskData` | `Object` | 否 | 空对象 | 默认 job config；与 `taskData` 深度合并后生成 `job.json` |
| `flinkConfig` | `Object<String,String>` | 否 | 空对象 | 插件级 Flink 配置，如 checkpoint、state backend、metrics 配置 |
| `parallelism` | `Integer` | 否 | 空 | 作业默认并行度；任务级可覆盖 |
| `logStorageUri` | `String` | 否 | 空 | 外挂日志存储 URI；未配置时 K8S 终态可采集 Pod 日志 |
| `kubernetes` | `FlinkKubernetesParam` | K8S / K8S_OPERATOR 条件必填 | 空 | Kubernetes 插件级运行配置 |

### 3.2 pluginParam 示例

```json
{
  "runMode": "K8S_OPERATOR",
  "jobName": "kafka-json-paimon",
  "entryClass": "com.datafusion.plugin.kafka.json.KafkaJsonPaimonApplication",
  "args": ["--config", "{{jobJsonMountPath}}"],
  "parallelism": 2,
  "flinkConfig": {
    "state.backend": "rocksdb",
    "execution.checkpointing.interval": "60s",
    "execution.checkpointing.mode": "AT_LEAST_ONCE",
    "state.checkpoints.dir": "s3://datafusion/flink/checkpoints/kafka-json",
    "state.savepoints.dir": "s3://datafusion/flink/savepoints/kafka-json"
  },
  "kubernetes": {
    "namespace": "datafusion",
    "image": "flink:2.2.0-scala_2.12-java17",
    "flinkAppName": "datafusion-plugin-kafka-json",
    "sharedPvcName": "datafusion-shared-data",
    "sharedPluginRoot": "/opt/datafusion/plugins",
    "flinkWebUiUriTemplate": "http://{{deploymentName}}-rest.{{namespace}}.svc:8081",
    "serviceAccountName": "flink-runner",
    "jobManager": {
      "resource": {
        "memory": "2048m",
        "cpu": 1.0
      }
    },
    "taskManager": {
      "resource": {
        "memory": "4096m",
        "cpu": 2.0
      }
    },
    "nodeSelector": {
      "kubernetes.io/arch": "amd64"
    }
  },
  "defaultTaskData": {
    "job": {},
    "source": {},
    "runtime": {},
    "sink": {}
  }
}
```

## 4. TaskRequest.taskData

`taskData` 是本次任务执行数据，来源于任务定义和运行实例上下文。它保存本次 Flink job config，以及允许单次任务覆盖的运行参数。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `jobJson` | `String` 或 `Object` | 否 | 空 | 完整 Flink 作业配置 JSON；存在时优先写入任务运行目录的 `job.json` |
| `job` / `source` / `runtime` / `sink` | `Object` | 否 | 空 | 与 `pluginParam.defaultTaskData` 深度合并，用于生成 `job.json` |
| `jobName` | `String` | 否 | 空 | 单次任务覆盖作业名 |
| `args` | `List<String>` | 否 | 空 | 单次任务覆盖作业参数 |
| `parallelism` | `Integer` | 否 | 空 | 单次任务覆盖并行度 |
| `flinkConfig` | `Object<String,String>` | 否 | 空 | 单次任务追加或覆盖 Flink 配置 |
| `kubernetes` | `FlinkKubernetesTaskOverride` | 否 | 空 | K8S / K8S_OPERATOR 单任务覆盖项 |

`taskData` 规则：

- `taskData.jobJson` 存在时直接作为最终 `job.json`。
- `taskData.jobJson` 为空时，使用 `deepMerge(pluginParam.defaultTaskData, taskData)` 生成 `job.json`。
- `bizRef`、`data`、`options`、`kubernetes`、`args`、`parallelism`、`flinkConfig` 是 Agent 侧注册或运行元数据，不写入最终 `job.json`。
- 数组按整体替换；普通值按 `taskData` 覆盖默认值。

## 5. 运行模式枚举

| 枚举 | 首版实现 | 说明 |
|------|----------|------|
| `LOCAL` | 否 | 本地 Flink MiniCluster 或本地 CLI，后续用于开发和单机调试 |
| `STANDALONE` | 否 | 提交到已有 Flink Standalone 集群，后续通过 REST 或 CLI 实现 |
| `YARN` | 否 | 提交到 YARN Application / Per-Job，后续通过 Flink CLI 或 REST 实现 |
| `K8S` | 否 | Flink Native Kubernetes Application 模式，后续实现 |
| `K8S_OPERATOR` | 是 | Flink Kubernetes Operator `FlinkDeployment` 模式，首版优先实现 |

## 6. Kubernetes 参数

`pluginParam.kubernetes` 只保存 Kubernetes 运行时参数。构建 profile、主 jar、共享盘 app 目录、`jarURI`
等发布细节由 `plugin-manifest.json`、`flink-app-manifest.json` 和固定运行约定派生，不重复放入
`pluginParam.kubernetes`。

`taskData.kubernetes` 提供单任务覆盖值，只允许覆盖下表中明确可覆盖的运行时字段。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `namespace` | `String` | 否 | `default` | Kubernetes namespace |
| `image` | `String` | K8S / K8S_OPERATOR 必填 | `flink:2.2.0-scala_2.12-java17` | Flink runtime 基础镜像；业务 jar 和依赖不打入镜像；首版与 `flinkVersion=v2_2`、`buildFlinkVersion=2.2.0` 成组使用 |
| `imagePullPolicy` | `String` | 否 | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 否 | 空 | Flink Pod 使用的 ServiceAccount |
| `flinkAppName` | `String` | K8S_OPERATOR 必填 | `datafusion-plugin-kafka-json` | Flink app 发布包名，建议使用 Maven `artifactId`；不是 agent 运行模式概念 |
| `sharedPvcName` | `String` | K8S_OPERATOR 必填 | `datafusion-shared-data` | 共享 PVC 名称，定义见 `docs/k8s/datafusion-common-pvc.yml` |
| `sharedPluginRoot` | `String` | K8S_OPERATOR 必填 | `/opt/datafusion/plugins` | Flink Pod 内共享插件根目录；实际 app 目录派生为 `{sharedPluginRoot}/flink/{flinkAppName}` |
| `flinkWebUiUriTemplate` | `String` | 否 | `http://{{deploymentName}}-rest.{{namespace}}.svc:8081` | Flink Web UI 地址模板；支持 `namespace`、`deploymentName`、`appId` 占位，提交后写入结果 `flinkWebUiUri` |
| `labels` | `Object<String,String>` | 否 | 空 | 附加 label，不允许覆盖 `datafusion.*` 和 `datafusion.io/*` 保留 label |
| `annotations` | `Object<String,String>` | 否 | 空 | 附加 annotation |
| `env` | `Object<String,String>` | 否 | 空 | Flink container 环境变量；Secret 和 job 路径相关变量由 Agent 派生 |
| `jobManager` | `Object` | 否 | 空 | JobManager resource / pod 配置 |
| `taskManager` | `Object` | 否 | 空 | TaskManager resource / pod 配置 |
| `nodeSelector` | `Object<String,String>` | 否 | `{"kubernetes.io/arch":"amd64"}` | Pod nodeSelector；当前仅支持 amd64 |
| `collectLogsOnFinish` | `Boolean` | 否 | `true` | 未配置外挂日志存储时，终态是否拉取 Pod 日志到任务运行目录 |
| `deleteDeploymentOnFinish` | `Boolean` | 否 | `false` | K8S_OPERATOR finish 时是否删除 `FlinkDeployment` |
| `deleteSecretOnFinish` | `Boolean` | 否 | `true` | finish 时是否删除本次任务 Secret |
| `upgradeMode` | `String` | K8S_OPERATOR 可选 | `stateless` | Flink Operator `spec.job.upgradeMode` |
| `podTemplate` | `Object` | K8S_OPERATOR 可选 | 空 | Flink Operator podTemplate 透传结构 |

派生字段和固定约定：

| 派生项 | 来源 | 说明 |
|--------|------|------|
| `operatorVersion` | 固定约定 | 首版目标集群为 `flink-kubernetes-operator:1.14.0`，仅用于环境校验 |
| `flinkVersion` | 固定约定 | `FlinkDeployment.spec.flinkVersion=v2_2` |
| `buildFlinkVersion` | `flink-app-manifest.json` | 首版为 `2.2.0`，必须和 runtime image / `flinkVersion` 对齐 |
| `artifactMode` | `flink-app-manifest.json` | `FAT_JAR` 或 `THIN_JAR_LIB` |
| `flinkAppMainJar` | `flink-app-manifest.json` | 用于派生 `jarURI=local:///opt/flink/usrlib/{flinkAppMainJar}` |
| `flinkAppDir` | `sharedPluginRoot + flinkAppName` | Pod 内路径为 `{sharedPluginRoot}/flink/{flinkAppName}` |
| `usrlibPath` | 固定约定 | `/opt/flink/usrlib` |
| `deploymentNamePrefix` | 固定约定 | `df-flink-` |
| `secretNamePrefix` | 固定约定 | `df-flink-job-` |
| `jobJsonSecretKey` | 固定约定 | `job.json` |
| `jobJsonMountPath` | 固定约定 | `/opt/datafusion/flink/job/job.json` |
| `imagePlatform` | 固定约定 | 当前只支持 `linux/amd64`，通过 `nodeSelector` 或等价调度约束表达 |
| `mavenProfile` / `builderScriptPath` / `agentFlinkAppDir` | 构建发布流程 | 只属于 builder，不进入运行时参数 |

## 7. 插件目录 Manifest

插件目录 manifest 只用于插件构建、发布和校验，不改变 agent 公共 `pluginType` / `runMode` 模型。

### 7.1 PluginTypeManifest

`plugins/{pluginType}/plugin-manifest.json` 描述插件类型目录能力：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `pluginType` | `String` | 是 | 无 | 插件类型目录名，例如 `flink`、`datax` |
| `multiApp` | `Boolean` | 是 | `false` | 是否支持一个插件类型目录下存在多个 app 发布包；Flink 为 `true`，DataX 可为 `false` |
| `appDirPattern` | `String` | 条件必填 | 空 | `multiApp=true` 时的 app 目录模式，例如 `plugins/flink/{flinkAppName}` |
| `appNameField` | `String` | 条件必填 | 空 | app 名称对应的任务参数字段，Flink 使用 `flinkAppName` |
| `builderDirPolicy` | `String` | 否 | `src/main/resources/builder` | builder 脚本所在目录策略；builder 不进入运行目录 |

### 7.2 FlinkAppManifest

`plugins/flink/{flinkAppName}/flink-app-manifest.json` 描述单个 Flink app 发布包：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `flinkAppName` | `String` | 是 | 无 | Flink app 发布包名，建议使用 Maven `artifactId` |
| `artifactMode` | `String` | 是 | `FAT_JAR` | `FAT_JAR` 或 `THIN_JAR_LIB` |
| `flinkAppMainJar` | `String` | 是 | 无 | app 主 jar 文件名 |
| `libDir` | `String` | 是 | `lib` | 依赖目录；fat 模式为空目录，thin 模式保存随作业发布的依赖 |
| `confDir` | `String` | 否 | `conf` | app 静态配置目录 |
| `jobsDir` | `String` | 否 | `jobs` | app 样例 job 配置目录，不保存运行实例密钥 |
| `buildFlinkVersion` | `String` | 是 | `2.2.0` | 构建 app 使用的 Flink 版本 |

## 8. Service / Runtime 模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|------|------|------|----------|----------|------|
| `FlinkRunMode` | 运行模式枚举 | `LOCAL`, `STANDALONE`, `YARN`, `K8S`, `K8S_OPERATOR` | enum | 请求解析后固定 | 与现有规范一致，配置和状态均使用大写枚举 |
| `FlinkExecutionParam` | 提交前归一化参数 | `runMode`, `jobName`, `entryClass`, `args`, `jobJson`, `effectiveTaskData`, `workDir`, `flinkConfig`, `parallelism`, `kubernetes` | Java object | 单次任务 | 从 `pluginParam` 和 `taskData` 归一化 |
| `PluginTypeManifest` | 插件类型目录清单 | `pluginType`, `multiApp`, `appDirPattern`, `appNameField`, `builderDirPolicy` | JSON/YAML | 随 agent 插件目录发布 | 描述 `plugins/{pluginType}` 是否支持多个 app 包；Flink 为 `multiApp=true`，DataX 可为 `multiApp=false` |
| `FlinkAppManifest` | 单个 Flink app 清单 | `flinkAppName`, `artifactMode`, `flinkAppMainJar`, `libDir`, `confDir`, `jobsDir`, `buildFlinkVersion` | JSON/YAML | 随单个 Flink app 发布 | builder 输出后生成或校验，便于 agent 和发布脚本确认主 jar、依赖目录和版本 |
| `FlinkKubernetesParam` | K8S / K8S_OPERATOR 提交参数 | `namespace`, `deploymentName`, `secretName`, `image`, `flinkAppName`, `sharedPvcName`, `sharedPluginRoot`, `upgradeMode`, `flinkWebUiUriTemplate` | Java object | 单次任务 | 只保存运行时字段；构建和 jar 路径由 manifest / 固定约定派生 |
| `FlinkKubernetesRuntimeRef` | K8S / K8S_OPERATOR 接管参数 | `namespace`, `deploymentName`, `secretName`, `podLabelSelector`, `logStorageUri`, `flinkWebUiUri`, `collectLogsOnFinish`, `deleteDeploymentOnFinish`, `deleteSecretOnFinish` | Java object | 单次状态查询或控制命令 | 由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建 |
| `FlinkTaskRunner` | 运行模式分派 | `runMode()` | interface | Spring Bean | 各运行模式 Runner 实现同一接口 |
| `FlinkSubmitResult` | Runner 提交返回 | `status`, `appId`, `workDirPath`, `result`, `kubernetesRuntimeRef` | Java object | 单次提交 | 由 `FlinkPluginTaskExecutor` 转为 `TaskResult.workerResult` |

## 9. 状态模型

| 字段 / 枚举 | 所属对象 | 值 | 存储类型 | 转换规则 | 说明 |
|-------------|----------|----|----------|----------|------|
| `pluginType` | `WorkerTaskExecutionSnap` | `FLINK` | String | 固定大写 | Flink 插件路由键 |
| `runMode` | `WorkerTaskExecutionSnap` | `LOCAL`, `STANDALONE`, `YARN`, `K8S`, `K8S_OPERATOR` | String | 由 `FlinkExecutionParam.runMode` 写入 | 状态映射按 `FLINK + runMode` 选择 |
| `appId` | `WorkerTaskExecutionState` | K8S: cluster id；K8S_OPERATOR: `FlinkDeployment` name | String | Runner 提交后写入 | 终端任务 ID |
| `workDirPath` | `WorkerTaskExecutionState` | 任务运行目录 | String | Runner 提交后写入 | 保存 `job.json` 快照和终态日志 |
| `status` | `WorkerTaskExecutionState` | `RUNNING`, `RUN_SUCCESS`, `RUN_FAILURE`, `STOPPING`, `STOP_SUCCESS`, `KILLING`, `KILLED`, `UNKNOWN` | `StatusEnum` | K8S 由 Flink REST / Kubernetes Deployment / Pod 状态映射；K8S_OPERATOR 由 `FlinkDeployment.status` 映射 | 不新增 Agent 私有状态 |
| `result` | `WorkerTaskExecutionState` | `JsonNode` | Java object | Runner / 状态映射写入 | 可包含 `pluginLogUri`、`flinkWebUiUri`、`flinkJobId`、`savepointLocation`，不包含完整 job JSON 或密码 |

## 10. 复用结构

| 对象 | 来源 | 复用方式 | 说明 |
|------|------|----------|------|
| `TaskRequest` / `TaskResult` / `WorkerResult` | `datafusion-common-data` | 直接复用 | manager / agent 通信模型 |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | 直接复用 | 插件执行和状态映射 SPI |
| `WorkerTaskExecutionSnap` / `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | 直接复用 | 本地运行态持久化和重启接管 |
| `TaskRuntimeFiles` | `datafusion-common-data` | 直接复用 | 任务运行目录标准日志 |
| `TemplateSpecRenderer` / `TemplateYamlFragments` | `datafusion-agent` | 可复用 | YAML 模板渲染和片段缩进工具 |
