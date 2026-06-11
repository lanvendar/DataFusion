# flink-schema-paimon 数据定义

> 本文档是 `plugin-flink-schema-paimon` 功能的数据结构唯一事实源，覆盖 Kafka 输入消息、插件运行配置、Paimon 表结构描述与运行时状态。设计文档只描述流程、约束和文件改动，不重复完整字段定义。

## 1. Database Data Model

### 1.1 Table Model

- Table name: 无
- Operation: not involved
- Primary key: 无
- Candidate key / unique business key: 无
- Partition strategy: 无
- Table comment: 无
- Notes: `plugin-flink-schema-paimon` 当前是独立 Flink 插件运行时，本次设计不新增 DataFusion 平台数据库表。Paimon 目标表由 Kafka 消息中的 schema 与插件配置动态创建或校验。

### 1.2 DDL / Migration

无

### 1.3 Field / Column Model

无

## 2. Java Backend Data Model

### 2.1 Persistence Model

无

### 2.2 API Input Model

无

### 2.3 API Output Model

无

### 2.4 Service Model

| Object | Scenario | Field | Field type | Lifecycle | Notes |
|--------|----------|-------|------------|-----------|-------|
| `FlinkSchemaPaimonJobConfig` | 插件启动加载整份任务配置 | `job` | `JobConfig` | 启动时加载，运行期只读 | 顶层任务元信息 |
| `FlinkSchemaPaimonJobConfig` | 插件启动加载整份任务配置 | `source` | `KafkaSourceConfig` | 启动时加载，运行期只读 | Kafka 消费配置 |
| `FlinkSchemaPaimonJobConfig` | 插件启动加载整份任务配置 | `runtime` | `RuntimeConfig` | 启动时加载，运行期只读 | Flink checkpoint、并发与容错配置 |
| `FlinkSchemaPaimonJobConfig` | 插件启动加载整份任务配置 | `sink` | `PaimonSinkGroupConfig` | 启动时加载，运行期只读 | Paimon catalog 默认配置与写入控制 |
| `PaimonSinkGroupConfig` | 配置任意数量目标表 | `tables` | `List<PaimonTableSinkConfig>` | 启动时加载，运行期只读 | 多表写入目标和写入参数的主要配置入口，不重复 Kafka 中的 schema 结构 |
| `KafkaEnvelope` | 反序列化 Kafka 消息 | `schema` | `MessageSchema` | 每条消息临时存在 | 对应样例中的 `schema` 节点 |
| `KafkaEnvelope` | 反序列化 Kafka 消息 | `data` | `List<Map<String, Object>>` | 每条消息临时存在 | 对应样例中的 `data` 数组 |
| `KafkaEnvelope` | 反序列化 Kafka 消息 | `meta` | `MessageMeta` | 每条消息临时存在 | 可选的路由、来源和追踪元信息 |
| `ResolvedTableWritePlan` | 单条消息解析并路由后生成写入计划 | `tableIdentifier` | `String` | 每批次临时存在 | 形如 `database.table` |
| `ResolvedTableWritePlan` | 单条消息解析并路由后生成写入计划 | `tableConfig` | `ResolvedTableConfig` | 每批次临时存在 | 落表定义，可能来自消息，也可能来自默认模板补齐 |
| `ResolvedTableWritePlan` | 单条消息解析并路由后生成写入计划 | `records` | `List<RecordPayload>` | 每批次临时存在 | 当前消息需要写入目标表的记录 |
| `ResolvedTableConfig` | 统一后的落表结构 | `table` | `TableConfig` | 运行期缓存，可复用 | 来自 Kafka 消息 `schema.table`，可用 `tables[].database/tableName` 指定写入库表名 |
| `ResolvedTableConfig` | 统一后的落表结构 | `columns` | `List<ColumnConfig>` | 运行期缓存，可复用 | 来自 Kafka 消息 `schema.columns` |
| `ResolvedTableConfig` | 统一后的落表结构 | `options` | `Map<String, String>` | 运行期缓存，可复用 | 表级 options，只来自 `job.json` 的 `sink.tables[].options` |
| `TableWriterRegistry` | 按表缓存 Paimon writer | `writers` | `Map<String, TableWriterHandle>` | 作业运行期存在 | 减少重复创建表与 writer 的开销 |

