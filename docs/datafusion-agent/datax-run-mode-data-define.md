# DataX 运行模式数据结构定义

> 本文档是 `datafusion-agent` 侧 DataX LOCAL / K8S 运行模式的字段、任务参数和状态持久化事实源。Agent 总体运行时结构见 [agent-data-define.md](./agent-data-define.md)。

## 1. 表结构

无。DataX 运行模式第一版不新增 Agent 侧数据库表。

Manager 侧已有 `scheduler_task_info.definition`、`scheduler_task_instance.task_data`、`system_plugin_config.env` 和 `system_plugin_config.run_mode` 可以承载任务定义与插件运行配置；如后续要把 `runMode` 做成跨模块一等字段，应另行更新 scheduler / common-data 文档。

## 2. 配置边界

DataX 的 LOCAL / K8S 运行参数不定义在 `datafusion-agent/src/main/resources/application.yml` 中，也不通过 `datafusion.agent.datax.*` 读取。Agent 只使用已有基础运行配置定位本地状态和日志目录：

| 对象 | 字段 | 说明 |
|------|------|------|
| `AgentProperties` | `modules` | Agent 模块根目录，用于拼出本地 work/log/state 路径 |
| `AgentProperties.Storage` | `logsDir` | Agent 本地日志目录名 |
| `AgentProperties.Storage` | `taskRuntimeDir` | Agent 本地 `task-runtime` 目录名 |
| `AgentProperties.Kubernetes` | `apiServer`, `token`, `tokenFile`, `caCertFile` | 仅用于 Fabric8 Kubernetes client 连接集群；不是 DataX Job 参数 |

DataX LOCAL / K8S 的静态结构均由 YAML 模板管理：

| runMode | 模板 | 渲染产物 |
|---------|------|----------|
| `LOCAL` | `datafusion-agent/src/main/resources/plugins/datax/templates/datax-local-runtime.yml` | `LocalProcessSpec` |
| `K8S` | `datafusion-agent/src/main/resources/plugins/datax/templates/datax-k8s-runtime.yml` | Kubernetes manifest |

`jobJsonMountPath=/datafusion/job/job.json`、`containerName=datax`、`DATAX_HOME=/opt/datafusion/datax` 等 K8S 结构常量由模板和 `DataxKubernetesTemplateConstants` 维护。

## 3. TaskRequest.pluginParam

`pluginParam` 是插件级运行配置，来源于 Manager 侧 `system_plugin_config.env`，并由 Manager 把 `PluginConfigEntity.runMode` 注入到 `pluginParam.runMode`。Agent 不从 `taskData.runMode` 或配置文件推断运行模式。

### 3.1 DataxPluginParam

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `runMode` | `String` | 是 | 无 | 只允许 `LOCAL` 或 `K8S` |
| `resourcesRoot` | `String` | LOCAL 条件必填 | 无 | DataX 插件资源根目录，通常包含 `datax/`、`job/` |
| `dataxHome` | `String` | LOCAL 条件必填 | `${resourcesRoot}/datax` | DataX home；`resourcesRoot` 为空时不会自动推导 |
| `dataxJar` | `String` | LOCAL 条件必填 | `${dataxHome}/lib/datax-bundle-0.0.1.jar` | DataX bundle jar；`dataxHome` 为空时不会自动推导 |
| `logbackConfigFile` | `String` | LOCAL 条件必填 | `${dataxHome}/conf/logback.xml` | DataX LOCAL 模式 logback 配置文件；`dataxHome` 为空时不会自动推导 |
| `javaBin` | `String` | 否 | `java` | LOCAL 模式启动 DataX 使用的 Java 命令 |
| `logLevel` | `String` | 否 | `INFO` | DataX 日志级别 |
| `logMaxSize` | `String` | 否 | `100MB` | DataX logback 单文件滚动大小 |
| `logMaxIndex` | `Integer` | 否 | `100` | DataX logback 滚动文件数量 |
| `writeJobFilePermissions` | `String` | 否 | `OWNER_READ,OWNER_WRITE` | Agent 写入 `jobJson` 本地临时文件后的 POSIX 权限 |
| `env` | `Object<String,String>` | 否 | 空 | 插件级环境变量，任务级 `taskData.env` 可覆盖同名键 |
| `jvmOptions` | `List<String>` | 否 | `["--add-opens","java.base/java.lang=ALL-UNNAMED"]` | 插件级 JVM 参数，任务级 `taskData.jvmOptions` 会追加 |
| `kubernetes` | `DataxKubernetesParam` | K8S 条件必填 | 空 | K8S 插件级运行配置 |

