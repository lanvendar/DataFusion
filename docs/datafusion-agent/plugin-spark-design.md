# Spark 插件设计

> 数据结构见 [plugin-spark-data-define.md](./plugin-spark-data-define.md)。Agent 总体设计见
> [agent-design.md](./agent-design.md)。`plugin-spark-sql.jar` 的配置与执行逻辑见
> [../datafusion-plugin/datafusion-plugin-spark-sql/spark-sql-design.md](../datafusion-plugin/datafusion-plugin-spark-sql/spark-sql-design.md)。

## 1. Capability

- Capability: `datafusion-agent` 以 `SPARK + K8S_OPERATOR` 方式提交 Spark SQL 作业，通过 kubeflow `spark-operator` 2.5.0 创建 `SparkApplication`，使用官方镜像 `apache/spark:4.0.2-scala2.13-java17-ubuntu`，通过 initContainer 从共享插件目录加载 `plugin-spark-sql.jar`。
- Module: `datafusion-agent`
- Java backend package: `com.datafusion.agent.runtime.worker.plugin.spark`
- Frontend path: 无
- Route / API prefix: 复用 `/internal/scheduler/*`
- Call chain: `TaskRequest` -> `WorkerTaskService` -> `SparkPluginTaskExecutor` -> `K8sOperatorSparkTaskRunner` -> `SparkApplication + ConfigMap` -> `AgentTaskStateReportScheduler` -> `ManagerTaskResultReporter`

首版只实现 `K8S_OPERATOR`。`LOCAL` 和 `THRIFT` 保留在后续路线中，不在本次 agent 设计内实现。

## 2. Data Flow

| Scenario | Source | Through | Target | Data structure | Notes |
|----------|--------|---------|--------|----------------|-------|
| 提交 Spark SQL | Manager `TaskRequest` | `SparkPluginTaskExecutor.validateTaskRequest` -> `SparkParamResolver` | `SparkExecutionParam` | `pluginParam + taskData` | `pluginParam.runMode` 必须为 `K8S_OPERATOR`，Agent 不回写 `pluginParam` |
| 生成任务配置 | `effectiveTaskData` | `K8sOperatorSparkTaskRunner` | 本地任务运行目录 | `spark-sql-job.json` | 用于排查和 ConfigMap 内容来源 |
| 创建 SQL 配置资源 | `spark-sql-job.json` | Fabric8 client | Kubernetes ConfigMap | `df-spark-sql-job-{taskInstanceId}` | 默认只挂载到 driver pod |
| 创建 Spark 应用 | `SparkExecutionParam` | `SparkKubernetesTemplateRenderer` | Kubernetes API | `SparkApplication` | `apiVersion=sparkoperator.k8s.io/v1beta2` |
| 加载插件 jar | 共享插件目录 | driver / executor initContainer | pod 内 `emptyDir` | `plugin-spark-sql.jar` | 不二次制作业务镜像 |
| 状态刷新 | `SparkApplication.status` / pod 存活事实 | `K8sOperatorRunModeStateMapping` | `WorkerTaskExecutionState` | `StatusEnum` | Client 只返回 Kubernetes 状态事实，mapping 负责转换调度状态 |
| 终态处理 | driver pod log / CRD status | `beforeFinalReport` | `WorkerResult` | `pluginLogUri`, `sparkWebUiUri` | 只采集日志和写最终结果，资源清理由 finish 处理 |

## 3. API Contract

| HTTP method | Path | Request object | Response object | Notes |
|-------------|------|----------------|-----------------|-------|
| `POST` | `/internal/scheduler/submitTask` | `TaskRequest` | `TaskResult` | 提交 `SparkApplication` |
| `POST` | `/internal/scheduler/stopTask` | `TaskRequest` | `TaskResult` | patch `spec.suspend=true` |
| `POST` | `/internal/scheduler/killTask` | `TaskRequest` | `TaskResult` | 删除 `SparkApplication` 和本次任务 ConfigMap |
| `POST` | `/internal/scheduler/finishTask` | `TaskRequest` | `Boolean` | master 确认终态后执行资源清理 |

## 4. File Changes

### 4.1 New Files

