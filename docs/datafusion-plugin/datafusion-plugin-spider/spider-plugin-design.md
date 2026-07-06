# datafusion-plugin-spider 设计

## 背景与目标

当前 SPIDER 任务依赖 `sh-web-spider` 运行时与 browser-agent，当前采用一体化镜像部署（datafusion-agent-spider）。为了降低镜像耦合、统一插件化交付方式，并保持与 `kafka-json` / `datax` 的发布方式一致，采用统一的插件化交付实现：

- SPIDER 任务执行能力继续复用 Agent 的 `Shell LOCAL` 执行链路（`PLUGIN_TYPE=SPIDER` 的轻量包装）。
- `sh-web-spider` 运行时从镜像打包中抽离到插件资源，交由 `datafusion-plugin` 的插件发布链路管理。
- 通过 `DATAFUSION_WORKER_PLUGIN_TYPES` 指定该 Worker 支持 `SPIDER`。

> 相关前提：`datafusion-agent` 已存在 `spider/local` 执行器与状态映射（`pluginType=SPIDER`，`runMode=LOCAL`），可直接被路由。

## 现状对齐

- 可执行器
  - [SpiderLocalPluginTaskExecutor](/Users/lanvendar/Projects/DataFusion/datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spider/local/SpiderLocalPluginTaskExecutor.java)（执行实现）
  - [SpiderLocalRunModeStateMapping](/Users/lanvendar/Projects/DataFusion/datafusion-agent/src/main/java/com/datafusion/agent/runtime/worker/plugin/spider/local/SpiderLocalRunModeStateMapping.java)（状态映射）
- 插件模板
  - [spider-local-plugin-config.json](/Users/lanvendar/Projects/DataFusion/datafusion-agent/src/main/resources/plugins/spider/templates/spider-local-plugin-config.json)（可直接复用）
- `sh-web-spider` 打包产物
  - `browser-agent-linux-amd64-runtime.tar.gz`
  - `sh-web-spider-linux-amd64-runtime.tar.gz`

本实现无需新增执行算法代码，需补齐资源化交付与任务参数/环境对齐。

## 方案范围（数据层面）

`SPIDER` 任务使用 `runMode=LOCAL`，其 `pluginParam`/`taskData` 行为与 SHELL LOCAL 一致；任务命令示例：

```bash
cd /opt/datafusion/plugins/spider/sh-web-spider && ./run-spider.sh --site bkccpr --date-range ...
```

插件模板建议仍采用：

```json
{
  "command": "sh",
  "args": ["-c"],
  "env": {},
  "pluginLogUri": ""
}
```

`taskData.args` 拼接到 shell 命令后执行。

## 资源化实现模型

### 模块建议

- 在 `datafusion-plugin` 侧新增模块目录：`datafusion-plugin-spider/`（不与现有 runtime jar 冲突）。
- 以 `pluginType: SPIDER`，`artifactMode: none` 的 manifest 进行发布。

### manifest 草案

`plugin-build-manifest.json` 关键字段：

- `pluginType`: `SPIDER`
- `modulePath`: `datafusion-plugin/datafusion-plugin-spider`
- `artifactMode`: `none`
- `runtimeResourceDir`: `src/main/resources/plugins/spider`
- `agentPublishDir`: `datafusion-agent/src/main/resources/plugins/spider`
- `resourceDirs`: `browser-agent`、`sh-web-spider`
- `resourceFiles`：`src/main/resources/plugins/init-runtime-unpack.sh` -> `scripts/init-runtime-unpack.sh`

当前 `sh-web-spider` 运行包为两个归档文件：
- `browser-agent-linux-amd64-runtime.tar.gz`
- `sh-web-spider-linux-amd64-runtime.tar.gz`
建议统一保存在 `datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/spider`，并发布到  
`datafusion-agent/src/main/resources/plugins/spider`。

### 目录约定

推荐目录：`datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/spider`：

```text
datafusion-agent/src/main/resources/plugins/spider/
  README.md
  .env
  run-spider.sh
  install-runtime.sh
  app/
  .venv/         （若打进插件仓库）
  browser-agent/
    browser-agent-linux-amd64-runtime.tar.gz（若采用归档方式）
  sh-web-spider/
    sh-web-spider-linux-amd64-runtime.tar.gz（若采用归档方式）
```

## 执行流水（核心）

### 1) 生成并 copy 运行包（重点）

在路径：
`datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/`
新增脚本 `sync-spider-runtime.sh`，将 Python 工程产物拷贝到  
`datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/spider/`。

- 入参：`--source`
  - 支持重复传参或逗号分隔（`,`）
  - 按文件名映射到目标目录
    - `browser-agent-linux-amd64-runtime.tar.gz` -> `browser-agent`
    - `sh-web-spider-linux-amd64-runtime.tar.gz` -> `sh-web-spider`
  - 其他文件可通过 `key=value`/`source:target` 显式映射

脚本建议输出统一结构：

```text
datafusion-agent/src/main/resources/plugins/spider/
  browser-agent/
    browser-agent-linux-amd64-runtime.tar.gz
  sh-web-spider/
    sh-web-spider-linux-amd64-runtime.tar.gz
```

