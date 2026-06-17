# 插件运行模式模板化设计

本文定义 agent 插件运行模式的统一模板机制。Shell 和 DataX 插件都应使用同一套边界：静态结构放模板，动态值来自 `TaskRequest.pluginParam`、`TaskRequest.taskData` 和 agent 生成的运行时路径。

## 目标

```text
TaskRequest(pluginParam, taskData)
    -> ParamResolver 校验和合并参数
    -> YAML template 渲染
    -> typed ExecutionSpec
    -> Runner 执行 ExecutionSpec
```

`application.yml` 只保留 agent 自身配置，例如日志目录、状态目录、线程池和 Kubernetes client 连接信息；不保存插件第三方运行参数。

## 边界

| 对象 | 职责 |
|------|------|
| `pluginParam` | 插件级默认参数、运行模式、环境参数 |
| `taskData` | 单次任务数据和覆盖参数 |
| YAML template | runMode 的静态命令骨架或 manifest 骨架 |
| `ParamResolver` | 校验必填、合并参数、生成渲染上下文 |
| `TemplateRenderer` | 加载模板、替换占位符、生成 typed spec |
| `Runner` | 执行 spec、写状态、控制终端任务 |

模板不做业务推导，不记录密码明文，不查询进程或外部系统。

## 模板规则

模板文件放在：

```text
datafusion-agent/src/main/resources/plugins/{plugin}/templates/
```

运行模板命名为 `{plugin}-{runMode}-runtime.yml`，对应的 Manager 插件配置模板命名为
`{plugin}-{runMode}-plugin-config.json`。

已定义模板：

| pluginType | runMode | 模板 | 渲染产物 |
|------------|---------|------|----------|
| `SHELL` | `LOCAL` | `plugins/shell/templates/shell-local-runtime.yml` | `LocalProcessSpec` |
| `DATAX` | `LOCAL` | `plugins/datax/templates/datax-local-runtime.yml` | `LocalProcessSpec` |
| `DATAX` | `K8S` | `plugins/datax/templates/datax-k8s-runtime.yml` | Kubernetes YAML |

Shell 第一版只有 `LOCAL`，实现按 `shell.local` 包归类；只有出现第二种 Shell 运行模式时，才引入类似 DataX 的 runner 分发抽象。

占位符使用 `{{name}}`。值只能来自：

- `pluginParam` 派生值。
- `taskData` 派生值。
- agent 根据任务实例生成的路径、日志文件、工作目录、运行 ID。

## ExecutionSpec

`LocalProcessSpec` 是本地进程执行计划：

| 字段 | 说明 |
|------|------|
| `kind` | 固定 `LocalProcessSpec` |
| `workDir` | 工作目录 |
| `command` | 完整命令行，第一项为可执行文件 |
| `env` | 环境变量 |
| `stdout` / `stderr` | 标准输出和错误日志文件 |
| `pluginLogUri` | 插件第三方日志入口 |

`KubernetesManifestSpec` 第一版不新增 Java DTO，渲染结果是 Kubernetes YAML 字符串，由 Fabric8 提交。

## 插件规则

- `SHELL + LOCAL`：`command` 可来自 `pluginParam.command` 或 `taskData.command`；`taskData` 覆盖任务级参数。
- `DATAX + LOCAL`：`jobJson`、`jobPath`、`jobFileName` 至少一个必填；渲染为本地 DataX 进程。
- `DATAX + K8S`：镜像由 `pluginParam.kubernetes.image` 或 `taskData.kubernetes.image` 提供；运行引用由 `.snap`
  参数和 `.state.appId` 重建。

## 错误处理

| 场景 | 行为 |
|------|------|
| 模板缺失、占位符缺失、YAML 解析失败 | 提交失败，返回 `SUBMIT_FAILURE` |
| `LocalProcessSpec.command` 为空 | 提交失败，返回 `SUBMIT_FAILURE` |
| K8S 终态日志采集失败 | 不改变任务终态，透传可用的 `pluginLogUri` |

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-agent -am
```
