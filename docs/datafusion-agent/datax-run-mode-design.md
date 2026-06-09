# DataX LOCAL / K8S 运行模式设计

> 数据结构定义见 [datax-run-mode-data-define.md](./datax-run-mode-data-define.md)。Agent 总体设计见 [agent-design.md](./agent-design.md)。

## 1. 能力范围

- 能力: 在 `datafusion-agent` 中实现 `DATAX` 插件，支持 LOCAL 本地进程运行模式和 K8S Kubernetes Job 运行模式。
- 模块: `datafusion-agent`，少量 Manager / common-data 协作。
- Java 后端包: `com.datafusion.agent.runtime.worker.plugin.datax`。
- API 前缀: 不新增 HTTP API，复用 Agent 已有 `/internal/schedule/submitTask`、`stopTask`、`killTask`、`finishTask`。
- 参数来源: DataX 运行参数只来自 `TaskRequest.pluginParam` 和 `TaskRequest.taskData`；`application.yml` 不定义 `datafusion.agent.datax.*`。

调用链:

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

## 2. 总体设计

当前 `WorkerTaskOperatorRouter` 是 `pluginType -> PluginTaskExecutor` 的一对一映射，因此 DataX 采用一个 `DataxPluginTaskExecutor` 注册 `pluginType=DATAX`，执行器内部按归一化后的 `pluginParam.runMode` 分派给不同 `DataxTaskRunner`。

状态映射按已有框架规则分别注册：

- `DataxLocalRunModeStateMapping`: `pluginType=DATAX`, `runMode=LOCAL`
- `DataxK8sRunModeStateMapping`: `pluginType=DATAX`, `runMode=K8S`

这样可以复用 `AgentTaskStateReportScheduler` 的 `pluginType + runMode` 查询机制，又不会破坏插件路由器的唯一键约束。

运行模板遵守 [plugin-run-mode-template-design.md](./plugin-run-mode-template-design.md)：

- `pluginParam` 和 `taskData` 是渲染参数来源。
- `templates/datax/datax-local.yml` 是 `DATAX + LOCAL` 静态模板，渲染产物为 `LocalProcessSpec`。
- `templates/datax/datax-k8s-job.yml` 是 `DATAX + K8S` 静态模板，渲染产物为 Kubernetes manifest。
- Runner 只消费渲染后的 `ExecutionSpec`，不在提交阶段硬编码命令骨架或 manifest 骨架。

## 3. 数据流

| 场景 | 来源 | Through | Target | 数据结构 | 说明 |
|------|------|---------|--------|----------|------|
| 创建调度任务 | Manager 任务定义 | `scheduler_task_info.definition` | Agent | `TaskRequest.taskData` | 存放 DataX job JSON 或 job 引用 |
| 选择运行模式 | Manager 插件配置 | `PluginConfigEntity.runMode` | Agent | `TaskRequest.pluginParam.runMode` | Manager 必须注入，Agent 缺失即拒绝提交 |
| 提交 LOCAL | Agent RPC | `DataxParamResolver` -> `datax-local.yml` -> `LocalProcessSpec` -> `LocalDataxTaskRunner` | 本地 Java 进程 | `DataxExecutionParam`, `LocalProcessSpec` | 生成 job 文件、渲染本地执行计划、启动 DataX Engine |
| 提交 K8S | Agent RPC | `DataxParamResolver` -> `datax-k8s-job.yml` -> `K8sDataxTaskRunner` | Kubernetes Secret + Job | `DataxKubernetesParam` | 渲染固定 YAML 模板后提交 |
| 状态刷新 | 本地 `.state` | `PluginRunModeStateMapping` | Manager | `WorkerTaskExecutionState` / `TaskResult` | 周期上报终端状态和 `logPath` |
| 停止 / 强杀 | Manager task action | Runner 控制终端任务 | 本地进程 / K8S Job | `TaskRequest.appId` + `.state._runtime` | K8S 等 Pod 真正退出后才进入停止终态 |

## 4. API 契约

无新增 HTTP API。

