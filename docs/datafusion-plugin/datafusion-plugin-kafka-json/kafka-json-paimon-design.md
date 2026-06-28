# datafusion-plugin-kafka-json 设计

> 数据结构定义见 [kafka-json-paimon-data-define.md](./kafka-json-paimon-data-define.md)。本文档只描述 Kafka JSON 到 Paimon 的数据流、JMESPath 解析策略、主键策略、代码边界与验证计划。

## 1. Capability

- Capability: 从 Kafka 消费 JSON 消息，通过 `columnsMapping` 的消息级 JMESPath 生成记录数组，表级元数据优先复用 Kafka JSON 中的标准 `schema.table`；`job.json` 除 `database` 外一旦配置 table 元数据就按整段覆盖，字段定义在 `job.json` 配置 `columns[]` 时全量使用 job 定义，否则全量使用 Kafka `schema.columns`，最终写入 Paimon。
- Module: `datafusion-plugin/datafusion-plugin-kafka-json`
- Module boundary: 该模块命名带 `datafusion-plugin` 前缀，定位为通用 Kafka JSON 解析插件，不绑定 OilChem 或 `schema + data` 固定协议。
- Java backend package: `com.datafusion.plugin.kafka.json`
- Frontend path: 无
- Route / API prefix: 无
- Call chain: `job config` -> `KafkaSource` -> `JsonNode parser` -> `JMESPath extractor` -> `table resolver` -> `record mapper` -> `Paimon Sink V2 writer` -> `Paimon committer`

## 2. Data Flow

| Scenario | Source | Through | Target | Data structure | Notes |
|----------|--------|---------|--------|----------------|-------|
| 启动作业 | 本地 `job.json` 或启动参数配置文件 | `ConfigLoader` -> `ConfigValidator` | Flink 作业运行时 | `KafkaJsonPaimonJobConfig` | 启动前完成配置和表达式校验 |
| 消费 Kafka 消息 | Kafka topic | Flink `KafkaSource<String>` | `JsonMessageParser` | 原始 JSON -> Jackson `JsonNode` | 不要求固定 envelope |
| 表路由解析 | `JsonNode` + `sink.tables[]` | `TableResolver` | `PaimonTableConfig` | 命中的目标表配置 | `table.database` 和 `table.name` 必须由 job 静态配置；Kafka JSON 中 `schema.table.name` 等于 `table.name` 时命中；无表名或未命中时过滤消息 |
| 解析记录数组 | Kafka `JsonNode` + `columnsMapping` | `ExpressionEvaluator` | record nodes | `Array<Object>` | `columnsMapping.path` 是消息级 JMESPath，结果支持对象数组或单层对象，单层对象自动包装为一条记录 |
| 解析目标表结构 | Kafka `JsonNode` + `PaimonTableConfig` | `TableResolver` | `ResolvedTableConfig` | database/tableName/table metadata/columns/options | `table.database/name` 来自 job；真实 Paimon 表存在时结构优先；缺表建表时优先使用 job 完整定义，job 未配置 `columns[]` 时字段可取 Kafka `schema.columns[]` |
| 解析单条记录 | record `JsonNode` + `columns[]` | `RecordMapper` | `Map<String, Object>` | 写入记录 | 每列按 `columns[].value` 取值和兜底 |
| 生成代理主键 | mapped record + `PrimaryKeyConfig` | `PrimaryKeyResolver` | mapped record | `_id_` | `PROXY` 模式自动补充固定代理主键列；`mode` 未配置时默认 `FIELDS` |
| 初始化或复用 writer | `ResolvedTableConfig` | `PaimonTableWriterRegistry` | `PaimonTableWriter` | 按 `database.table` 缓存 | 启动预加载并标记真实 Paimon 表状态；存在则按真实 schema 写入，缺表则由首条命中数据触发建表 |
| 批量写入 | mapped records | `RecordNormalizer` -> `PaimonTableWriter` | Paimon | `GenericRow` / `CommitMessage` | writer 只写文件并产出 `CommitMessage`，不直接提交 snapshot |
| 按表提交 | `PaimonCommittable` | `PaimonCommitter` | Paimon snapshot | `identifier + commitIdentifier` | committer 按表和提交编号聚合提交，避免不同表或不同 checkpoint 的消息混提 |
| 容错恢复 | Flink checkpoint state | Flink runtime | Kafka offsets + Paimon commit | runtime 配置 | `UPSERT` 结合主键提升重放幂等性 |

## 3. job.json Contract

