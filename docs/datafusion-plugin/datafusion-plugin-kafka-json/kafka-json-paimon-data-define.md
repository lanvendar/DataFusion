# datafusion-plugin-kafka-json 数据定义

> 本文档是 `datafusion-plugin-kafka-json` 的数据结构唯一事实源，覆盖 Kafka JSON 解析配置、JMESPath 表达式取值、Paimon 表结构、主键策略与运行时写入计划。设计文档只描述流程、约束和文件改动，不重复完整字段定义。

## 1. Database Data Model

### 1.1 Table Model

- Table name: 无
- Operation: not involved
- Primary key: 无
- Candidate key / unique business key: 无
- Partition strategy: 无
- Table comment: 无
- Notes: 本插件是独立 Flink 插件运行时，不新增 DataFusion 平台数据库表。Paimon 目标表由 `job.json` 中的表结构配置动态创建或校验。

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
| `KafkaJsonPaimonJobConfig` | 插件启动加载整份任务配置 | `job` | `JobConfig` | 启动时加载，运行期只读 | 作业元信息 |
| `KafkaJsonPaimonJobConfig` | 插件启动加载整份任务配置 | `source` | `KafkaSourceConfig` | 启动时加载，运行期只读 | Kafka 消费配置 |
| `KafkaJsonPaimonJobConfig` | 插件启动加载整份任务配置 | `runtime` | `RuntimeConfig` | 启动时加载，运行期只读 | Flink checkpoint、并发与容错配置 |
| `KafkaJsonPaimonJobConfig` | 插件启动加载整份任务配置 | `sink` | `PaimonSinkConfig` | 启动时加载，运行期只读 | Paimon catalog、表解析和写入配置 |
| `ExpressionSpec` | 从 Kafka JSON 或单条 record 中取值 | `path` | `String` | 配置期定义，运行期编译或缓存 | JMESPath 表达式；为空时只使用 `defaultValue` |
| `ExpressionSpec` | 从 Kafka JSON 或单条 record 中取值 | `defaultValue` | `Object` | 配置期定义，运行期兜底 | 当 `path` 为空、取不到、结果为 `null` 或空字符串时使用 |
| `ExpressionSpec` | 从 Kafka JSON 或单条 record 中取值 | `jsonType` | `String` | 配置期定义，运行期校验 | 只校验顶层 JSON 类型，不递归校验数组元素或对象字段 |
| `PaimonSinkConfig` | 配置 Paimon sink | `type` | `String` | 启动时加载，运行期只读 | 固定为 `PAIMON` |
| `PaimonSinkConfig` | 配置 Paimon sink | `connectType` | `String` | 启动时加载，运行期只读 | 第一版支持 `S3` / filesystem catalog 相关 options |
| `PaimonSinkConfig` | 配置 Paimon sink | `options` | `Map<String, String>` | 启动时加载，运行期只读 | 全局 Paimon catalog options 和默认表 options |
| `PaimonSinkConfig` | 配置 Paimon sink | `tables` | `List<PaimonTableConfig>` | 启动时加载，运行期只读 | 多表路由、字段定义与表级 options |
| `PaimonSinkConfig` | 配置 Paimon sink | `loadMode` | `String` | 启动时加载，运行期只读 | 全局默认写入模式 |
| `PaimonSinkConfig` | 配置 Paimon sink | `writer` | `WriterConfig` | 启动时加载，运行期只读 | Paimon writer 缓冲、flush 和缓存控制 |
| `PaimonTableConfig` | 单张目标表配置 | `database` | `ExpressionSpec` | 每条 Kafka 消息解析 | 最终值必须为字符串 |
| `PaimonTableConfig` | 单张目标表配置 | `tableName` | `ExpressionSpec` | 每条 Kafka 消息解析 | 兼做路由表达式和目标表名定义 |
| `PaimonTableConfig` | 单张目标表配置 | `columnsMapping` | `ExpressionSpec` | 每条 Kafka 消息解析 | 消息级 JMESPath，结果必须是对象数组或单层对象 |
| `PaimonTableConfig` | 单张目标表配置 | `columns` | `List<ColumnConfig>` | 启动时加载，运行期只读 | Paimon 表字段定义与单条记录取值规则 |
| `PaimonTableConfig` | 单张目标表配置 | `primaryKey` | `PrimaryKeyConfig` | 启动时加载，运行期只读 | 普通字段主键或代理主键 |
| `ResolvedTableWritePlan` | 单条消息解析后生成写入计划 | `tableConfig` | `ResolvedTableConfig` | 每条 Kafka 消息临时存在 | database、tableName、table schema、options 合并后的结果 |
| `ResolvedTableWritePlan` | 单条消息解析后生成写入计划 | `records` | `List<Map<String, Object>>` | 每条 Kafka 消息临时存在 | 已按 `columns[].value` 抽取、默认值和代理主键处理后的记录 |

