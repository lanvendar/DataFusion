# DataX 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 DataX LOCAL / K8S 运行模式的字段、任务参数和状态持久化事实源。
> Agent 总体运行时结构见 [agent-data-define.md](./agent-data-define.md)。

## 1. 表结构

无。DataX 运行模式不新增 Agent 侧数据库表。

Manager 侧已有 `scheduler_task_info.definition`、`scheduler_task_instance.task_data`、
`system_plugin_config.plugin_param` 和 `system_plugin_config.run_mode` 承载任务定义与插件运行配置。

## 2. 配置边界

DataX 的 LOCAL / K8S 运行参数不定义在 `datafusion-agent/src/main/resources/application.yml` 中，也不通过 `datafusion.agent.datax.*` 读取。Agent 只使用已有基础运行配置定位本地状态和插件运行目录：

| 对象 | 字段 | 说明 |
|------|------|------|
| `AgentProperties` | `modules` | Agent 模块根目录，不参与插件任务运行目录拼接 |
| `AgentProperties.Storage` | `logsDir` | Agent 自身日志目录名，不作为 DataX 插件日志目录 |
| `AgentProperties.Storage` | `taskRuntimeDir` | 任务运行态绝对根目录，默认 `/opt/datafusion/task-runtime` |
| `AgentProperties.Kubernetes` | `apiServer`, `token`, `tokenFile`, `caCertFile` | 仅用于 Fabric8 Kubernetes client 连接集群；不是 DataX Job 参数 |

DataX LOCAL / K8S 的静态结构均由 YAML 模板管理：

| runMode | 模板 | 渲染产物 |
|---------|------|----------|
| `LOCAL` | `datafusion-agent/src/main/resources/plugins/datax/templates/datax-local-runtime.yml` | `LocalShellProcess` |
| `K8S` | `datafusion-agent/src/main/resources/plugins/datax/templates/datax-k8s-runtime.yml` | Kubernetes manifest |

`jobJsonMountPath=/opt/datafusion/plugins/datax/job/job.json`、`containerName=datax`、`DATAX_HOME=/opt/datafusion/plugins/datax` 等 K8S 结构常量由模板和 `DataxKubernetesTemplateConstants` 维护。

## 3. TaskRequest

Manager 将 `system_plugin_config.run_mode` 映射为 `TaskRequest.runMode`，将
`system_plugin_config.plugin_param` 映射为 `TaskRequest.pluginParam`。Agent 不改写两者。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `runMode` | `String` | 是 | `LOCAL` 或 `K8S`，与 `pluginType=DATAX` 共同路由执行器 |
| `pluginParam` | `Object` | 是 | 插件级运行配置，不包含 `runMode` |

### 3.1 DataxPluginParam

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `javaBin` | `String` | 否 | `java` | LOCAL 模式启动 DataX 使用的 Java 命令 |
| `dataxHome` | `String` | LOCAL 必填 | 无 | DataX home |
| `dataxJar` | `String` | LOCAL 条件必填 | `${dataxHome}/lib/datax-bundle-0.0.1.jar` | DataX bundle jar；`dataxHome` 为空时不会自动推导 |
| `jobFile` | `String` | 否 | 空 | 预置 DataX job JSON 文件路径；非空时复制到任务运行目录的 `job.json` |
| `writeJobFilePermissions` | `String` | 否 | `OWNER_READ,OWNER_WRITE,OWNER_EXECUTE,GROUP_READ,GROUP_EXECUTE,OTHERS_READ,OTHERS_EXECUTE` | 生成或复制到任务运行目录的 `job.json` 的 POSIX 权限，默认等价 `755` |
| `logConfigFile` | `String` | LOCAL 条件必填 | `${dataxHome}/conf/logback.xml` | DataX LOCAL 模式 logback 配置文件；`dataxHome` 为空时不会自动推导 |
| `logLevel` | `String` | 否 | `INFO` | DataX 日志级别 |
| `logMaxSize` | `String` | 否 | `100MB` | DataX logback 单文件滚动大小 |
| `logMaxIndex` | `Integer` | 否 | `100` | DataX logback 滚动文件数量 |
| `mainClass` | `String` | 否 | `com.alibaba.datax.core.Engine` | DataX LOCAL 启动主类 |
| `jvmOptions` | `List<String>` | 否 | `["--add-opens","java.base/java.lang=ALL-UNNAMED"]` | 插件级 JVM 参数；不被 `taskData` 覆盖 |
| `jobId` | `Integer/String` | 否 | `-1` | DataX `-jobid` 参数 |
| `jobMode` | `String` | 否 | `standalone` | DataX `-mode` 参数 |
| `defaultTaskData` | `Object` | 否 | 空对象 | 默认 DataX task data；当 `jobFile` 为空时与 `taskData` 深度合并后生成 `job.json` |
| `kubernetes` | `DataxKubernetesParam` | K8S 条件必填 | 空 | K8S 插件级运行配置 |

