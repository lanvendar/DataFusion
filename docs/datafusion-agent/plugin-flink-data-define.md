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
| `K8S_OPERATOR` | `plugins/flink/templates/flink-k8s-operator-deployment.yml` | `FlinkDeployment` | 优先实现；集群 Operator 版本固定 `flink-kubernetes-operator:1.14.0` |

## 3. TaskRequest.pluginParam

`pluginParam` 是插件级运行配置，来源于 Manager 侧 `system_plugin_config.plugin_param`，并由 Manager 把
`PluginConfigEntity.runMode` 注入到 `pluginParam.runMode`。`runMode` 与现有规范一致，使用大写枚举值。
Agent 不从 `taskData.runMode` 或配置文件推断运行模式。

### 3.1 FlinkPluginParam

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 是 | 无 | 支持 `LOCAL`、`STANDALONE`、`YARN`、`K8S`、`K8S_OPERATOR` |
| `flinkAppDir` | `String` | K8S_OPERATOR 必填 | `/opt/datafusion/plugins/flink/datafusion-plugin-flink-table` | Flink Pod 内共享盘 app 目录；目录下保存主 jar 和依赖目录 |
| `launchMode` | `String` | 否 | `JAR` | 启动模式，首版只实现 `JAR`；`CLASSPATH` 预留给后续无主 jar 或多 classpath 启动 |
| `flinkAppJar` | `String` | K8S_OPERATOR 且 `launchMode=JAR` 必填 | 无 | app 主 jar 文件名，用于派生 `jarURI=local:///opt/flink/usrlib/{flinkAppJar}` |
| `classpath` | `String` | 否 | 空 | 额外 classpath 表达式；首版 `JAR` 模式默认不使用，后续 `CLASSPATH` 模式或特殊依赖顺序场景使用 |
| `mainClass` | `String` | K8S_OPERATOR 必填 | 无 | Flink application main class；渲染到 `FlinkDeployment.spec.job.entryClass` |
| `flinkVersion` | `String` | K8S_OPERATOR 必填 | `2.2.0` | app 构建和 runtime Flink 版本，必须和 runtime image / Operator `spec.flinkVersion` 对齐 |
| `libDir` | `String` | 否 | `lib` | 共享盘 app 目录下的依赖目录；initContainer 从该目录复制依赖 jar |
| `flinkCheckpointRootDir` | `String` | 否 | `s3a://data-lake-warehouse/flink` | Agent 派生 checkpoint / savepoint Flink 路径参数的根目录 |
| `defaultTaskData` | `Object` | 否 | 空对象 | 默认 job config；与 `taskData` 深度合并后生成 `job.json` |
| `flinkConfig` | `Object<String,String>` | 否 | 空对象 | 插件级 Flink 配置，如 checkpoint、state backend、metrics 配置 |
| `logStorageUri` | `String` | 否 | 空 | 外挂日志存储 URI；未配置时 K8S 终态可采集 Pod 日志 |
| `kubernetes` | `FlinkKubernetesParam` | K8S / K8S_OPERATOR 条件必填 | 空 | Kubernetes 插件级运行配置 |

### 3.2 pluginParam 示例

```json
{
  "runMode": "K8S_OPERATOR",
  "flinkAppDir": "/opt/datafusion/plugins/flink/datafusion-plugin-flink-table",
  "launchMode": "JAR",
  "flinkAppJar": "datafusion-plugin-flink-table-1.0.0-executable.jar",
  "classpath": "",
  "mainClass": "com.datafusion.plugin.flink.table.FlinkTablePaimonApplication",
  "flinkVersion": "2.2.0",
  "libDir": "lib",
  "flinkCheckpointRootDir": "s3a://data-lake-warehouse/flink",
  "flinkConfig": {
    "state.backend": "rocksdb",
    "parallelism.default": "2",
    "execution.checkpointing.interval": "60s",
    "execution.checkpointing.mode": "AT_LEAST_ONCE",
    "fs.s3a.endpoint": "172.26.185.200",
    "fs.s3a.path.style.access": "true",
    "fs.s3a.connection.ssl.enabled": "false",
    "fs.s3a.aws.credentials.provider": "com.amazonaws.auth.EnvironmentVariableCredentialsProvider"
  },
  "kubernetes": {
    "namespace": "datafusion",
    "image": "flink:2.2.0-scala_2.12-java17",
    "sharedPvcName": "datafusion-shared-data",
    "serviceAccountName": "flink",
    "env": {
      "HADOOP_CONF_DIR": "/opt/flink/conf"
    },
    "envFrom": [
      {
        "secretRef": {
          "name": "flink-objectstore"
        }
      }
    ],
    "jobManager": {
      "replicas": 1,
      "resource": {
        "memory": "2048m",
        "cpu": 1.0
      }
    },
    "taskManager": {
      "replicas": 1,
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
    "flinkConfig": {},
    "sink": {},
    "bizRef": ""
  }
}
```