`job.json` 是通用插件的目标表路由和建表配置源。Kafka 消息可携带标准结构 `schema.table` / `schema.columns`，其中 `schema.table.name` 用于与 `sink.tables[].table.name` 做路由匹配，`schema.columns[]` 只在真实 Paimon 表不存在且 job 未配置 `columns[]` 时作为建表字段定义兜底。`table.database` 和 `table.name` 必须由 job 静态定义；字段定义不能局部覆盖，job 中一旦配置 `columns[]` 就必须是完整字段定义。

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
| `sink.options` | Yes | 全局 Paimon options，包含 catalog 连接参数和默认表 options |
| `sink.tables` | Yes | 目标表数组，每个元素定义路由、表结构、字段取值和写入策略 |
| `sink.tables[].columnsMapping` | Yes | 消息级 JMESPath，结果必须是待映射的对象数组或单层对象 |
| `sink.tables[].table.database` | Yes | 目标 Paimon database，必须静态配置 |
| `sink.tables[].table.name` | Yes | 目标 Paimon table，必须静态配置，也是默认路由匹配值 |
| `sink.tables[].table.includeKafkaMetadataFields` | No | 当前表是否自动补充 Kafka topic、partition、offset 字段 |
| `sink.tables[].columns` | No | 完整字段定义；未配置时读取 Kafka `schema.columns` |
| `sink.loadMode` | No | 全局默认写入模式，支持 `APPEND`、`UPSERT`，表级 `loadMode` 可覆盖 |
| `sink.writer` | No | Paimon writer 缓冲、flush 和缓存控制；当前只支持 `writer` 字段 |

OilChem 样例配置骨架：

```json
{
  "job": {
    "id": "kafka_json_oilchem_paimon",
    "name": "Kafka JSON OilChem To Paimon",
    "version": "1.0.0"
  },
  "source": {
    "type": "KAFKA",
    "bootstrapServers": "kafka-1:9092,kafka-2:9092",
    "topics": ["dw-web-spider-paimon-test"],
    "groupId": "datafusion-plugin-kafka-json",
    "startingOffsets": "group-offsets",
    "properties": {}
  },
  "runtime": {
    "parallelism": 2,
    "deploymentMode": "LOCAL",
    "executionMode": "STREAMING",
    "checkpointMode": "AT_LEAST_ONCE",
    "checkpointIntervalMs": 60000,
    "checkpointTimeoutMs": 600000,
    "maxConcurrentCheckpoints": 1,
    "checkpointStorage": "file:///tmp/flink-checkpoints/kafka-json-oilchem-paimon",
    "stateBackend": "HASHMAP",
    "restartStrategy": "FIXED_DELAY",
    "restartAttempts": 3,
    "restartDelayMs": 10000
  },
  "sink": {
    "type": "PAIMON",
    "connectType": "S3",
    "schemaMismatchPolicy": "SKIP",
    "recordErrorPolicy": "SKIP",
    "loadMode": "UPSERT",
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
        "table": {
          "database": "dw_dev",
          "name": "ods_spider_oilchem_price_market",
          "comment": "隆众市场价格行情表",
          "createIfNotExists": true,
          "includeKafkaMetadataFields": false,
          "partitionKeys": ["day_pt"],
          "primaryKeys": {
            "mode": "PROXY",
            "algorithm": "SHA-256",
            "defaultValue": [
              "day_pt",
              "varieties_id",
              "category_name",
              "sub_category_name",
              "business_type",
              "two_level_business_type",
              "region_id",
              "internal_market_name",
              "sale_region_name",
              "standard",
              "brand_name",
              "specifications_name",
              "purpose_name",
              "process",
              "producer",
              "price_type_name",
              "receiving_station"
            ]
          }
        },
        "columnsMapping": "data",
        "loadMode": "UPSERT",
        "columns": [
          {
            "name": "today",
            "dataType": "VARCHAR",
            "length": 32,
            "nullable": false,
            "comment": "价格日期"
          },
          {
            "name": "day_pt",
            "dataType": "DATE",
            "nullable": false,
            "comment": "分区日期",
            "format": "yyyy-MM-dd",
            "value": {
              "path": "day_pt",
              "defaultValue": "1970-01-01"
            }
          },
          {
            "name": "mainstream_price",
            "dataType": "DECIMAL",
            "precision": 18,
            "scale": 4,
            "comment": "主流价"
          }
        ],
        "options": {
          "bucket": "4",
          "write-buffer-size": "128mb",
          "file.format": "parquet"
        }
      }
    ],
    "writer": {
      "batchSize": 1000,
      "flushIntervalMs": 5000,
      "maxOpenWriters": 256
    }
  }
}
```