#### 2.4.1 `FlinkSchemaPaimonJobConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `job` | `JobConfig` | Yes | 无 | 任务元信息 |
| `source` | `KafkaSourceConfig` | Yes | 无 | Kafka 来源配置 |
| `runtime` | `RuntimeConfig` | No | 默认对象 | Flink 运行控制 |
| `sink` | `PaimonSinkGroupConfig` | Yes | 无 | Paimon 默认 catalog、表 options 与写入配置 |

#### 2.4.2 `JobConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `id` | `String` | Yes | 无 | 作业唯一标识 |
| `name` | `String` | No | 无 | 作业名称 |
| `description` | `String` | No | 无 | 作业说明 |
| `version` | `String` | No | 无 | 配置版本 |

#### 2.4.3 `RuntimeConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `parallelism` | `Integer` | No | `1` | Flink 作业并行度 |
| `deploymentMode` | `String` | No | `LOCAL` | Flink 部署模式 |
| `executionMode` | `String` | No | `STREAMING` | Flink 执行模式 |
| `checkpointMode` | `String` | No | `EXACTLY_ONCE` | Flink checkpoint 模式 |
| `checkpointIntervalMs` | `Long` | No | `60000` | checkpoint 间隔 |
| `checkpointTimeoutMs` | `Long` | No | `600000` | checkpoint 超时 |
| `maxConcurrentCheckpoints` | `Integer` | No | `1` | 最大并发 checkpoint 数 |
| `checkpointStorage` | `String` | No | 无 | checkpoint 存储路径或类型 |
| `stateBackend` | `String` | No | `HASHMAP` | Flink state backend |
| `restartStrategy` | `String` | No | `FIXED_DELAY` | Flink restart strategy |
| `restartAttempts` | `Integer` | No | `3` | 重启次数 |
| `restartDelayMs` | `Long` | No | `10000` | 重启间隔 |
| `failurePolicy` | `String` | No | `FAIL_FAST` | 消息解析或写入失败策略 |

#### 2.4.4 `PaimonSinkGroupConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `loadMode` | `String` | No | `APPEND` | `APPEND` 或 `UPSERT` |
| `connectType` | `String` | No | `S3` | 与现有 API 插件保持一致 |
| `catalogOptions` | `Map<String, String>` | Yes | 无 | `warehouse`、`endpoint`、S3 参数等 Paimon catalog 连接参数 |
| `unmatchedTablePolicy` | `String` | No | `SKIP` | Kafka 消息表名没有匹配到 `tables[]` 时的处理策略 |
| `includeKafkaMetadataFields` | `Boolean` | No | `false` | 是否为所有表补充 Kafka 元数据字段 |
| `tables` | `List<PaimonTableSinkConfig>` | Yes | 无 | 配置任意数量目标库表 |
| `write` | `WriteConfig` | No | 默认对象 | 批量写控制 |

#### 2.4.5 `PaimonTableSinkConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `enabled` | `Boolean` | No | `true` | 是否启用当前目标表 |
| `database` | `String` | Yes | 无 | 写入的 Paimon database |
| `tableName` | `String` | Yes | 无 | 写入的 Paimon table name；默认也用于匹配 Kafka 消息 `schema.table.name` |
| `loadMode` | `String` | No | 继承 `sink.loadMode` | 当前表写入模式，可覆盖全局值 |
| `options` | `Map<String, String>` | No | 空 map | 当前表 options，如 `bucket`、`write-buffer-size` |

`PaimonTableSinkConfig` 不定义字段、主键、分区和表注释。这些结构必须来自 Kafka 消息中的 `schema.table` 与 `schema.columns`，避免 `job.json` 和 Kafka 消息各维护一份表结构。

#### 2.4.6 `TableConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `name` | `String` | Yes | 无 | Paimon 表名 |
| `comment` | `String` | No | 无 | 表注释 |
| `createIfNotExists` | `Boolean` | No | `true` | 是否允许自动建表 |
| `primaryKeys` | `List<String>` | Conditional | 空列表 | `UPSERT` 时必填 |
| `partitionKeys` | `List<String>` | No | 空列表 | 分区字段列表 |