`defaultTaskData.flinkConfig` 默认可以为空；它用于任务模板层的可调 Flink 配置，优先级高于插件级
`pluginParam.flinkConfig`。例如插件级可以固定 state backend，任务模板层或单次任务再按任务类型设置并行度、
checkpoint 和 savepoint 目录。

`flinkAppDir` 是 Pod 内共享盘 app 目录，例如 `/opt/datafusion/plugins/flink/datafusion-plugin-flink-table`。
Agent 会从该路径反推出共享 PVC 挂载点：如果路径包含 `/plugins/`，默认把 PVC 挂载到 `/plugins/`
之前的目录，例如 `/opt/datafusion`。

`libDir` 和 `classpath` 的职责不同：

- `libDir` 是 `flinkAppDir` 下的依赖目录，例如 `/opt/datafusion/plugins/flink/datafusion-plugin-flink-table/lib`；
  initContainer 根据它把依赖 jar 复制到 `/opt/flink/usrlib`。
- `classpath` 是额外传给 Flink runtime 的 classpath 表达式。首版 `launchMode=JAR` 下依赖统一通过
  `/opt/flink/usrlib` 被 Flink 发现，因此 `classpath` 默认留空，不作为依赖目录配置入口。
- 后续如果支持 `launchMode=CLASSPATH`，或需要显式控制 classpath 顺序，再启用 `classpath`。

有效 Flink 配置合并顺序：

```text
pluginParam.flinkConfig
    -> effectiveTaskData.flinkConfig
```

其中 `effectiveTaskData` 由 `pluginParam.defaultTaskData` 和 `taskData` 深度合并得到；如果 `taskData.jobJson`
存在，则以 `taskData.jobJson` 解析结果作为 `effectiveTaskData`。后者覆盖前者。合并后的 `flinkConfig`
同时写回 `effectiveTaskData.flinkConfig`，写入运行目录 `flink-job.json` 快照，并通过 `--job`
参数传给业务 main class，同时用于渲染 `FlinkDeployment.spec.flinkConfiguration`。因此运行时只存在两个 Flink 配置来源：插件级
`pluginParam.flinkConfig` 和任务级 `effectiveTaskData.flinkConfig`。

Agent 会按 `taskData.job.id` 派生 checkpoint / savepoint 目录，并覆盖旧模板中的固定路径：

```text
s3a://data-lake-warehouse/flink/checkpoints/{jobId}
s3a://data-lake-warehouse/flink/savepoints/{jobId}
```

如果 `sink.options` 中存在非密钥类 `s3.*` 配置，Agent 会把它们映射为 Flink S3A 参数补充到
`FlinkDeployment.spec.flinkConfiguration`，例如 `s3.endpoint -> fs.s3a.endpoint`。access key / secret key
应通过 `kubernetes.envFrom.secretRef` 暴露给 Flink Pod。

完整示例中 `flinkConfig` 合并后的效果：

```json
{
  "flinkConfig": {
    "state.backend": "rocksdb",
    "execution.checkpointing.interval": "60s",
    "execution.checkpointing.mode": "AT_LEAST_ONCE",
    "execution.checkpointing.dir": "s3a://data-lake-warehouse/flink/checkpoints/kafka-json-job",
    "state.checkpoints.dir": "s3a://data-lake-warehouse/flink/checkpoints/kafka-json-job",
    "state.savepoints.dir": "s3a://data-lake-warehouse/flink/savepoints/kafka-json-job",
    "parallelism.default": "2"
  }
}
```

## 4. TaskRequest.taskData

