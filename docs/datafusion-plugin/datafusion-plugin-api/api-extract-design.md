# DataFusion Plugin API - API 抽数配置设计

`datafusion-plugin-api` 使用一份 `job.json` 描述 API 抽数任务：执行 HTTP 请求，解析响应字段，可选写入 Redis 中间结果，最终写入 StarRocks、Paimon 或 NOOP。

V1.0 只做轻量任务执行，不做完整工作流编排，不保证 exactly-once，不提供可视化配置。

## 核心约定

- HTTP 默认配置放在顶层 `httpConfig`，step 可用同名 key 覆盖。
- `httpConfig` 保持扁平 KV，不拆 `timeout/retry/failurePolicy` 子对象。
- 输入变量统一为 `inputVars`，步骤输出统一为 `outputVars`。
- Redis、StarRocks、Paimon 统一使用 `loadMode + connectType + options` 描述连接和写入语义。
- StarRocks 支持 `JDBC` 和 `LOAD_STREAM`；Paimon V1.0 只支持 `S3`；调试可使用 `NOOP` sink。

## 顶层结构

```json
{
  "job": {},
  "trigger": {},
  "runtime": {},
  "httpConfig": {},
  "redis": {},
  "inputVars": {},
  "steps": [],
  "sink": {}
}
```

| 字段 | 说明 |
|------|------|
| `job` | 任务基础信息，必填 |
| `trigger` | 本地触发配置；接入调度系统后可忽略，默认 `ONCE` |
| `runtime` | 执行控制，例如循环次数和轮询间隔 |
| `httpConfig` | 全局 HTTP 默认配置 |
| `redis` | Redis 连接与默认缓存策略 |
| `inputVars` | job 级输入变量 |
| `steps` | HTTP 请求步骤，必填 |
| `sink` | 最终写入目标，必填 |

## HTTP 配置

`httpConfig` 可出现在顶层和 step 内。执行 step 时，先读取顶层配置，再用 step 内同名 key 覆盖。

常用 key：

| key | 默认值 | 说明 |
|-----|--------|------|
| `connectMs` | `5000` | 连接超时 |
| `readMs` | `30000` | 读取超时 |
| `writeMs` | `30000` | 写入超时 |
| `maxAttempts` | `3` | 最大请求次数，包含首次请求 |
| `retryIntervalMs` | `1000` | 重试间隔 |
| `backoffMultiplier` | `2.0` | 重试退避倍数 |
| `retryOnStatus` | `[429,500,502,503,504]` | 触发重试的 HTTP 状态码 |
| `onHttpError` | `FAIL` | HTTP 失败策略 |
| `onEmptyData` | `SUCCESS` | 空数据策略 |
| `onParseError` | `FAIL` | 解析失败策略 |

## Step 结构

```json
{
  "id": "fetch_users",
  "type": "HTTP",
  "dependsOn": [],
  "enabled": true,
  "httpConfig": {
    "maxAttempts": 1
  },
  "request": {
    "method": "GET",
    "url": "https://example.com/api/users",
    "headers": {
      "Accept": "application/json"
    },
    "queryParams": {},
    "bodyType": "NONE",
    "body": {}
  },
  "pagination": {
    "type": "NONE"
  },
  "response": {
    "contentType": "JSON",
    "successStatus": [200],
    "successExpression": "code == `0`",
    "messageExpression": "message",
    "recordMode": "ARRAY",
    "fields": [
      {
        "name": "id",
        "expression": "data.list[].id"
      },
      {
        "name": "dt",
        "value": "${inputVars.bizDate}"
      }
    ]
  },
  "redisCache": {
    "enabled": false
  },
  "outputVars": {}
}
```

规则：

- `id` 在 job 内唯一。
- V1.0 step 类型固定为 `HTTP`。
- 未配置 `dependsOn` 时按数组顺序执行。
- `request.method` 支持 `GET`、`POST`、`PUT`、`DELETE`。
- `request.url`、header、query、body 支持模板变量。
- `pagination.type` 支持 `NONE`、`PAGE`、`OFFSET`。
- `response.fields[].expression` 使用 JMESPath；`expression` 和 `value` 二选一。
- `recordMode=ARRAY` 时，至少一个字段表达式应包含 `[]`，用于推断数组路径。
- 字段类型、默认值、主键、分区等落表约束放在 `sink.columns` 和 `sink.table`。

变量引用示例：

```text
${job.id}
${run.id}
${inputVars.bizDate}
${steps.fetch_token.outputVars.accessToken}
${redis.token}
${secrets.API_APP_SECRET}
```

## Redis 缓存

顶层 `redis` 描述连接和默认写入策略；step 级 `redisCache` 只缓存当前 step 的最小必要字段。

```json
{
  "redis": {
    "enabled": true,
    "loadMode": "UPSERT",
    "connectType": "REDIS",
    "options": {
      "host": "localhost",
      "port": 6379,
      "database": 0,
      "passwordRef": "REDIS_PASSWORD",
      "keyPrefix": "datafusion:plugin:api",
      "ttlSeconds": 3600
    }
  },
  "redisCache": {
    "enabled": true,
    "key": "token:${job.id}",
    "ttlSeconds": 1800,
    "valueExpressions": [
      {
        "name": "accessToken",
        "expression": "data.access_token"
      }
    ]
  }
}
```