| HTTP method | Path | Request object | Response object | 说明 |
|-------------|------|----------------|-----------------|------|
| `POST` | `/internal/schedule/submitTask` | `TaskRequest` | `Result<TaskResult>` | 提交 DataX 任务 |
| `POST` | `/internal/schedule/stopTask` | `TaskRequest` | `Result<TaskResult>` | 优雅停止 |
| `POST` | `/internal/schedule/killTask` | `TaskRequest` | `Result<TaskResult>` | 强制停止 |
| `POST` | `/internal/schedule/finishTask` | `TaskRequest` | `Result<TaskResult>` | 终态清理 |

## 5. 文件变更设计

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/datax/DataxPluginTaskExecutor.java` | `PluginTaskExecutor` 实现，注册 `DATAX` |
| `.../datax/DataxTaskRunner.java` | 运行模式 Runner SPI |
| `.../datax/DataxRunMode.java` | LOCAL / K8S 枚举与归一化 |
| `.../datax/DataxExecutionParam.java` | Agent 内部归一化执行参数 |
| `.../datax/DataxParamResolver.java` | 解析 `TaskRequest.pluginParam` 和 `TaskRequest.taskData` |
| `.../datax/DataxJobFileService.java` | 生成 LOCAL job JSON 文件，控制文件权限 |
| `.../datax/local/LocalDataxTaskRunner.java` | LOCAL 进程提交、停止、kill、finish |
| `.../datax/local/DataxLocalRunModeStateMapping.java` | LOCAL 状态映射 |
| `.../datax/k8s/DataxKubernetesParam.java` | K8S Job 参数 |
| `.../datax/k8s/DataxKubernetesRuntimeRef.java` | K8S 接管参数 |
| `.../datax/k8s/K8sDataxTaskRunner.java` | Kubernetes Job 提交、停止、kill、finish |
| `.../datax/k8s/DataxKubernetesClient.java` | K8S 操作抽象，便于测试 |
| `.../datax/k8s/Fabric8DataxKubernetesClient.java` | Kubernetes client 实现 |
| `.../datax/k8s/DataxK8sRunModeStateMapping.java` | K8S 状态映射 |
| `.../datax/k8s/DataxK8sNameGenerator.java` | Job / Secret 名称生成和 DNS-1123 规整 |
| `.../datax/k8s/DataxKubernetesTemplateRenderer.java` | 渲染固定 Kubernetes YAML 模板 |
| `.../plugin/template/TemplateSpecRenderer.java` | 插件运行模式 YAML 模板渲染器 |
| `.../plugin/template/LocalProcessSpec.java` | LOCAL 本地进程执行计划 |
| `datafusion-agent/src/main/resources/templates/datax/datax-k8s-job.yml` | K8S Secret + Job 固定模板 |
| `datafusion-agent/src/main/resources/templates/datax/datax-local.yml` | LOCAL 本地进程固定模板 |

### 5.2 修改文件

| 文件 | 说明 |
|------|------|
| `datafusion-agent/pom.xml` | K8S 模式新增 Fabric8 `kubernetes-client` 依赖 |
| `datafusion-agent/src/main/java/com/datafusion/agent/config/AgentProperties.java` | 仅保留 Agent 基础配置和 Kubernetes client 连接配置，不新增 DataX 运行参数 |
| `datafusion-agent/src/main/resources/application.yml` | 保持无 `datafusion.agent.datax.*` 配置 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/TaskStorageImpl.java` | 把 `PluginConfigEntity.runMode` 注入 `pluginParam.runMode` |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/state/WorkerTaskExecutionState.java` | 增加 `logPath`，用于重启恢复和上报 |

Manager 注入 `pluginParam.runMode` 是 DataX 运行模式的强契约。Agent 不从 `taskData.runMode` 或默认配置推断运行模式，避免插件配置为 K8S 但实际落到 LOCAL。

## 6. 参数契约

`TaskRequest.pluginParam` 保存插件级运行参数，`TaskRequest.taskData` 保存本次任务数据和单任务覆盖。完整字段定义见数据定义文档，核心形态如下。

```json
{
  "pluginParam": {
    "runMode": "K8S",
    "logLevel": "INFO",
    "kubernetes": {
      "namespace": "datafusion",
      "image": "registry.example.com/datafusion/datax-runtime:1.0.0",
      "imagePullPolicy": "IfNotPresent",
      "serviceAccountName": "datax-runner",
      "ttlSecondsAfterFinished": 86400,
      "collectLogsOnFinish": true,
      "deleteJobOnFinish": false
    }
  },
  "taskData": {
    "jobName": "ods_customer.json",
    "jobJson": {
      "job": {
        "content": []
      }
    },
    "kubernetes": {
      "namespace": "datafusion-prod",
      "image": "registry.example.com/datafusion/datax-runtime:20260608"
    }
  }
}
```

参数合并规则：

1. `runMode` 只读取 `pluginParam.runMode`，必填。
2. `taskData.jobJson`、`taskData.jobPath`、`taskData.jobFileName` 至少一个必填；K8S 模式不允许 `taskData.jobPath`。
3. 通用运行参数优先级为 `taskData.env` 覆盖 `pluginParam.env`，`taskData.jvmOptions` 追加到默认 JVM 参数和 `pluginParam.jvmOptions` 之后。
4. K8S 参数优先级为 `taskData.kubernetes` 覆盖 `pluginParam.kubernetes`。
5. 缺失字段只使用代码内协议默认值，例如 `javaBin=java`、`namespace=default`、`backoffLimit=0`、`collectLogsOnFinish=true`；不从 `application.yml` 补 DataX 默认值。
6. K8S 镜像必须由 `pluginParam.kubernetes.image` 或 `taskData.kubernetes.image` 提供。

## 7. Java 后端设计

### 7.1 DataxPluginTaskExecutor

职责：

- `pluginType()` 返回 `DATAX`。
- `prepareTask` 调用 `DataxParamResolver` 做参数校验，确保 `pluginParam.runMode` 必填且能解析到 job 来源。
- `submitTask` 根据 `DataxExecutionParam.runMode` 找到 Runner，提交后写入 `WorkerTaskExecutionState`。
- `stopTask` / `killTask` 从 `.state` 读取历史 `runMode` 和 `appId`，交给对应 Runner。
- `finishTask` 只在终端状态后执行资源清理，未终态时返回当前状态。
- K8S 提交成功后，把 `DataxKubernetesRuntimeRef` 写入 `.state.pluginParam._runtime`，支持 Agent 重启后继续接管。

Runner 查找按 `DataxRunMode` 建 `Map<DataxRunMode, DataxTaskRunner>`，不依赖 Spring Bean 名称。

### 7.2 LOCAL 模式

提交流程：

```text
解析 DataxExecutionParam
    -> 如 taskData.jobJson 存在，写入本地 job 文件
    -> 创建日志目录 ${modules}/logs/{date}/{flowInstanceId}/{taskInstanceId}
    -> 渲染 templates/datax/datax-local.yml 得到 LocalProcessSpec
    -> ProcessBuilder 按 LocalProcessSpec 启动 DataX Engine
    -> appId = process.pid()
    -> 写 WorkerTaskExecutionState(status=RUNNING, runMode=LOCAL)
    -> watcher 等待退出码并更新 RUN_SUCCESS / RUN_FAILURE
