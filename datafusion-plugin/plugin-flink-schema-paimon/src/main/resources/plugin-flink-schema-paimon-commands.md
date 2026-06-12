# Flink Schema Paimon 插件使用手册

## 路径

```text
插件模块: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon
插件 resources: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon/src/main/resources
样例配置: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/sample-kafka-schema-paimon-job.json
测试配置: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/dw-web-spider-paimon-test.json
可执行 Jar: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon/target/plugin-flink-schema-paimon-1.0.0-executable.jar
```

## 构建

```bash
cd /Users/lanvendar/Projects/DataFusion
mvn -pl datafusion-plugin/plugin-flink-schema-paimon -am -DskipTests package
```

构建成功后运行 `target/plugin-flink-schema-paimon-1.0.0-executable.jar`，不要用未带 `executable` classifier 的普通 jar。

## 运行 Job

```bash
cd /Users/lanvendar/Projects/DataFusion
java -jar datafusion-plugin/plugin-flink-schema-paimon/target/plugin-flink-schema-paimon-1.0.0-executable.jar \
  --config datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/dw-web-spider-paimon-test.json
```

`--config` 也可以写成 `-c`：

```bash
java -jar datafusion-plugin/plugin-flink-schema-paimon/target/plugin-flink-schema-paimon-1.0.0-executable.jar \
  -c datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/sample-kafka-schema-paimon-job.json
```

## 日志

默认日志路径：

```text
/Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon/target/flink-schema-paimon-logs/flink-schema-paimon.log
/Users/lanvendar/Projects/DataFusion/datafusion-plugin/plugin-flink-schema-paimon/target/flink-schema-paimon-logs/flink-schema-paimon.yyyy-MM-dd.0.log.gz
```

日志由 `src/main/resources/logback.xml` 控制，默认单文件 `100MB` 切分，保留 `7` 天。

```bash
cd /Users/lanvendar/Projects/DataFusion
java \
  -Dpaimon.log.dir=datafusion-plugin/plugin-flink-schema-paimon/target/flink-schema-paimon-logs \
  -Dpaimon.log.file=dw-web-spider-paimon-test \
  -Dpaimon.log.level=INFO \
  -Dpaimon.kafka.log.level=WARN \
  -Dpaimon.log.max.file.size=100MB \
  -Dpaimon.log.max.history=7 \
  -jar datafusion-plugin/plugin-flink-schema-paimon/target/plugin-flink-schema-paimon-1.0.0-executable.jar \
  --config datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/dw-web-spider-paimon-test.json
```

```text
paimon.log.dir 默认 datafusion-plugin/plugin-flink-schema-paimon/target/flink-schema-paimon-logs。
paimon.log.file 默认 flink-schema-paimon。
paimon.log.level 默认 INFO，可改为 WARN、ERROR 或 DEBUG。
paimon.flink.log.level 默认 INFO。
paimon.kafka.log.level 默认 WARN，需要排查 Kafka 连接时可改为 INFO。
paimon.hadoop.log.level 默认 WARN。
paimon.log.max.file.size 默认 100MB。
paimon.log.max.history 默认 7。
paimon.log.total.size.cap 默认 10GB。
```

如果需要使用外部 logback 配置文件：

```bash
java \
  -Dlogback.configurationFile=/path/to/logback.xml \
  -jar datafusion-plugin/plugin-flink-schema-paimon/target/plugin-flink-schema-paimon-1.0.0-executable.jar \
  --config datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/dw-web-spider-paimon-test.json
```

## 配置规则

`sink.options` 是全局 Paimon options，`sink.tables[].options` 是单表局部 options。解析时按就近原则合并，单表局部 options 会覆盖全局同名 key，不会反向污染其他表。

S3 建议使用 Paimon 原生配置：

```json
{
  "warehouse": "s3://data-lake-warehouse/paimon",
  "catalogType": "filesystem",
  "s3.endpoint": "http://sz-s3.indusmind.me",
  "s3.endpoint.region": "us-east-1",
  "s3.access-key": "access-key",
  "s3.secret-key": "secret-key",
  "s3.path.style.access": "true",
  "s3.connection.ssl.enabled": "false"
}
```

`warehouse=s3://...` 配合 `s3.*` 走 Paimon S3 FileIO；`warehouse=s3a://...` 会走 Hadoop S3A，配置 key 也需要换成 Hadoop 的 `fs.s3a.*` 或 `hadoop.fs.s3a.*` 体系。

## 验证

```bash
cd /Users/lanvendar/Projects/DataFusion
mvn -pl datafusion-plugin/plugin-flink-schema-paimon test
```

快速校验 JSON：

```bash
node -e "const fs=require('fs'); for (const f of ['datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/sample-kafka-schema-paimon-job.json','datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/dw-web-spider-paimon-test.json']) JSON.parse(fs.readFileSync(f,'utf8')); console.log('ok')"
```

## 注意事项

- Kafka 服务端版本为 `4.2.0` 时，当前构建显式使用 `org.apache.kafka:kafka-clients:4.2.0`。
- 本地运行 `deploymentMode=LOCAL` 会启动 Flink MiniCluster，需要允许本机端口绑定。
- 当前测试环境如果 Kafka metadata 获取超时，优先检查网络、SASL_SSL 认证和 topic 可达性。