LOCAL 模式下，`pluginParam` 外层字段只能通过修改 `system_plugin_config.plugin_param` 改变，`taskData` 只覆盖 `defaultTaskData` 中的同名嵌套字段。

### 3.2 pluginParam 示例

以下示例用于说明字段结构。`datax-k8s-plugin-config.json` 中的默认模板应保留安全默认值，
不要为了展示结构写入会真实生效的示例 label、env、nodeSelector 或 resources。

```json
{
  "logLevel": "INFO",
  "logMaxSize": "100MB",
  "logMaxIndex": 100,
  "jvmOptions": [
    "-Xms512m",
    "-Xmx2g"
  ],
  "kubernetes": {
    "namespace": "datafusion",
    "image": "registry.example.com/datafusion/datax-runtime:1.0.0",
    "imagePullPolicy": "IfNotPresent",
    "serviceAccountName": "datax-runner",
    "backoffLimit": 0,
    "ttlSecondsAfterFinished": 86400,
    "namePrefix": "df-datax",
    "logStorageUri": "s3://datafusion-logs/datax/",
    "collectLogsOnFinish": true,
    "deleteJobOnFinish": false,
    "labels": {
      "app.kubernetes.io/part-of": "datafusion"
    },
    "annotations": {
      "datafusion.io/log-owner": "scheduler"
    },
    "env": {
      "TZ": "Asia/Shanghai",
      "DATAX_EXTRA_ENV": "value"
    },
    "nodeSelector": {
      "workload": "data"
    },
    "resources": {
      "requests": {
        "cpu": "1",
        "memory": "2Gi"
      },
      "limits": {
        "cpu": "2",
        "memory": "4Gi"
      }
    }
  }
}
```

LOCAL 示例：

```json
{
  "javaBin": "java",
  "dataxHome": "/opt/datafusion-builtin/plugins/datax",
  "dataxJar": "/opt/datafusion-builtin/plugins/datax/lib/datax-bundle-0.0.1.jar",
  "jobFile": "",
  "logConfigFile": "/opt/datafusion-builtin/plugins/datax/conf/logback.xml",
  "logLevel": "INFO",
  "logMaxSize": "100MB",
  "logMaxIndex": 100,
  "mainClass": "com.alibaba.datax.core.Engine",
  "jvmOptions": [
    "--add-opens",
    "java.base/java.lang=ALL-UNNAMED"
  ],
  "jobId": -1,
  "jobMode": "standalone",
  "defaultTaskData": {
    "job": {
      "setting": {
        "speed": {
          "channel": 1
        }
      }
    }
  }
}
```

## 4. TaskRequest.taskData

`taskData` 是渲染后的任务执行数据，来源于任务定义和运行实例上下文。它保存本次 DataX job 内容，以及允许单次任务覆盖的运行参数。

### 4.1 DataxTaskData

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `jobJson` | `String` 或 `Object` | 否 | 空 | DataX 标准 job JSON；存在时优先写入任务运行目录的 `job.json` |
| `job` | `Object` | 否 | 空 | 与 `pluginParam.defaultTaskData.job` 深度合并，用于生成 `job.json` |
| `kubernetes` | `DataxKubernetesTaskOverride` | 否 | 空 | K8S 单任务覆盖项 |