Redis `loadMode` 支持 `PUT`、`UPSERT`、`APPEND_LIST`、`HASH`。启用 step 缓存时，必须配置 `key` 和非空 `valueExpressions`。执行器只写入声明字段，不缓存完整响应体。

## Sink

```json
{
  "type": "STARROCKS",
  "loadMode": "APPEND",
  "connectType": "LOAD_STREAM",
  "options": {},
  "table": {},
  "columns": [],
  "write": {}
}
```

通用规则：

- `type` 支持 `STARROCKS`、`PAIMON`、`NOOP`。
- `loadMode` 支持 `APPEND`、`UPSERT`、`OVERWRITE_PARTITION`。
- `connectType` 由 sink 类型决定。
- `options` 是扁平 KV；固定字段按语义校验，未知字段透传到底层客户端。
- 非 `NOOP` sink 需要校验 `response.fields[].name` 都能在 `sink.columns[].name` 中找到。
- `UPSERT` 必须配置主键；覆盖分区必须配置分区字段或分区键。

StarRocks JDBC 适合小批量或调试，必填 `jdbcUrl`、`username`、`database`。

StarRocks Load Stream 适合正式批量写入，必填 `loadUrl`、`username`、`database`，常用 `write` 参数包括 `format`、`batchSize`、`flushIntervalMs`、`headers`、`labelPrefix`。

Paimon S3 必填 `warehouse`、`database`、`endpoint`，访问凭证推荐使用 `accessKeyRef` / `secretKeyRef`。

## 最小样例

```json
{
  "job": {
    "id": "benchmark_price_minimal",
    "name": "Benchmark Price Minimal",
    "version": "1.0.0"
  },
  "runtime": {
    "loopCount": 1,
    "loopIntervalMs": 0
  },
  "httpConfig": {
    "connectMs": 10000,
    "readMs": 30000,
    "maxAttempts": 1
  },
  "inputVars": {
    "bizDate": "${date:yyyy-MM-dd}"
  },
  "steps": [
    {
      "id": "getBenchmark",
      "type": "HTTP",
      "enabled": true,
      "request": {
        "method": "GET",
        "url": "http://172.30.19.4:9232/api/product/getBenchmark",
        "headers": {
          "Accept": "application/json"
        },
        "bodyType": "NONE"
      },
      "pagination": {
        "type": "NONE"
      },
      "response": {
        "contentType": "JSON",
        "successStatus": [200],
        "successExpression": "Success == `true`",
        "messageExpression": "Message",
        "recordMode": "ARRAY",
        "fields": [
          {
            "name": "today",
            "expression": "Data[].Today"
          },
          {
            "name": "price",
            "expression": "Data[].Price"
          },
          {
            "name": "dt",
            "value": "${inputVars.bizDate}"
          }
        ]
      }
    }
  ],
  "sink": {
    "type": "NOOP",
    "loadMode": "APPEND",
    "connectType": "NOOP",
    "options": {},
    "table": {
      "name": "benchmark_price_minimal"
    },
    "columns": [
      {
        "name": "today",
        "type": "VARCHAR"
      },
      {
        "name": "price",
        "type": "DOUBLE"
      },
      {
        "name": "dt",
        "type": "DATE"
      }
    ],
    "write": {
      "batchSize": 1000
    }
  }
}
```

## 执行流程

1. 解析 `job.json`。
2. 合并全局与 step 级 `httpConfig`。
3. 解析 `inputVars` 和模板变量。
4. 按 `dependsOn` 或数组顺序串行执行 step。
5. HTTP step 执行请求、分页、重试、成功表达式校验和字段映射。
6. 如启用 `redisCache`，将声明字段写入 Redis。
7. 按批次写入 sink。
8. 返回任务状态、记录数、错误信息和耗时。

## 校验规则

- `job.id`、`steps`、`sink.type`、`sink.loadMode` 必填。
- step id 必须唯一，`dependsOn` 不能循环。
- HTTP step 必须配置 `request.method` 和 `request.url`。
- 模板引用必须可解析。
- `redis.enabled=true` 时必须配置 Redis 连接。
- `redisCache.enabled=true` 时必须配置 `key` 和 `valueExpressions`。
- `STARROCKS + JDBC` 必须配置 `jdbcUrl`、`username`、`database`。
- `STARROCKS + LOAD_STREAM` 必须配置 `loadUrl`、`username`、`database`。
- `PAIMON + S3` 必须配置 `warehouse`、`database`、`endpoint`。

## V1.0 范围

- 支持 `ONCE`、`CRON`、`SCHEDULER` 触发语义。
- 支持 HTTP `GET`、`POST`、`PUT`、`DELETE`。
- 支持 `NONE`、`PAGE`、`OFFSET` 分页。
- 支持 JMESPath 字段映射、`inputVars`、`outputVars` 和模板替换。
- 支持 Redis 中间缓存。
- 支持 StarRocks JDBC / Load Stream 写入。
- 支持 Paimon S3 写入。