| File | Notes |
|------|-------|
| `docs/datafusion-agent/plugin-spark-data-define.md` | Spark agent 插件字段事实源 |
| `docs/datafusion-agent/plugin-spark-design.md` | Spark agent 插件设计 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/SparkPluginTaskExecutor.java` | `pluginType=SPARK` 执行器 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/SparkRunMode.java` | 运行模式枚举，首版只启用 `K8S_OPERATOR` |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/SparkParamResolver.java` | 参数归一化 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/SparkExecutionParam.java` | 提交参数模型 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/SparkTaskRunner.java` | runner SPI |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/SparkTaskResult.java` | runner 返回结果 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/K8sOperatorSparkTaskRunner.java` | `K8S_OPERATOR` runner |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/SparkKubernetesParam.java` | Kubernetes 归一化参数 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/SparkKubernetesRuntimeRef.java` | Kubernetes 运行引用 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/K8sOperatorClient.java` | Spark Operator client 端口 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/K8sOperatorFabric8Client.java` | Fabric8 + `SparkApplication` 实现 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/SparkKubernetesTemplateRenderer.java` | YAML / resource 渲染 |
| `datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spark/k8s/K8sOperatorRunModeStateMapping.java` | `SPARK + K8S_OPERATOR` 状态映射 |
| `datafusion-agent/src/main/resources/plugins/spark/templates/spark-k8s-operator-plugin-config.json` | 插件配置模板 |
| `datafusion-agent/src/main/resources/plugins/spark/templates/spark-k8s-operator-application.yml` | `SparkApplication` 模板 |

### 4.2 Modified Files

| File | Notes |
|------|-------|
| `docs/datafusion-agent/agent-data-define.md` | 后续实现时补充 Spark 插件链接 |
| `docs/datafusion-agent/agent-design.md` | 后续实现时补充 Spark 插件链接和 `SPARK` 注册说明 |
| `datafusion-agent/pom.xml` | 复用已有 Fabric8 依赖；如模板渲染需要额外库，应先在设计中说明 |

### 4.3 Reused Objects

| Object | Path | Notes |
|--------|------|-------|
| `FlinkPluginTaskExecutor` pattern | `datafusion-agent/.../plugin/flink` | 复用 `pluginType + runMode runner` 结构 |
| `K8sOperatorFlinkTaskRunner` pattern | `datafusion-agent/.../plugin/flink/k8s` | 复用 Fabric8 提交、运行引用、日志采集和 cleanup 思路 |
| `AgentTaskStateReportScheduler` | `datafusion-agent/.../worker/reporter` | 复用周期状态刷新和终态上报 |

## 5. Java Backend Design

### 5.1 Controller

无新增 Controller。`AgentExecutorRpcProvider` 保持调度 RPC 适配职责。

### 5.2 Service

| Method | Input | Output | Notes |
|--------|-------|--------|-------|
| `SparkPluginTaskExecutor.validateTaskRequest` | `TaskRequest` | void | 提交前解析校验请求，不改写 `pluginParam` |
| `SparkPluginTaskExecutor.submitTask` | `TaskRequest` | `TaskResult` | 按 `runMode` 分派到 `SparkTaskRunner`，保存 `.snap` / `.state` |
| `K8sOperatorSparkTaskRunner.submit` | `SparkExecutionParam` | `SparkTaskResult` | 创建 ConfigMap 和 `SparkApplication` |
| `K8sOperatorClient.queryStatus` | `SparkKubernetesRuntimeRef` | `SparkOperatorStatus` | 读取 CRD 状态和运行资源事实 |
| `K8sOperatorRunModeStateMapping.mapState` | `WorkerTaskExecutionState` | `StatusEnum` | 结合本地状态映射 Spark Kubernetes 状态 |
| `K8sOperatorClient.collectLogs` | `SparkKubernetesRuntimeRef` | `String` | 采集 driver pod 日志 |
| `K8sOperatorClient.stop` | `SparkKubernetesRuntimeRef` | void | patch `spec.suspend=true` |
| `K8sOperatorClient.kill` | `SparkKubernetesRuntimeRef` | void | 强制删除 SparkApplication、Pod 和 ConfigMap |
| `K8sOperatorClient.cleanup` | `SparkKubernetesRuntimeRef` | boolean | 幂等删除 SparkApplication 和 ConfigMap |

### 5.3 Business Rules