可接收的 Kafka 消息可以是当前 spider 项目的 `schema + data`：

```json
{
  "schema": {
    "table": {
      "name": "ods_spider_oilchem_price_market",
      "comment": "隆众市场价格行情表",
      "createIfNotExists": true,
      "primaryKeys": {
        "mode": "PROXY",
        "algorithm": "SHA-256",
        "defaultValue": [
          "day_pt",
          "varieties_id",
          "business_type",
          "two_level_business_type"
        ]
      },
      "partitionKeys": ["day_pt"]
    },
    "columns": [
      {
        "name": "today",
        "type": "VARCHAR",
        "length": 32,
        "nullable": false,
        "comment": "价格日期"
      }
    ]
  },
  "data": [
    {
      "today": "2026-06-12",
      "day_pt": "2026-06-12"
    }
  ]
}
```

当 job.json 已完整配置 table 元数据和 columns 定义且作业只写一张表时，Kafka 消息可以只保留 `columnsMapping.path` 能取到的记录数组。`columnsMapping.path` 也可以使用 JMESPath 投影复杂 JSON，例如把 `data[].{today: today, day_pt: day_pt}` 转成标准行数组。如果 `sink.tables[]` 配置多张表，Kafka 消息需要保留 `schema.table.name` 或等价表名字段，用于多表路由、日志和排障。

标准结构解析规则：

```text
表路由: sink.tables[].table.database 和 table.name 必须静态配置；Kafka JSON schema.table.name 与 table.name 相等才命中该配置
真实表: 真实 Paimon 表存在时，写入字段顺序、字段类型、主键、分区键和 NOT NULL 校验均以真实表为准
缺表建表: 真实 Paimon 表不存在时，使用 job table 元数据建表；字段优先使用 job columns[]，job 未配置 columns[] 时使用 Kafka schema.columns[]
字段定义: job.json sink.tables[].columns[] 存在时全量使用 job 定义；不存在时才允许读取 Kafka JSON schema.columns[]
```

表级字段规则：

- `table.database`: job.json 必须静态配置，用于目标 Paimon 表定位。
- `table.name`: job.json 必须静态配置，用于目标 Paimon 表定位和默认路由匹配；Kafka JSON `schema.table.name` 为空或不等于该值时跳过当前表配置。
- `table.comment`: 可选；真实表已存在时仅用于注释差异 WARN，不影响写入；缺表建表时用于表注释。
- `table.createIfNotExists`: 可选，默认 `true`；缺表时为 `false` 则按 `schemaMismatchPolicy` 处理。
- `table.partitionKeys`: 缺表建表时必须能解析出非空数组；真实表已存在时仍会与真实表分区键做兼容校验。
- `table.primaryKeys`: `UPSERT` 缺表建表时必须配置；`mode` 未配置时默认 `FIELDS`；`defaultValue` 是主键字段数组；`PROXY` 模式真实 Paimon 主键为 `_id_` 加分区键。

字段定义规则：

- Kafka `schema.columns[]` 可以提供标准结构字段定义。
- job.json `columns[]` 一旦配置就表示完整字段定义，不与 Kafka `schema.columns[]` 做局部合并。
- 真实 Paimon 表已存在时优先级最高，写入字段、类型转换和 NOT NULL 校验以真实 Paimon 表结构为准。
- 真实 Paimon 表不存在时，若 job 配置了 `columns[]`，按 job 完整字段定义建表。
- 真实 Paimon 表不存在且 job 未配置 `columns[]` 时，按 Kafka `schema.columns[]` 建表；这种模式要求第一条用于建表的 Kafka JSON schema 准确。
- `columns[].value` 未配置时仍默认按列名从单条 record 中取值。

启动和运行期表状态流程：