LOCAL 模式下，`dataxHome` / `dataxJar` 的条件必填含义是：如果任务使用 `jobJson` 或 `jobFileName` 提交，并且不能通过 `resourcesRoot` 推导出 DataX bundle，则本地进程启动会失败。

### 3.2 pluginParam 示例

```json
{
  "runMode": "K8S",
  "logLevel": "INFO",
  "logMaxSize": "100MB",
  "logMaxIndex": 100,
  "env": {
    "TZ": "Asia/Shanghai"
  },
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
    "jobNamePrefix": "df-datax-",
    "secretNamePrefix": "df-datax-job-",
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
      "JAVA_OPTS": "-Xmx2g"
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
  "runMode": "LOCAL",
  "resourcesRoot": "/opt/datafusion/plugins/datax",
  "dataxHome": "/opt/datafusion/plugins/datax/datax",
  "dataxJar": "/opt/datafusion/plugins/datax/datax/lib/datax-bundle-0.0.1.jar",
  "logbackConfigFile": "/opt/datafusion/plugins/datax/datax/conf/logback.xml",
  "javaBin": "/usr/bin/java",
  "logLevel": "INFO"
}
```

## 4. TaskRequest.taskData

`taskData` 是渲染后的任务执行数据，来源于任务定义和运行实例上下文。它保存本次 DataX job 内容，以及允许单次任务覆盖的运行参数。

### 4.1 DataxTaskData

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `jobName` | `String` | 否 | `${taskInstanceId}.json` | Agent 生成 job 文件和日志文件时使用的名称 |
| `jobJson` | `String` 或 `Object` | 条件必填 | 空 | DataX 标准 job JSON；Manager 侧 `DataxJsonService` 生成的 JSON 可直接放入此字段 |
| `jobFileName` | `String` | 条件必填 | 空 | 复用 `pluginParam.resourcesRoot/job/` 下已有 job 文件时使用 |
| `jobPath` | `String` | 条件必填 | 空 | Agent 本机已存在 job JSON 的绝对路径；仅 LOCAL 模式允许 |
| `env` | `Object<String,String>` | 否 | 空 | 本任务环境变量，覆盖 `pluginParam.env` 同名键 |
| `jvmOptions` | `List<String>` | 否 | 空 | 本任务追加 JVM 参数 |
| `dataxArgs` | `List<String>` | 否 | 空 | `com.alibaba.datax.core.Engine` 额外命令行参数 |
| `kubernetes` | `DataxKubernetesTaskOverride` | 否 | 空 | K8S 单任务覆盖项 |

`jobJson`、`jobFileName`、`jobPath` 至少提供一个。实际取值优先级为 `jobJson` > `jobPath` > `jobFileName`；K8S 模式收到 `jobPath` 时直接拒绝提交。

### 4.2 taskData 示例