LOCAL 模式下，`pluginParam.jobFile` 非空时优先复制该文件到任务运行目录的 `job.json`；为空时使用 `taskData.jobJson`，再为空时使用 `deepMerge(pluginParam.defaultTaskData, taskData)` 生成 `job.json`。数组按整体替换处理，普通值按 `taskData` 覆盖默认值处理。

`taskData` 规则：

- `job.content` 使用 DataX 标准数组结构，任务定义中应直接提交完整 `content`。
- `data`、`options` 是注册扩展数据，不写入最终 DataX job。
- `kubernetes` 是 K8S 任务覆盖项，不写入最终 DataX job。

### 4.2 taskData 示例

```json
{
  "job": {
    "setting": {
      "speed": {
        "channel": 2
      }
    },
    "content": [
      {
        "reader": {
          "name": "mysqlreader",
          "parameter": {}
        },
        "writer": {
          "name": "paimonwriter",
          "parameter": {}
        }
      }
    ]
  },
  "kubernetes": {
    "namespace": "datafusion-prod",
    "image": "registry.example.com/datafusion/datax-runtime:20260608",
    "logStorageUri": "oss://datafusion/logs/20260608/task-1/",
    "collectLogsOnFinish": false,
    "resources": {
      "requests": {
        "cpu": "2",
        "memory": "4Gi"
      }
    }
  }
}
```

## 5. Kubernetes 参数

`pluginParam.kubernetes` 提供插件级默认值，`taskData.kubernetes` 提供允许单任务覆盖的运行参数。
`namePrefix` 只属于插件配置，不允许任务级覆盖；其他同名字段由任务级配置优先。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `namespace` | `String` | 否 | `default` | Kubernetes namespace |
| `image` | `String` | K8S 必填 | 无 | DataX 运行镜像；可放在 `pluginParam.kubernetes`，也可由 `taskData.kubernetes` 覆盖 |
| `imagePullPolicy` | `String` | 否 | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 否 | 空 | Pod 使用的 ServiceAccount |
| `labels` | `Object<String,String>` | 否 | 空 | 附加 Job / Pod label，不允许覆盖 `datafusion.*` 和 `datafusion.io/*` 保留 label |
| `annotations` | `Object<String,String>` | 否 | 空 | 附加 Job / Pod annotation |
| `env` | `Object<String,String>` | 否 | 空 | 容器环境变量；只允许通过 `pluginParam.kubernetes.env` 和 `taskData.kubernetes.env` 配置。`DATAX_HOME`、`DATAX_JOB_FILE`、`DATAX_LOG_FILE`、`DATAX_LOG_LEVEL`、`DATAX_LOG_MAX_SIZE`、`DATAX_LOG_MAX_INDEX`、`DATAX_JOB_ID`、`JAVA_OPTS` 由 Agent 派生 |
| `resources` | `Object` | 否 | 空 | 容器 requests / limits，结构透传 Kubernetes |
| `nodeSelector` | `Object<String,String>` | 否 | 空 | Pod nodeSelector |
| `activeDeadlineSeconds` | `Long` | 否 | 空 | Job 最大运行时间 |
| `backoffLimit` | `Integer` | 否 | `0` | Job 重试次数，默认由调度系统处理重试 |
| `ttlSecondsAfterFinished` | `Integer` | 否 | `86400` | Job 终态后的自动清理 TTL |
| `namePrefix` | `String` | 否 | `df-datax` | Kubernetes 资源名称前缀；Job 使用 `{namePrefix}-{taskInstanceId}`，Secret 使用 `{namePrefix}-job-config-{taskInstanceId}`，只允许插件配置覆盖 |
| `logStorageUri` | `String` | 否 | 空 | 外挂日志存储 URI；为空时终态可采集 Pod 日志到 Agent 本地 |
| `collectLogsOnFinish` | `Boolean` | 否 | `true` | 未配置外挂日志存储时，终态是否拉取 Pod 日志到任务运行目录 |
| `deleteJobOnFinish` | `Boolean` | 否 | `false` | `finishTask` 时是否主动删除 Job；默认交给 TTL 清理 |