```text
Flink 启动:
  1. 遍历 sink.tables[] 中 enabled=true 的静态 database/name 配置。
  2. 查询真实 Paimon 表。
  3. 表存在时，schema cache 标记 EXISTS，并缓存真实 Paimon schema。
  4. 表不存在时，schema cache 标记 MISSING_CONFIGURED，不立即失败，等待首条命中数据触发建表。

Kafka 消息进入:
  1. 读取 schema.table.name。
  2. 逐个匹配 enabled=true 的 tables[]。
  3. 只有 schema.table.name == tables[].table.name 时命中。
  4. 未命中任何配置时 WARN 并跳过该消息。

schema cache 状态 = EXISTS:
  1. 使用真实 Paimon schema 校验解析后的目标表配置。
  2. 使用真实 Paimon rowType 做字段顺序、类型转换和 NOT NULL 校验。

schema cache 状态 = MISSING_CONFIGURED:
  1. createIfNotExists=false 时按 schemaMismatchPolicy 处理。
  2. createIfNotExists=true 时准备建表 schema。
  3. columns 优先使用 job columns[]；job 未配置 columns[] 时使用 Kafka schema.columns[]。
  4. partitionKeys/primaryKeys 使用 job table 配置。
  5. 建表信息不完整或非法时按 schemaMismatchPolicy 处理；SKIP 时 WARN 并跳过本批 records，表状态保持 MISSING_CONFIGURED。
  6. 建表成功后 catalog.getTable()，reload 真实 Paimon schema 到 cache，状态改为 EXISTS。
  7. 本批 records 继续按真实 Paimon schema 写入。
```

错误策略分层：

- 表缺失、建表信息不完整、建表失败、真实表结构不兼容属于 schema 初始化或 schema 兼容问题，受 `schemaMismatchPolicy` 控制。
- `schemaMismatchPolicy=SKIP` 时记录 WARN 并跳过本批 records，不让 Flink 任务失败；缺表状态保持 `MISSING_CONFIGURED`，下一条命中数据会继续尝试建表。
- `schemaMismatchPolicy=FAIL` 时抛错，Flink 按 restart 策略处理。
- 字段映射失败、字段值格式不合法、类型转换失败、真实 Paimon NOT NULL 字段为空、单条写入失败属于 record 错误，受 `recordErrorPolicy` 控制。
- `recordErrorPolicy=SKIP` 时只跳过当前 record，不影响同一条 Kafka JSON 中 `data[]` 的其他 record，并以 WARN 记录 `topic`、`partition`、`offset`、`identifier`、`recordIndex`、`column`（如果能定位）和 `reason`。
- `recordErrorPolicy=FAIL` 时抛错，Flink 按 restart 策略处理。

`columnsMapping.path` 与 `columns[].value.path` 的上下文不同：

- `columnsMapping.path` 在整条 Kafka JSON 上求值，目标是生成 `Array<Object>` 或单层 `Object`；单层对象自动包装为一条记录。
- `columns[].value.path` 在单条 record 上求值，目标是生成单列值。
- `table.database` 来自 job；其它 `table.*` 元数据可以共享 Kafka 标准结构中的 `schema.table`，也可以由 job 整段覆盖；`columns[]` 是完整字段定义，不能只写局部字段覆盖 Kafka `schema.columns[]`。

因此 `data[].today` 这类表达式放在 `columnsMapping.path` 的投影里；列值表达式保持相对于单条 record，例如 `today`。

配置支持简写：常量值可以直接写字符串、布尔值或数组，并统一归一化为 `ExpressionSpec.defaultValue`；`columnsMapping` 可以直接写 JMESPath 字符串，并归一化为 `ExpressionSpec.path`；`columns[].value` 不写时默认按列名取值；`jsonType` 通常由字段语义或 `dataType` 推断，只有表达式返回类型不明显时才显式配置。

## 4. File Changes

### 4.1 New Files

