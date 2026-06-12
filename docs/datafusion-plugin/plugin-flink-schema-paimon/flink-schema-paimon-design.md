# flink-schema-paimon 设计

> 数据结构定义见 [flink-schema-paimon-data-define.md](./flink-schema-paimon-data-define.md)。本文档只描述 Kafka 到 Paimon 的数据流、动态多表写入策略、生产端 topic 拆分扩容方案、代码边界与验证计划。

## 1. Capability

- Capability: 从 Kafka 消费携带 schema 与 data 的 JSON 消息，按 `sink.tables[]` 白名单写入 Paimon，支持单实例多表写入，并通过生产端 topic 拆分与 Kafka consumer group 扩容分摊压力。
- Module: `datafusion-plugin/plugin-flink-schema-paimon`
- Module boundary: 该模块没有 `datafusion-plugin` 前缀，是业务定制模块插件，不作为 `datafusion-plugin` 下的公共插件模块沉淀。
- Java backend package: `com.datafusion.plugin.flink.schema.paimon`
- Frontend path: 无
- Route / API prefix: 无
- Call chain: `job config` -> `KafkaSource` -> `message parser` -> `table resolver` -> `table writer registry` -> `Paimon catalog/table writer`

## 2. Data Flow

| Scenario | Source | Through | Target | Data structure | Notes |
|----------|--------|---------|--------|----------------|-------|
| 启动作业 | 本地 `job.json` 或启动参数指向的配置文件 | `ConfigLoader` -> `ConfigValidator` | Flink 作业运行时 | `FlinkSchemaPaimonJobConfig` | 启动前完成配置校验 |
| 消费 Kafka 消息 | Kafka topic | Flink `KafkaSource<String>` | `MessageParser` | 原始 JSON -> `KafkaEnvelope` | 一条消息可包含一张表的多条数据 |
| 解析目标表 | `KafkaEnvelope`、`sink.tables[]` 与插件默认 sink 配置 | `TableResolver` | `ResolvedTableConfig` | `database + tableName + schema + options` | 按 Kafka 消息 `schema.table.name` 查找同名 `sink.tables[].tableName`，未匹配时跳过并记录 WARN，表结构仍来自 Kafka 消息 schema |
| 单消息生成写入计划 | `KafkaEnvelope.data` | `TableResolver` | `ResolvedTableWritePlan` | 表配置 + 记录列表 | 一条消息按 `schema.table.name` 写入一张 Paimon 表 |
| 初始化或复用 writer | `ResolvedTableConfig` | `TableWriterRegistry` | `PaimonTableWriter` | 按 `database.table` 缓存 | 减少重复建表与 schema 校验开销 |
| 批量写入 | `RecordPayload` 列表 | `RecordNormalizer` -> `PaimonTableWriter` | Paimon | `GenericRow` / `CommitMessage` | 支持 APPEND 或 UPSERT；单条记录必输字段为空时只跳过当前记录并记录 WARN |
| 容错恢复 | Flink checkpoint state | Flink runtime | Kafka offsets + 内存态恢复 | `runtime` 配置 | V1 以 Flink checkpoint + Paimon commit 为基础，`UPSERT` 场景通过主键提升重放幂等性 |

## 3. job.json Contract

`job.json` 是插件启动时加载的唯一运行配置。Kafka 消息已经携带 `schema.table`、`schema.columns` 和 `data`，且不会出现 Kafka 中不同表写入同一个 Paimon 表的情况，所以 `job.json` 不重复定义字段、主键、分区和表注释，也不需要额外 dispatch 配置，只关注消费配置、写入配置、以及允许写入哪些 Paimon database/table。

顶层结构如下：

```json
{
  "job": {},
  "source": {},
  "runtime": {},
  "sink": {}
}
```

关键字段：