`datax-k8s-plugin-config.json` 中 `labels`、`annotations`、`env`、`nodeSelector`、`resources`
默认保持空对象。这些字段不是注释样例，而是会直接渲染到 Kubernetes Job / Pod：

- `labels` 会追加到 Job 和 Pod label，但不能覆盖 `datafusion.*` 或 `datafusion.io/*` 保留 label。
- `annotations` 会追加到 Job 和 Pod annotation。
- `env` 会追加容器环境变量，只能位于 `pluginParam.kubernetes.env` 或 `taskData.kubernetes.env`；顶层 `taskData.env` 不参与 K8S 容器环境变量合并。`DATAX_HOME`、`DATAX_JOB_FILE`、`DATAX_LOG_FILE`、`DATAX_LOG_LEVEL`、`DATAX_LOG_MAX_SIZE`、`DATAX_LOG_MAX_INDEX`、`DATAX_JOB_ID`、`JAVA_OPTS` 由 Agent 派生。
- `nodeSelector` 会限制 Pod 调度节点，默认模板不要写示例值，避免目标集群没有匹配节点导致任务 Pending。
- `resources` 会作为容器 `resources.requests` / `resources.limits` 透传给 Kubernetes，默认模板不设置资源限制。

## 6. Service / Runtime 模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|------|------|------|----------|----------|------|
| `DataxRunMode` | 运行模式枚举 | `LOCAL`, `K8S` | enum | 请求解析后固定 | Agent 内部大小写归一 |
| `DataxExecutionParam` | 归一化执行参数 | `runMode`, `taskInstanceId`, `flowInstanceId`, `jobFile`, `jobJson`, `effectiveTaskData`, `workDir`, `logFile`, `dataxHome`, `dataxJar`, `jvmOptions`, `kubernetes` | Java object | 单次任务 | 从 `TaskRequest` 归一化 |
| `DataxKubernetesParam` | K8S 提交参数 | `namespace`, `jobName`, `secretName`, `image`, `labels`, `annotations`, `resources` | Java object | 单次任务 | 由 `pluginParam.kubernetes` 和 `taskData.kubernetes` 派生 |
| `DataxKubernetesRuntimeRef` | K8S 接管参数 | `namespace`, `jobName`, `secretName`, `podLabelSelector`, `containerName`, `logStorageUri`, `collectLogsOnFinish`, `deleteJobOnFinish` | Java object | 单次状态查询或控制命令 | 由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建，用于查询、停止、强杀和清理 |
| `DataxKubernetesStatus` | K8S 状态事实 | `state`, `jobExists`, `jobStatusExists`, `podExists`, `podRunning` | Java object | 单次状态查询 | Client 只返回 Kubernetes 事实，DataFusion 状态由 `DataxK8sRunModeStateMapping` 映射 |
| `DataxKubernetesCleanupMode` | K8S 清理场景 | `BEFORE_SUBMIT`, `AFTER_FINISH` | enum | 单次清理 | 提交前强清理旧资源；终态后按 `deleteJobOnFinish` 和 TTL 策略清理 |
| `LocalShellProcess` | LOCAL 模板渲染产物 | `kind`, `command` | Java object | 单次提交 | DataX LOCAL 模板只描述启动命令，工作目录和日志路径由 Agent 生成 |
| `DataxPluginTaskExecutor` | 多模式公共执行逻辑 | 请求适配、状态持久化、结果组装 | abstract class | Agent 进程 | LOCAL / K8S 执行器共用；提交快照由 `WorkerTaskService` 保存 |
| `LocalDataxPluginTaskExecutor` / `K8sDataxPluginTaskExecutor` | 模式执行器 | `LOCAL` / `K8S` | Spring Bean | Agent 进程 | 按 `DATAX + runMode` 独立注册 |
| `DataxTaskResult` | 执行结果 | `status`, `appId`, `workDirPath`, `result` | Java object | 单次动作 | 转换为 `TaskResult.workerResult` |

## 7. 状态模型

