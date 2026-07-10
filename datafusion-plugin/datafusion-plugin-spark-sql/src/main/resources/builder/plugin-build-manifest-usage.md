# Spark SQL 插件构建与发布

当前清单：`datafusion-plugin/datafusion-plugin-spark-sql/src/main/resources/builder/plugin-build-manifest.json`

执行路径建议：仓库根目录。

## 1) 构建并发布插件

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-spark-sql/src/main/resources/builder/plugin-build-manifest.json \
  --mode fat
```

模块 POM 会生成固定文件 `target/plugin-spark-sql.jar`。manifest 使用 `artifactMode=none`，通过
`resourceFiles` 和 `resourceDirs` 将该 jar、`conf/`、`jobs/` 发布到 Agent，因此 `--mode fat|thin`
不改变发布结果。

发布后目录结构：

```text
datafusion-agent/src/main/resources/plugins/spark/
  datafusion-plugin-spark-sql/
    plugin-spark-sql.jar
    conf/
      logback.xml
    jobs/
      spark-sql-job-example.json
```

SparkApplication 的 driver 和 executor initContainer 会从共享插件目录复制
`plugin-spark-sql.jar`，运行镜像保持 `apache/spark:4.0.2-scala2.13-java17-ubuntu`。

## 2) 复用已有 Maven 产物

适用于已经完成 Maven package，只重新同步 jar 和任务示例的场景：

```bash
./datafusion-plugin/build-plugin.sh \
  --manifest datafusion-plugin/datafusion-plugin-spark-sql/src/main/resources/builder/plugin-build-manifest.json \
  --no-maven
```

执行前必须存在 `datafusion-plugin/datafusion-plugin-spark-sql/target/plugin-spark-sql.jar`。

更多参数和发布规则请看 [build-plugin.md](../../../../../build-plugin.md)。