```

命令骨架由 `templates/datax/datax-local.yml` 管理。模板中的静态参数包括 DataX Engine main class、`-mode standalone`、`-jobid -1`、日志系统属性等；动态占位符来自 `DataxExecutionParam`，而 `DataxExecutionParam` 只由 `pluginParam`、`taskData` 和 Agent 生成路径派生。

模板渲染后的命令形态：

```text
java
  --add-opens java.base/java.lang=ALL-UNNAMED
  -Ddatax.home={dataxHome}
  -Ddatax.log.level={logLevel}
  -Ddatax.log.file={logFile}
  -Ddatax.log.max.size={logMaxSize}
  -Ddatax.log.max.index={logMaxIndex}
  -Dlogback.configurationFile={dataxHome}/conf/logback.xml
  -classpath {dataxJar}
  com.alibaba.datax.core.Engine
  -mode standalone
  -jobid -1
  -job {jobFile}
```

控制规则：

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | `ProcessHandle.destroy()` | `STOP_SUCCESS`；进程不存在也记录 `STOP_SUCCESS`，保证幂等 |
| kill | `ProcessHandle.destroyForcibly()` | `KILLED`；进程不存在也记录 `KILLED` |
| finish | 终态后删除状态文件；本地 job 文件按保留策略处理 | 当前终态 |

LOCAL 状态映射优先使用 `.state.status` 终态，其次使用 `exitCode`，再用 pid 是否存活判断 `RUNNING` 或 `UNKNOWN`。

### 7.3 K8S 模式

提交流程：

```text
解析 DataxExecutionParam
    -> 生成 DNS-1123 合法 Job name / Secret name
    -> 渲染 classpath 模板 templates/datax/datax-k8s-job.yml
    -> YAML 中包含 Secret(job.json) 和 batch/v1 Job
    -> Fabric8 load(renderedYaml).createOrReplace()
    -> appId = Kubernetes Job name
    -> 写 WorkerTaskExecutionState(status=RUNNING, runMode=K8S, pluginParam._runtime)