| 字段 / 枚举 | 所属对象 | 值 | 存储类型 | 转换规则 | 说明 |
|-------------|----------|----|----------|----------|------|
| `pluginType` | `WorkerTaskExecutionSnap` | `DATAX` | String | 固定大写 | DataX 插件路由键 |
| `runMode` | `WorkerTaskExecutionSnap` | `LOCAL`, `K8S` | String | 由 `DataxExecutionParam.runMode` 写入 | 状态映射和控制请求恢复按 `DATAX + runMode` 选择 |
| `appId` | `WorkerTaskExecutionState` | LOCAL: pid；K8S: Job name | String | 执行器提交后写入 | 终端任务 ID |
| `workDirPath` | `WorkerTaskExecutionState` | 任务运行目录 | String | 执行器提交后写入 | Agent 重启后周期上报仍保留任务运行目录；插件日志入口保存在 `result.pluginLogUri` |
| `status` | `WorkerTaskExecutionState` | `RUNNING`, `RUN_SUCCESS`, `RUN_FAILURE`, `STOPPING`, `STOP_SUCCESS`, `KILLING`, `KILLED`, `UNKNOWN` | `StatusEnum` | LOCAL 由退出码映射；K8S 由 Job status 和 Pod 退出状态映射 | 不新增 Agent 私有状态 |
| `exitCode` | `WorkerTaskExecutionState` | 整数或空 | Integer | LOCAL watcher 写入 | 本地运行态诊断字段，不进入 `WorkerResult`；K8S 不写入 |
| `result` | `WorkerTaskExecutionState` | `JsonNode` | Java object | 执行器写入动作结果；映射器准备终态结果后由状态监听器持久化 | 不包含完整 job JSON、密码和本地退出码；上报时映射为 `TaskResult.workerResult` |

## 8. 层间转换

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `PluginConfigEntity.runMode` -> `PluginData.runMode` -> `TaskRequest.runMode` | Manager 原样映射 | Agent 收不到 `runMode` 时提交失败，不使用默认运行模式 |
| `TaskRequest` -> `DataxExecutionParam` | `runMode` 决定执行模式；`taskData` 覆盖任务级字段，`pluginParam` 提供插件级字段 | `jobJson` 不写日志；控制请求从 `.snap` 恢复完整请求 |
| `DataxExecutionParam` -> LOCAL job file | `pluginParam.jobFile` 非空时复制到 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/job.json`；否则把 `jobJson` 或 `effectiveTaskData` 写入该文件 | 已提交任务保留本次 job 快照 |
| `DataxExecutionParam` -> LOCAL `LocalShellProcess` | 渲染 `plugins/datax/templates/datax-local-runtime.yml` | 模板保存命令静态骨架，动态值只来自 `pluginParam/taskData` 和 Agent 生成路径 |
| `DataxExecutionParam` -> K8S Secret | 先把 `jobJson` 或 `effectiveTaskData` 写入 `${taskRuntimeDir}/{date}/{flowInstanceId}/{taskInstanceId}/job.json`，再读取该快照写入 Secret key `job.json` 并挂载到 `jobJsonMountPath` | DataX JSON 可能含密码，默认不用 ConfigMap |
| K8S submit 前置清理 | `.state.appId` 存在时，以 `BEFORE_SUBMIT` 清理旧 Job、匹配 Pod 和 Secret，清理完成后再提交；清理失败返回 `SUBMIT_FAILURE` | 用于同一 taskInstanceId 重试，避免旧失败 Pod 或同名 Job 阻塞重新提交 |
| `DataxExecutionParam` -> K8S Job | 渲染固定模板并提交 `Secret + batch/v1 Job` | Job name 需满足 DNS-1123，过长时 hash 截断 |
| K8S submit result -> `WorkerTaskExecutionState.appId` | 写入 Kubernetes Job name | `.snap` 保留重建 `DataxKubernetesRuntimeRef` 所需参数，支持 Agent 重启后继续接管 |
| K8S / LOCAL plugin log -> `TaskResult.workerResult.pluginLogUri` | LOCAL 写 `local-datax.log`；K8S 写 `k8s-datax.log`、`logStorageUri` 或 `k8s://...` | 不覆盖 `workerResult.workDirPath` |
| K8S finish -> Kubernetes resources | 以 `AFTER_FINISH` 清理资源：Secret 主动删除；`deleteJobOnFinish=true` 时删除 Job，否则 Job/Pod 交给 `ttlSecondsAfterFinished` | master 确认终态后调用，清理失败返回 `false` 并记录日志 |
| K8S stop -> Kubernetes resources | 删除 Job 和本次任务 Secret | 返回 `STOPPING`，等待状态映射转终态；finish 仍会幂等兜底清理 |
| K8S kill -> Kubernetes resources | 删除 Job 和本次任务 Secret；无 Job 引用视为无残留 | 有运行引用且删除请求成功返回 `KILLING`，Job、Pod 均不存在后映射为 `KILLED`；无资源直接返回 `KILLED`；失败返回 `UNKNOWN`；finish 仍会幂等兜底清理 |
| LOCAL 退出码 -> `StatusEnum` | `0 -> RUN_SUCCESS`，非 `0 -> RUN_FAILURE` | stop/kill 已写终态时 watcher 不覆盖 |
| K8S Job / Pod status -> `StatusEnum` | `Complete -> RUN_SUCCESS`，`Failed -> RUN_FAILURE`，`active > 0 -> RUNNING`，stop 后 Pod 全部退出 -> `STOP_SUCCESS`，kill 后 Job、Pod 均不存在 -> `KILLED`，找不到且未终态 -> `UNKNOWN` | stop 先写 `STOPPING`；有运行引用的 kill 先写 `KILLING` |

