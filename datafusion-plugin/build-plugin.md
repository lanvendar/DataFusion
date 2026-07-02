# DataFusion Plugin Builder

`build-plugin.sh` 是 `datafusion-plugin` 父模块下的公共插件发布脚本，用于把一个插件子模块的
jar 和运行资源同步到 `datafusion-agent/src/main/resources/plugins/` 下。

当前脚本一次只发布一个插件。发布哪个插件由 `--manifest` 指定的
`plugin-build-manifest.json` 决定，脚本不会自动扫描并发布所有插件。

## 使用方式

以 API 插件为例：

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json \
  --mode fat
```

thin jar + lib 模式：

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-api/src/main/resources/builder/plugin-build-manifest.json \
  --mode thin
```

资源发布型插件，例如 DataX：

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-datax/src/main/resources/builder/plugin-build-manifest.json \
  --no-maven
```

常用参数：

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `--manifest` | 是 | 无 | 指定单个插件的构建 manifest |
| `--mode` | 否 | `fat` | 发布模式，支持 `fat`、`thin` |
| `--skip-tests` | 否 | `true` | Maven package 时是否跳过测试 |
| `--no-maven` | 否 | `false` | 不执行 Maven，仅使用已有 `target/` 产物发布 |

也可以通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `PLUGIN_PACKAGE_MODE` | `fat` | 默认发布模式 |
| `SKIP_TESTS` | `true` | 默认是否跳过测试 |
| `RUN_MAVEN` | `true` | 默认是否执行 Maven |
| `MAVEN_CMD` | `mvn` | Maven 命令 |

## Manifest 结构

每个插件子模块在自己的 `src/main/resources/builder/` 下维护一个
`plugin-build-manifest.json`。该文件只给 builder 使用，不会复制到 agent 的插件运行目录。

示例：

```json
{
  "pluginType": "API",
  "multiApp": false,
  "modulePath": "datafusion-plugin/datafusion-plugin-api",
  "artifactId": "datafusion-plugin-api",
  "artifactMode": "jar",
  "runtimeResourceDir": "src/main/resources/plugins/api",
  "agentPublishDir": "datafusion-agent/src/main/resources/plugins/api",
  "resourceDirs": [
    "conf",
    "jobs"
  ],
  "resourceFiles": [
    {
      "source": "src/main/resources/plugins/plugin-api-commands.md",
      "target": "plugin-api-commands.md"
    }
  ]
}
```

字段说明：

| 字段 | 必填 | 说明 |
|------|------|------|
| `pluginType` | 是 | 插件类型，例如 `API`、`FLINK` |
| `multiApp` | 是 | 当前公共 builder 仅支持 `false` |
| `modulePath` | 是 | Maven reactor 模块路径，相对仓库根目录 |
| `artifactId` | `artifactMode=jar` 时是 | Maven artifactId，用于定位 jar |
| `artifactMode` | 否 | 构建产物模式，默认 `jar`；资源型插件使用 `none` |
| `runtimeResourceDir` | 是 | 插件模块内运行资源目录，相对插件模块根目录 |
| `agentPublishDir` | 是 | 发布到 agent 的目录，相对仓库根目录 |
| `resourceDirs` | 否 | 从 `runtimeResourceDir` 下复制的目录列表 |
| `resourceFiles` | 否 | 从插件模块复制到发布目录的文件映射 |

## 发布行为

脚本会先执行：

```bash
mvn -q -pl ${modulePath} -am package
```

如果 `--skip-tests true`，会追加 `-DskipTests`。

`artifactMode=none`：

- 只发布 `resourceDirs` 和 `resourceFiles`
- 不查找或复制 Maven jar
- `--mode fat|thin` 不影响发布结果

`fat` 模式：

- 查找 `${artifactId}-*-executable.jar`
- 复制到 `agentPublishDir`
- 清空并保留 `agentPublishDir/lib/` 空目录

`thin` 模式：

- 查找普通 `${artifactId}-*.jar`
- 执行 `dependency:copy-dependencies`
- 复制普通 jar 到 `agentPublishDir`
- 复制 runtime 依赖到 `agentPublishDir/lib/`

公共资源：

- `resourceDirs` 中的目录会从 `runtimeResourceDir` 下复制到 `agentPublishDir`
- `resourceFiles` 中的文件会按 `source -> target` 复制到 `agentPublishDir`
- manifest 中声明的资源必须存在，路径错误会直接导致发布失败
- agent 侧未被 manifest 管理的目录不会被清理，例如插件模板目录

## 多插件发布

当前脚本不负责一次发布多个插件。需要发布多个插件时，应显式多次调用：

```bash
./datafusion-plugin/build-plugin.sh --manifest <api-manifest> --mode fat
./datafusion-plugin/build-plugin.sh --manifest <datax-manifest> --no-maven
./datafusion-plugin/build-plugin.sh --manifest <flink-manifest> --mode thin
```

如果后续需要“一次发布一组插件”，建议新增聚合 manifest 或独立 orchestrator 脚本，由它逐个调用
`build-plugin.sh --manifest ...`，不要让单插件 manifest 同时承担聚合发布职责。
