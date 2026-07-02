# Flink 插件设计

> 数据结构见 [plugin-flink-data-define.md](./plugin-flink-data-define.md)。Agent 总体设计见
> [agent-design.md](./agent-design.md)。

## 1. 定位

`FLINK` 插件在 agent 中统一承接 Flink 作业提交、停止、强杀、状态映射和日志入口上报。

运行模式包括：

- `LOCAL`
- `STANDALONE`
- `YARN`
- `K8S`
- `K8S_OPERATOR`

首版优先实现 `K8S_OPERATOR`。`K8S_OPERATOR` 表示 Flink Kubernetes Operator 的
`FlinkDeployment` 模式；`K8S` 表示 Flink Native Kubernetes Application 模式，作为独立后续能力设计。

不新增 HTTP API，复用 agent 任务控制接口：

```text
POST /internal/scheduler/submitTask
POST /internal/scheduler/stopTask
POST /internal/scheduler/killTask
POST /internal/scheduler/finishTask
```

参数只来自 `TaskRequest.pluginParam` 和 `TaskRequest.taskData`。`application.yml` 不定义
`datafusion.agent.flink.*`。

## 2. 总体链路

```text
Manager scheduler
    -> TaskRequest(pluginType=FLINK, taskData, pluginParam)
    -> AgentExecutorRpcProvider
    -> WorkerTaskService
    -> FlinkPluginTaskExecutor
    -> FlinkTaskRunner(K8S_OPERATOR first)
    -> job.json snapshot + Secret
    -> FlinkDeployment template
    -> Fabric8 createOrReplace
    -> WorkerTaskExecutionStore
    -> AgentTaskStateReportScheduler
    -> ManagerTaskResultReporter
```

`WorkerTaskOperatorRouter` 仍按 `pluginType -> PluginTaskExecutor` 一对一映射，因此 Flink 使用一个
`FlinkPluginTaskExecutor` 注册 `pluginType=FLINK`，执行器内部按 `pluginParam.runMode` 分派到不同 runner。

状态映射按 `pluginType + runMode` 注册。首版只注册：

- `FLINK + K8S_OPERATOR`

`LOCAL`、`STANDALONE`、`YARN`、`K8S` 先保留枚举、参数解析和错误提示，不注册可执行 runner。

## 3. K8S_OPERATOR 首版方案

### 3.1 提交方式

首版 `K8S_OPERATOR` 使用 Flink Kubernetes Operator 的 `FlinkDeployment` Application 模式。

选择该方案的边界：

- 目标集群已安装 `flink-kubernetes-operator:1.14.0`。
- 目标集群已安装 `flink.apache.org/v1beta1` `FlinkDeployment` CRD。
- Flink runtime 镜像固定使用 `--platform=linux/amd64 flink:2.2.0-scala_2.12-java17`。
- 当前只支持 amd64，`FlinkDeployment` 必须通过 `nodeSelector` 或等价调度约束落到 amd64 节点。
- 插件构建 profile 必须把 Flink 构建依赖锁定到 `2.2.0` 体系，并与 runtime image、Operator
  `spec.flinkVersion=v2_2` 成组校验。
- 业务 jar 和依赖不打入镜像，统一由
  `datafusion-agent/src/main/resources/plugins/flink/{flinkAppName}/` 随 agent 插件目录准备。
- 首版支持两种发布形态：`FAT_JAR` 和 `THIN_JAR_LIB`，由 Maven profile 控制。
- `flink:2.2.0-scala_2.12-java17` 镜像内 `FLINK_HOME=/opt/flink`，容器默认工作目录也是 `/opt/flink`。
- 构建、agent 发布目录、manifest、共享盘目录和模板变量遵循
  [agent-design.md](./agent-design.md) 的“插件部署规范”。
- 共享 PVC 中 Flink 类型根目录推荐为 `/opt/datafusion/plugins/flink/`，单个 app 目录为
  `/opt/datafusion/plugins/flink/{flinkAppName}/`。
- `FlinkDeployment` 通过 `initContainers` 从共享盘 Flink 插件目录抽取 jar 到 `/opt/flink/usrlib/`，主容器直接执行
  `flinkAppMainJar` 指定的 jar。
