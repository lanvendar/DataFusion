# datafusion-plugin-kafka-json 设计

> 数据结构定义见 [kafka-json-paimon-data-define.md](./kafka-json-paimon-data-define.md)。本文档只描述 Kafka JSON 到 Paimon 的数据流、JMESPath 解析策略、主键策略、代码边界与验证计划。

## 1. Capability

- Capability: 从 Kafka 消费 JSON 消息，通过 `columnsMapping` 的消息级 JMESPath 生成记录数组，再通过 JMESPath 从消息和单条记录中提取表名和字段值，按 `job.json` 中的表结构写入 Paimon。
- Module: `datafusion-plugin/datafusion-plugin-kafka-json`
- Module boundary: 该模块命名带 `datafusion-plugin` 前缀，定位为通用 Kafka JSON 解析插件，不绑定 OilChem 或 `schema + data` 固定协议。
- Java backend package: `com.datafusion.plugin.kafka.json`
- Frontend path: 无
- Route / API prefix: 无
- Call chain: `job config` -> `KafkaSource` -> `JsonNode parser` -> `JMESPath extractor` -> `table resolver` -> `record mapper` -> `Paimon writer registry`

## 2. Data Flow

| Scenario | Source | Through | Target | Data structure | Notes |
|----------|--------|---------|--------|----------------|-------|
| 启动作业 | 本地 `job.json` 或启动参数配置文件 | `ConfigLoader` -> `ConfigValidator` | Flink 作业运行时 | `KafkaJsonPaimonJobConfig` | 启动前完成配置和表达式校验 |
| 消费 Kafka 消息 | Kafka topic | Flink `KafkaSource<String>` | `JsonMessageParser` | 原始 JSON -> Jackson `JsonNode` | 不要求固定 envelope |
| 表路由解析 | `JsonNode` + `sink.tables[]` | `TableResolver` | `PaimonTableConfig` | 命中的目标表配置 | 使用 `tableName.path` 读取消息表名；取不到时使用 `defaultValue`；最终仍为空则过滤消息；按 `tables[]` 顺序匹配，第一版一条消息只写入第一张命中表 |
| 解析记录数组 | Kafka `JsonNode` + `columnsMapping` | `ExpressionEvaluator` | record nodes | `Array<Object>` | `columnsMapping.path` 是消息级 JMESPath，结果支持对象数组或单层对象，单层对象自动包装为一条记录 |
| 解析目标表结构 | Kafka `JsonNode` + `PaimonTableConfig` | `TableResolver` | `ResolvedTableConfig` | database/tableName/table metadata/columns/options | 表结构以 job.json 为准，Kafka 仅提供可选动态取值 |
| 解析单条记录 | record `JsonNode` + `columns[]` | `RecordMapper` | `Map<String, Object>` | 写入记录 | 每列按 `columns[].value` 取值和兜底 |
| 生成代理主键 | mapped record + `PrimaryKeyConfig` | `PrimaryKeyResolver` | mapped record | `_id_` 或配置字段名 | `PROXY` 模式自动补充代理主键列 |
| 初始化或复用 writer | `ResolvedTableConfig` | `PaimonWriterRegistry` | `PaimonTableWriter` | 按 `database.table` 缓存 | 启动预加载真实 Paimon schema，运行期校验当前目标结构 |
| 批量写入 | mapped records | `RecordNormalizer` -> `PaimonTableWriter` | Paimon | `GenericRow` / `CommitMessage` | 单条错误按 `recordErrorPolicy` 处理 |
| 容错恢复 | Flink checkpoint state | Flink runtime | Kafka offsets + Paimon commit | runtime 配置 | `UPSERT` 结合主键提升重放幂等性 |

## 3. job.json Contract