#### 2.4.1 `KafkaJsonPaimonJobConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `job` | `JobConfig` | Yes | 无 | 作业元信息 |
| `source` | `KafkaSourceConfig` | Yes | 无 | Kafka 来源配置 |
| `runtime` | `RuntimeConfig` | No | 默认对象 | Flink 运行控制 |
| `sink` | `PaimonSinkConfig` | Yes | 无 | Paimon 写入配置 |

#### 2.4.2 `JobConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `id` | `String` | Yes | 无 | 作业唯一标识 |
| `name` | `String` | No | 无 | 作业名称 |
| `description` | `String` | No | 无 | 作业说明 |
| `version` | `String` | No | 无 | 配置版本 |

#### 2.4.3 `KafkaSourceConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `type` | `String` | No | `KAFKA` | source 类型 |
| `bootstrapServers` | `String` | Yes | 无 | Kafka broker 地址 |
| `topics` | `List<String>` | Conditional | 空列表 | 与 `topicPattern` 二选一 |
| `topicPattern` | `String` | Conditional | 无 | 与 `topics` 二选一 |
| `groupId` | `String` | Yes | 无 | Kafka consumer group |
| `startingOffsets` | `String` | No | `group-offsets` | 支持 `earliest`、`latest`、`group-offsets`、`timestamp` |
| `properties` | `Map<String, String>` | No | 空 map | Kafka 透传配置 |

#### 2.4.4 `RuntimeConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `parallelism` | `Integer` | No | `1` | Flink 作业并行度 |
| `deploymentMode` | `String` | No | `LOCAL` | Flink 部署模式 |
| `executionMode` | `String` | No | `STREAMING` | Flink 执行模式 |
| `checkpointMode` | `String` | No | `AT_LEAST_ONCE` | Flink checkpoint 模式；当前自定义 sink 不声明端到端 exactly-once |
| `checkpointIntervalMs` | `Long` | No | `60000` | checkpoint 间隔 |
| `checkpointTimeoutMs` | `Long` | No | `600000` | checkpoint 超时 |
| `maxConcurrentCheckpoints` | `Integer` | No | `1` | 最大并发 checkpoint 数 |
| `checkpointStorage` | `String` | No | 无 | checkpoint 存储路径 |
| `stateBackend` | `String` | No | `HASHMAP` | Flink state backend |
| `restartStrategy` | `String` | No | `FIXED_DELAY` | Flink restart strategy |
| `restartAttempts` | `Integer` | No | `3` | 重启次数 |
| `restartDelayMs` | `Long` | No | `10000` | 重启间隔 |

#### 2.4.5 `PaimonSinkConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `type` | `String` | No | `PAIMON` | sink 类型 |
| `connectType` | `String` | No | `S3` | 与现有 Paimon 插件保持一致 |
| `schemaMismatchPolicy` | `String` | No | `SKIP` | 表结构不匹配策略，支持 `SKIP`、`FAIL` |
| `recordErrorPolicy` | `String` | No | `SKIP` | 单条记录错误策略，支持 `SKIP`、`FAIL` |
| `includeKafkaMetadataFields` | `Boolean` | No | `false` | 是否自动补充 Kafka topic、partition、offset 字段 |
| `loadMode` | `String` | No | `APPEND` | 全局默认写入模式，支持 `APPEND`、`UPSERT`，表级 `loadMode` 可覆盖 |
| `options` | `Map<String, String>` | Yes | 空 map | 全局 Paimon options，包含 catalog 连接参数和默认表 options |
| `tables` | `List<PaimonTableConfig>` | Yes | 无 | 目标表解析配置 |
| `writer` | `WriterConfig` | No | 默认对象 | Paimon writer 缓冲、flush 和缓存控制 |

