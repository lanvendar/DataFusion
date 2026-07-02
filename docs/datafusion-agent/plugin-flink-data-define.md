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

| runMode | 配置值 | 模板 | 渲染产物 | 首版状态 |
|---------|--------|------|----------|----------|
| `LOCAL` | `local` | `plugins/flink/templates/flink-local-runtime.yml` | `LocalShellProcess` | 只定义契约，暂不实现 |
| `STANDALONE` | `standalone` | `plugins/flink/templates/flink-standalone-runtime.yml` | Flink CLI / REST submit spec | 只定义契约，暂不实现 |
| `YARN` | `yarn` | `plugins/flink/templates/flink-yarn-runtime.yml` | Flink CLI submit spec | 只定义契约，暂不实现 |
| `K8S` | `k8s` | `plugins/flink/templates/flink-k8s-runtime.yml` | Flink Native Kubernetes Application submit spec | 只定义契约，暂不实现 |
| `K8S_OPERATOR` | `k8s_operator` | `plugins/flink/templates/flink-k8s-operator-deployment.yml` | Secret + `FlinkDeployment` | 优先实现；集群 Operator 版本固定 `flink-kubernetes-operator:1.14.0` |

## 3. TaskRequest.pluginParam

`pluginParam` 是插件级运行配置，来源于 Manager 侧 `system_plugin_config.plugin_param`，并由 Manager 把
`PluginConfigEntity.runMode` 注入到 `pluginParam.runMode`。Agent 不从 `taskData.runMode` 或配置文件推断运行模式。

### 3.1 FlinkPluginParam

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 是 | 无 | 支持 `local`、`standalone`、`yarn`、`k8s`、`k8s_operator`；Agent 内部归一为大写枚举 |
| `jobName` | `String` | 否 | `taskName` 或 `flink-task-{taskInstanceId}` | Flink 作业名 |
| `entryClass` | `String` | 条件必填 | 无 | Flink application main class；K8S / K8S_OPERATOR 可由 `kubernetes.entryClass` 覆盖 |
| `args` | `List<String>` | 否 | `["--config", "{{jobJsonMountPath}}"]` | 作业启动参数；Agent 自动替换配置文件路径占位 |
| `defaultTaskData` | `Object` | 否 | 空对象 | 默认 job config；与 `taskData` 深度合并后生成 `job.json` |
| `flinkConfiguration` | `Object<String,String>` | 否 | 空对象 | 插件级 Flink 配置，如 checkpoint、state backend、metrics 配置 |
| `parallelism` | `Integer` | 否 | 空 | 作业默认并行度；任务级可覆盖 |
| `logStorageUri` | `String` | 否 | 空 | 外挂日志存储 URI；未配置时 K8S 终态可采集 Pod 日志 |
| `kubernetes` | `FlinkKubernetesParam` | K8S / K8S_OPERATOR 条件必填 | 空 | Kubernetes 插件级运行配置 |

### 3.2 pluginParam 示例