## 9. WorkerResult

DataX 插件返回的 `TaskResult.workerResult` 必须使用 worker 通用 `WorkerResult` 结构。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `outputVars` | `Map<String, Variable>` | 否 | 输出变量 |
| `workerId` | `String` | 否 | worker ID |
| `appId` | `String` | 否 | LOCAL 为 pid，K8S 为 Job name |
| `workDirPath` | `String` | 否 | 任务运行目录 |
| `message` | `String` | 否 | 简短执行说明 |
| `pluginLogUri` | `String` | 否 | 插件日志入口；LOCAL 为 `local-datax.log`，K8S 为 `k8s-datax.log`、`logStorageUri` 或 `k8s://{namespace}/jobs/{jobName}` |

`WorkerResult` 不包含 `pluginType`、`runMode`、`exitCode` 或扩展 `detail`。LOCAL DataX 进程退出码只保存在 `.state.exitCode` 和 `state.log`。

K8S 示例：

```json
{
  "workerId": "f4b31edf-1f48-4d0c-ba1b-de40ccca6b41",
  "appId": "df-datax-task-1",
  "workDirPath": "/opt/datafusion/task-runtime/20260609/flow-1/task-1",
  "message": "K8S DataX job submitted",
  "pluginLogUri": "/opt/datafusion/task-runtime/20260609/flow-1/task-1/k8s-datax.log"
}
```

LOCAL 示例：

```json
{
  "workerId": "f4b31edf-1f48-4d0c-ba1b-de40ccca6b41",
  "appId": "12345",
  "workDirPath": "/opt/datafusion/task-runtime/20260609/flow-1/task-1",
  "message": "LOCAL DataX process exited",
  "pluginLogUri": "/opt/datafusion/task-runtime/20260609/flow-1/task-1/local-datax.log"
}
```

## 10. 复用对象

| 对象 | 来源 | 用途 |
|------|------|------|
| `TaskRequest` / `TaskResult` | `datafusion-common-data` | Agent 与 Manager 的任务控制报文 |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | DataX 执行器与状态映射 SPI |
| `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | Agent 本地状态记录 |
| `AgentTaskStateListenerRegistry` | `datafusion-agent` | 按任务注册周期监听并在状态变化后上报 DataX 状态 |
| `DataxJsonService` / `DataxJsonVo` | `datafusion-manager` | 生成标准 DataX job JSON |
| `datafusion-plugin-datax` resources | `datafusion-plugin` | LOCAL 模式 DataX bundle、job 示例和 K8S 镜像构建输入 |
