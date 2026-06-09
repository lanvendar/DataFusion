# 插件运行模式模板化设计

> 本文档定义 `datafusion-agent` 插件运行模式的统一模板机制。Shell 和 DataX 插件必须遵守本文档的模板、渲染上下文和 `ExecutionSpec` 边界。

## 1. 目标

插件运行模式使用 YAML 模板管理静态结构，使用 `TaskRequest.pluginParam` 和 `TaskRequest.taskData` 提供动态渲染参数。

核心链路：

```text
TaskRequest(pluginParam, taskData)
    -> ParamResolver 校验和合并参数
    -> YAML template 渲染
    -> typed ExecutionSpec
    -> Runner 执行 ExecutionSpec
```

## 2. 边界

| 对象 | 职责 | 禁止事项 |
|------|------|----------|
| `pluginParam` | 插件级运行参数、默认值、runMode | 不保存 Agent 本地日志和状态目录 |
| `taskData` | 单次任务数据、单任务覆盖、渲染后的业务数据 | 不保存插件级全局默认值 |
| YAML template | runMode 静态结构、命令骨架、manifest 骨架、动态占位符 | 不做业务推导、不保存密码明文日志 |
| `ParamResolver` | 校验必填、合并 `pluginParam/taskData`、生成渲染上下文 | 不提交任务 |
| `TemplateRenderer` | 加载模板、替换占位符、转成 typed spec | 不查询进程、不查询 K8S |
| `Runner` | 执行 typed spec、写状态、控制终端任务 | 不从 `application.yml` 读取第三方运行参数 |

`application.yml` 只保留 Agent 自身配置，例如日志目录、状态目录、线程池和 Kubernetes client 连接信息。

## 3. 模板规则

模板文件位于 `datafusion-agent/src/main/resources/templates/{plugin}/`。

命名规则：

| pluginType | runMode | 模板 | 渲染产物 |
|------------|---------|------|----------|
| `SHELL` | `LOCAL` | `templates/shell/shell-local.yml` | `LocalProcessSpec` |
| `DATAX` | `LOCAL` | `templates/datax/datax-local.yml` | `LocalProcessSpec` |
| `DATAX` | `K8S` | `templates/datax/datax-k8s-job.yml` | `KubernetesManifestSpec` |

模板占位符使用 `{{name}}`。占位符值必须来自以下上下文：

| 上下文 | 来源 | 示例 |
|--------|------|------|
| `pluginParam` 派生值 | `TaskRequest.pluginParam` | `javaBin`, `dataxHome`, `kubernetes.image` |
| `taskData` 派生值 | `TaskRequest.taskData` | `jobName`, `jobFile`, `dataxArgs` |
| Agent 生成值 | Agent 本地路径和运行时 ID | `agentLogDir`, `workDir`, `stdout`, `stderr` |

Agent 生成值必须能从 `AgentProperties` 和 `TaskRequest` 推导，不允许来自插件运行配置文件。

## 4. ExecutionSpec

### 4.1 LocalProcessSpec

`LocalProcessSpec` 是本地进程运行模式的统一执行计划。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `kind` | `String` | 是 | 固定 `LocalProcessSpec` |
| `workDir` | `String` | 否 | 进程工作目录 |
| `command` | `List<String>` | 是 | 完整命令行，第一项为可执行文件 |
| `env` | `Map<String,String>` | 否 | 进程环境变量 |
| `stdout` | `String` | 是 | 标准输出文件 |
| `stderr` | `String` | 是 | 标准错误文件 |
| `pluginLogUri` | `String` | 否 | 插件第三方日志 URI |

### 4.2 KubernetesManifestSpec

`KubernetesManifestSpec` 第一版不新增 Java DTO，渲染结果是 Kubernetes YAML 字符串，由 Fabric8 `load(...).createOrReplace()` 提交。运行时接管信息仍写入 `DataxKubernetesRuntimeRef`。

## 5. Shell LOCAL

`SHELL + LOCAL` 使用 `shell-local.yml` 渲染 `LocalProcessSpec`。

参数规则：

- `pluginParam.runMode` 由 Manager 注入，Shell 第一版固定 `LOCAL`。
- `command` 可来自 `pluginParam.command` 或 `taskData.command`；推荐固定放在 `pluginParam.command`。
- `args` 由 `pluginParam.args` 后接 `taskData.args`。
- `env` 由 `pluginParam.env` 被 `taskData.env` 覆盖。
- `pluginLogUri` 优先取 `taskData.pluginLogUri`，其次取 `pluginParam.pluginLogUri`。

## 6. DataX LOCAL

`DATAX + LOCAL` 使用 `datax-local.yml` 渲染 `LocalProcessSpec`。

参数规则：

- `pluginParam.runMode` 必填，且为 `LOCAL`。
- `jobJson`、`jobPath`、`jobFileName` 至少一个必填。
- `jobJson` 提交时由 `DataxJobFileService` 写入 Agent 本地 job 文件。
- `command` 的静态结构放在模板中；`javaBin`、`jvmOptions`、`dataxHome`、`dataxJar`、`jobFile` 和 `dataxArgs` 作为渲染参数。
- `env` 由 `pluginParam.env` 被 `taskData.env` 覆盖，模板追加 DataX 日志相关环境变量。

## 7. DataX K8S

`DATAX + K8S` 使用 `datax-k8s-job.yml` 渲染 Kubernetes manifest。

参数规则：

- `pluginParam.runMode` 必填，且为 `K8S`。
- `pluginParam.kubernetes.image` 或 `taskData.kubernetes.image` 必填。
- K8S 参数由 `pluginParam.kubernetes` 被 `taskData.kubernetes` 覆盖。
- Job / Secret 名称由 Agent 根据任务实例 ID 生成，写入渲染上下文。
- 提交成功后必须把 `DataxKubernetesRuntimeRef` 写入 `.state.pluginParam._runtime`，用于 Agent 重启接管。

## 8. 错误处理

| 场景 | 行为 |
|------|------|
| 模板缺失 | 提交失败，返回 `SUBMIT_FAILURE` |
| 占位符缺失 | 提交失败，返回 `SUBMIT_FAILURE` |
| YAML 解析失败 | 提交失败，返回 `SUBMIT_FAILURE` |
| `LocalProcessSpec.command` 为空 | 提交失败，返回 `SUBMIT_FAILURE` |
| K8S 终态日志采集失败 | 不改变任务终态；`pluginLogUri` 有值时直接透传 |

## 9. 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
mvn -pl datafusion-agent -am -Dtest=TemplateSpecRendererTest,DataxParamResolverTest,DataxPluginTaskExecutorTest,K8sDataxTaskRunnerTest,AgentWorkerTaskContextStorageTest,DataxKubernetesTemplateRendererTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```