这个脚本放在：
`/Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker`

### 2) 运行包落盘

将两类运行包分别落到约定目录：

- `browser-agent`：`datafusion-agent/src/main/resources/plugins/spider/browser-agent/`
- `sh-web-spider`：`datafusion-agent/src/main/resources/plugins/spider/sh-web-spider/`

`build-plugin.sh` 仅做资源同步，可运行件由 `sync-spider-runtime.sh` 统一产出。

对应上游 Python 项目已补充 DataFusion 打包入口（在外部仓库脚本目录）用于产出一致文件名：
- `browser-agent/scripts/package-datafusion-runtime.sh`
- `sh-web-spider/scripts/package-datafusion-runtime.sh`

以上脚本均默认产出:
- `browser-agent-linux-amd64-runtime.tar.gz`
- `sh-web-spider-linux-amd64-runtime.tar.gz`

并支持 `--name/--output` 指定输出到同步链路可消费的路径。

### 3) Agent 启动时完成运行环境初始化（关键）

方案中，Spider Agent 启动时调用 SPIDER 插件目录下的“初始化解压脚本（`plugins/spider/scripts/init-runtime-unpack.sh`）”：

它只扫描 SPIDER 插件目录下的 `.tar.gz` 并解压到归档所在目录，不解析任务参数、不拉取远端资源，也不参与调度决策。

### 初始化解压脚本建议行为（init-runtime-unpack.sh）

核心行为（单一职责）：

- 输入参数：`--root` 或 `--source`，`--source` 支持多个路径（`,` 分隔）
- 动作：
  - 未指定 `--source` 时，扫描 `--root` 下所有 `*.tar.gz`；
  - 每个归档执行 `tar -xzf` 到它所在目录；
  - 不执行 `venv` 安装、命令注入、`.env` 拼装。
- 返回值：
  - 成功返回 `0`；
  - 任一解压/目录异常返回非 `0`（启动视为环境未就绪）。

`init-runtime-unpack.sh` 仅在启动阶段执行一次；运行期不做全量初始化，只保留目录和关键文件存在性检查。

> 目标：把初始化固定在启动链路前置，避免任务第一次提交才触发运行文件不存在问题。

## 关键风险与对齐点

1. `MCP_URL` 依赖

- 现有 spider runtime `.env` 常见值为 `http://localhost:8000/mcp`。
- 若改为纯 agent 镜像/独立运行，需把 `MCP_URL` 指向实际可达服务（例如内外网可达的 browser-agent）。

2. 环境变量与工作目录

- `sh-web-spider` 脚本当前依赖工作目录及 `.env`。
- 建议固定命令使用绝对路径，防止 `ProcessBuilder` 的 `workDir` 切换导致脚本查找偏差。

3. 初始化时机

- 推荐在 Agent 启动阶段完成运行时目录修复、压缩包解压、权限修正和可执行检查。
- 若只带源码不带 venv，则需在首次执行前增加 `install-runtime.sh` 安装步骤；更推荐带 `.venv` 避免首次启动慢和网络抖动。

4. 观察项

- 任务提交日志 `pluginLogUri`、`workDirPath` 是否可定位。
- 校验 `SPIDER` 与 `SHELL` 的状态映射一致性。

## 接口与配置对齐（最小）

- Worker 插件类型：`DATAFUSION_WORKER_PLUGIN_TYPES=SPIDER`
- 任务请求示例：
  - `pluginType=SPIDER`
  - `runMode=LOCAL`
  - `pluginParam.command=sh`, `pluginParam.args=["-c"]`
  - `taskData.args` 中填充 `cd /opt/datafusion/plugins/spider/sh-web-spider && ./run-spider.sh ...`

## 验收标准（非代码）

1. 通过 `build-plugin.sh` 与 `sync-spider-runtime.sh` 可在 `datafusion-agent/src/main/resources/plugins/spider` 生成完整插件资源目录（含两个运行包文件）。
2. Agent 启动时完成运行环境检查/初始化，无需手工补包。
3. Agent 注册 `SPIDER` 插件类型后，Manager 可发起 SPIDER 任务。
4. SPIDER 任务能在 `TaskType/Shell` 流程内返回 RUNNING/终态，且能上报 `workDirPath` 与日志。
5. 插件化交付后的 `agent` 运行环境中，`MCP_URL` 与 `Kafka` 访问行为一致。

## 交付清单（预期）

- `docs/datafusion-plugin/datafusion-plugin-spider/spider-plugin-design.md`（本文档）
- `datafusion-plugin/datafusion-plugin-spider/src/main/resources/builder/plugin-build-manifest.json`
- `datafusion-plugin/datafusion-plugin-spider/src/main/resources/builder/plugin-build-manifest-usage.md`
- `datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/spider/` 运行资源与说明
- `datafusion-plugin/datafusion-plugin-spider/src/main/resources/plugins/init-runtime-unpack.sh`
- `datafusion-plugin/datafusion-plugin-spider/src/main/resources/docker/sync-spider-runtime.sh`（本设计要求）