- agent 不需要内置 Flink CLI，使用 Fabric8 创建 Secret 和 `FlinkDeployment`。
- agent 通过 Kubernetes API 查询 `FlinkDeployment` / Pod 状态。

K8S_OPERATOR 集群前置条件：

- agent 能访问 Kubernetes API。
- agent ServiceAccount 有目标 namespace 内 Secret、Pod、Pod log 和 `flinkdeployments.flink.apache.org` 的必要权限。
- Flink application 镜像为官方 Flink Java17 基础镜像，业务 jar 和依赖由共享盘 + initContainer 注入。
- 共享盘使用 `docs/k8s/datafusion-common-pvc.yml` 中的 `datafusion-shared-data` PVC。
- 共享盘插件目录由人工上传；Flink JobManager / TaskManager Pod 以只读方式挂载。

### 3.2 K8S_OPERATOR 提交流程

```text
解析参数
    -> 生成 FlinkDeployment / Secret 名称
    -> 在任务运行目录生成 job.json 快照
    -> 读取 plugins/flink/plugin-manifest.json 和 plugins/flink/{flinkAppName}/flink-app-manifest.json
    -> 派生 flinkAppDir、flinkAppMainJar、artifactMode、jarURI
    -> 校验共享盘 app 目录下 flinkAppMainJar 存在
    -> 校验共享盘 app 目录/lib 目录和 artifactMode 匹配
    -> 校验 image、buildFlinkVersion、flinkVersion、operatorVersion 固定组合匹配
    -> 校验当前架构约束包含 amd64 调度条件
    -> 读取任务运行目录/job.json 生成 Secret data.job.json
    -> 渲染 plugins/flink/templates/flink-k8s-operator-deployment.yml
    -> Fabric8 load(renderedYaml).createOrReplace()
    -> appId = FlinkDeployment name
    -> 按 flinkWebUiUriTemplate 生成 flinkWebUiUri
    -> 写 WorkerTaskExecutionSnap(...)
    -> 写 WorkerTaskExecutionState(status=RUNNING, appId=deploymentName, workDirPath=任务运行目录, result.pluginLogUri=插件日志入口, result.flinkWebUiUri=Flink Web UI 地址)
```

K8S_OPERATOR 资源约定：

- `FlinkDeployment` name 由 `deploymentNamePrefix + taskInstanceId` 生成，并做 DNS-1123 截断。
- label 必须包含 `datafusion.io/plugin-type=FLINK`、`datafusion.io/run-mode=K8S_OPERATOR`、
  `datafusion.io/task-instance-id`、`datafusion.io/flow-instance-id`。