| Path | Required | Notes |
|------|----------|-------|
| `job.id` | Yes | 作业唯一 ID |
| `source.bootstrapServers` | Yes | Kafka broker 地址 |
| `source.topics` / `source.topicPattern` | Yes | 二选一 |
| `source.groupId` | Yes | 同一消费组内多个实例由 Kafka partition 分摊消息 |
| `runtime.deploymentMode` | No | Flink 部署模式，支持 `LOCAL`、`STANDALONE`、`YARN`、`KUBERNETES` |
| `runtime.executionMode` | No | Flink 执行模式，支持 `STREAMING`、`BATCH` |
| `runtime.checkpointMode` | No | Flink checkpoint 模式，支持 `EXACTLY_ONCE`、`AT_LEAST_ONCE` |
| `runtime.stateBackend` | No | Flink state backend，支持 `HASHMAP`、`ROCKSDB` |
| `runtime.restartStrategy` | No | Flink 重启策略，支持 `NO_RESTART`、`FIXED_DELAY`、`FAILURE_RATE` |
| `sink.options` | Yes | 全局 Paimon options，包含 catalog 连接参数和默认表 options |
| `sink.includeKafkaMetadataFields` | No | 默认 `false`；开启后所有表补充 `_kafka_topic`、`_kafka_partition`、`_kafka_offset` |
| `sink.tables` | Yes | 目标 Paimon 库表数组，数组长度就是本 job 可写入表数量 |

最小多表配置骨架：

```json
{
  "job": {
    "id": "kafka_schema_paimon",
    "name": "Kafka Schema Paimon"
  },
  "source": {
    "type": "KAFKA",
    "bootstrapServers": "kafka-1:9092,kafka-2:9092",
    "topics": ["ods-schema-data"],
    "groupId": "datafusion-plugin-flink-schema-paimon",
    "startingOffsets": "group-offsets",
    "properties": {}
  },
  "runtime": {
    "parallelism": 2,
    "deploymentMode": "LOCAL",
    "executionMode": "STREAMING",
    "checkpointMode": "EXACTLY_ONCE",
    "checkpointIntervalMs": 60000,
    "checkpointTimeoutMs": 600000,
    "maxConcurrentCheckpoints": 1,
    "checkpointStorage": "file:///tmp/flink-checkpoints/kafka-schema-paimon",
    "stateBackend": "HASHMAP",
    "restartStrategy": "FIXED_DELAY",
    "restartAttempts": 3,
    "restartDelayMs": 10000
  },
  "sink": {
    "loadMode": "UPSERT",
    "connectType": "S3",
    "includeKafkaMetadataFields": false,
    "options": {
      "warehouse": "s3://data-lake-warehouse/paimon",
      "catalogType": "filesystem",
      "database": "dw_prod",
      "s3.endpoint": "http://s3.example.com",
      "s3.endpoint.region": "us-east-1",
      "s3.access-key": "${env:PAIMON_S3_ACCESS_KEY}",
      "s3.secret-key": "${env:PAIMON_S3_SECRET_KEY}",
      "s3.path.style.access": "true",
      "s3.connection.ssl.enabled": "false",
      "s3.fast.upload.buffer": "array",
      "bucket": "2",
      "write-buffer-size": "128mb",
      "file.format": "parquet"
    },
    "tables": [
      {
        "enabled": true,
        "database": "dw_dev",
        "tableName": "ods_spider_bkccpr_central_parity_rate",
        "options": {
          "bucket": "4"
        }
      }
    ],
    "write": {
      "batchSize": 1000,
      "flushIntervalMs": 5000,
      "maxOpenWriters": 256
    }
  }
}
```

多实例分摊只保留一种模式：Kafka topic + consumer group。

| Scenario | How to run | Notes |
|----------|------------|-------|
| 同一批表压力变大 | 增加 topic partition，并启动更多使用同一 `source.groupId` 的插件实例 | Kafka 按 partition 分摊消息 |
| 不同表集合需要拆分压力 | 生产端将 N 张表写入 topic A，M 张表写入 topic B；消费端分别启动订阅 topic A/topic B 的 job，并配置对应 `sink.tables[]` | 未配置表固定跳过并记录 WARN |
| topic 中混入未配置表 | 消费端不做复杂路由，按 `sink.tables[]` 白名单过滤 | 默认跳过并记录 WARN/指标 |

## 4. File Changes

### 4.1 New Files