`taskData` 是本次任务执行数据，来源于任务定义和运行实例上下文。它保存本次 Flink job config，以及允许单次任务覆盖的运行参数。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `jobJson` | `String` 或 `Object` | 否 | 空 | 完整 Flink 作业配置 JSON；存在时优先作为 `effectiveTaskData`，并写入任务运行目录快照 |
| `job` | `Object` | 否 | 空 | 作业元信息；与 `pluginParam.defaultTaskData.job` 深度合并 |
| `source` | `Object` | 否 | 空 | Flink 作业 source 配置；与 `pluginParam.defaultTaskData.source` 深度合并 |
| `flinkConfig` | `Object<String,String>` | 否 | 空 | 任务级 Flink 配置；与 `pluginParam.defaultTaskData.flinkConfig` 深度合并后参与 Flink 配置合并 |
| `sink` | `Object` | 否 | 空 | Flink 作业 sink 配置；与 `pluginParam.defaultTaskData.sink` 深度合并 |
| `bizRef` | `String` 或 `Object` | 否 | 空 | 业务引用信息；与 `pluginParam.defaultTaskData.bizRef` 合并或覆盖 |
| `args` | `List<String>` | 否 | 空 | 单次任务覆盖作业参数 |
| `kubernetes` | `FlinkKubernetesTaskOverride` | 否 | 空 | K8S / K8S_OPERATOR 单任务覆盖项 |

`taskData` 规则：

- `taskData.jobJson` 存在时先解析为 `effectiveTaskData`；`taskData.jobJson` 必须使用同一套 `job` / `source` /
  `flinkConfig` / `sink` / `bizRef` 顶层结构。
- `taskData.jobJson` 为空时，使用 `deepMerge(pluginParam.defaultTaskData, taskData)` 生成 `effectiveTaskData`。
- `effectiveTaskData.flinkConfig` 覆盖 `pluginParam.flinkConfig`；合并结果写回 `job.json` 的
  顶层 `flinkConfig`，并渲染到 `FlinkDeployment.spec.flinkConfiguration`。
- `args`、`kubernetes` 是 Agent 侧运行元数据，不写入最终 `job.json`。
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

`pluginParam.kubernetes` 只保存 Kubernetes 运行时参数。构建 profile、主 jar、artifact mode、共享盘 app 目录、
`jarURI` 等发布细节由 `pluginParam` 和固定运行约定派生，不依赖运行时读取 app 发布包清单。

`taskData.kubernetes` 提供单任务覆盖值，只允许覆盖下表中明确可覆盖的运行时字段。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `namespace` | `String` | 否 | `default` | Kubernetes namespace；必须被 Flink Operator watch，并且 namespace 内存在所需 SA / 运行 Secret / PVC |
| `image` | `String` | K8S / K8S_OPERATOR 必填 | `flink:2.2.0-scala_2.12-java17` | Flink runtime 基础镜像；业务 jar 和依赖不打入镜像；首版与 `pluginParam.flinkVersion=2.2.0`、Operator `spec.flinkVersion=v2_2` 成组使用 |
| `imagePullPolicy` | `String` | 否 | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 否 | 空 | Flink Pod 使用的 ServiceAccount |
| `sharedPvcName` | `String` | K8S_OPERATOR 必填 | `datafusion-shared-data` | 共享 PVC 名称，定义见 `docs/k8s/datafusion-common-pvc.yml` |
| `flinkWebUiUriTemplate` | `String` | 否 | `http://{{serviceName}}.{{namespace}}.svc:8081` | Flink Web UI 地址模板；仅作为任务级高级覆盖项，插件模板默认不配置 |
| `labels` | `Object<String,String>` | 否 | 空 | 附加 label，不允许覆盖 `datafusion.*` 和 `datafusion.io/*` 保留 label |
| `annotations` | `Object<String,String>` | 否 | 空 | 附加 annotation |
| `env` | `Object<String,String>` | 否 | 空 | Flink container 环境变量 |
| `envFrom` | `Array<Object>` | 否 | 空 | Flink container `envFrom`；默认用于引用 `flink-objectstore` Secret，Secret 必须与作业在同一 namespace |
| `jobManager` | `Object` | 否 | 空 | JobManager resource / pod 配置 |
| `taskManager` | `Object` | 否 | 空 | TaskManager resource / pod 配置 |
| `nodeSelector` | `Object<String,String>` | 否 | `{"kubernetes.io/arch":"amd64"}` | Pod nodeSelector；当前仅支持 amd64 |
| `collectLogsOnFinish` | `Boolean` | 否 | `true` | 未配置外挂日志存储时，终态是否拉取 Pod 日志到任务运行目录 |
| `deleteDeploymentOnFinish` | `Boolean` | 否 | `false` | K8S_OPERATOR finish 时是否删除 `FlinkDeployment` |
| `upgradeMode` | `String` | K8S_OPERATOR 可选 | `stateless` | Flink Operator `spec.job.upgradeMode` |
| `podTemplate` | `Object` | K8S_OPERATOR 可选 | 空 | 受限 podTemplate 覆盖项；只允许追加安全字段，不允许覆盖 Agent 生成的 volume、volumeMount、initContainer |