| Scenario | Rule | Error / return |
|----------|------|----------------|
| 运行模式缺失 | `pluginParam.runMode` 必须存在且为 `K8S_OPERATOR` | `SUBMIT_FAILURE` |
| 运行模式持久化 | 提交后以 `.snap.runMode` 作为运行模式事实来源 | 控制和状态恢复不依赖 `.snap.pluginParam.runMode` |
| Spark 版本 | `sparkVersion` 固定 `4.0.2`，Scala binary 固定 `2.13` | 不匹配则提交失败 |
| Spark 镜像 | 默认 `apache/spark:4.0.2-scala2.13-java17-ubuntu` | 任务级可覆盖，但必须由显式配置承担兼容风险 |
| Operator 版本 | 目标集群为 kubeflow `spark-operator` 2.5.0，CRD 为 `sparkoperator.k8s.io/v1beta2` | CRD 不存在或不支持字段时提交失败 |
| 重启策略 | `restartPolicy.type` 固定为 `Never`，DataFusion 重启通过新任务实例和新 SparkApplication 完成 | 不依赖 Operator 原地重启 |
| 插件 jar | `plugin-spark-sql.jar` 位于共享插件目录，driver/executor initContainer 复制到 pod 内 `emptyDir` | 源路径不存在或不是文件时提交失败 |
| SQL 配置 | SQL job 配置写入 ConfigMap，启动参数只传 `--job-file` | 不把完整 SQL 放进 SparkApplication arguments |
| 用户 labels | 不允许覆盖 `datafusion.*` / `datafusion.io/*` 保留 label | 覆盖项被拒绝 |
| 重启提交 | `.state.appId` 存在时先清理旧 SparkApplication 和 ConfigMap | 清理失败返回 `SUBMIT_FAILURE` |
| 停止语义 | `stop` patch `spec.suspend=true`；`SUSPENDING -> STOPPING`，`SUSPENDED -> STOP_SUCCESS` | 非停止态 `SUSPENDED` 返回 `UNKNOWN` |
| 资源清理 | `cleanup` 幂等删除 SparkApplication 和 ConfigMap；`kill` 额外强制删除 Pod | 资源不存在视为成功 |
| 终态清理 | 状态映射只做终态日志采集和结果写入；finish 执行资源清理 | 清理失败返回 `false` |
| 状态查询边界 | `K8sOperatorClient` 只返回 `SparkOperatorStatus`；`K8sOperatorRunModeStateMapping` 负责转 `StatusEnum` | 返回 `UNKNOWN` 前打印精简 warn |

### 5.4 Transaction Boundary

- Needs transaction: 否
- Transactional method: 无
- Rollback condition: Kubernetes 资源创建失败时 best-effort 删除已创建 ConfigMap，返回 `SUBMIT_FAILURE`

### 5.5 Mapper / DAO / SQL

无

## 6. Frontend Design

无。首版不新增前端页面。

## 7. Integration

| Integration target | Method | Notes |
|--------------------|--------|-------|
| kubeflow spark-operator 2.5.0 | Kubernetes CRD `SparkApplication` | `apiVersion=sparkoperator.k8s.io/v1beta2` |
| Kubernetes API | Fabric8 client | 复用 `AgentProperties.Kubernetes` |
| Shared plugin directory | PVC / runtime plugin root | 默认 `/opt/datafusion/plugins/spark/datafusion-plugin-spark-sql/plugin-spark-sql.jar` |
| Spark official image | Pod image | 默认 `apache/spark:4.0.2-scala2.13-java17-ubuntu` |
| plugin-spark-sql | Java main class | 默认 `com.datafusion.plugin.spark.sql.SparkSqlApplication` |

## 8. Security and Context

- Current user: agent 运行用户和 Kubernetes ServiceAccount。
- Tenant / project / app context: 不新增租户字段，任务身份通过 `flowInstanceId`、`taskInstanceId`、label 和 annotation 传递。
- Password / token handling: Kubernetes API token 继续由 `AgentProperties.Kubernetes` 或 Pod ServiceAccount 提供；SQL job ConfigMap 不应写入密码。需要敏感参数时后续扩展 Secret 模式。
- Permission boundary: agent ServiceAccount 只授予目标 namespace 内 `sparkapplications`, `configmaps`, `pods`, `pods/log`, `services` 的必要权限。

## 9. Out of Scope

- 不实现 `LOCAL` 和 `THRIFT`。
- 不实现 Spark jar / Python application 通用提交，只提交 `plugin-spark-sql.jar` 承载的 SQL 作业。
- 不构建自定义 Spark 镜像。
- 不把 `plugin-spark-sql.jar` 塞进 ConfigMap。
- 不在 agent 侧解析 SQL 血缘、校验 SQL 语义或改写 SQL。
- 不实现跨 agent 迁移接管；只支持基于 `.snap + .state` 的本机重启接管。

## 10. Verification

- Unit tests: `SparkParamResolver`、`SparkKubernetesTemplateRenderer`、状态映射、资源清理幂等。
- Compile / build command: `mvn -DskipTests compile -pl datafusion-agent -am`
- Frontend verification: 无
- Style / lint: Java Checkstyle 按项目规则执行。
- Manual check: 提交 SQL 成功、SQL 失败、stop、kill、agent 重启恢复、driver pod 日志采集、资源清理、CRD 缺失失败提示。
