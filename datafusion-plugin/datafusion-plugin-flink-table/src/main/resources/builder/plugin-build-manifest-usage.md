# Kafka JSON Flink 插件构建与发布

当前清单：`datafusion-plugin/datafusion-plugin-flink-table/src/main/resources/builder/plugin-build-manifest.json`

执行路径建议：项目根目录。

### 1) 打包发布 fat jar（首版默认）

首版使用 jar 模式发布，构建并发布可直接运行的 executable fat jar 到 Agent Flink app 目录。

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-flink-table/src/main/resources/builder/plugin-build-manifest.json \
  --mode fat
```

发布后目标结构：

```text
datafusion-agent/src/main/resources/plugins/flink/
  datafusion-plugin-flink-table/
    datafusion-plugin-flink-table-1.0.0-executable.jar
    conf/
    jobs/
    lib/
    plugin-kafka-json-commands.md
```

`lib/` 在 fat jar 模式下会保留为空目录。`templates/` 不由当前 manifest 管理，公共 builder 不会清理 Flink 类型根目录。

### 2) 无 Maven 发布（复用已有产物）

适用于已经执行过 Maven package，只需要重新同步 jar 和资源到 Agent 插件目录的场景。

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-flink-table/src/main/resources/builder/plugin-build-manifest.json \
  --mode fat \
  --no-maven
```

更多参数和行为请看 [build-plugin.md](../../../../build-plugin.md)。
