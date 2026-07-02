# API 抽数数据定义

本文定义 `datafusion-plugin-api` 当前配置对象、运行期对象和端口模型。执行流程见 [api-extract-design.md](./api-extract-design.md)。

## 数据库数据模型

无。

## Java 配置模型

### 顶层配置

| 类 | 字段 | 默认值 | 说明 |
|----|------|--------|------|
| `ApiExtractJobConfig` | `job` | `new JobConfig()` | 任务基础信息 |
| `ApiExtractJobConfig` | `runtime` | `new RuntimeConfig()` | 循环执行配置 |
| `ApiExtractJobConfig` | `httpConfig` | `new HttpConfig()` | 全局 HTTP 默认配置 |
| `ApiExtractJobConfig` | `redis` | `new RedisConfig()` | Redis 连接和默认缓存配置 |
| `ApiExtractJobConfig` | `inputVars` | 空 `LinkedHashMap` | 输入变量 |
| `ApiExtractJobConfig` | `steps` | 空 `ArrayList` | HTTP step 列表 |
| `ApiExtractJobConfig` | `sink` | `new SinkConfig()` | 写入目标配置 |

### 基础配置

| 类 | 字段 | 默认值 | 说明 |
|----|------|--------|------|
| `JobConfig` | `id` / `name` / `description` / `version` | 无 | 任务元信息 |
| `RuntimeConfig` | `loopCount` | `1` | 循环次数 |
| `RuntimeConfig` | `loopIntervalMs` | `0` | 循环间隔，毫秒 |

### HTTP 与 Redis

| 类 | 字段 | 默认值 | 说明 |
|----|------|--------|------|
| `HttpConfig` | `connectMs` / `readMs` / `writeMs` | `5000` / `30000` / `30000` | 请求超时，毫秒 |
| `HttpConfig` | `probeConnectMs` / `probeReadMs` | `5000` / `15000` | 探测超时，毫秒 |
| `HttpConfig` | `maxAttempts` / `retryIntervalMs` / `backoffMultiplier` | `3` / `1000` / `2.0` | 重试参数 |
| `HttpConfig` | `retryOnStatus` | `[429,500,502,503,504]` | 触发重试的 HTTP 状态码 |
| `HttpConfig` | `onHttpError` / `onEmptyData` / `onParseError` | `FAIL` / `SUCCESS` / `FAIL` | 失败处理策略 |
| `RedisConfig` | `enabled` | `false` | 是否启用 Redis |
| `RedisConfig` | `loadMode` | `UPSERT` | 默认缓存写入模式 |
| `RedisConfig` | `connectType` | `REDIS` | 连接类型 |
| `RedisConfig` | `options` | 空 `LinkedHashMap` | 连接和 key 参数 |

### Step 配置

| 类 | 字段 | 默认值 | 说明 |
|----|------|--------|------|
| `StepConfig` | `id` | 无 | step id |
| `StepConfig` | `type` | `HTTP` | step 类型 |
| `StepConfig` | `dependsOn` | 空 `ArrayList` | 上游 step id |
| `StepConfig` | `enabled` | `true` | 是否执行 |
| `StepConfig` | `httpConfig` | 无 | step 级 HTTP 覆盖配置 |
| `StepConfig` | `request` / `pagination` / `response` / `redisCache` | new 对象 | 请求、分页、响应、缓存配置 |
| `StepConfig` | `outputVars` | 空 `LinkedHashMap` | step 输出表达式 |
| `RequestConfig` | `method` / `url` | `GET` / 无 | HTTP 方法和 URL |
| `RequestConfig` | `headers` / `queryParams` | 空 `LinkedHashMap` | 请求头和查询参数 |
| `RequestConfig` | `bodyType` / `body` / `rawBody` | `NONE` / 无 / 无 | 请求体配置 |
| `PaginationConfig` | `type` | `NONE` | 分页类型 |
| `PaginationConfig` | `pageParam` / `pageSizeParam` / `startPage` / `pageSize` / `maxPages` | 无 / 无 / `1` / `100` / `1` | PAGE 分页 |
| `PaginationConfig` | `offsetParam` / `limitParam` / `startOffset` / `limit` / `maxRequests` | 无 / 无 / `0` / `100` / `1` | OFFSET 分页 |
| `PaginationConfig` | `stopWhenEmpty` | `true` | 空页时停止 |
| `ResponseConfig` | `contentType` | `JSON` | 响应类型 |
| `ResponseConfig` | `successStatus` | `[200]` | 成功状态码 |
| `ResponseConfig` | `successExpression` / `messageExpression` | 无 | 业务成功和错误消息表达式 |
| `ResponseConfig` | `recordMode` | 无 | `OBJECT` 或 `ARRAY` |
| `ResponseConfig` | `fields` | 空 `ArrayList` | 字段映射 |
| `FieldConfig` | `name` / `expression` / `value` | 无 | 目标字段、JMESPath 表达式或固定值 |
| `RedisCacheConfig` | `enabled` / `key` / `ttlSeconds` / `loadMode` | `false` / 无 / `0` / 无 | step 缓存配置 |
| `RedisCacheConfig` | `valueExpressions` | 空 `ArrayList` | 缓存值表达式 |
| `ValueExpressionConfig` | `name` / `expression` | 无 | 缓存字段名和表达式 |