#### 2.4.6 `PaimonTableConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `enabled` | `Boolean` | No | `true` | 是否启用当前表 |
| `database` | `ExpressionSpec` or scalar | Yes | 无 | 解析目标 Paimon database；常量可简写为字符串 |
| `tableName` | `ExpressionSpec` or scalar | Yes | 无 | 解析目标 Paimon table name；常量可简写为字符串 |
| `columnsMapping` | `ExpressionSpec` or string | Yes | 无 | 消息级 JMESPath，用于把 Kafka JSON 转换为待映射记录；可简写为 path 字符串，结果支持对象数组或单层对象，默认 `jsonType=ANY` |
| `loadMode` | `String` | No | 继承 `sink.loadMode` 或 `APPEND` | 支持 `APPEND`、`UPSERT` |
| `tableComment` | `ExpressionSpec` or scalar | No | 无 | 解析 Paimon 表注释；常量可简写为字符串，默认 `jsonType=STRING` |
| `createIfNotExists` | `ExpressionSpec` or boolean | No | `true` | 解析是否允许自动建表；常量可简写为布尔值，默认 `jsonType=BOOLEAN` |
| `partitionKeys` | `ExpressionSpec` or array | No | 空列表 | 解析分区字段列表；常量可简写为字符串数组，默认 `jsonType=ARRAY` |
| `primaryKey` | `PrimaryKeyConfig` | Conditional | 无 | `UPSERT` 时必须配置普通主键或代理主键 |
| `columns` | `List<ColumnConfig>` | Yes | 无 | Paimon 字段定义和记录取值规则 |
| `options` | `Map<String, String>` | No | 空 map | 表级 Paimon options，覆盖全局同名 key |

#### 2.4.7 `ExpressionSpec`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `path` | `String` | No | 无 | JMESPath 表达式；空字符串等价于未配置 |
| `defaultValue` | `Object` | No | 无 | `path` 取不到、为 `null` 或空字符串时使用 |
| `jsonType` | `String` | No | `ANY` | 顶层类型约束，支持 `STRING`、`NUMBER`、`BOOLEAN`、`ARRAY`、`OBJECT`、`ANY` |

`ExpressionSpec` 不支持 `itemType` 或对象字段 schema，不递归校验数组元素和对象内部结构。具体业务字段如 `partitionKeys` 需要字符串列表，由使用该配置的业务规则做轻校验。

支持 `ExpressionSpec` 简写规则：

- 业务字段需要常量值时，可直接写标量或数组，例如 `"database": "dw_dev"`、`"partitionKeys": ["day_pt"]`。
- `columnsMapping` 可直接写 JMESPath 字符串，例如 `"columnsMapping": "schema.data"`，等价于 `{"path": "schema.data", "jsonType": "ANY"}`；业务校验只接受数组或对象结果。
- 标量或数组简写统一归一化为 `ExpressionSpec.defaultValue`；`columnsMapping` 字符串简写归一化为 `ExpressionSpec.path`。
- `columns[].value` 未配置时，默认等价于 `{"path": "<column.name>"}`。
- `jsonType` 由字段语义或 `dataType` 推断，只有需要强约束或表达式返回类型不明显时才显式配置。

#### 2.4.8 `ColumnConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `name` | `String` | Yes | 无 | Paimon 字段名 |
| `dataType` | `String` | Yes | `STRING` | 支持 `STRING/VARCHAR/INT/BIGINT/DOUBLE/FLOAT/DECIMAL/BOOLEAN/DATE/TIMESTAMP` |
| `length` | `Integer` | No | `255` | `VARCHAR` 长度；非 `VARCHAR` 类型忽略 |
| `precision` | `Integer` | No | `18` | `DECIMAL` 精度；非 `DECIMAL` 类型忽略 |
| `scale` | `Integer` | No | `4` | `DECIMAL` 小数位；非 `DECIMAL` 类型忽略 |
| `nullable` | `Boolean` | No | `true` | 是否允许空值 |
| `comment` | `String` | No | 无 | 字段注释 |
| `format` | `String` | No | 无 | 日期时间格式，支持 `source->target` |
| `value` | `ExpressionSpec` | No | `{"path": "<name>"}` | 从单条 record 中解析当前列值 |

`length/precision/scale/nullable/comment/format/value` 都是可选配置。`VARCHAR` 未配置 `length` 时默认 `255`；`DECIMAL` 未配置 `precision/scale` 时默认 `18/4`；`nullable` 未配置时默认 `true`；`comment` 与 `format` 未配置时为空。