```

Job 关键约定：

- Job / Secret 的结构性 YAML 固定在 `datafusion-agent/src/main/resources/templates/datax/datax-k8s-job.yml`，运行参数由 `pluginParam.kubernetes` 和 `taskData.kubernetes` 渲染进去。
- label 必须包含 `datafusion.io/plugin-type=DATAX`、`datafusion.io/run-mode=K8S`、`datafusion.io/task-instance-id` 和 `datafusion.io/flow-instance-id`，所有 label value 必须经过 Kubernetes label 规则规整。
- `workerId`、`taskName`、第三方日志 URI 等可能包含冒号、斜杠或超长内容的值写入 annotation，不写入 label。
- `backoffLimit` 默认 `0`，避免 Kubernetes 自己重试导致调度系统状态含义混乱。
- DataX job JSON 默认放 Secret，不使用 ConfigMap，因为 JSON 中可能含数据源账号密码。
- 容器镜像必须内置 DataX bundle；Agent 不把本地 `datafusion-plugin-datax` 目录上传到集群。
- 提交成功后必须把 `DataxKubernetesRuntimeRef(namespace, jobName, secretName, podLabelSelector, containerName, logStorageUri, collectLogsOnFinish, deleteJobOnFinish)` 写入本地 `.state` 的 `pluginParam._runtime`。

K8S 镜像约定：

- Dockerfile: `datafusion-plugin/datafusion-plugin-datax/Dockerfile`。
- 构建上下文: `datafusion-plugin/datafusion-plugin-datax`。
- 基础镜像: `eclipse-temurin:17-jre-jammy`，与父 POM 的 Java 17 保持一致。
- 内置 DataX 目录: `/opt/datafusion/datax`。
- 默认 job 挂载路径: `/datafusion/job/job.json`，由 Agent 创建 Secret 后挂载。
- 默认日志文件: `/datafusion/logs/datax.log`，由容器内 logback 按 `100MB` 和递增序号切分。
- 容器启动入口: `/usr/local/bin/datax-k8s-entrypoint.sh`。

构建命令：

```bash
docker build \
  -t datafusion/datax-runtime:1.0.0 \
  datafusion-plugin/datafusion-plugin-datax