```json
{
  "jobName": "ods_customer_20260608.json",
  "jobJson": {
    "job": {
      "setting": {
        "speed": {
          "channel": 2
        }
      },
      "content": []
    }
  },
  "env": {
    "BIZ_DATE": "2026-06-08"
  },
  "jvmOptions": [
    "-Ddatax.trace.id=task-20260608"
  ],
  "dataxArgs": [
    "-Dcustom.param=value"
  ],
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

`pluginParam.kubernetes` 提供插件级默认值，`taskData.kubernetes` 提供单任务覆盖值。两者字段结构相同，任务级字段优先。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `namespace` | `String` | 否 | `default` | Kubernetes namespace |
| `image` | `String` | K8S 必填 | 无 | DataX 运行镜像；可放在 `pluginParam.kubernetes`，也可由 `taskData.kubernetes` 覆盖 |
| `imagePullPolicy` | `String` | 否 | `IfNotPresent` | 镜像拉取策略 |
| `serviceAccountName` | `String` | 否 | 空 | Pod 使用的 ServiceAccount |
| `labels` | `Object<String,String>` | 否 | 空 | 附加 Job / Pod label，不允许覆盖 `datafusion.*` 和 `datafusion.io/*` 保留 label |
| `annotations` | `Object<String,String>` | 否 | 空 | 附加 Job / Pod annotation |
| `env` | `Object<String,String>` | 否 | 空 | 容器环境变量 |
| `resources` | `Object` | 否 | 空 | 容器 requests / limits，结构透传 Kubernetes |
| `nodeSelector` | `Object<String,String>` | 否 | 空 | Pod nodeSelector |
| `activeDeadlineSeconds` | `Long` | 否 | 空 | Job 最大运行时间 |
| `backoffLimit` | `Integer` | 否 | `0` | Job 重试次数，默认由调度系统处理重试 |
| `ttlSecondsAfterFinished` | `Integer` | 否 | `86400` | Job 终态后的自动清理 TTL |
| `jobNamePrefix` | `String` | 否 | `df-datax-` | Kubernetes Job 名称前缀 |
| `secretNamePrefix` | `String` | 否 | `df-datax-job-` | 承载 DataX job JSON 的 Secret 名称前缀 |
| `logStorageUri` | `String` | 否 | 空 | 外挂日志存储 URI；为空时终态可采集 Pod 日志到 Agent 本地 |
| `collectLogsOnFinish` | `Boolean` | 否 | `true` | 未配置外挂日志存储时，终态是否拉取 Pod 日志到 Agent 本地日志目录 |
| `deleteJobOnFinish` | `Boolean` | 否 | `false` | `finishTask` 时是否主动删除 Job；默认交给 TTL 清理 |

## 6. Service / Runtime 模型

| 对象 | 场景 | 字段 | 字段类型 | 生命周期 | 说明 |
|------|------|------|----------|----------|------|
| `DataxRunMode` | 运行模式枚举 | `LOCAL`, `K8S` | enum | 请求解析后固定 | Agent 内部大小写归一 |
| `DataxExecutionParam` | 提交前归一化参数 | `runMode`, `jobName`, `jobJson`, `jobFileName`, `jobPath`, `logDir`, `workDir`, `env`, `jvmOptions`, `kubernetes` | Java object | 单次任务 | 从 `pluginParam` 和 `taskData` 归一化；不读取 `datafusion.agent.datax.*` |
| `DataxKubernetesParam` | K8S 提交参数 | `namespace`, `jobName`, `secretName`, `image`, `labels`, `annotations`, `resources` | Java object | 单次任务 | 由 `pluginParam.kubernetes` 和 `taskData.kubernetes` 派生 |
| `DataxKubernetesRuntimeRef` | K8S 接管参数 | `namespace`, `jobName`, `secretName`, `podLabelSelector`, `containerName`, `logStorageUri`, `collectLogsOnFinish`, `deleteJobOnFinish` | Java object | 单次状态查询或控制命令 | 由 `.snap` 中的 `pluginParam/taskData` 和 `.state.appId` 重建，用于查询、停止、强杀和清理 |
| `LocalProcessSpec` | LOCAL 模板渲染产物 | `kind`, `workDir`, `command`, `env`, `stdout`, `stderr`, `pluginLogUri` | Java object | 单次提交 | Shell / DataX LOCAL 共用；Runner 只按 spec 启动本地进程 |
| `DataxTaskRunner` | 运行模式分派 | `runMode()` | interface | Spring Bean | LOCAL / K8S Runner 实现同一接口 |
| `DataxSubmitResult` | Runner 提交返回 | `status`, `appId`, `logPath`, `result`, `kubernetesRuntimeRef` | Java object | 单次提交 | 由 `DataxPluginTaskExecutor` 转为 `TaskResult`；K8S runtime ref 用于持久化接管信息 |

## 7. 状态模型

| 字段 / 枚举 | 所属对象 | 值 | 存储类型 | 转换规则 | 说明 |
|-------------|----------|----|----------|----------|------|
| `pluginType` | `WorkerTaskExecutionSnap` | `DATAX` | String | 固定大写 | DataX 插件路由键 |
| `runMode` | `WorkerTaskExecutionSnap` | `LOCAL`, `K8S` | String | 由 `DataxExecutionParam.runMode` 写入 | 状态映射按 `DATAX + runMode` 选择 |
| `appId` | `WorkerTaskExecutionState` | LOCAL: pid；K8S: Job name | String | Runner 提交后写入 | 终端任务 ID |
| `logPath` | `WorkerTaskExecutionState` | Agent 本地日志目录 | String | Runner 提交后写入 | Agent 重启后周期上报仍保留日志入口；第三方日志 URI 写入 `TaskResult.result.pluginLogUri` |
| `status` | `WorkerTaskExecutionState` | `RUNNING`, `RUN_SUCCESS`, `RUN_FAILURE`, `STOPPING`, `STOP_SUCCESS`, `KILLING`, `KILLED`, `UNKNOWN` | `StatusEnum` | LOCAL 由退出码映射；K8S 由 Job status 和 Pod 退出状态映射 | 不新增 Agent 私有状态 |
| `exitCode` | `WorkerTaskExecutionState` | 整数或空 | Integer | LOCAL watcher 写入 | K8S 不写入 |
| `result` | `WorkerTaskExecutionState` | JSON 对象 | JsonNode | Runner / 状态映射写入 | 不包含完整 job JSON 和密码 |

## 8. 层间转换

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `PluginConfigEntity.runMode` -> `TaskRequest.pluginParam.runMode` | Manager 必须在 `TaskStorageImpl.toPluginData` 中注入 | Agent 收不到 `runMode` 时提交失败，不使用默认运行模式 |
| `TaskRequest.pluginParam` + `TaskRequest.taskData` -> `DataxExecutionParam` | `taskData` 覆盖任务级字段，`pluginParam` 提供插件级字段；缺失字段只使用代码内协议默认值 | `jobJson` 不写日志 |
| `DataxExecutionParam` -> LOCAL job file | `jobJson` 写入 `${modules}/datax-work/{date}/{flowInstanceId}/{taskInstanceId}/{jobName}` | 文件权限按 `writeJobFilePermissions` 设置 |
| `DataxExecutionParam` -> LOCAL `LocalProcessSpec` | 渲染 `plugins/datax/templates/datax-local-runtime.yml` | 模板保存命令静态骨架，动态值只来自 `pluginParam/taskData` 和 Agent 生成路径 |
| `DataxExecutionParam` -> K8S Secret | `jobJson` 或 `jobFileName` 对应内容写入 Secret key `job.json` 并挂载到 `jobJsonMountPath` | DataX JSON 可能含密码，默认不用 ConfigMap |
| `DataxExecutionParam` -> K8S Job | 渲染固定模板并提交 `Secret + batch/v1 Job` | Job name 需满足 DNS-1123，过长时 hash 截断 |
| K8S submit result -> `WorkerTaskExecutionState.appId` | 写入 Kubernetes Job name | `.snap` 保留重建 `DataxKubernetesRuntimeRef` 所需参数，支持 Agent 重启后继续接管 |
| LOCAL 退出码 -> `StatusEnum` | `0 -> RUN_SUCCESS`，非 `0 -> RUN_FAILURE` | stop/kill 已写终态时 watcher 不覆盖 |
| K8S Job / Pod status -> `StatusEnum` | `Complete -> RUN_SUCCESS`，`Failed -> RUN_FAILURE`，`active > 0 -> RUNNING`，stop 后 Pod 全部退出 -> `STOP_SUCCESS`，kill 后 Pod 全部退出 -> `KILLED`，找不到且未终态 -> `UNKNOWN` | stop/kill 发起删除后先写 `STOPPING` / `KILLING` |

## 9. TaskResult.result

DataX 插件返回的 `TaskResult.result` 必须是紧凑 JSON 对象，字段结构复用 worker 通用 `TaskResultResult`。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | `String` | 是 | 简短执行说明 |
| `pluginType` | `String` | 是 | 固定 `DATAX` |
| `runMode` | `String` | 是 | `LOCAL` 或 `K8S` |
| `pluginLogUri` | `String` | 否 | K8S Job 日志入口或 `logStorageUri`；LOCAL 通常为空 |
| `agentLogPath` | `String` | 否 | Agent 本地日志目录或终态采集到的日志文件路径 |
| `exitCode` | `Integer` | 否 | LOCAL DataX 进程退出码 |
| `detail` | `Object` | 否 | 小体积扩展信息，不写完整 job JSON 和密码 |

K8S 示例：

```json
{
  "message": "K8S DataX job submitted",
  "pluginType": "DATAX",
  "runMode": "K8S",
  "pluginLogUri": "oss://datafusion/logs/20260608/task-1/",
  "agentLogPath": "/opt/datafusion-agent/logs/20260609/flow-1/task-1"
}
```

LOCAL 示例：

```json
{
  "message": "LOCAL DataX process exited, exitCode=0",
  "pluginType": "DATAX",
  "runMode": "LOCAL",
  "agentLogPath": "/opt/datafusion-agent/logs/20260609/flow-1/task-1",
  "exitCode": 0
}
```

## 10. 复用对象

| 对象 | 来源 | 用途 |
|------|------|------|
| `TaskRequest` / `TaskResult` | `datafusion-common-data` | Agent 与 Manager 的任务控制报文 |
| `PluginTaskExecutor` / `PluginRunModeStateMapping` | `datafusion-scheduler-worker` | DataX 执行器与状态映射 SPI |
| `WorkerTaskExecutionState` / `WorkerTaskExecutionStore` | `datafusion-scheduler-worker` | Agent 本地状态记录 |
| `AgentTaskStateReportScheduler` | `datafusion-agent` | 周期刷新并上报 DataX 状态 |
| `DataxJsonService` / `DataxJsonVo` | `datafusion-manager` | 生成标准 DataX job JSON |
| `datafusion-plugin-datax` resources | `datafusion-plugin` | LOCAL 模式 DataX bundle、job 示例和 K8S 镜像构建输入 |
