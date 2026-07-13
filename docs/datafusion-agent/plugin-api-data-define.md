# API 插件数据结构定义

> 本文档是 `datafusion-agent` 侧 API LOCAL 运行模式的字段、任务参数和状态持久化事实源。
> Shell LOCAL 字段见 [plugin-shell-data-define.md](./plugin-shell-data-define.md)。

## 1. 表结构

无。API 插件不新增 agent 侧数据库表。

Manager 侧新增插件配置时使用 `pluginType=API`、`runMode=LOCAL`。

默认模板位于：

```text
datafusion-agent/src/main/resources/plugins/api/templates/api-local-plugin-config.json
datafusion-agent/src/main/resources/plugins/api/templates/api-local-runtime.yml
```

API 是单包插件，发布目录就是 `plugins/api/` 本身，不再下钻 app 子目录。构建侧发布策略放在
`datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json`，公共 builder 启动时读取
`modulePath`、`artifactId`、`runtimeResourceDir`、`agentPublishDir` 并校验 `pluginType=API`，该文件不进入
`plugins/api/` 运行目录。

## 2. Agent 配置

API 插件入口随 agent 启动加载，不提供独立启用开关。worker 是否上报 `API` 能力，由
`AgentProperties.Worker.pluginTypes` 控制。

部署环境变量：

```text
DATAFUSION_WORKER_PLUGIN_TYPES=API
```

## 3. TaskRequest.pluginParam

`runMode=LOCAL` 是模板顶层字段，不放入 `pluginParam`。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `launchMode` | `String` | 否 | `JAR` | 启动模式，枚举为 `JAR`、`CLASSPATH` |
| `javaBin` | `String` | 否 | `java` | Java 可执行命令 |
| `apiJar` | `String` | `launchMode=JAR` 时是 | `/opt/datafusion/plugins/api/datafusion-plugin-api-1.0.0-executable.jar` | API 抽数 executable jar 路径 |
| `classpath` | `String` | `launchMode=CLASSPATH` 时是 | 空 | thin jar + lib 的 classpath |
| `mainClass` | `String` | `launchMode=CLASSPATH` 时是 | `com.datafusion.plugin.api.ApiExtractApplication` | CLASSPATH 模式启动主类 |
| `jvmOptions` | `List<String>` | 否 | 见模板 | JVM 参数，追加在 `javaBin` 后、`-jar/-classpath` 前 |
| `logHome` | `String` | 否 | `logs` | API 进程日志目录，传给 logback 的 `LOG_HOME` |
| `logLevel` | `String` | 否 | `INFO` | API 进程日志级别 |
| `logMaxSize` | `String` | 否 | `100MB` | API 进程日志滚动大小 |
| `logMaxIndex` | `Integer` | 否 | `100` | API 进程日志保留索引 |
| `logConfigFile` | `String` | 否 | `/opt/datafusion/plugins/api/conf/logback.xml` | logback 配置文件路径 |
| `defaultTaskData` | `Object` | 否 | 空对象 | 默认 API job 配置，与 `taskData` 深度合并 |
| `env` | `Object<String,String>` | 否 | 空 | 插件级环境变量 |
| `pluginLogUri` | `String` | 否 | 空 | 插件日志入口；为空时不写 |

## 4. TaskRequest.taskData

`taskData` 必须是完整的 API 抽数 job JSON 对象，结构与 `datafusion-plugin-api` 的 `ApiExtractJobConfig`
一致，例如包含 `job`、`runtime`、`httpConfig`、`steps`、`sink` 等字段。
agent 不从 `taskData` 读取 jar、JVM、env 等运行参数。

提交前 agent 合并 `pluginParam.defaultTaskData` 与 `taskData`，过滤调度属性 `bizRef` 和 `trigger` 后写入：

```text
${taskRuntimeDir}/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/api-job.json
```

## 5. 参数归一化

API LOCAL 提交前先把 `taskData` 写入 `api-job.json`，再渲染 `api-local-runtime.yml` 并转换为 Shell LOCAL 请求：

`api-local-runtime.yml` 只有一份模板，executor 根据 `launchMode` 生成 `launchArgs`：
`JAR` 生成 `-jar {apiJar}`，`CLASSPATH` 生成 `-classpath {classpath} {mainClass}`。
executor 固定追加 `-job api-job.json`，API 程序从当前工作目录读取该 job 文件。

```json
{
  "pluginType": "API",
  "pluginParam": {
    "command": "java",
    "args": [
      "-jar",
      "/opt/datafusion/plugins/api/datafusion-plugin-api-1.0.0-executable.jar",
      "-job",
      "api-job.json"
    ],
    "env": {}
  },
  "taskData": {}
}
```

`.snap` 保存的是归一化后的 Shell `pluginParam/taskData`，运行目录中的 `api-job.json` 保存本次 API job 快照。

`launchMode=CLASSPATH` 时归一化命令示例：

```json
{
  "pluginType": "API",
  "pluginParam": {
    "command": "java",
    "args": [
      "-classpath",
      "/opt/datafusion/plugins/api/lib/*:/opt/datafusion/plugins/api/datafusion-plugin-api-1.0.0.jar",
      "com.datafusion.plugin.api.ApiExtractApplication",
      "-job",
      "api-job.json"
    ],
    "env": {}
  },
  "taskData": {}
}
```

## 6. Runtime 模型

| 对象 | 场景 | 字段 | 生命周期 | 说明 |
|------|------|------|----------|------|
| `WorkerTaskExecutionSnap` | 提交快照 | `pluginType=API`, `runMode=LOCAL`, `taskData`, `pluginParam` | 提交后到 finish | 控制请求恢复上下文 |
| `WorkerTaskExecutionState` | 运行态 | `appId`, `workDirPath`, `status`, `exitCode`, `result` | 提交后到 finish | 与 Shell LOCAL 一致，`appId` 为顶层进程 PID |

## 7. WorkerResult

复用 Shell LOCAL `WorkerResult` 结构。`pluginType` 和 `runMode` 不写入 `WorkerResult`，只保存在 `.snap` 和结果摘要 JSON 中。