`columns[].value.defaultValue` 是列值兜底值。第一版不再额外提供 `columns[].defaultValue`，避免同一默认值语义出现两份配置。

#### 2.4.9 `PrimaryKeyConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `mode` | `String` | Yes | 无 | 支持 `FIELDS`、`PROXY` |
| `algorithm` | `String` | No | `UUID` | `PROXY` 模式支持 `UUID`、`SHA-256`、`SHA-512` |
| `path` | `String` | No | 无 | JMESPath 表达式；空字符串等价于未配置 |
| `defaultValue` | `Object` | Conditional | 无 | 主键字段列表兜底值，最终值必须是字符串数组 |
| `jsonType` | `String` | No | `ARRAY` | 固定按数组校验 |

`FIELDS` 与 `PROXY` 互斥。`FIELDS` 模式下 `path/defaultValue` 解析出来的是 Paimon 主键字段列表；`PROXY` 模式下解析出来的是代理主键源字段列表。`PROXY` 模式会自动向 Paimon schema 和输出记录中补充固定 `_id_` 字段，真实 Paimon 主键由 `_id_` 和分区字段组成。

#### 2.4.10 `WriterConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `batchSize` | `Integer` | No | `1000` | 单表批量写入前聚合记录数 |
| `flushIntervalMs` | `Long` | No | `5000` | 空闲 flush 间隔 |
| `maxOpenWriters` | `Integer` | No | `256` | 单实例最多缓存 writer 数 |

#### 2.4.11 `ResolvedTableConfig`

| Field | Field type | Required | Default | Notes |
|-------|------------|----------|---------|-------|
| `database` | `String` | Yes | 无 | 解析后的 Paimon database |
| `tableName` | `String` | Yes | 无 | 解析后的 Paimon table name |
| `tableComment` | `String` | No | 无 | 解析后的表注释 |
| `createIfNotExists` | `Boolean` | No | `true` | 是否允许自动建表 |
| `partitionKeys` | `List<String>` | No | 空列表 | 分区字段 |
| `primaryKeys` | `List<String>` | Conditional | 空列表 | 真实 Paimon 主键字段 |
| `columns` | `List<ResolvedColumnConfig>` | Yes | 无 | 解析后的 Paimon 字段 |
| `options` | `Map<String, String>` | No | 空 map | 合并后的表 options |

### 2.5 Integration Model

| Object | Integration target | Direction | Field | Field type | Conversion rule | Notes |
|--------|--------------------|-----------|-------|------------|-----------------|-------|
| `KafkaSourceConfig` | Kafka | Inbound | `bootstrapServers` | `String` | 直传 Flink KafkaSource builder | 必填 |
| `KafkaSourceConfig` | Kafka | Inbound | `topics` / `topicPattern` | `List<String>` / `String` | 二选一订阅 Kafka | 支持多 topic 和正则 |
| `KafkaSourceConfig` | Kafka | Inbound | `properties` | `Map<String, String>` | 原样透传 | SASL/SSL 等配置 |
| `ExpressionSpec` | JMESPath | Inbound | `path` | `String` | 基于 Jackson JSON 节点编译和求值 | 使用 `io.burt:jmespath-jackson` 或等价库 |
| `PaimonSinkConfig` | Paimon Catalog | Outbound | `options` | `Map<String, String>` | 原样传给 Paimon catalog，并过滤连接参数后作为表 options | 包含 S3 和 filesystem catalog 配置 |
| `PaimonTableConfig` | Paimon Table | Outbound | `columns` / `primaryKey` / `partitionKeys` | Config objects | 转换为 Paimon `Schema` | 自动建表和已有表校验使用同一结构 |

### 2.6 State / Enum Model