| File | Notes |
|------|-------|
| `docs/datafusion-plugin/datafusion-plugin-kafka-json/kafka-json-paimon-data-define.md` | 新插件数据结构定义 |
| `docs/datafusion-plugin/datafusion-plugin-kafka-json/kafka-json-paimon-design.md` | 新插件设计文档 |
| `datafusion-plugin/datafusion-plugin-kafka-json/pom.xml` | 新 Maven 插件模块 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/java/com/datafusion/plugin/kafka/json/KafkaJsonPaimonApplication.java` | 新插件启动入口 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/java/com/datafusion/plugin/kafka/json/config/*` | 配置模型、加载和校验 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/java/com/datafusion/plugin/kafka/json/expression/*` | JMESPath 表达式编译、求值和类型校验 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/java/com/datafusion/plugin/kafka/json/resolve/*` | 表路由、字段映射、主键解析 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/java/com/datafusion/plugin/kafka/json/source/*` | Kafka source 构建和反序列化 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/java/com/datafusion/plugin/kafka/json/sink/paimon/*` | Paimon writer、schema 校验、批量提交 |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/sample-standard-kafka-json-paimon-job.json` | 标准结构简化样例，Kafka 消息提供 `schema.table` / `schema.columns` |
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/sample-generic-kafka-json-paimon-job.json` | 通用结构样例，job 配置完整定义 table、columns 和 JMESPath |

### 4.2 Modified Files

| File | Notes |
|------|-------|
| `datafusion-plugin/pom.xml` | 增加 `datafusion-plugin-kafka-json` module |
| 根 `pom.xml` | 如依赖管理中缺少 JMESPath，则增加 `io.burt:jmespath-jackson` 版本管理 |

### 4.3 Reused Objects

| Object | Path | Notes |
|--------|------|-------|
| Flink/Kafka runtime 配置 | `datafusion-plugin/plugin-flink-schema-paimon` | 复用字段命名和 Flink 初始化方式 |
| Paimon writer/schema 校验 | `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/sink` | 可作为 Paimon 写入组件参考 |
| 代理主键生成 | `ProxyPrimaryKeyGenerator` | 保持 `_id_` 与算法行为一致 |
| OilChem spider 输出 | `/Users/lanvendar/PycharmProjects/sh-web-spider/src/sh_web_spider/sites/oilchem/parser.py` | 作为真实 Kafka JSON 样例来源 |

## 5. Java Backend Design

### 5.1 Controller

无

### 5.2 Service

| Method | Input | Output | Notes |
|--------|-------|--------|-------|
| `load(String configPath)` | 配置文件路径 | `KafkaJsonPaimonJobConfig` | 加载 JSON 配置并解析环境变量 |
| `validate(KafkaJsonPaimonJobConfig config)` | 顶层配置 | `void` | 校验 Kafka、runtime、sink、JMESPath 表达式和 Paimon 表结构配置 |
| `parse(String message)` | Kafka 原始消息 | `JsonNode` | 使用 Jackson 解析任意 JSON |
| `evaluate(ExpressionSpec spec, JsonNode context)` | 表达式配置和上下文 | `Object` | JMESPath 求值、默认值兜底和 `jsonType` 顶层校验 |
| `resolveTable(JsonNode message, List<PaimonTableConfig> tables)` | Kafka 消息和表配置 | `Optional<PaimonTableConfig>` | 使用 `table.name.path/defaultValue` 判断当前消息目标表 |
| `resolve(JsonNode message, KafkaRecord record)` | Kafka 消息和 Kafka 元信息 | `Optional<ResolvedTableWritePlan>` | 通过 `columnsMapping` 生成单表写入计划 |
| `write(ResolvedTableWritePlan plan)` | 写入计划 | `void` | 获取 writer，按策略校验和写入 |

### 5.3 Business Rules

| Scenario | Rule | Error / return |
|----------|------|----------------|
| Kafka 消息不是合法 JSON | `JsonMessageParser` 解析失败 | `recordErrorPolicy=SKIP` 时记录 WARN 并跳过当前 Kafka record；`FAIL` 时抛异常 |
| `ExpressionSpec.path` 为空 | 不执行 JMESPath，直接使用 `defaultValue` | 若没有默认值，由调用方按必填规则处理 |
| `ExpressionSpec.path` 取不到值 | 使用 `defaultValue` | 若最终仍为空，由调用方按必填规则处理 |
| `jsonType` 不匹配 | 表达式最终值与顶层类型不一致 | 启动期能发现的配置错误直接 fail；运行期按 `recordErrorPolicy` 或 `schemaMismatchPolicy` 处理 |
| 多表路由 | `table.name.path` 有值时以该值路由；取不到值时使用 `defaultValue`；最终仍为空则过滤消息 | 未命中或为空时记录 WARN 并跳过，日志包含 topic、partition、offset、tableName、reason |
| 一条消息写多张表 | 不支持 | 一条消息只命中第一张有效目标表配置 |
| `columnsMapping` 结果不是数组或对象 | 当前消息无法生成记录列表 | 按 `recordErrorPolicy` 处理 |
| `columnsMapping` 结果是单层对象 | 自动包装为单条 record | 继续执行字段映射 |
| `columnsMapping` 数组元素不是对象 | 当前元素跳过或失败 | 按 `recordErrorPolicy` 处理，不影响同一数组其他元素；日志包含 topic、partition、offset、tableName、recordIndex、reason |
| `columns[].value` 未配置 | 默认按列名作为 JMESPath，即 `path=<column.name>` | 减少简单字段配置 |
| 必输字段为空 | `nullable=false` 且表达式最终值为空 | 按 `recordErrorPolicy` 处理 |
| `UPSERT` 未配置主键 | `loadMode=UPSERT` 时从 job.json `table.primaryKeys`、Kafka `schema.table.primaryKeys` 解析主键 | 解析不到主键字段时配置校验或运行期 schema 校验失败 |
| 普通主键 | `primaryKeys.mode=FIELDS` 时从 `primaryKeys.path/defaultValue` 得到主键列表；`mode` 为空默认 `FIELDS` | 主键字段必须存在于 `columns[]` |
| 代理主键 | `primaryKeys.mode=PROXY` 时自动补充固定 `_id_` 字段；真实 Paimon 主键只有 `_id_` 和分区字段 | `primaryKeys.path/defaultValue` 得到的源字段用于生成代理键；字段值允许为空字符串参与拼接 |
| Paimon 表不存在 | `createIfNotExists=true` 时自动建表 | `false` 时抛异常 |
| Paimon 表已存在且不兼容 | 比较字段缺失、类型、主键、分区和表 options | 按 `schemaMismatchPolicy` 处理；comment 不一致只 WARN；日志包含 topic、partition、offset、tableName、reason |
| Kafka 元数据字段 | `sink.tables[].table.includeKafkaMetadataFields=true` 时自动补 `_kafka_topic`、`_kafka_partition`、`_kafka_offset` | 关闭时不写入 |

### 5.4 Transaction Boundary

- Consistency boundary: 当前使用 Flink Sink V2 提交模型，writer 并行写文件并产出 `CommitMessage`，committer 按表和提交编号聚合提交。
- Replay behavior: `UPSERT` + 主键或代理主键可以提升失败重放时的幂等性；`APPEND` 表在 Paimon commit 成功但 Kafka offset checkpoint 未完成时可能重复写入。
- Failure behavior: 单表批次写入异常、Paimon commit 失败、`schemaMismatchPolicy=FAIL` 的 schema 校验失败会让当前 Flink task 失败并触发重启策略。

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
| Kafka | Flink `KafkaSource` + consumer group | 使用 topic/partition/consumer group 分摊消费压力 |
| JMESPath | `io.burt:jmespath-jackson` 或等价 Java 库 | 负责用 `columnsMapping` 从消息投影记录数组，从消息中解析表名和表元信息，并从单条记录中解析字段值 |
| Paimon | Catalog API + Table Write API | 负责建库建表、真实表 schema 校验、批量写入和 commit |
| S3 / 对象存储 | Paimon options 注入 | 推荐 `warehouse=s3://...` 配合 `s3.*` |
| Flink Runtime | checkpoint、state backend、operator parallelism | 大表写入和失败恢复依赖 Flink 运行时 |
| sh-web-spider | Kafka JSON 生产端 | OilChem 现有输出 `schema + data` 可直接作为样例输入 |

## 8. Security and Context

- Current user: 无
- Tenant / project / app context: 独立插件作业，不依赖 Manager 运行时上下文。
- Password / token handling: Kafka SASL、S3 密钥等优先通过环境变量或外部配置引用传入，样例文件不放真实密钥。
- Permission boundary: 仅访问配置中声明的 Kafka topic 与 Paimon warehouse。

## 9. Out of Scope

- 不实现 UI 页面化配置。
- 不实现一条 Kafka 消息同时写入多张表。
- 不实现 JSON Schema 或递归类型系统，`ExpressionSpec.jsonType` 只校验顶层类型。
- 不支持脚本表达式或任意 Java/Groovy 代码执行。
- 不实现 Paimon schema evolution，例如在线加列、改类型。
- 不实现死信队列或死信队列平台化治理，仅保留 WARN 日志和失败策略。

## 10. Verification

- Unit tests: `ExpressionSpec` 求值、`jsonType` 校验、`table.name.path/defaultValue` 路由、`columnsMapping` 解析、record 映射、代理主键生成、配置校验。
- Compile / build command: `mvn -DskipTests compile -pl datafusion-plugin/datafusion-plugin-kafka-json -am`。
- Frontend verification: 无。
- Style / lint: Java 文件走项目 Checkstyle 约定。
- Manual check:
  1. 使用 OilChem 当前 `schema + data` Kafka 样例验证路由和写入。
  2. 使用只包含记录数组的简化 Kafka 样例验证 `columnsMapping` 和 `defaultValue` 兜底。
  3. 使用已有 Paimon 表验证主键、分区、字段类型和表 options 不兼容时的 `SKIP/FAIL` 行为。