- `job.json` 先落盘到 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/job.json`，
  再读取该快照写入 Secret，不用 ConfigMap。
- `plugins/flink/` 是 agent 随包的 Flink 类型根目录，可以承载多个 Flink app 发布包。
- `plugins/flink/{flinkAppName}/` 是单个 Flink application 发布包目录，`flinkAppName` 建议使用 Maven
  `artifactId`，例如 `datafusion-plugin-kafka-json`、`datafusion-plugin-flink-sql`。
- `FAT_JAR` 模式下，`plugins/flink/{flinkAppName}/{flinkAppMainJar}` 是 executable fat jar，
  `plugins/flink/{flinkAppName}/lib/` 为空目录。
- `THIN_JAR_LIB` 模式下，`plugins/flink/{flinkAppName}/{flinkAppMainJar}` 是 thin jar，
  `plugins/flink/{flinkAppName}/lib/` 保存需要随作业发布的依赖。
- 共享盘 Flink 类型根目录推荐为 `/opt/datafusion/plugins/flink/`，单个 app 目录推荐为
  `/opt/datafusion/plugins/flink/{flinkAppName}/`。
- 首版不要求依赖目录不可变；插件开发保证向下兼容。运行中任务重启时允许读取同一路径下较新的兼容依赖。
- `FlinkDeployment` 使用 `emptyDir` 挂载 `/opt/flink/usrlib`，并用 initContainer 执行
  `cp -a {flinkAppDir}/*.jar /opt/flink/usrlib/` 和
  `cp -a {flinkAppDir}/lib/*.jar /opt/flink/usrlib/`；空 `lib/` 目录必须被允许。
- initContainer 只复制当前任务选中的 `{flinkAppName}` app 目录内容，不复制整个 `plugins/flink/`。
- 不把业务插件目录复制到 `/opt/flink/plugins/`。`/opt/flink/plugins` 是 Flink runtime plugin 目录，
  用于 metrics、filesystem、external resource 等 Flink 插件，直接拷入业务 jar 会引入类加载语义混淆；
  如果整体 `plugins/` 目录复制进去，还可能把 DataX、Shell、Spider 等非 Flink 插件带入 Flink runtime plugin 目录。
- 主容器镜像保持 `flink:2.2.0-scala_2.12-java17`，`jarURI` 指向 initContainer 拷贝后的主 jar。
- Secret 通过 `podTemplate` 挂载到 Flink JobManager / TaskManager container，默认容器内路径为
  `/opt/datafusion/flink/job/job.json`。
- 作业参数默认传入 `--config /opt/datafusion/flink/job/job.json`。
- `flinkWebUiUri` 默认按 `http://{deploymentName}-rest.{namespace}.svc:8081` 生成；如果配置
  `flinkWebUiUriTemplate`，按模板渲染 `namespace`、`deploymentName`、`appId` 占位。该地址只作为任务运行结果返回，
  agent 不做代理转发。
- 提交成功后只把 `FlinkDeployment` name 写入 `.state.appId`；namespace、secretName、podLabelSelector 等运行引用
  由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建。

## 4. K8S 后续方案

`K8S` 是独立运行模式，不是 `K8S_OPERATOR` 的别名。它使用 Flink Native Kubernetes Application 模式。
后续实现时，agent 在本地调用受控的 Flink CLI：

```text
flink run-application --target kubernetes-application
```

该模式后续实现时需要：

- agent 节点或镜像具备 Flink CLI。
- 独立注册 `FLINK + K8S` 状态映射。
- `appId` 使用 Flink native application cluster id。
- stop 可优先通过 Flink REST cancel job；kill 可删除 cluster Deployment / Service / Pod。

## 5. 参数契约

核心规则：

- `pluginParam.runMode` 必填，且只能由 manager 插件配置注入。
- `runMode` 与现有规范一致，配置和状态文件均使用大写枚举值。
- `K8S_OPERATOR` 镜像默认使用 `flink:2.2.0-scala_2.12-java17`，平台固定 `linux/amd64`，`flinkVersion=v2_2`。
- `K8S_OPERATOR` 运行参数只必须提供 `entryClass`、`flinkAppName`、`sharedPvcName`、`sharedPluginRoot` 等运行时字段。
- `operatorVersion`、`flinkVersion`、`buildFlinkVersion`、`artifactMode`、`flinkAppMainJar`、`jarURI`、`usrlibPath`
  由固定约定和 `flink-app-manifest.json` 派生，不放入 `pluginParam.kubernetes`。
- `flinkAppDir` 派生为 `{sharedPluginRoot}/flink/{flinkAppName}`；`/opt/flink/lib` 是 Flink runtime 自带目录，
  `/opt/flink/plugins` 是 Flink runtime plugin 目录，二者都不作为业务依赖挂载目标。
- `buildFlinkVersion=2.2.0`、runtime image `flink:2.2.0-scala_2.12-java17`、Operator `spec.flinkVersion=v2_2`
  必须成组出现。
- `FAT_JAR` 模式执行 `datafusion-plugin-kafka-json-1.0.0-executable.jar`；`THIN_JAR_LIB` 模式执行
  `datafusion-plugin-kafka-json-1.0.0.jar` 并同时上传独立依赖 jar。
- `K8S` 后续实现时必须提供 `jarURI`、`entryClass` 和 Flink CLI 路径，缺省 `flinkHome=/opt/flink`、`flinkBin=${flinkHome}/bin/flink`。
- Kubernetes 参数由 `taskData.kubernetes` 覆盖 `pluginParam.kubernetes`。
- `taskData` 只覆盖 `pluginParam.defaultTaskData` 生成 job config，不覆盖 `pluginParam` 外层启动参数。
- `flinkConfig` 按 `pluginParam.flinkConfig -> taskData.flinkConfig` 顺序合并，后者覆盖前者。
- 用户自定义 label 不允许覆盖 `datafusion.*` / `datafusion.io/*` 保留 label。

## 6. Flink 发布和依赖规则

首版依赖以 Flink 官方镜像内置 runtime 为基线，避免把 Flink runtime 重复放入 `/opt/flink/usrlib`。

### 6.1 公共部署规范引用

Flink 插件遵循 agent 公共插件部署规范：

```text
plugins/flink/plugin-manifest.json
plugins/flink/{flinkAppName}/flink-app-manifest.json
plugins/flink/{flinkAppName}/{flinkAppMainJar}
plugins/flink/{flinkAppName}/lib/
```

Flink 类型 manifest：

```json
{
  "pluginType": "flink",
  "multiApp": true,
  "appDirPattern": "plugins/flink/{flinkAppName}",
  "appNameField": "flinkAppName",
  "builderDirPolicy": "src/main/resources/builder"
}
```

单个 Flink app manifest：

```json
{
  "flinkAppName": "datafusion-plugin-kafka-json",
  "artifactMode": "FAT_JAR",
  "flinkAppMainJar": "datafusion-plugin-kafka-json-1.0.0-executable.jar",
  "libDir": "lib",
  "confDir": "conf",
  "jobsDir": "jobs",
  "buildFlinkVersion": "2.2.0"
}
```

`datafusion-plugin-kafka-json` 子模块 builder 入口仍建议为：

```text
datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/builder/build-flink-plugin.sh
```

builder 只负责输出 `plugins/flink/datafusion-plugin-kafka-json/`；不得清空 `plugins/flink/` 根目录。

### 6.2 版本规则

- `flink-kubernetes-operator:1.14.0` 的 `FlinkVersion` 枚举支持 `v2_2`，首版按 `v2_2` 提交。
- `datafusion-plugin-kafka-json` 首版 K8S_OPERATOR 构建 profile 使用 `flink.version=2.2.0`。
- Flink connector 版本必须选择 Flink 2.2 兼容系列，例如当前父 POM 中
  `flink.kafka.connector.version=5.0.0-2.2`。
- 未来若升级 Operator 或 runtime image，再新增一组 profile，不在同一 profile 内混用不同 Flink 大小版本依赖。

### 6.3 scope / 发布边界

| 依赖类别 | 示例 | 结论 | 原因 |
|----------|------|------|------|
| Flink runtime / API | `flink-runtime`, `flink-streaming-java`, `flink-clients`, `flink-connector-base`, `flink-statebackend-rocksdb` | `provided`，不上传共享盘 | 官方 Flink 镜像已提供同版本 runtime；重复上传易造成类冲突 |
| Flink Kafka connector | `flink-connector-kafka`, `kafka-clients` | 随作业发布 | connector 通常不应假定基础镜像内置；版本需与作业显式绑定 |
| Paimon | `paimon-bundle`, `paimon-s3` | 随作业发布 | 基础 Flink 镜像不包含 Paimon |
| Hadoop / S3 | `hadoop-client-api`, `hadoop-client-runtime`, `hadoop-aws` | 随作业发布 | Paimon S3 访问依赖，集群不保证内置 |
| DataFusion 内部模块 | `datafusion-common` | 随作业发布 | 业务代码直接依赖 |
| 配置 / 表达式 / JSON | `jmespath-core`, Jackson 非 Flink runtime 已有部分 | 随作业发布；如后续发现与 Flink runtime 冲突再 relocation | 插件配置解析依赖，基础镜像不应作为契约来源 |
| 日志实现 | `logback-classic`, `logback-core` | `provided`，不上传共享盘 | Flink 作业日志由 Flink runtime 控制；用户 jar 不携带日志实现，避免多 binding |
| 日志 API | `slf4j-api` | `provided`，不上传共享盘 | Flink runtime 已提供日志 API |

`FAT_JAR` 模式下，shade 只打入“随作业发布”的依赖，并排除上表 `provided` 类别。
`THIN_JAR_LIB` 模式下，`flinkAppMainJar` 是普通业务 jar，`lib/` 只包含“随作业发布”的依赖 jar。

按当前 `datafusion-plugin-kafka-json/pom.xml` 分析，首版结论如下：

- 改为 `provided` 并从 fat jar / thin lib 排除：`flink-connector-base`、`flink-runtime`、`flink-streaming-java`、
  `flink-statebackend-rocksdb`、`flink-clients`、`slf4j-api`、`logback-classic`、`logback-core`。
- 随作业发布：`datafusion-common`、`flink-connector-kafka`、`kafka-clients`、`paimon-bundle`、`paimon-s3`、
  `hadoop-client-api`、`hadoop-client-runtime`、`hadoop-aws`、`jmespath-core`、`jackson-databind`。
- `jackson-*` 首版随作业发布；如果 fat jar 运行中确认和 Flink runtime 版本冲突，再通过 shade relocation 处理。

## 7. K8S_OPERATOR 控制规则

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| `submit` | 校验共享盘依赖目录和主 jar，创建或更新 Secret + `FlinkDeployment` | `RUNNING`；校验、创建或提交失败返回 `SUBMIT_FAILURE` |
| `stop` | patch `spec.job.state=SUSPENDED`；checkpoint / savepoint 存储路径来自任务配置 JSON 中的 S3 配置 | `STOPPING`，待状态映射转终态 |
| `kill` | 删除 `FlinkDeployment`；按配置删除 Secret；必要时删除匹配 Pod | 成功或无资源返回 `KILLED`；失败返回 `UNKNOWN` |
| `finish` | 查询终态，按配置采集日志，清理 Secret / `FlinkDeployment` | 当前终态 |

`stop` 是温和停止，允许 Operator 按 `upgradeMode` 处理挂起；`kill` 是不可恢复的 Kubernetes 资源清理。
首版不承诺自动从 savepoint 恢复新任务，只记录 Operator status 中暴露的 `savepointLocation` 到 `.state.result`。
checkpoint / savepoint 的 S3 目录统一在任务配置 JSON 中声明，不放在共享盘或 agent 配置中。

## 8. 状态映射

`FlinkK8sOperatorRunModeStateMapping` 查询 `FlinkDeployment.status` 和 Pod 状态，再结合本地控制状态映射：

| FlinkDeployment / Operator 状态 | DataFusion 状态 |
|----------------------------------|-----------------|
| `RUNNING`, `CREATED`, `INITIALIZING`, `RECONCILING`, `RESTARTING` | `RUNNING` |
| `FINISHED` | `RUN_SUCCESS` |
| `FAILED`, `FAILING` | `RUN_FAILURE` |
| `CANCELLING` | `STOPPING` |
| `CANCELED`, `SUSPENDED` 且本地状态为 `STOPPING` | `STOP_SUCCESS` |
| `CANCELED`, `SUSPENDED` 且本地状态不是 `STOPPING` | `RUN_FAILURE` |
| 本地 `KILLING` 且 `FlinkDeployment` 和 Pod 都不存在 | `KILLED` |
| `FlinkDeployment` 不存在且本地非终态 | `UNKNOWN` |

状态刷新只查询终端状态，不主动 stop、kill 或重启任务。连续 `UNKNOWN` 的推进仍遵循 agent 公共
`AgentProperties.StateRefresh.unknownThreshold` 规则。

## 9. 日志

- `TaskResult.workerResult.workDirPath` 返回任务运行目录，manager 只用该目录识别 `stdout.log`、`stderr.log`、`state.log`。
- `TaskResult.workerResult.pluginLogUri` 返回插件日志入口，可以是对象存储 URI、本地终态日志文件，或
  `k8s-operator://{namespace}/flinkdeployments/{deploymentName}`。
- `TaskResult.workerResult.result.flinkWebUiUri` 返回任务 Flink Web UI 地址。首版默认返回 Kubernetes 集群内
  REST service 地址 `http://{deploymentName}-rest.{namespace}.svc:8081`，如需外部可访问地址，由
  `flinkWebUiUriTemplate` 配置成 ingress / 网关地址模板。
- 未配置外部日志且 `collectLogsOnFinish=true` 时，agent 在终态 best-effort 拉取 Flink JobManager / TaskManager Pod 日志，
  写入 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/k8s-flink.log`。
- 日志采集失败不改变任务终态。
- 不在日志、状态文件、label 或 annotation 中输出完整 job JSON。

## 10. 文件变化

### 10.1 新增文件

| File | Notes |
|------|-------|
| `docs/datafusion-agent/plugin-flink-data-define.md` | Flink agent 插件字段事实源 |
| `docs/datafusion-agent/plugin-flink-design.md` | Flink agent 插件设计 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/FlinkPluginTaskExecutor.java` | `pluginType=FLINK` 执行器 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/FlinkRunMode.java` | 运行模式枚举 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/FlinkParamResolver.java` | 参数归一化 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/FlinkExecutionParam.java` | 提交参数模型 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/FlinkTaskRunner.java` | runner SPI |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/FlinkSubmitResult.java` | runner 提交结果 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8soperator/K8sOperatorFlinkTaskRunner.java` | K8S_OPERATOR runner |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8soperator/FlinkKubernetesOperatorClient.java` | K8S_OPERATOR client 端口 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8soperator/Fabric8FlinkKubernetesOperatorClient.java` | Fabric8 + `FlinkDeployment` 实现 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8s/FlinkKubernetesParam.java` | Kubernetes 参数模型 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8s/FlinkKubernetesRuntimeRef.java` | Kubernetes 接管引用 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8soperator/FlinkK8sOperatorRunModeStateMapping.java` | K8S_OPERATOR 状态映射 |
| `datafusion-agent/src/main/resources/plugins/flink/templates/flink-k8s-operator-deployment.yml` | Secret + `FlinkDeployment` 模板，包含共享盘 volume、`emptyDir` usrlib volume 和 initContainer |
| `datafusion-agent/src/main/resources/plugins/flink/templates/flink-k8s-operator-plugin-config.json` | K8S_OPERATOR 插件配置样例 |
| `datafusion-agent/src/main/resources/plugins/flink/plugin-manifest.json` | Flink 插件类型 manifest，声明 `multiApp=true`、`appNameField=flinkAppName` 和 app 目录模式 |
| `datafusion-agent/src/main/resources/plugins/flink/{flinkAppName}/` | 单个 Flink app 产物目录；fat 模式主 jar 在 app 根目录且 `lib/` 为空，thin 模式主 jar 在 app 根目录且依赖在 `lib/` |
| `datafusion-agent/src/main/resources/plugins/flink/{flinkAppName}/flink-app-manifest.json` | 单个 Flink app manifest，记录主 jar、artifact mode、lib/conf/jobs 目录和构建 Flink 版本 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/builder/build-flink-plugin.sh` | `datafusion-plugin-kafka-json` builder 入口脚本，调用 Maven profile 并部署产物到 agent 插件目录 |

### 10.2 后续 K8S 文件

| File | Notes |
|------|-------|
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8s/K8sFlinkTaskRunner.java` | K8S native runner |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/flink/k8s/Fabric8FlinkKubernetesClient.java` | Fabric8 Kubernetes native 资源查询和清理 |
| `datafusion-agent/src/main/resources/plugins/flink/templates/flink-k8s-runtime.yml` | K8S native submit 模板 |

### 10.3 修改文件

| File | Notes |
|------|-------|
| `docs/datafusion-agent/agent-design.md` | 补充 Flink 文档入口和未实现范围 |
| `docs/datafusion-agent/agent-data-define.md` | 在复用/插件说明中补充 Flink 文档入口 |
| `datafusion-agent/pom.xml` | 补充 Fabric8 / CRD 访问所需依赖；不引入 Flink runtime |
| `datafusion-plugin/datafusion-plugin-kafka-json/pom.xml` | 增加 `flink-fat-jar`、`flink-thin-lib` profile，并按 6.3 调整 `provided` 和发布依赖 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/plugins/flink/` | 由 Flink 类型目录继续下沉到 `plugins/flink/datafusion-plugin-kafka-json/` |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/plugins/plugin-kafka-json-commands.md` | 当前仍有旧样例路径描述，后续需和 `plugins/flink/{flinkAppName}/jobs` 结构对齐 |

## 11. 安全

- Flink job config 可能包含 Kafka、Paimon、S3、数据库密钥。K8S / K8S_OPERATOR 模式使用 Secret，不使用 ConfigMap。
- Agent 本地 `job.json` 快照只允许当前用户读写。
- 共享盘 `/opt/datafusion/plugins/flink/{flinkAppName}/` 只保存 jar、依赖和静态样例资源，不保存 job config、账号密码或运行实例 Secret。
- Flink Pod 以只读方式挂载共享盘依赖目录，initContainer 只向 Pod 内 `emptyDir` 复制。
- ServiceAccount 最小权限：目标 namespace 内 Secret、Pod、Pod log 和 `flinkdeployments.flink.apache.org`。
- 后续 K8S native 模式额外需要 Deployment / Service 权限。
- 用户自定义 label / annotation 需要过滤保留前缀，避免覆盖调度定位信息。
- `WorkerResult`、`.state.result` 和 `state.log` 不保存完整 job JSON。

## 12. 非目标

- 首版不实现 `LOCAL`、`STANDALONE`、`YARN`、`K8S` 的真实提交。
- 首版不构建业务定制 Flink application 镜像；统一使用 `--platform=linux/amd64 flink:2.2.0-scala_2.12-java17`。
- 首版不把 job config 放入共享盘；共享盘只承载插件 jar 和依赖。
- 首版不由 agent 自动上传共享盘依赖；外部流程人工上传整个 `plugins/` 目录到共享盘。
- 首版不强制共享盘依赖目录不可变；插件开发需保证向下兼容。
- 首版不实现 savepoint 触发和恢复编排；checkpoint / savepoint 路径由任务配置 JSON 中的 Flink/Paimon/S3 配置控制。
- 首版不实现 Flink Session Cluster 复用。
- 首版不实现跨 agent 迁移接管。
- 首版不在 agent 侧解析 Kafka/Paimon 业务配置，只把 job config 作为插件作业输入。
- 首版不提供 Flink Web UI 代理；只在任务结果中返回 Flink Web UI 地址。

## 13. 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```

重点验证：

- `runMode=K8S_OPERATOR` / `runMode=K8S` 按大写枚举解析和持久化。
- `operatorVersion=flink-kubernetes-operator:1.14.0`、`flinkVersion=v2_2`、`buildFlinkVersion=2.2.0`、
  runtime image `flink:2.2.0-scala_2.12-java17` 匹配。
- `FAT_JAR` / `THIN_JAR_LIB` 两种 profile 产物和 `flinkAppMainJar` / `jarURI` 匹配。
- `provided` 依赖未进入 fat jar 或 thin lib，随作业发布依赖均已上传共享盘。
- Flink builder、`plugins/flink/plugin-manifest.json`、`flink-app-manifest.json` 和模板变量符合
  [agent-design.md](./agent-design.md) 的插件部署规范。
- `flink:2.2.0-scala_2.12-java17` 镜像内 `FLINK_HOME=/opt/flink`，共享 PVC `datafusion-shared-data`、
  `/opt/datafusion/plugins/flink/{flinkAppName}/` 挂载和主 jar 存在性校验正确。
- K8S_OPERATOR Secret 和 `FlinkDeployment` manifest 渲染正确。
- `FlinkDeployment` 包含 initContainer，并能把共享盘 `plugins/flink/{flinkAppName}` app 目录 jar 和 `lib/*.jar` 复制到
  `/opt/flink/usrlib/`。
- initContainer 不把业务插件目录复制到 `/opt/flink/plugins/`。
- `WorkerResult.result.flinkWebUiUri` 返回默认 REST service 地址或 `flinkWebUiUriTemplate` 渲染后的地址。
- `FlinkDeployment` 包含 amd64 调度约束。
- K8S_OPERATOR submit 写入 `.snap/.state`，`appId` 为 `FlinkDeployment` name。
- `FlinkDeployment` / Pod 状态映射正确。
- stop / kill 幂等。
- agent 重启后基于 `.snap + .state` 接管状态查询和控制。
- 终态日志采集失败不影响任务终态。