| Field / enum | Owner object | Values | Storage type | Display label | Conversion rule | Notes |
|--------------|--------------|--------|--------------|---------------|-----------------|-------|
| `JsonType` | `ExpressionSpec` | `STRING`, `NUMBER`, `BOOLEAN`, `ARRAY`, `OBJECT`, `ANY` | `String` | JSON 顶层类型 | 启动校验并在运行期验证取值结果 | 不递归校验 |
| `PrimaryKeyMode` | `PrimaryKeyConfig` | `FIELDS`, `PROXY` | `String` | 普通字段主键 / 代理主键 | 启动校验 | `UPSERT` 必须配置 |
| `ProxyPrimaryKeyAlgorithm` | `PrimaryKeyConfig` | `UUID`, `SHA-256`, `SHA-512` | `String` | UUID / SHA-256 / SHA-512 | 运行期生成 `_id_` | `UUID` 使用确定性 name-based UUID |
| `SchemaMismatchPolicy` | `PaimonSinkConfig` | `SKIP`, `FAIL` | `String` | 跳过 / 失败 | 启动校验 | 控制已有 Paimon 表结构不匹配时行为 |
| `RecordErrorPolicy` | `PaimonSinkConfig` | `SKIP`, `FAIL` | `String` | 跳过 / 失败 | 启动校验 | 控制单条记录解析、默认值、类型转换失败时行为 |
| `LoadMode` | `PaimonTableConfig` | `APPEND`, `UPSERT` | `String` | 追加 / 更新写入 | 启动校验 | `UPSERT` 需要主键 |
| `startingOffsets` | `KafkaSourceConfig` | `earliest`, `latest`, `group-offsets`, `timestamp` | `String` | Kafka 起始位点 | 转换为 Flink offset initializer | `timestamp` 需配套时间戳参数 |

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
| Kafka raw JSON -> Jackson `JsonNode` | `MessageParser` 解析整条 Kafka value | 非法 JSON 由 `recordErrorPolicy` 或作业失败策略处理 |
| `ExpressionSpec.path` -> extracted value | 使用 JMESPath 在消息级或记录级上下文求值 | `path` 为空时跳过求值，直接使用 `defaultValue` |
| extracted value -> final expression value | 当取值为 `null`、缺失或空字符串时使用 `defaultValue` | 若最终仍为空，由调用方按必填规则处理 |
| final expression value -> typed value | 按 `jsonType` 校验顶层类型 | `ANY` 不校验；`ARRAY` 不校验元素类型 |
| Kafka JSON + `PaimonTableConfig` -> `ResolvedTableConfig` | 使用 `tableName.path` 从 Kafka JSON 读取目标表名；取不到时使用 `defaultValue`；最终仍为空则过滤消息 | 多表按 `tables[]` 顺序解析，第一张得到有效目标表名的配置命中；第一版不支持一条消息写多张表 |
| Kafka JSON + `columnsMapping` -> record JSON | 使用 `columnsMapping.path` 在整条 Kafka JSON 上求值，生成待映射记录 | 结果可以是对象数组或单层对象；单层对象自动包装为一条记录；数组元素必须是对象；可用 JMESPath 投影复杂 JSON 为标准行 |
| record JSON + `columns[]` -> output record | 每列从 `columns[].value.path` 提取，取不到用 `defaultValue`，再按 `dataType` 转换 | 必输字段为空或转换失败时按 `recordErrorPolicy` 处理 |
| `PrimaryKeyConfig.PROXY` -> output record | 按 `path/defaultValue` 解析出的源字段列表从输出 record 取值，用 `_` 拼接后生成 UUID/SHA 摘要 | 自动补充代理主键列；真实 Paimon 主键为代理主键列和分区字段 |
| `ResolvedTableConfig` -> Paimon `Schema` | 将字段类型、主键、分区、表 options 转换为 Paimon schema | 建表与真实表 schema 校验使用同一结构 |

## 5. Reused Structures

| Object | Path | Reuse method | Notes |
|--------|------|--------------|-------|
| Kafka source/runtime 配置语义 | `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/config/FlinkSchemaPaimonJobConfig.java` | 复制同构配置或抽公共对象 | 保持 Kafka/Flink 配置字段一致 |
| Paimon writer registry 思路 | `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/sink` | 复用或迁移为通用 Paimon sink 组件 | 避免重复实现 writer 缓存、schema 校验、批量提交 |
| Paimon 类型转换规则 | `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/sink/PaimonTableSchemaValidator.java` | 复用或抽公共工具 | 保持 `VARCHAR/DECIMAL/DATE/TIMESTAMP` 行为一致 |
| 代理主键生成规则 | `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/resolve/ProxyPrimaryKeyGenerator.java` | 复用或移动到新插件 | 保持 `_id_`、`UUID/SHA-256/SHA-512` 行为一致 |
| OilChem Kafka JSON 生产协议 | `/Users/lanvendar/PycharmProjects/sh-web-spider/src/sh_web_spider/sites/oilchem/parser.py` | 作为样例输入来源 | 现有生产端输出 `payload={"schema": ..., "data": rows}` |