```json
{
  "runMode": "k8s_operator",
  "jobName": "kafka-json-paimon",
  "entryClass": "com.datafusion.plugin.kafka.json.KafkaJsonPaimonApplication",
  "args": ["--config", "{{jobJsonMountPath}}"],
  "parallelism": 2,
  "flinkConfiguration": {
    "state.backend": "rocksdb",
    "execution.checkpointing.interval": "60s",
    "execution.checkpointing.mode": "AT_LEAST_ONCE",
    "state.checkpoints.dir": "s3://datafusion/flink/checkpoints/kafka-json",
    "state.savepoints.dir": "s3://datafusion/flink/savepoints/kafka-json"
  },
  "kubernetes": {
    "namespace": "datafusion",
    "image": "flink:2.2.0-scala_2.12-java17",
    "imagePlatform": "linux/amd64",
    "operatorVersion": "flink-kubernetes-operator:1.14.0",
    "flinkVersion": "v2_2",
    "buildFlinkVersion": "2.2.0",
    "artifactMode": "FAT_JAR",
    "mavenProfile": "flink-fat-jar",
    "jarURI": "local:///opt/flink/usrlib/datafusion-plugin-kafka-json-1.0.0-executable.jar",
    "flinkAppName": "datafusion-plugin-kafka-json",
    "flinkAppDir": "/opt/datafusion/plugins/flink/datafusion-plugin-kafka-json",
    "flinkAppMainJar": "datafusion-plugin-kafka-json-1.0.0-executable.jar",
    "builderScriptPath": "datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/builder/build-flink-plugin.sh",
    "agentFlinkAppDir": "datafusion-agent/src/main/resources/plugins/flink/datafusion-plugin-kafka-json",
    "sharedPvcName": "datafusion-shared-data",
    "sharedFlinkAppUri": "pvc://datafusion/datafusion-shared-data/opt/datafusion/plugins/flink/datafusion-plugin-kafka-json",
    "usrlibPath": "/opt/flink/usrlib",
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
| `flinkConfiguration` | `Object<String,String>` | 否 | 空 | 单次任务追加或覆盖 Flink 配置 |
| `kubernetes` | `FlinkKubernetesTaskOverride` | 否 | 空 | K8S / K8S_OPERATOR 单任务覆盖项 |

`taskData` 规则：

- `taskData.jobJson` 存在时直接作为最终 `job.json`。
- `taskData.jobJson` 为空时，使用 `deepMerge(pluginParam.defaultTaskData, taskData)` 生成 `job.json`。
- `bizRef`、`data`、`options`、`kubernetes`、`args`、`parallelism`、`flinkConfiguration` 是 Agent 侧注册或运行元数据，不写入最终 `job.json`。
- 数组按整体替换；普通值按 `taskData` 覆盖默认值。

## 5. 运行模式枚举

| 枚举 | 配置值 | 首版实现 | 说明 |
|------|--------|----------|------|
| `LOCAL` | `local` | 否 | 本地 Flink MiniCluster 或本地 CLI，后续用于开发和单机调试 |
| `STANDALONE` | `standalone` | 否 | 提交到已有 Flink Standalone 集群，后续通过 REST 或 CLI 实现 |
| `YARN` | `yarn` | 否 | 提交到 YARN Application / Per-Job，后续通过 Flink CLI 或 REST 实现 |
| `K8S` | `k8s` | 否 | Flink Native Kubernetes Application 模式，后续实现 |
| `K8S_OPERATOR` | `k8s_operator` | 是 | Flink Kubernetes Operator `FlinkDeployment` 模式，首版优先实现 |

## 6. Kubernetes 参数

`pluginParam.kubernetes` 提供插件级默认值，`taskData.kubernetes` 提供单任务覆盖值。两者字段结构相同，任务级字段优先。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `namespace` | `String` | 否 | `default` | Kubernetes namespace |
| `image` | `String` | K8S / K8S_OPERATOR 必填 | `flink:2.2.0-scala_2.12-java17` | Flink runtime 基础镜像；业务 jar 和依赖不打入镜像；首版与 `flinkVersion=v2_2`、`buildFlinkVersion=2.2.0` 成组使用 |
| `imagePlatform` | `String` | 否 | `linux/amd64` | 当前仅支持 amd64；Kubernetes 调度需限制到 amd64 节点 |
| `imagePullPolicy` | `String` | 否 | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 否 | 空 | Flink Pod 使用的 ServiceAccount |
| `flinkHome` | `String` | 否 | `/opt/flink` | Flink home；`flink:2.2.0-scala_2.12-java17` 镜像内 `FLINK_HOME=/opt/flink`，K8S Native Application 后续也复用该路径 |
| `flinkBin` | `String` | K8S 条件必填 | `${flinkHome}/bin/flink` | Agent 提交 K8S Native Application 时使用的 Flink CLI |
| `artifactMode` | `String` | K8S_OPERATOR 必填 | `FAT_JAR` | 依赖发布模式，支持 `FAT_JAR` 和 `THIN_JAR_LIB` |
| `mavenProfile` | `String` | K8S_OPERATOR 必填 | `flink-fat-jar` | 构建 profile；`flink-fat-jar` 产出 fat jar，`flink-thin-lib` 产出 thin jar + lib |
| `buildFlinkVersion` | `String` | K8S_OPERATOR 必填 | `2.2.0` | 构建插件 jar 使用的 Flink 依赖版本；首版必须和 runtime image、`flinkVersion` 对齐 |
| `jarURI` | `String` | K8S 必填；K8S_OPERATOR 可派生 | `local:///opt/flink/usrlib/datafusion-plugin-kafka-json-1.0.0-executable.jar` | Flink job jar URI，K8S_OPERATOR 默认由 `usrlibPath + flinkAppMainJar` 派生 |
| `flinkAppName` | `String` | K8S_OPERATOR 必填 | `datafusion-plugin-kafka-json` | Flink app 发布包名，建议使用 Maven `artifactId`；不是 agent 运行模式概念 |
| `flinkAppDir` | `String` | K8S_OPERATOR 必填 | `/opt/datafusion/plugins/flink/datafusion-plugin-kafka-json` | Flink Pod 内共享盘 app 目录，表示完整 Flink application 发布包目录 |
| `flinkAppMainJar` | `String` | K8S_OPERATOR 必填 | `datafusion-plugin-kafka-json-1.0.0-executable.jar` | 本次执行的主业务 jar 文件名；thin 模式可为 `datafusion-plugin-kafka-json-1.0.0.jar` |
| `builderScriptPath` | `String` | K8S_OPERATOR 必填 | `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/builder/build-flink-plugin.sh` | 插件模块内 builder 入口脚本；负责调用 Maven profile 并部署产物到 agent 插件目录 |
| `agentFlinkAppDir` | `String` | K8S_OPERATOR 必填 | `datafusion-agent/src/main/resources/plugins/flink/datafusion-plugin-kafka-json` | agent 随包准备的单个 Flink app 目录；fat 模式主 jar 在 app 根目录且 `lib/` 为空，thin 模式主 jar 在 app 根目录且依赖在 `lib/` |
| `sharedPvcName` | `String` | K8S_OPERATOR 必填 | `datafusion-shared-data` | 共享 PVC 名称，定义见 `docs/k8s/datafusion-common-pvc.yml` |
| `sharedFlinkAppUri` | `String` | K8S_OPERATOR 必填 | `pvc://datafusion/datafusion-shared-data/opt/datafusion/plugins/flink/datafusion-plugin-kafka-json` | 共享盘上的单个 Flink app 目录 URI；由整体 `plugins/` 目录上传后形成 |
| `usrlibPath` | `String` | K8S_OPERATOR 必填 | `/opt/flink/usrlib` | initContainer 拷贝依赖的目标目录，Flink main container 从这里加载用户 jar |
| `flinkWebUiUriTemplate` | `String` | 否 | `http://{{deploymentName}}-rest.{{namespace}}.svc:8081` | Flink Web UI 地址模板；支持 `namespace`、`deploymentName`、`appId` 占位，提交后写入结果 `flinkWebUiUri` |
| `entryClass` | `String` | 条件必填 | 外层 `entryClass` | Application main class |
| `args` | `List<String>` | 否 | 外层 `args` | Application 参数 |
| `parallelism` | `Integer` | 否 | 外层 `parallelism` | 作业并行度 |
| `clusterIdPrefix` | `String` | 否 | `df-flink-` | K8S Native Application cluster id 前缀 |
| `deploymentNamePrefix` | `String` | 否 | `df-flink-` | K8S_OPERATOR `FlinkDeployment` 名称前缀 |
| `secretNamePrefix` | `String` | 否 | `df-flink-job-` | 承载 job JSON 的 Secret 名称前缀 |
| `jobJsonSecretKey` | `String` | 否 | `job.json` | Secret key |
| `jobJsonMountPath` | `String` | 否 | `/opt/datafusion/flink/job/job.json` | 容器内 job JSON 文件路径 |
| `labels` | `Object<String,String>` | 否 | 空 | 附加 label，不允许覆盖 `datafusion.*` 和 `datafusion.io/*` 保留 label |
| `annotations` | `Object<String,String>` | 否 | 空 | 附加 annotation |
| `env` | `Object<String,String>` | 否 | 空 | Flink container 环境变量；Secret 和 job 路径相关变量由 Agent 派生 |
| `flinkConfiguration` | `Object<String,String>` | 否 | 空 | Kubernetes 级 Flink 配置，和外层配置合并 |
| `jobManager` | `Object` | 否 | 空 | JobManager resource / pod 配置 |
| `taskManager` | `Object` | 否 | 空 | TaskManager resource / pod 配置 |
| `nodeSelector` | `Object<String,String>` | 否 | `{"kubernetes.io/arch":"amd64"}` | Pod nodeSelector；当前仅支持 amd64 |
| `logStorageUri` | `String` | 否 | 外层 `logStorageUri` | 外挂日志存储 URI |
| `collectLogsOnFinish` | `Boolean` | 否 | `true` | 未配置外挂日志存储时，终态是否拉取 Pod 日志到任务运行目录 |
| `deleteClusterOnFinish` | `Boolean` | 否 | `false` | K8S finish 时是否主动删除 native cluster 资源 |
| `deleteDeploymentOnFinish` | `Boolean` | 否 | `false` | K8S_OPERATOR finish 时是否删除 `FlinkDeployment` |
| `deleteSecretOnFinish` | `Boolean` | 否 | `true` | finish 时是否删除本次任务 Secret |
| `operatorVersion` | `String` | K8S_OPERATOR 必填 | `flink-kubernetes-operator:1.14.0` | 目标集群已安装的 Flink Kubernetes Operator 版本，只用于校验和文档记录，不写入 `FlinkDeployment.spec` |
| `flinkVersion` | `String` | K8S_OPERATOR 必填 | `v2_2` | Flink Operator `spec.flinkVersion`，与 Flink runtime `2.2.0` 对齐 |
| `upgradeMode` | `String` | K8S_OPERATOR 可选 | `stateless` | Flink Operator `spec.job.upgradeMode` |
| `podTemplate` | `Object` | K8S_OPERATOR 可选 | 空 | Flink Operator podTemplate 透传结构 |

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
| `FlinkRunMode` | 运行模式枚举 | `LOCAL`, `STANDALONE`, `YARN`, `K8S`, `K8S_OPERATOR` | enum | 请求解析后固定 | 配置值小写，Agent 内部大写 |
| `FlinkExecutionParam` | 提交前归一化参数 | `runMode`, `jobName`, `entryClass`, `args`, `jobJson`, `effectiveTaskData`, `workDir`, `flinkConfiguration`, `parallelism`, `kubernetes` | Java object | 单次任务 | 从 `pluginParam` 和 `taskData` 归一化 |
| `PluginTypeManifest` | 插件类型目录清单 | `pluginType`, `multiApp`, `appDirPattern`, `appNameField`, `builderDirPolicy` | JSON/YAML | 随 agent 插件目录发布 | 描述 `plugins/{pluginType}` 是否支持多个 app 包；Flink 为 `multiApp=true`，DataX 可为 `multiApp=false` |
| `FlinkAppManifest` | 单个 Flink app 清单 | `flinkAppName`, `artifactMode`, `flinkAppMainJar`, `libDir`, `confDir`, `jobsDir`, `buildFlinkVersion` | JSON/YAML | 随单个 Flink app 发布 | builder 输出后生成或校验，便于 agent 和发布脚本确认主 jar、依赖目录和版本 |
| `FlinkKubernetesParam` | K8S / K8S_OPERATOR 提交参数 | `namespace`, `clusterId`, `deploymentName`, `secretName`, `image`, `imagePlatform`, `artifactMode`, `mavenProfile`, `buildFlinkVersion`, `jarURI`, `flinkAppName`, `flinkAppDir`, `flinkAppMainJar`, `entryClass`, `args`, `jobJsonMountPath`, `flinkConfiguration`, `operatorVersion`, `flinkVersion`, `upgradeMode`, `builderScriptPath`, `agentFlinkAppDir`, `sharedPvcName`, `sharedFlinkAppUri`, `usrlibPath`, `flinkWebUiUriTemplate` | Java object | 单次任务 | 按运行模式渲染 native submit spec 或 `FlinkDeployment` |
| `FlinkKubernetesRuntimeRef` | K8S / K8S_OPERATOR 接管参数 | `namespace`, `clusterId`, `deploymentName`, `secretName`, `podLabelSelector`, `logStorageUri`, `flinkWebUiUri`, `collectLogsOnFinish`, `deleteClusterOnFinish`, `deleteDeploymentOnFinish`, `deleteSecretOnFinish` | Java object | 单次状态查询或控制命令 | 由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建 |
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