`job.json` 是通用插件的唯一表结构配置源。Kafka 消息不再必须携带 `schema.columns` 或主键定义；它只需要能被 `columnsMapping.path` 投影成对象数组或单层对象，并提供可选的表名字段供 `tableName.path` 做多表路由。

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
| `sink.tables[].columns` | Yes | Paimon 表字段与 record 取值规则 |
| `sink.loadMode` | No | 全局默认写入模式，支持 `APPEND`、`UPSERT`，表级 `loadMode` 可覆盖 |
| `sink.writer` | No | Paimon writer 缓冲、flush 和缓存控制；新插件只支持 `writer`，不兼容旧字段 `write` |

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
    "checkpointMode": "EXACTLY_ONCE",
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
        "database": "dw_dev",
        "tableName": {
          "path": "schema.table.name",
          "defaultValue": "ods_spider_oilchem_price_market"
        },
        "columnsMapping": "schema.data",
        "loadMode": "UPSERT",
        "tableComment": "隆众市场价格行情表",
        "createIfNotExists": true,
        "partitionKeys": ["day_pt"],
        "primaryKey": {
          "mode": "PROXY",
          "field": "_id_",
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
        },
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
      "partitionKeys": ["day_pt"]
    }
  },
  "data": [
    {
      "today": "2026-06-12",
      "day_pt": "2026-06-12"
    }
  ]
}
```

当 `tableName.defaultValue`、`tableComment.defaultValue`、`partitionKeys.defaultValue` 都固定且作业只写一张表时，Kafka 消息可以逐步简化到只保留 `columnsMapping.path` 能取到的记录数组。`columnsMapping.path` 也可以使用 JMESPath 投影复杂 JSON，例如把 `schema.data[].{today: today, day_pt: day_pt}` 转成标准行数组。如果 `sink.tables[]` 配置多张表，建议保留 `schema.table.name` 或等价表名字段，用于多表路由、日志和排障。

`columnsMapping.path` 与 `columns[].value.path` 的上下文不同：

- `columnsMapping.path` 在整条 Kafka JSON 上求值，目标是生成 `Array<Object>` 或单层 `Object`；单层对象自动包装为一条记录。
- `columns[].value.path` 在单条 record 上求值，目标是生成单列值。

因此 `schema.data[].today` 这类表达式适合放在 `columnsMapping.path` 的投影里，不建议直接作为列值表达式；列值表达式应保持相对于单条 record，例如 `today`。

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
| `datafusion-plugin/datafusion-plugin-kafka-json/src/main/resources/sample-kafka-json-paimon-job.json` | 样例配置 |

### 4.2 Modified Files

| File | Notes |
|------|-------|
| `datafusion-plugin/pom.xml` | 增加 `datafusion-plugin-kafka-json` module |
| 根 `pom.xml` | 如依赖管理中缺少 JMESPath，则增加 `io.burt:jmespath-jackson` 版本管理 |

### 4.3 Reused Objects

| Object | Path | Notes |
|--------|------|-------|
| Flink/Kafka runtime 配置 | `datafusion-plugin/plugin-flink-schema-paimon` | 复用字段命名和 Flink 初始化方式 |
| Paimon writer/schema 校验 | `datafusion-plugin/plugin-flink-schema-paimon/src/main/java/com/datafusion/plugin/flink/schema/paimon/sink` | 迁移或抽公共组件 |
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
| `resolveTable(JsonNode message, List<PaimonTableConfig> tables)` | Kafka 消息和表配置 | `Optional<PaimonTableConfig>` | 使用 `tableName.path/defaultValue` 判断当前消息目标表 |
| `resolve(JsonNode message, KafkaRecord record)` | Kafka 消息和 Kafka 元信息 | `Optional<ResolvedTableWritePlan>` | 通过 `columnsMapping` 生成单表写入计划 |
| `write(ResolvedTableWritePlan plan)` | 写入计划 | `void` | 获取 writer，按策略校验和写入 |

### 5.3 Business Rules

| Scenario | Rule | Error / return |
|----------|------|----------------|
| Kafka 消息不是合法 JSON | `JsonMessageParser` 解析失败 | `recordErrorPolicy=SKIP` 时记录 WARN 并跳过当前 Kafka record；`FAIL` 时抛异常 |
| `ExpressionSpec.path` 为空 | 不执行 JMESPath，直接使用 `defaultValue` | 若没有默认值，由调用方按必填规则处理 |
| `ExpressionSpec.path` 取不到值 | 使用 `defaultValue` | 若最终仍为空，由调用方按必填规则处理 |
| `jsonType` 不匹配 | 表达式最终值与顶层类型不一致 | 启动期能发现的配置错误直接 fail；运行期按 `recordErrorPolicy` 或 `schemaMismatchPolicy` 处理 |
| 多表路由 | `tableName.path` 有值时以该值路由；取不到值时使用 `defaultValue`；最终仍为空则过滤消息 | 未命中或为空时记录 WARN 并跳过，日志包含 topic、partition、offset、tableName、reason |
| 一条消息写多张表 | 第一版不支持 | 后续可增加 `multiMatch=true` |
| `columnsMapping` 结果不是数组或对象 | 当前消息无法生成记录列表 | 按 `recordErrorPolicy` 处理 |
| `columnsMapping` 结果是单层对象 | 自动包装为单条 record | 继续执行字段映射 |
| `columnsMapping` 数组元素不是对象 | 当前元素跳过或失败 | 按 `recordErrorPolicy` 处理，不影响同一数组其他元素；日志包含 topic、partition、offset、tableName、recordIndex、reason |
| `columns[].value` 未配置 | 默认按列名作为 JMESPath，即 `path=<column.name>` | 减少简单字段配置 |
| 必输字段为空 | `nullable=false` 且表达式最终值为空 | 按 `recordErrorPolicy` 处理 |
| `UPSERT` 未配置主键 | `loadMode=UPSERT` 时必须有 `primaryKey` | 配置校验失败 |
| 普通主键 | `primaryKey.mode=FIELDS` 时从 `primaryKey.path/defaultValue` 得到主键列表 | 主键字段必须存在于 `columns[]` |
| 代理主键 | `primaryKey.mode=PROXY` 时自动补充 `field` 字段；真实 Paimon 主键只有代理主键字段和分区字段 | `primaryKey.path/defaultValue` 得到的源字段必须能从输出记录中取到；生成值为空或转换失败按 `recordErrorPolicy` 处理 |
| Paimon 表不存在 | `createIfNotExists=true` 时自动建表 | `false` 时抛异常 |
| Paimon 表已存在且不兼容 | 比较字段缺失、类型、主键、分区和表 options | 按 `schemaMismatchPolicy` 处理；comment 不一致只 WARN；日志包含 topic、partition、offset、tableName、reason |
| Kafka 元数据字段 | `includeKafkaMetadataFields=true` 时自动补 `_kafka_topic`、`_kafka_partition`、`_kafka_offset` | 关闭时不写入 |

### 5.4 Transaction Boundary

- Needs transaction: 由 Flink checkpoint 与 Paimon commit 语义控制，默认 `EXACTLY_ONCE`。
- Transactional method: `PaimonTableWriter.writeBatch`。
- Rollback condition: 单表批次写入异常、Paimon commit 失败、`schemaMismatchPolicy=FAIL` 的 schema 校验失败时当前 checkpoint 不提交。

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
| sh-web-spider | Kafka JSON 生产端 | OilChem 现有输出 `schema + data` 可直接作为第一版样例输入 |

## 8. Security and Context

- Current user: 无
- Tenant / project / app context: 独立插件作业，不依赖 Manager 运行时上下文。
- Password / token handling: Kafka SASL、S3 密钥等优先通过环境变量或外部配置引用传入，样例文件不放真实密钥。
- Permission boundary: 仅访问配置中声明的 Kafka topic 与 Paimon warehouse。

## 9. Out of Scope

- 不在第一版实现 UI 页面化配置。
- 不在第一版实现一条 Kafka 消息同时写入多张表。
- 不在第一版实现 JSON Schema 或递归类型系统，`ExpressionSpec.jsonType` 只校验顶层类型。
- 不在第一版支持脚本表达式或任意 Java/Groovy 代码执行。
- 不在第一版实现 Paimon schema evolution，例如在线加列、改类型。
- 不在第一版实现死信队列或死信队列平台化治理，仅保留 WARN 日志和失败策略。

## 10. Verification

- Unit tests: `ExpressionSpec` 求值、`jsonType` 校验、`tableName.path/defaultValue` 路由、`columnsMapping` 解析、record 映射、代理主键生成、配置校验。
- Compile / build command: `mvn -DskipTests compile -pl datafusion-plugin/datafusion-plugin-kafka-json -am`。
- Frontend verification: 无。
- Style / lint: Java 文件走项目 Checkstyle 约定。
- Manual check:
  1. 使用 OilChem 当前 `schema + data` Kafka 样例验证路由和写入。
  2. 使用只包含记录数组的简化 Kafka 样例验证 `columnsMapping` 和 `defaultValue` 兜底。
  3. 使用已有 Paimon 表验证主键、分区、字段类型和表 options 不兼容时的 `SKIP/FAIL` 行为。
