# DataFusion Plugin API - API 抽数配置设计

> 数据结构见 [api-extract-data-define.md](./api-extract-data-define.md)。本文只描述当前执行流程、校验规则和能力边界。

`datafusion-plugin-api` 用 `ApiExtractJobConfig` 承载 `job.json`，执行 HTTP 请求、解析 JSON 响应、可选写入中间缓存，并把记录写入 StarRocks、Paimon 或 NOOP。触发时机由 DataFusion 调度系统负责，`ApiExtractJobConfig` 不包含 `trigger`。

## 能力边界

- step 类型只支持 `HTTP`。
- `dependsOn` 当前按单链校验：最多一个父节点、最多一个子节点，存在依赖时必须只有一个 root。
- HTTP 方法支持 `GET`、`POST`、`PUT`、`DELETE`。
- 分页支持 `NONE`、`PAGE`、`OFFSET`。
- 响应内容按 JSON 解析，字段表达式使用 JMESPath。
- sink 支持 `STARROCKS`、`PAIMON`、`NOOP`。

当前不提供可视化配置、分布式工作流编排或 exactly-once 语义。

## 执行链路

```text
DefaultApiExtractRunner.run
    -> ConfigValidator.validate
    -> runOnce
    -> StepPlanner.plan
    -> HttpStepExecutor.execute
    -> RecordMapper.map
    -> SinkWriter.write
    -> ApiExtractResult
```

`runOnce` 为每次运行生成 `runId`。如果 `runtime.loopCount > 1`，同一个 `runId` 下重复执行 step，并按 `runtime.loopIntervalMs` 间隔等待。

## HTTP Step

执行 step 时先复制顶层 `httpConfig`，再用 step 级 `httpConfig` 覆盖。请求模板由 `TemplateResolver` 解析，支持上游 step 输出、Redis 缓存、环境变量和日期时间函数。

分页执行规则：

- `NONE`：执行一次请求。
- `PAGE`：按 `pageParam`、`pageSizeParam`、`startPage`、`pageSize`、`maxPages` 发起多次请求。
- `OFFSET`：按 `offsetParam`、`limitParam`、`startOffset`、`limit`、`maxRequests` 发起多次请求。
- `stopWhenEmpty=true` 且本页记录数为 0 时停止；记录数小于页大小时也停止。

HTTP 请求按 `maxAttempts` 重试，`retryOnStatus` 命中且未达到最大次数时等待 `retryIntervalMs`，并按 `backoffMultiplier` 递增。

## 响应映射

`successStatus` 不匹配时按 `onHttpError` 决定成功忽略或失败。JSON 解析失败时按 `onParseError` 决定返回空对象或失败。`successExpression` 为 false 时同样按 `onHttpError` 处理。

`RecordMapper` 支持两种记录模式：

- `OBJECT`：把 `response.fields` 映射为一条记录。
- `ARRAY`：从第一个可拆分的 `[]` 表达式推断数组路径，再在每个数组元素上提取字段。

字段配置中 `value` 优先于 `expression`。字段结果如果是 Map 或 Iterable，会序列化为 JSON 字符串。

## Cache 与 Sink

启用 step 缓存时必须启用顶层 Redis。`redisCache.valueExpressions` 从响应 JSON 中提取字段，按 `PUT`、`UPSERT`、`APPEND_LIST` 或 `HASH` 写入 `IntermediateCache`。缓存 key 会加上顶层 `redis.options.keyPrefix`，默认前缀为 `datafusion:plugin:api`。

sink 由 `SinkWriterFactory` 创建：

- `NOOP`：只接收记录，不写外部系统。
- `STARROCKS`：支持 `JDBC` 和 `LOAD_STREAM`，可按配置建表和校验 schema。
- `PAIMON`：通过 Paimon Catalog 写入，可按配置建表和校验 schema。

非 `NOOP` sink 会校验所有 `response.fields[].name` 都存在于 `sink.columns[].name`。

## 校验规则

- `job.id`、`steps`、`sink.type` 必填。
- `runtime.loopCount >= 1`，`runtime.loopIntervalMs >= 0`。
- step id 必须唯一；step 类型必须是 `HTTP`。
- HTTP step 必须配置 `request.method` 和 `request.url`。
- `response.recordMode` 只能为 `OBJECT` 或 `ARRAY`；`ARRAY` 至少需要一个可拆分的 `[]` 字段表达式。
- `redisCache.enabled=true` 时必须配置 `key` 和非空 `valueExpressions`。
- `STARROCKS + JDBC` 必须配置 `jdbcUrl`、`username`、`database`。
- `STARROCKS + LOAD_STREAM` 必须配置 `loadUrl`、`username`、`database`；覆盖分区时还必须配置 `jdbcUrl`。
- `PAIMON` 必须配置 `warehouse`、`database`、`endpoint`。

## 验证

```powershell
mvn -DskipTests compile -pl datafusion-plugin/datafusion-plugin-api -am
```