#### 2.4.7 `ColumnConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `name` | `String` | Yes | 无 | 字段名 |
| `type` | `String` | Yes | `STRING` | 支持 `STRING/VARCHAR/INT/BIGINT/DOUBLE/FLOAT/DECIMAL/BOOLEAN/DATE/TIMESTAMP` |
| `length` | `Integer` | Conditional | 无 | `VARCHAR` 可配置 |
| `precision` | `Integer` | Conditional | `18` | `DECIMAL` 可配置 |
| `scale` | `Integer` | Conditional | `4` | `DECIMAL` 可配置 |
| `nullable` | `Boolean` | No | `true` | 是否允许空值 |
| `defaultValue` | `Object` | No | 无 | 空值时默认值 |
| `comment` | `String` | No | 无 | 字段注释 |
| `format` | `String` | No | 无 | 日期时间格式，支持 `source->target` |

#### 2.4.8 `WriteConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `batchSize` | `Integer` | No | `1000` | 单次写入前聚合记录数 |
| `flushIntervalMs` | `Long` | No | `5000` | 空闲 flush 间隔 |
| `maxOpenWriters` | `Integer` | No | `256` | 单实例最多缓存 writer 数 |

#### 2.4.9 `KafkaEnvelope`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `schema` | `MessageSchema` | Yes | 无 | Kafka 消息携带的表结构 |
| `data` | `List<Map<String, Object>>` | Yes | 无 | 写入记录数组 |
| `meta` | `MessageMeta` | No | 默认对象 | 运行元信息 |

#### 2.4.10 `MessageMeta`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `eventTime` | `String` | No | 无 | 事件时间 |
| `traceId` | `String` | No | 无 | 日志追踪 |

### 2.5 Integration Model

| Object | Integration target | Direction | Field | Field type | Conversion rule | Notes |
|--------|--------------------|-----------|-------|------------|-----------------|-------|
| `KafkaSourceConfig` | Kafka | Inbound | `bootstrapServers` | `String` | 直传 Flink KafkaSource builder | 必填 |
| `KafkaSourceConfig` | Kafka | Inbound | `topics` | `List<String>` | 与 `topicPattern` 二选一 | 支持多 topic |
| `KafkaSourceConfig` | Kafka | Inbound | `topicPattern` | `String` | 编译为正则订阅 | 用于批量接入相似 topic |
| `KafkaSourceConfig` | Kafka | Inbound | `groupId` | `String` | 直传 consumer group | 多实例消费分摊依赖此字段一致 |
| `KafkaSourceConfig` | Kafka | Inbound | `startingOffsets` | `String` | 转换为 `earliest/latest/group-offsets/timestamp` | 控制消费起点 |
| `KafkaSourceConfig` | Kafka | Inbound | `properties` | `Map<String, String>` | 原样透传 | SASL/SSL 等走此处 |
| `MessageSchema` | Kafka 消息 | Inbound | `table` | `TableConfig` | 反序列化并校验 | 与现有 Paimon sink table 结构一致 |
| `MessageSchema` | Kafka 消息 | Inbound | `columns` | `List<ColumnConfig>` | 反序列化并校验 | 与现有 Paimon sink column 结构一致 |
| `MessageMeta` | Kafka 消息 | Inbound | `eventTime` | `String` | 转换为 Flink 事件时间字段 | 可选 |
| `PaimonSinkGroupConfig` | Paimon Catalog | Outbound | `catalogOptions` | `Map<String, String>` | 复用现有 `sink.options` 语义 | 包含 `warehouse`、`endpoint`、S3 参数等 |
| `PaimonSinkGroupConfig` | Paimon Catalog | Outbound | `tables` | `List<PaimonTableSinkConfig>` | 逐表转换为 Paimon 写入目标 | 任意增减目标库表只修改数组元素 |
| `PaimonSinkGroupConfig` | Paimon Catalog | Outbound | `write` | `WriteConfig` | 控制 flush/batch/并发 | 可与 API 插件命名保持一致 |

### 2.6 State / Enum Model