```

运行时环境变量由模板渲染：

| 变量 | 默认值 | 来源 |
|------|--------|------|
| `DATAX_HOME` | `/opt/datafusion/datax` | 模板常量 |
| `DATAX_JOB_FILE` | `/datafusion/job/job.json` | 模板常量 |
| `DATAX_LOG_FILE` | `/datafusion/logs/datax.log` | 模板常量 |
| `DATAX_LOG_LEVEL` | `INFO` | `pluginParam.logLevel` |
| `DATAX_LOG_MAX_SIZE` | `100MB` | `pluginParam.logMaxSize` |
| `DATAX_LOG_MAX_INDEX` | `100` | `pluginParam.logMaxIndex` |
| `JAVA_OPTS` | 空 | `pluginParam.kubernetes.env` / `taskData.kubernetes.env` |

状态映射：

| Kubernetes Job 状态 | DataFusion 状态 |
|---------------------|-----------------|
| `status.conditions[type=Complete,status=True]` | `RUN_SUCCESS` |
| `status.conditions[type=Failed,status=True]` | `RUN_FAILURE` |
| `status.active > 0` | `RUNNING` |
| 本地状态为 `STOPPING` 且该 Job 的 Pod 全部退出 | `STOP_SUCCESS` |
| 本地状态为 `KILLING` 且该 Job 的 Pod 全部退出 | `KILLED` |
| 本地状态为 `STOPPING` / `KILLING` 且仍存在运行中 Pod | 保持当前状态 |
| Job 不存在且本地状态非终态 | `UNKNOWN` |
| 本地状态已是 `STOP_SUCCESS` / `KILLED` | 保持本地终态 |

控制规则：

| 动作 | 行为 | 返回状态 |
|------|------|----------|
| stop | 删除 Job，使用默认 grace period，不立即清理 Secret | `STOPPING`；状态映射确认 Pod 全部退出后转 `STOP_SUCCESS` |
| kill | 删除 Job，`gracePeriodSeconds=0`，不立即清理 Secret | `KILLING`；状态映射确认 Pod 全部退出后转 `KILLED` |
| finish | 仅在终态后执行；按 `collectLogsOnFinish` 采集日志并清理 Secret / Job | 当前终态 |

Agent 重启接管：

- `FileWorkerTaskExecutionStateStore.listRecords` 会恢复未删除的 `.state`，DataX K8S 状态映射必须优先读取 `pluginParam._runtime`。
- `stopTask` / `killTask` / `finishTask` 使用 `_runtime.namespace`、`_runtime.jobName`、`_runtime.secretName`、`_runtime.podLabelSelector` 和 `_runtime.containerName` 操作 Kubernetes，避免插件配置变更后误操作其他 namespace 或 Job。
- K8S Job / Secret 名称生成必须稳定且幂等，同一个 `taskInstanceId` 在 Agent 重启后生成同一组名称。

日志：

- `TaskResult.logPath` 固定返回 Agent 管理的本地日志目录或终态采集到的本地日志文件路径。
- 若配置 `logStorageUri`，Pod 通过挂载卷、Sidecar 或运行环境约定写入外部日志，Agent 在 `TaskResult.result.pluginLogUri` 透传该地址。
- 未配置 `logStorageUri` 时，Agent 在 `TaskResult.result.pluginLogUri` 返回 `k8s://{namespace}/jobs/{jobName}` 作为第三方运行系统日志入口。
- 未配置外挂日志存储且 `collectLogsOnFinish=true` 时，Agent 在终态 best-effort 拉取 Pod 主容器日志到 `${modules}/logs/{date}/{flowInstanceId}/{taskInstanceId}/datax-k8s.log`；采集失败不改变任务原始终态。
- 第一版允许 K8S 运行中没有可读本地日志；需要实时日志时必须配置外挂日志存储。
- 不在状态文件或日志中输出完整 job JSON。

### 7.4 YAML 模板边界

当前已接入的插件运行模式都使用 YAML 模板管理静态运行结构。`SHELL + LOCAL` 和 `DATAX + LOCAL`
渲染为 `LocalProcessSpec`，`DATAX + K8S` 渲染为 Kubernetes Job manifest。模板中必须清楚暴露动态占位符，
动态值只能来自 `TaskRequest.pluginParam`、`TaskRequest.taskData` 和 Agent 根据任务实例生成的本地运行值；命令骨架、
日志重定向、K8S Job 结构等静态内容留在模板中。