`jobManager.resource.cpu` 和 `taskManager.resource.cpu` 渲染为 Flink Operator CRD 数字字段，例如 `1.0`；
`memory` 仍按字符串渲染，例如 `"2048m"`。

派生字段和固定约定：

| 派生项 | 来源 | 说明 |
|--------|------|------|
| `operatorVersion` | 固定约定 | 首版目标集群为 `flink-kubernetes-operator:1.14.0`，仅用于环境校验 |
| `operatorFlinkVersion` | `pluginParam.flinkVersion` | `2.2.0` 映射为 `FlinkDeployment.spec.flinkVersion=v2_2` |
| `jarURI` | `pluginParam.flinkAppJar` | `local:///opt/flink/usrlib/{flinkAppJar}` |
| `flinkAppDir` | `pluginParam.flinkAppDir` | Pod 内共享盘 app 目录，例如 `/opt/datafusion/plugins/flink/datafusion-plugin-flink-table` |
| `sharedMountPath` | `pluginParam.flinkAppDir` | 如果 `flinkAppDir` 包含 `/plugins/`，挂载点为 `/plugins/` 前缀目录；否则使用 `flinkAppDir` 父目录 |
| `usrlibPath` | 固定约定 | `/opt/flink/usrlib` |
| `deploymentNamePrefix` | 固定约定 | `df-flink-` |
| `jobArg` | 固定约定 | `--job <base64(job-json)>` |
| `imagePlatform` | 固定约定 | 当前只支持 `linux/amd64`，通过 `nodeSelector` 或等价调度约束表达 |
| `mavenProfile` / `builderScriptPath` / `agentFlinkAppDir` | 构建发布流程 | 只属于 builder，不进入运行时参数 |

## 7. 插件目录约定

Flink 是多 app 插件类型，agent 发布侧目录固定为：

```text
plugins/flink/{appDirName}/
plugins/flink/{appDirName}/{flinkAppJar}
plugins/flink/{appDirName}/{libDir}/
```

agent 发布侧仍按 `plugins/flink/{appDirName}/` 组织；运行侧由 `pluginParam.flinkAppDir` 直接指定 Pod 内目录。
`flinkAppJar` 和 `libDir` 均来自 `pluginParam`。

## 8. Service / Runtime 模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|------|------|------|----------|----------|------|
| `FlinkRunMode` | 运行模式枚举 | `LOCAL`, `STANDALONE`, `YARN`, `K8S`, `K8S_OPERATOR` | enum | 请求解析后固定 | 与现有规范一致，配置和状态均使用大写枚举 |
| `FlinkExecutionParam` | 提交前归一化参数 | `runMode`, `args`, `jobJson`, `effectiveTaskData`, `workDir`, `flinkConfig`, `flinkAppDir`, `launchMode`, `flinkAppJar`, `classpath`, `mainClass`, `flinkVersion`, `libDir`, `kubernetes` | Java object | 单次任务 | 从 `pluginParam` 和 `taskData` 归一化 |
| `FlinkKubernetesParam` | K8S / K8S_OPERATOR 提交参数 | `namespace`, `deploymentName`, `image`, `sharedPvcName`, `sharedMountPath`, `upgradeMode`, `flinkWebUiUri` | Java object | 单次任务 | 只保存 Kubernetes 运行时字段；构建和 jar 路径由 `pluginParam` / 固定约定派生 |
| `FlinkKubernetesRuntimeRef` | K8S / K8S_OPERATOR 接管参数 | `namespace`, `deploymentName`, `podLabelSelector`, `logStorageUri`, `flinkWebUiUri`, `collectLogsOnFinish`, `deleteDeploymentOnFinish` | Java object | 单次状态查询或控制命令 | 由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建 |
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