| Field / enum | Owner object | Values | Storage type | Display label | Conversion rule | Notes |
|--------------|--------------|--------|--------------|---------------|-----------------|-------|
| `UnmatchedTablePolicy` | `PaimonSinkGroupConfig` | `SKIP`, `FAIL` | `String` | 跳过 / 失败 | 启动时解析枚举 | 默认 `SKIP`，用于过滤未配置目标表的数据 |
| `DeploymentMode` | `RuntimeConfig` | `LOCAL`, `STANDALONE`, `YARN`, `KUBERNETES` | `String` | 本地 / Standalone / Yarn / Kubernetes | 启动时解析枚举 | 控制作业部署方式 |
| `ExecutionMode` | `RuntimeConfig` | `STREAMING`, `BATCH` | `String` | 流 / 批 | 启动时解析枚举 | 默认流处理 |
| `CheckpointMode` | `RuntimeConfig` | `EXACTLY_ONCE`, `AT_LEAST_ONCE` | `String` | 精确一次 / 至少一次 | 启动时解析枚举 | 控制 Flink checkpoint 语义 |
| `StateBackend` | `RuntimeConfig` | `HASHMAP`, `ROCKSDB` | `String` | HashMap / RocksDB | 启动时解析枚举 | 控制 Flink 状态后端 |
| `RestartStrategy` | `RuntimeConfig` | `NO_RESTART`, `FIXED_DELAY`, `FAILURE_RATE` | `String` | 不重启 / 固定延迟 / 失败率 | 启动时解析枚举 | 控制失败恢复策略 |
| `startingOffsets` | `KafkaSourceConfig` | `earliest`, `latest`, `group-offsets`, `timestamp` | `String` | Kafka 起始位点模式 | 按字符串分支转换 | 与 Flink KafkaSource 对接 |

## 3. Frontend Data Model

### 3.1 API Client Model

无

### 3.2 Page Query Model

无

### 3.3 Page Display Model

无

## 4. Data Mapping Rules

### 4.1 API Mapping

无

### 4.2 Layer Conversion

| Direction | Conversion rule | Special handling |
|-----------|-----------------|------------------|
| Kafka JSON -> `KafkaEnvelope` | 使用 Jackson 反序列化消息体 | 非法 JSON 进入脏数据处理或失败策略 |
| `KafkaEnvelope` + `sink.tables[]` -> `ResolvedTableConfig` | 按 Kafka 消息 `schema.table.name` 查找同名 `tables[].tableName`，使用 `tables[].database/tableName/options/loadMode` 决定写入目标 | 未匹配时按 `sink.unmatchedTablePolicy` 处理；表结构仍来自 Kafka 消息中的 `schema.table` 与 `schema.columns` |
| `KafkaEnvelope.schema` -> `ResolvedTableConfig` | 复用消息中的 `schema.table` / `schema.columns` 字段语义 | `tables[].database/tableName` 只改变写入目标库表名，不改变字段、主键、分区和注释 |
| `KafkaEnvelope.data[]` -> `RecordPayload` | 每条记录按 `columns` 顺序、默认值、格式规则归一化 | 复用 `SinkRecordNormalizer` 的日期格式和非空校验思路 |
| `ResolvedTableConfig` -> Paimon `Schema` | 将 `VARCHAR/DECIMAL/DATE/TIMESTAMP` 等转换为 Paimon 类型 | `sink.includeKafkaMetadataFields=true` 时为所有表补充 `_kafka_topic`、`_kafka_partition`、`_kafka_offset` |

## 5. Reused Structures

| Object | Path | Reuse method | Notes |
|--------|------|--------------|-------|
| `ApiExtractJobConfig.TableConfig` 语义 | `datafusion-plugin/datafusion-plugin-api/src/main/java/com/datafusion/plugin/api/config/ApiExtractJobConfig.java` | 复用字段命名与校验语义，必要时抽公共类或在新插件复制同构结构 | 保持 `name/comment/createIfNotExists/primaryKeys/partitionKeys` 一致 |
| `ApiExtractJobConfig.ColumnConfig` 语义 | `datafusion-plugin/datafusion-plugin-api/src/main/java/com/datafusion/plugin/api/config/ApiExtractJobConfig.java` | 复用字段命名与类型映射语义 | 保持 `type/length/precision/scale/nullable/comment/format/defaultValue` 一致 |
| `PaimonSinkWriter` 类型映射规则 | `datafusion-plugin/datafusion-plugin-api/src/main/java/com/datafusion/plugin/api/sink/paimon/PaimonSinkWriter.java` | 复用或提炼公共工具 | 避免新插件和 API 插件的 Paimon 字段行为不一致 |
| `SinkRecordNormalizer` 归一化规则 | `datafusion-plugin/datafusion-plugin-api/src/main/java/com/datafusion/plugin/api/sink/SinkRecordNormalizer.java` | 复用或提炼公共工具 | 统一默认值、非空和 format 行为 |