| File | Notes |
|------|-------|
| `docs/datafusion-plugin/plugin-flink-schema-paimon/flink-schema-paimon-data-define.md` | Kafka schema 驱动 Paimon 写入的数据定义 |
| `docs/datafusion-plugin/plugin-flink-schema-paimon/flink-schema-paimon-design.md` | Kafka -> Paimon 动态多表写入设计 |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/FlinkSchemaPaimonApplication.java` | 新插件启动入口 |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/config/*` | 配置模型与校验器 |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/source/*` | Kafka Source 构建与消息反序列化 |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/resolve/*` | 目标表解析、schema 解析 |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/sink/*` | Paimon writer 封装与 writer registry |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/resources/sample-kafka-schema-paimon-job.json` | 参考样例配置 |

### 4.2 Modified Files

| File | Notes |
|------|-------|
| `datafusion-plugin/plugin-flink-schema-paimon/pom.xml` | 增加 Flink、Kafka、Paimon、Jackson、日志和打包插件依赖 |
| `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/Main.java` | 替换占位 main，迁移到正式包结构 |

### 4.3 Reused Objects

| Object | Path | Notes |
|--------|------|-------|
| Paimon 字段类型转换逻辑 | `datafusion-plugin/datafusion-plugin-api/src/main/java/com/datafusion/plugin/api/sink/paimon/PaimonSinkWriter.java` | 新插件应尽量复用或抽公共工具，避免 Paimon 建表行为不一致 |
| 字段默认值/格式归一化逻辑 | `datafusion-plugin/datafusion-plugin-api/src/main/java/com/datafusion/plugin/api/sink/SinkRecordNormalizer.java` | Kafka 消息 `data[]` 进入 Paimon 前应保持相同规则 |
| Paimon `table/columns/options` 字段命名 | `datafusion-plugin/datafusion-plugin-api/src/main/resources/*-paimon-job.json` | 让 Kafka 和 API 插件的落表配置语言一致 |

## 5. Java Backend Design

### 5.1 Controller

无

### 5.2 Service

| Method | Input | Output | Notes |
|--------|-------|--------|-------|
| `load(String configPath)` | 配置文件路径 | `FlinkSchemaPaimonJobConfig` | 读取 JSON 配置 |
| `validate(FlinkSchemaPaimonJobConfig config)` | 顶层配置 | `void` | 校验 Kafka、sink、runtime |
| `parse(String message)` | Kafka 原始消息 | `KafkaEnvelope` | Jackson 反序列化 |
| `resolve(KafkaEnvelope envelope, FlinkSchemaPaimonJobConfig config)` | 消息与默认配置 | `ResolvedTableWritePlan` 或 `List<ResolvedTableWritePlan>` | 统一 schema、表名、database、table options |
| `write(ResolvedTableWritePlan plan)` | 单表写入计划 | `void` | 获取或创建 writer，执行归一化后写入 |

### 5.3 Business Rules

| Scenario | Rule | Error / return |
|----------|------|----------------|
| Kafka 消息缺失 `schema.table.name` | `job.json` 不提供额外表名解析规则，无法回退 | 抛配置/数据异常 |
| Kafka 消息缺失 `schema.columns` | `job.json` 不维护字段结构，无法回退 | 抛配置/数据异常 |
| Kafka 消息表名未配置 | 直接过滤该条消息，不写 Paimon | 记录 WARN 日志和 skipped 计数；不提供配置开关 |
| 多表配置 | 必须配置非空 `sink.tables[]`，每个启用表必须有 `database` 和 `tableName` | 启动校验失败 |
| 新增目标表 | 只追加一个 `sink.tables[]` 元素，表结构仍由 Kafka 消息中的 `schema` 决定 | 不需要修改 Java 代码 |
| 同一批次消息写入多张表 | 先按目标表分组，再逐表写入 | 每张表独立 writer 和 commit |
| 目标表已存在但字段不兼容 | 字段缺失、类型不一致、主键不一致、分区不一致直接失败；字段或表 comment 不一致只 WARN 一次 | 抛异常并停止作业，避免脏写 |
| 多实例分摊 | 仅通过 Kafka topic、partition 和 consumer group 分摊，不在插件内做表 hash 归属 | 降低误配置风险 |
| 按表集合拆分压力 | 生产端将不同表集合写入不同 topic，消费端分别订阅并配置对应 `sink.tables[]` | 未匹配表固定跳过并记录 WARN |
| 消息包含多条 `data` | 同一消息内所有记录共享 Kafka 消息中的 `schema` | 若记录缺列则按默认值与 nullable 规则处理；必输字段无默认值时只跳过当前记录 |
| 必输字段为空 | `ColumnConfig.nullable=false` 且归一化后为空，并且没有 `defaultValue` | 只跳过当前 `data[]` 记录并记录 WARN，不影响同一消息中的其他记录；若整个批次被过滤为空则不提交 |
| `UPSERT` 写入 | Kafka 消息 `schema.table.primaryKeys` 必须非空 | 运行期遇到不合法消息直接 fail |
| `APPEND` 写入 | 可以没有主键 | 允许动态表追加 |
| 自动建表 | 只由 Kafka 消息 `schema.table.createIfNotExists` 决定 | `true` 时允许自动建表；`false` 时要求表预先存在 |
| Kafka 元数据补充字段 | `sink.includeKafkaMetadataFields=true` 时，所有表自动补充 `_kafka_topic`、`_kafka_partition`、`_kafka_offset` | 关闭时不写入这三个字段；开启时目标表 schema 也包含这三个字段 |

### 5.4 Transaction Boundary

- Needs transaction: 由 `runtime.checkpointMode` 控制，默认 `EXACTLY_ONCE`
- Transactional method: `PaimonTableWriter.writeBatch`
- Rollback condition: 单表批次写入异常、schema 校验失败、commit 失败时当前 checkpoint 不提交；`AT_LEAST_ONCE` 模式允许失败重放，`UPSERT` 依赖主键幂等降低重复影响

### 5.5 Mapper / DAO / SQL

无

## 6. Frontend Design

### 6.1 Page Structure

无

### 6.2 Interaction Behavior

无

### 6.3 Routing / Menu

无

## 7. Integration

| Integration target | Method | Notes |
|--------------------|--------|-------|
| Kafka | Flink `KafkaSource` + consumer group | 使用 topic/partition/consumer group 分摊消费压力；按表集合拆 topic 由生产端配合完成 |
| Paimon | Catalog API + Table Write API | 负责建库建表、schema 校验、批量写入与 commit |
| S3 / 对象存储 | 通过 Paimon options 注入 | 推荐 `warehouse=s3://...` 配合 `s3.*`，例如 `s3.endpoint`、`s3.access-key`、`s3.secret-key` |
| Flink Runtime | checkpoint、state backend、operator parallelism | 大表多写入和失败恢复依赖 Flink 运行时 |

## 8. Security and Context

- Current user: 无
- Tenant / project / app context: 当前是独立插件作业，不依赖 Manager 运行时上下文
- Password / token handling: Kafka SASL、S3 密钥等优先通过 `*.Ref` 或外部环境变量传入，样例文件不放真实密钥
- Permission boundary: 仅允许访问配置中声明的 Kafka topic 与 Paimon warehouse

## 9. Out of Scope

- 不在 V1 中实现 DataFusion Manager 页面化配置与发布能力
- 不在 V1 中实现自动 schema evolution，例如在线加列、改类型
- 不在 V1 中实现跨消息事务性多表两阶段提交
- 不在 V1 中实现死信队列平台化治理，仅预留失败处理扩展点

## 10. Verification

- Unit tests: 配置校验、schema 解析、表白名单解析、消息样例解析、Paimon writer 参数映射
- Compile / build command: `mvn -DskipTests compile -pl datafusion-plugin/plugin-flink-schema-paimon -am`
- Frontend verification: 无
- Style / lint: Java 文件走项目 Checkstyle 约定
- Manual check:
  1. 使用你提供的 Kafka 样例消息验证单表 UPSERT 建表与写入
  2. 使用两种不同表名消息验证单实例多表写入
  3. 使用未配置表名消息验证默认跳过、记录 WARN 和 skipped 计数
  4. 开启 `includeKafkaMetadataFields`，验证所有表补充 `_kafka_topic`、`_kafka_partition`、`_kafka_offset`
  5. 启动两个实例并设置相同 `groupId`，验证按 Kafka 分区分摊且不丢表
  6. 将不同表集合写入不同 topic，验证消费端订阅对应 topic 且未配置表被跳过
  7. 在同一 Kafka 消息的 `data[]` 中混入必输字段为空的记录，验证只跳过该记录且其他记录继续写入
