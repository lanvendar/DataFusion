# Kafka JSON Paimon 插件使用手册

## 路径

```text
插件源码: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-kafka-json
插件 resources: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources
标准结构样例: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/sample-standard-kafka-json-paimon-job.json
通用结构样例: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/sample-generic-kafka-json-paimon-job.json
编译产物: /Users/lanvendar/Projects/DataFusion/datafusion-plugin/datafusion-plugin-kafka-json/target/datafusion-plugin-kafka-json-1.0.0-executable.jar
```

## 构建

```bash
cd /Users/lanvendar/Projects/DataFusion
mvn -DskipTests package -pl datafusion-plugin/datafusion-plugin-kafka-json -am
```

## 运行 Job

```bash
cd /Users/lanvendar/Projects/DataFusion
java -jar datafusion-plugin/datafusion-plugin-kafka-json/target/datafusion-plugin-kafka-json-1.0.0-executable.jar \
  --config datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/sample-standard-kafka-json-paimon-job.json
```

标准结构样例用于 Kafka 消息已经包含 `schema.table` / `schema.columns` 的场景，job 配置只保留 Paimon 连接、database、`columnsMapping` 和表 options。通用结构样例用于任意 JSON，通过 job 配置完整定义 `table`、`columns` 和每列的 JMESPath。

启动参数：

```text
--config 或 -c: job JSON 配置文件路径。
不传 --config 时默认读取当前工作目录下的 job.json。
```

## 日志

日志由 `src/main/resources/logback.xml` 控制，不在 shell 中手动切分。

```text
<日志目录>/kafka-json-paimon.log
<日志目录>/kafka-json-paimon.<yyyy-MM-dd>.<index>.log.gz
<日志目录>/kafka-json-paimon-warn.log
<日志目录>/kafka-json-paimon-warn.<yyyy-MM-dd>.<index>.log.gz
```

默认规则：

```text
单个日志文件最大 100MB，超过 100MB 自动切分。
默认保留 7 天。
默认总日志上限 10GB。
```

启动命令可配置：

```text
KAFKA_JSON_PAIMON_LOG_DIR: 日志目录，默认 target/kafka-json-paimon-logs。
KAFKA_JSON_PAIMON_LOG_LEVEL: 根日志级别，默认 INFO，可改为 WARN 或 ERROR。
KAFKA_JSON_PAIMON_LOG_MAX_FILE_SIZE: 单个日志文件大小，默认 100MB。
KAFKA_JSON_PAIMON_LOG_MAX_HISTORY: 日志保留天数，默认 7。
KAFKA_JSON_PAIMON_LOG_TOTAL_SIZE_CAP: 日志总大小上限，默认 10GB。
```

过滤或跳过记录的关键信息会使用 WARN 日志打印，并同时写入 `kafka-json-paimon-warn.log`，常见字段包括：

```text
topic
partition
offset
identifier
recordIndex
records
reason
```

## Kafka 起始位点

```json
{
  "source": {
    "startingOffsets": "group-offsets"
  }
}
```

支持：

```text
earliest
latest
group-offsets
timestamp
```

使用 `timestamp` 时，需要在 `source.properties` 中配置毫秒时间戳：

```json
{
  "source": {
    "startingOffsets": "timestamp",
    "properties": {
      "starting.timestamp.ms": "1781247487869"
    }
  }
}
```

## 配置文件

配置文件顶层结构：

```json
{
  "job": {},
  "source": {},
  "runtime": {},
  "sink": {
    "type": "PAIMON",
    "tables": []
  }
}
```

`sink.tables[].columnsMapping` 是消息级 JMESPath，结果支持对象数组或单层对象；`columns[].value.path` 是单条记录级 JMESPath，不配置时默认按列名取值。

当前 sink 使用 Flink Sink V2 提交模型：writer 并行写文件并产出 Paimon `CommitMessage`，committer 按表和提交编号聚合后提交。写入计划会按目标表分组，避免多个 subtask 同时提交同一张 filesystem/S3 Paimon 表时抢占相同 snapshot 文件。

`UPSERT` + 主键或代理主键可以提升失败重放时的幂等性；`APPEND` 表在 Paimon commit 成功但 Kafka offset checkpoint 未完成时可能重复写入。

`sink.tables[].table` 是表级覆盖结构，默认会合并 Kafka 消息里的 `schema.table`；`sink.tables[].columns[]` 默认会合并 `schema.columns[]`。`table.primaryKeys.mode` 未配置时默认 `FIELDS`，`table.primaryKeys.defaultValue` 是主键字段数组，`PROXY` 模式下默认算法是 `UUID`。