后续新增运行模式时，优先按同一模型落地：先定义 runMode 的 typed `ExecutionSpec`，再定义 YAML 模板和渲染上下文。
只有在运行模式没有静态结构、没有复用价值且模板会明显降低可读性时，才允许在设计评审中显式豁免。

## 8. 集成

| 集成目标 | 方法 | 说明 |
|----------|------|------|
| Manager scheduler | 已有 Agent RPC | 不新增接口；需要任务定义携带 DataX job JSON |
| Manager DataX JSON 生成 | `DataxJsonService.buildDataxJson` | 生成的 `json` 字符串可写入 `taskData.jobJson` |
| `datafusion-plugin-datax` | 本地资源 / 镜像构建输入 | LOCAL 可使用 resources 下 DataX bundle；K8S 镜像从 `datafusion-plugin/datafusion-plugin-datax/Dockerfile` 构建 |
| Kubernetes API | Fabric8 `kubernetes-client` | Agent 需要命名空间内 Job、Pod、Pod log、Secret 的 create/get/list/delete 权限 |
| Agent 状态上报 | `AgentTaskStateReportScheduler` | 复用现有 `.state` 周期刷新与 Manager 上报机制 |

Kubernetes 依赖使用 `io.fabric8:kubernetes-client`，通过 `DataxKubernetesClient` 接口封装隔离；不引入 `crd-generator-apt`，也不把 Fabric8 类型暴露到 `datafusion-scheduler-worker`。

## 9. 安全和上下文

- DataX job JSON 可能包含数据库密码。LOCAL 写文件时使用仅当前用户可读写权限；K8S 挂载使用 Secret。
- 不在 `TaskResult.result`、状态文件 `result`、K8S label / annotation 或应用日志中打印完整 job JSON。
- K8S ServiceAccount 只授予目标 namespace 内 `jobs`、`pods`、`pods/log`、`secrets` 所需最小权限。
- 用户自定义 label 不允许覆盖 `datafusion.*` / `datafusion.io/*` 保留 label，且必须符合 Kubernetes label key/value 规则；不符合规则的上下文值写入 annotation。
- `taskData.jobPath` 只允许 LOCAL 模式，避免 K8S 提交从 Agent 本机任意读取 job 文件。

## 10. 不实现范围

- 不在 Agent 侧重新生成 DataX reader / writer JSON；生成逻辑继续归 Manager ingestion 域负责。
- 不实现 DataX 分布式模式，仍按 `-mode standalone` 运行。
- 不实现 K8S 镜像构建流水线；镜像由 `datafusion-plugin/datafusion-plugin-datax/Dockerfile` 手动或外部流水线构建。
- 不实现跨 Agent 迁移接管；但同一个 Agent 重启后必须基于本地 `.state` 继续接管 K8S Job。
- 不把 DataX 账号密码从 job JSON 拆分成独立 Secret key；后续可在数据源安全模型完善后再做。

## 11. 验证

- 单元测试:
  - `TemplateSpecRendererTest`
  - `DataxParamResolverTest`
  - `DataxPluginTaskExecutorTest`
  - `K8sDataxTaskRunnerTest` with fake client
  - `AgentWorkerTaskContextStorageTest`
  - `DataxKubernetesTemplateRendererTest`
- 编译命令:

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
mvn -pl datafusion-agent -am -Dtest=TemplateSpecRendererTest,DataxParamResolverTest,DataxPluginTaskExecutorTest,K8sDataxTaskRunnerTest,AgentWorkerTaskContextStorageTest,DataxKubernetesTemplateRendererTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

- 集成验证:
  - LOCAL: 使用 `datafusion-plugin-datax` resources 下的示例 job，确认提交、成功、失败、stop、kill、日志路径。
  - K8S: 在测试 namespace 中创建最小 RBAC、Secret、Job，确认 `RUNNING -> RUN_SUCCESS/RUN_FAILURE` 和删除 Job 后状态。
- 风格检查:
  - 新增 Java 类按项目 Checkstyle 技能检查 Javadoc、行宽、命名和导入顺序。