### Sink 配置

| 类 | 字段 | 默认值 | 说明 |
|----|------|--------|------|
| `SinkConfig` | `type` / `loadMode` / `connectType` | 无 / `APPEND` / 无 | 目标类型、写入模式、连接类型 |
| `SinkConfig` | `options` | 空 `LinkedHashMap` | 连接参数 |
| `SinkConfig` | `table` / `columns` / `write` | new 对象 / 空列表 / new 对象 | 表、字段和写入参数 |
| `TableConfig` | `name` / `comment` / `createIfNotExists` | 无 / 无 / `true` | 表信息 |
| `TableConfig` | `primaryKeys` / `partitionKeys` / `partition` | 空列表 / 空列表 / new 对象 | 主键和分区 |
| `PartitionConfig` | `enabled` / `field` / `type` | `false` / 无 / 无 | 单字段分区配置 |
| `ColumnConfig` | `name` / `type` / `length` / `precision` / `scale` | 无 | 字段定义 |
| `ColumnConfig` | `format` / `nullable` / `comment` / `defaultValue` | 无 / `true` / 无 / 无 | 格式、空值、注释、默认值 |
| `WriteConfig` | `format` / `batchSize` / `flushIntervalMs` | `JSON` / `1000` / `5000` | 写入批次参数 |
| `WriteConfig` | `headers` / `labelPrefix` / `partialUpdate` / `overwritePartition` | 空 map / `datafusion_api` / `false` / 空 map | 写入扩展参数 |

## Java 运行期模型

| 类 | 字段 | 说明 |
|----|------|------|
| `ApiExtractContext` | `config` / `runId` / `vars` / `stepOutputs` | 单次运行上下文 |
| `Record` | 继承 `LinkedHashMap<String,Object>` | 一条结构化记录 |
| `StepResult` | `stepId` / `success` / `records` / `elapsedMs` / `errorMessage` | step 执行结果 |
| `ApiExtractResult` | `success` / `jobId` / `runId` / `records` / `elapsedMs` / `errorMessage` / `steps` | job 执行结果 |
| `HttpRequestData` | `method` / `url` / `headers` / `body` / `connectTimeoutMs` / `readTimeoutMs` | 实际 HTTP 请求 |
| `HttpResponseData` | `statusCode` / `body` / `headers` | 实际 HTTP 响应 |

## 端口模型

| 端口 | 方法 | 说明 |
|------|------|------|
| `ApiExtractRunner` | `run(config)` | 执行 API 抽数任务 |
| `ApiHttpClient` | `execute(request)` | 发送 HTTP 请求 |
| `IntermediateCache` | `put` / `appendList` / `putHash` / `get` | 中间缓存 |
| `SinkWriter` | `open` / `write` / `flush` / `close` | 目标写入 |

## 前端数据模型

无。

## 枚举和值域

| 类型 | 值 |
|------|----|
| `SinkMode` | `APPEND` / `UPSERT` / `OVERWRITE_PARTITION` |
| `sink.type` | `STARROCKS` / `PAIMON` / `NOOP` |
| `sink.connectType` | `JDBC` / `LOAD_STREAM` / `S3` / `NOOP` |
| `pagination.type` | `NONE` / `PAGE` / `OFFSET` |
| `request.bodyType` | `NONE` / `JSON` / `FORM` / `RAW` |
| Redis load mode | `PUT` / `UPSERT` / `APPEND_LIST` / `HASH` |

## 数据映射

1. `job.json` 反序列化为 `ApiExtractJobConfig`。
2. `ApiExtractJobConfig` 校验后生成 `ApiExtractContext`。
3. `StepConfig` 渲染为 `HttpRequestData`。
4. `HttpResponseData` 解析为 JSON，再映射为 `Record`。
5. `Record` 批量写入 `SinkWriter`，step 输出和缓存写入运行上下文或 `IntermediateCache`。
