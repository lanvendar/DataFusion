# DataFusion Plugin API - API 抽数配置设计

## 1. 目标

`datafusion-plugin-api` 通过一份 `job.json` 描述 API 抽数任务：按触发模式执行 HTTP 请求，解析响应字段，必要时把中间结果写入 Redis，最终写入 StarRocks、Paimon 或 NOOP。

V1.0 只做轻量任务执行，不做完整工作流编排、不保证 exactly-once、不做可视化配置。

核心约定：

- HTTP 全局默认配置统一放在顶层 `httpConfig`，step 可配置同名 `httpConfig` 覆盖局部 key。
- `httpConfig` 是扁平 KV，不再拆成 `timeout`、`retry`、`failurePolicy` 三层。
- 输入变量统一为 `inputVars`，步骤输出统一为 `outputVars`。
- Redis、StarRocks、Paimon 统一使用 `loadMode + connectType + options` 描述写入/连接语义。
- StarRocks 支持 `JDBC` 和 `LOAD_STREAM` 两种写入连接类型；Paimon V1.0 只支持 `S3`。

## 2. 顶层结构

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

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `job` | 任务基础信息。 | 必须 | 无 |
| `trigger` | 本地触发配置。调度系统接入后可忽略。 | 可选 | `{"mode":"ONCE"}` |
| `runtime` | 非 HTTP 的运行控制，例如循环次数。 | 可选 | `{"loopCount":1,"loopIntervalMs":0}` |
| `httpConfig` | 全局 HTTP 默认配置，step 可局部覆盖。 | 可选 | 见 HTTP 配置表 |
| `redis` | Redis 连接与默认缓存策略。 | 可选 | `{"enabled":false}` |
| `inputVars` | job 级输入变量。 | 可选 | `{}` |
| `steps` | HTTP 请求步骤。 | 必须 | 无 |
| `sink` | 最终写入目标。 | 必须 | 无 |

## 3. job.json 字段结构

```json
{
  "job": {
    "id": "api_user_sync",
    "name": "User API Sync",
    "description": "Extract users from upstream API",
    "version": "1.0.0"
  },
  "trigger": {
    "mode": "ONCE",
    "cron": "0 */10 * * * ?",
    "timezone": "Asia/Shanghai"
  },
  "runtime": {
    "loopCount": 1,
    "loopIntervalMs": 0
  },
  "httpConfig": {
    "connectMs": 10000,
    "readMs": 30000,
    "writeMs": 30000,
    "probeConnectMs": 5000,
    "probeReadMs": 15000,
    "maxAttempts": 3,
    "retryIntervalMs": 1000,
    "backoffMultiplier": 2.0,
    "retryOnStatus": [429, 500, 502, 503, 504],
    "onHttpError": "FAIL",
    "onEmptyData": "SUCCESS",
    "onParseError": "FAIL"
  },
  "redis": {
    "enabled": false
  },
  "inputVars": {
    "bizDate": "${date:yyyy-MM-dd}"
  },
  "steps": [
    {
      "id": "fetch_users",
      "type": "HTTP",
      "dependsOn": [],
      "enabled": true,
      "httpConfig": {},
      "request": {},
      "pagination": {},
      "response": {},
      "redisCache": {},
      "outputVars": {}
    }
  ],
  "sink": {
    "type": "STARROCKS",
    "loadMode": "APPEND",
    "connectType": "LOAD_STREAM",
    "options": {},
    "table": {},
    "schema": [],
    "write": {}
  }
}
```

## 4. 运行与 HTTP 配置

`runtime` 只保留任务执行控制：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `loopCount` | 单次触发后执行轮数。 | 可选 | `1` |
| `loopIntervalMs` | 多轮之间的等待时间。 | 可选 | `0` |

`httpConfig` 可出现在顶层和 step 中。执行 step 时，先读取顶层 `httpConfig`，再用 step 内同名 key 覆盖。

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `connectMs` | 正式请求连接超时。Benchmark 建议 `10000`。 | 可选 | `5000` |
| `readMs` | 正式请求读取超时。Benchmark 建议 `30000`。 | 可选 | `30000` |
| `writeMs` | 正式请求写入超时。 | 可选 | `30000` |
| `probeConnectMs` | 连通性探测连接超时。Benchmark 测试使用 `5000`。 | 可选 | `5000` |
| `probeReadMs` | 连通性探测读取超时。Benchmark 测试使用 `15000`。 | 可选 | `15000` |
| `maxAttempts` | 最大请求次数，包含首次请求。 | 可选 | `3` |
| `retryIntervalMs` | 重试间隔。 | 可选 | `1000` |
| `backoffMultiplier` | 重试退避倍数。 | 可选 | `2.0` |
| `retryOnStatus` | 需要重试的 HTTP 状态码。 | 可选 | `[429,500,502,503,504]` |
| `onHttpError` | HTTP 失败策略：`FAIL`、`SUCCESS`。 | 可选 | `FAIL` |
| `onEmptyData` | 空数据策略：`SUCCESS`、`FAIL`。 | 可选 | `SUCCESS` |
| `onParseError` | 响应解析失败策略：`FAIL`、`SUCCESS`。 | 可选 | `FAIL` |

`BenchmarkApiIntegrationTest` 暴露的真实接口特征：

- URL：`http://172.30.19.4:9232/api/product/getBenchmark`
- Method：`GET`
- Header：`Accept: application/json`
- Pagination：`NONE`
- Success expression：`Success == \`true\``
- Message expression：`Message`
- Records：`Data[]`

## 5. Step 结构

```json
{
  "id": "getBenchmark",
  "type": "HTTP",
  "enabled": true,
  "httpConfig": {
    "maxAttempts": 1
  },
  "request": {
    "method": "GET",
    "url": "http://172.30.19.4:9232/api/product/getBenchmark",
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
    "successExpression": "Success == `true`",
    "messageExpression": "Message",
    "recordMode": "ARRAY",
    "fields": [
      {
        "name": "today",
        "type": "STRING",
        "expression": "Data[].Today",
        "isKey": true,
        "nullable": false
      },
      {
        "name": "price",
        "type": "DOUBLE",
        "expression": "Data[].Price",
        "nullable": true
      }
    ]
  },
  "redisCache": {
    "enabled": false
  },
  "outputVars": {}
}
```

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `id` | step 唯一标识。 | 必须 | 无 |
| `type` | step 类型，V1.0 固定为 `HTTP`。 | 必须 | `HTTP` |
| `enabled` | 是否启用该 step。 | 可选 | `true` |
| `dependsOn` | 上游 step id 列表。不配置时按数组顺序执行。 | 可选 | `[]` |
| `httpConfig` | step 级 HTTP 覆盖配置。 | 可选 | 继承全局 `httpConfig` |
| `request` | HTTP 请求配置。 | 必须 | 无 |
| `request.method` | `GET`、`POST`、`PUT`、`DELETE`。 | 必须 | 无 |
| `request.url` | HTTP URL，支持模板变量。 | 必须 | 无 |
| `request.headers` | 请求头 KV。 | 可选 | `{}` |
| `request.queryParams` | query 参数 KV。 | 可选 | `{}` |
| `request.bodyType` | `NONE`、`JSON`、`FORM`、`RAW`。 | 可选 | `NONE` |
| `request.body` | 请求体。 | 可选 | `{}` |
| `pagination` | 分页配置。 | 必须 | `{"type":"NONE"}` |
| `pagination.type` | V1.0 支持 `NONE`、`PAGE`、`OFFSET`。 | 必须 | `NONE` |
| `response` | 响应解析配置。 | 必须 | 无 |
| `response.recordMode` | `OBJECT` 或 `ARRAY`。 | 必须 | 无 |
| `response.fields` | JMESPath 字段映射。 | 必须 | `[]` |
| `redisCache` | step 级 Redis 写入配置，位置固定为 `steps[].redisCache`。 | 可选 | `{"enabled":false}` |
| `outputVars` | 从当前响应中提取变量，供下游引用。 | 可选 | `{}` |

`ARRAY` 模式要求有且只有一个字段配置 `isKey=true`，该字段表达式返回数组并决定记录条数。其它数组字段长度必须一致，标量字段广播到每条记录。

变量引用：

```text
${job.id}
${run.id}
${inputVars.bizDate}
${steps.fetch_token.outputVars.accessToken}
${redis.token}
${secrets.API_APP_SECRET}
```

## 6. Redis 缓存

顶层 `redis` 只描述 Redis 连接和默认写入策略；step 级缓存固定放在 `steps[].redisCache`，描述当前 step 要缓存的最小片段。

```json
{
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
}
```

step redisCache：

```json
{
  "enabled": true,
  "loadMode": "UPSERT",
  "key": "token:${job.id}",
  "ttlSeconds": 1800,
  "valueExpressions": [
    {
      "name": "accessToken",
      "expression": "data.access_token"
    },
    {
      "name": "expireIn",
      "expression": "data.expire_in"
    }
  ]
}
```

`loadMode` 支持 `PUT`、`UPSERT`、`APPEND_LIST`、`HASH`。

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `enabled` | 是否启用当前 step 的 Redis 缓存。 | 必须 | `false` |
| `key` | Redis key 模板。 | 启用时必须 | 无 |
| `ttlSeconds` | 缓存过期时间；不填继承 `redis.options.ttlSeconds`。 | 可选 | 继承 `redis.options.ttlSeconds` |
| `loadMode` | 当前缓存写入模式；不填继承 `redis.loadMode`。 | 可选 | 继承 `redis.loadMode` |
| `valueExpressions` | 要缓存的最小字段表达式数组。 | 启用时必须 | `[]` |
| `valueExpressions[].name` | 写入 Redis value 的字段名。 | 必须 | 无 |
| `valueExpressions[].expression` | JMESPath 表达式。 | 必须 | 无 |

`valueExpressions` 是多段 JMESPath 表达式数组。执行器只把数组中声明的字段写入 Redis，避免缓存完整响应体。Redis value 推荐为一个小 JSON 对象，例如：

```json
{
  "accessToken": "xxx",
  "expireIn": 7200
}
```

如果 `steps[].redisCache.enabled=true`，则必须配置 `key` 和非空 `valueExpressions`。`ttlSeconds`、`loadMode` 未配置时继承顶层 `redis.options.ttlSeconds` 和 `redis.loadMode`。

## 7. Sink 结构

```json
{
  "type": "STARROCKS",
  "loadMode": "APPEND",
  "connectType": "LOAD_STREAM",
  "options": {},
  "table": {},
  "schema": [],
  "write": {}
}
```

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `type` | `STARROCKS`、`PAIMON`、`NOOP`。 | 必须 | 无 |
| `loadMode` | `APPEND`、`UPSERT`、`OVERWRITE_PARTITION`。 | 必须 | `APPEND` |
| `connectType` | `JDBC`、`LOAD_STREAM`、`S3`、`NOOP`。StarRocks 必须显式选择 `JDBC` 或 `LOAD_STREAM`；Paimon 固定 `S3`；NOOP 固定 `NOOP`。 | 必须 | 无 |
| `options` | 由 `type + connectType` 决定的连接参数，保持扁平 KV。固定字段由本文定义，其余官方原生参数可动态透传。 | 必须 | `{}` |
| `table` | 表名、主键、分区、是否自动建表。 | 必须 | 无 |
| `schema` | 字段定义。 | 必须 | `[]` |
| `write` | 批大小、写入格式、覆盖分区等写入行为。 | 可选 | `{}` |

### 7.1 StarRocks JDBC

适合小批量写入或调试。

```json
{
  "type": "STARROCKS",
  "loadMode": "APPEND",
  "connectType": "JDBC",
  "options": {
    "jdbcUrl": "jdbc:mysql://starrocks-fe:9030/dwd",
    "username": "root",
    "passwordRef": "STARROCKS_PASSWORD",
    "database": "dwd"
  }
}
```

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `type` | 固定为 `STARROCKS`。 | 必须 | `STARROCKS` |
| `connectType` | 固定为 `JDBC`。 | 必须 | 无 |
| `options.jdbcUrl` | StarRocks MySQL 协议 JDBC 地址。 | 必须 | 无 |
| `options.username` | StarRocks 用户名。 | 必须 | 无 |
| `options.password` | 明文密码，不推荐。 | 可选 | 无 |
| `options.passwordRef` | 密码环境变量名，推荐。 | 可选 | 无 |
| `options.database` | StarRocks 数据库名。 | 必须 | 无 |

### 7.2 StarRocks Load Stream

适合正式批量写入。

```json
{
  "type": "STARROCKS",
  "loadMode": "APPEND",
  "connectType": "LOAD_STREAM",
  "options": {
    "loadUrl": "http://starrocks-fe:8030",
    "endpoint": "/api/{database}/{table}/_stream_load",
    "username": "root",
    "passwordRef": "STARROCKS_PASSWORD",
    "database": "dwd",
    "timeoutMs": 30000,
    "maxRetries": 3,
    "retryIntervalMs": 1000
  },
  "write": {
    "format": "JSON",
    "batchSize": 1000,
    "flushIntervalMs": 5000,
    "headers": {
      "format": "json",
      "strip_outer_array": "false",
      "read_json_by_line": "true"
    },
    "labelPrefix": "datafusion_api",
    "partialUpdate": false
  }
}
```

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `type` | 固定为 `STARROCKS`。 | 必须 | `STARROCKS` |
| `connectType` | 固定为 `LOAD_STREAM`。 | 必须 | 无 |
| `options.loadUrl` | StarRocks FE HTTP 地址。 | 必须 | 无 |
| `options.endpoint` | Stream Load endpoint 模板。 | 可选 | `/api/{database}/{table}/_stream_load` |
| `options.username` | StarRocks 用户名。 | 必须 | 无 |
| `options.password` | 明文密码，不推荐。 | 可选 | 无 |
| `options.passwordRef` | 密码环境变量名，推荐。 | 可选 | 无 |
| `options.database` | StarRocks 数据库名。 | 必须 | 无 |
| `options.timeoutMs` | 单次 Stream Load HTTP 超时。 | 可选 | `30000` |
| `options.maxRetries` | Stream Load 失败重试次数。 | 可选 | `3` |
| `options.retryIntervalMs` | Stream Load 重试间隔。 | 可选 | `1000` |
| `write.format` | 写入格式。 | 可选 | `JSON` |
| `write.batchSize` | 每批写入记录数。 | 可选 | `1000` |
| `write.flushIntervalMs` | 批次刷新间隔。 | 可选 | `5000` |
| `write.headers` | Stream Load header KV，允许官方参数透传。 | 可选 | `{}` |
| `write.labelPrefix` | Stream Load label 前缀。 | 可选 | `datafusion_api` |
| `write.partialUpdate` | 是否开启 StarRocks 主键表部分列更新。 | 可选 | `false` |

### 7.3 Paimon S3

Paimon V1.0 只定义 S3/Ceph 写入参数，不再嵌套 `options.s3` 或 `options.options`。

```json
{
  "type": "PAIMON",
  "loadMode": "APPEND",
  "connectType": "S3",
  "options": {
    "warehouse": "s3://ceph-bucket/warehouse/paimon",
    "catalogType": "filesystem",
    "database": "dwd",
    "endpoint": "http://ceph-rgw:7480",
    "region": "us-east-1",
    "accessKey": "CEPH_S3_ACCESS_KEY",
    "secretKey": "CEPH_S3_SECRET_KEY",
    "pathStyleAccess": true,
    "sslEnabled": false
  },
  "write": {
    "commitUser": "datafusion-api-plugin",
    "batchSize": 1000,
    "overwritePartition": {
      "enabled": false,
      "partitionKeys": ["dt"],
      "onePartitionPerBatch": true
    }
  }
}
```

字段说明：

| 字段 | 说明 | 是否必须 | 默认值 |
| --- | --- | --- | --- |
| `type` | 固定为 `PAIMON`。 | 必须 | `PAIMON` |
| `connectType` | 固定为 `S3`。 | 必须 | `S3` |
| `options.warehouse` | Paimon warehouse 路径。 | 必须 | 无 |
| `options.catalogType` | Paimon catalog 类型。 | 可选 | `filesystem` |
| `options.database` | Paimon database。 | 必须 | 无 |
| `options.endpoint` | S3/Ceph endpoint。 | 必须 | 无 |
| `options.region` | S3 region。 | 可选 | `us-east-1` |
| `options.accessKey` | 明文 AccessKey，不推荐。 | 可选 | 无 |
| `options.accessKeyRef` | AccessKey 环境变量名，推荐。 | 可选 | 无 |
| `options.secretKey` | 明文 SecretKey，不推荐。 | 可选 | 无 |
| `options.secretKeyRef` | SecretKey 环境变量名，推荐。 | 可选 | 无 |
| `options.pathStyleAccess` | 是否启用 path-style access。 | 可选 | `true` |
| `options.sslEnabled` | 是否启用 SSL。 | 可选 | `false` |
| `write.commitUser` | Paimon commit user。 | 可选 | `datafusion-api-plugin` |
| `write.batchSize` | 每批写入记录数。 | 可选 | `1000` |
| `write.overwritePartition` | 覆盖分区配置。 | 可选 | `{"enabled":false}` |

`options` 的边界规则：

- 文档表格中列出的字段为固定字段，必须按语义校验。
- `options` 允许继续携带官方原生参数或协议透传参数，按 KV 形式动态扩展。
- 对于未知字段，执行器不应直接拒绝，应按 `type + connectType` 透传到底层客户端，除非和固定字段冲突。
- 当固定字段与动态 KV 同名时，以固定字段为准。
- `redis.options`、`sink.options`、`write.headers` 这类 KV 区域，都属于可扩展参数区。

## 8. 最小 job 样例

```json
{
  "job": {
    "id": "benchmark_price_minimal",
    "name": "Benchmark Price Minimal",
    "version": "1.0.0"
  },
  "trigger": {
    "mode": "ONCE"
  },
  "runtime": {
    "loopCount": 1,
    "loopIntervalMs": 0
  },
  "httpConfig": {
    "connectMs": 10000,
    "readMs": 30000,
    "writeMs": 30000,
    "probeConnectMs": 5000,
    "probeReadMs": 15000,
    "maxAttempts": 1,
    "retryIntervalMs": 1000,
    "backoffMultiplier": 2.0,
    "retryOnStatus": [429, 500, 502, 503, 504],
    "onHttpError": "FAIL",
    "onEmptyData": "SUCCESS",
    "onParseError": "FAIL"
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
        "queryParams": {},
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
            "type": "STRING",
            "expression": "Data[].Today",
            "isKey": true,
            "nullable": false
          },
          {
            "name": "product_detail_id_name",
            "type": "STRING",
            "expression": "Data[].ProductdetailIdname",
            "nullable": true
          },
          {
            "name": "price",
            "type": "DOUBLE",
            "expression": "Data[].Price",
            "nullable": true
          },
          {
            "name": "range_value",
            "type": "DOUBLE",
            "expression": "Data[].Range",
            "nullable": true
          },
          {
            "name": "dt",
            "type": "DATE",
            "value": "${inputVars.bizDate}",
            "nullable": false
          }
        ]
      },
      "outputVars": {}
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
    "schema": [
      {
        "name": "today",
        "type": "VARCHAR",
        "nullable": false
      },
      {
        "name": "product_detail_id_name",
        "type": "VARCHAR",
        "nullable": true
      },
      {
        "name": "price",
        "type": "DOUBLE",
        "nullable": true
      },
      {
        "name": "range_value",
        "type": "DOUBLE",
        "nullable": true
      },
      {
        "name": "dt",
        "type": "DATE",
        "nullable": false
      }
    ],
    "write": {
      "batchSize": 1000
    }
  }
}
```

## 9. 完整 job 样例

```json
{
  "job": {
    "id": "api_user_sync",
    "name": "User API Sync",
    "version": "1.0.0"
  },
  "trigger": {
    "mode": "CRON",
    "cron": "0 */10 * * * ?",
    "timezone": "Asia/Shanghai"
  },
  "runtime": {
    "loopCount": 1,
    "loopIntervalMs": 0
  },
  "httpConfig": {
    "connectMs": 5000,
    "readMs": 30000,
    "writeMs": 30000,
    "probeConnectMs": 5000,
    "probeReadMs": 15000,
    "maxAttempts": 3,
    "retryIntervalMs": 1000,
    "backoffMultiplier": 2.0,
    "retryOnStatus": [429, 500, 502, 503, 504],
    "onHttpError": "FAIL",
    "onEmptyData": "SUCCESS",
    "onParseError": "FAIL"
  },
  "redis": {
    "enabled": true,
    "loadMode": "UPSERT",
    "connectType": "REDIS",
    "options": {
      "host": "172.26.185.208",
      "port": 6379,
      "database": 0,
      "passwordRef": "REDIS_PASSWORD",
      "keyPrefix": "datafusion:plugin:api",
      "ttlSeconds": 3600
    }
  },
  "inputVars": {
    "bizDate": "${date:yyyy-MM-dd}"
  },
  "steps": [
    {
      "id": "fetch_token",
      "type": "HTTP",
      "enabled": true,
      "request": {
        "method": "POST",
        "url": "https://example.com/oauth/token",
        "headers": {
          "Content-Type": "application/json"
        },
        "bodyType": "JSON",
        "body": {
          "appKey": "${secrets.API_APP_KEY}",
          "appSecret": "${secrets.API_APP_SECRET}"
        }
      },
      "pagination": {
        "type": "NONE"
      },
      "response": {
        "contentType": "JSON",
        "successStatus": [200],
        "successExpression": "code == `0`",
        "messageExpression": "message"
      },
      "redisCache": {
        "enabled": true,
        "key": "token:${job.id}",
        "ttlSeconds": 1800,
        "loadMode": "UPSERT",
        "valueExpressions": [
          {
            "name": "accessToken",
            "expression": "data.access_token"
          },
          {
            "name": "expireIn",
            "expression": "data.expire_in"
          }
        ]
      },
      "outputVars": {
        "accessToken": "data.access_token"
      }
    },
    {
      "id": "fetch_users",
      "type": "HTTP",
      "dependsOn": ["fetch_token"],
      "enabled": true,
      "httpConfig": {
        "readMs": 60000
      },
      "request": {
        "method": "GET",
        "url": "https://example.com/api/users",
        "headers": {
          "Accept": "application/json",
          "Authorization": "Bearer ${steps.fetch_token.outputVars.accessToken}"
        },
        "queryParams": {
          "status": "active"
        },
        "bodyType": "NONE"
      },
      "pagination": {
        "type": "PAGE",
        "pageParam": "page",
        "pageSizeParam": "pageSize",
        "startPage": 1,
        "pageSize": 100,
        "maxPages": 1000,
        "stopWhenEmpty": true
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
            "type": "STRING",
            "expression": "data.list[].id",
            "isKey": true,
            "nullable": false
          },
          {
            "name": "name",
            "type": "STRING",
            "expression": "data.list[].name",
            "nullable": true
          },
          {
            "name": "updated_time",
            "type": "DATETIME",
            "expression": "data.list[].updatedTime",
            "format": "yyyy-MM-dd HH:mm:ss",
            "nullable": true
          },
          {
            "name": "dt",
            "type": "DATE",
            "value": "${inputVars.bizDate}",
            "nullable": false
          }
        ]
      }
    }
  ],
  "sink": {
    "type": "STARROCKS",
    "loadMode": "APPEND",
    "connectType": "LOAD_STREAM",
    "options": {
      "loadUrl": "http://starrocks-fe:8030",
      "endpoint": "/api/{database}/{table}/_stream_load",
      "username": "root",
      "passwordRef": "STARROCKS_PASSWORD",
      "database": "dwd",
      "timeoutMs": 30000,
      "maxRetries": 3,
      "retryIntervalMs": 1000
    },
    "table": {
      "name": "api_user",
      "createIfNotExists": true,
      "primaryKeys": ["id"],
      "partition": {
        "enabled": true,
        "field": "dt",
        "type": "DAY"
      }
    },
    "schema": [
      {
        "name": "id",
        "type": "VARCHAR",
        "length": 64,
        "nullable": false
      },
      {
        "name": "name",
        "type": "VARCHAR",
        "length": 255,
        "nullable": true
      },
      {
        "name": "updated_time",
        "type": "DATETIME",
        "nullable": true
      },
      {
        "name": "dt",
        "type": "DATE",
        "nullable": false
      }
    ],
    "write": {
      "format": "JSON",
      "batchSize": 1000,
      "flushIntervalMs": 5000,
      "headers": {
        "format": "json",
        "strip_outer_array": "false",
        "read_json_by_line": "true"
      },
      "labelPrefix": "datafusion_api",
      "partialUpdate": false,
      "overwritePartition": {
        "enabled": false,
        "partitionField": "dt"
      }
    }
  }
}
```

Paimon sink 替换示例：

```json
{
  "type": "PAIMON",
  "loadMode": "APPEND",
  "connectType": "S3",
  "options": {
    "warehouse": "s3://ceph-bucket/warehouse/paimon",
    "catalogType": "filesystem",
    "database": "dwd",
    "endpoint": "http://ceph-rgw:7480",
    "region": "us-east-1",
    "accessKeyRef": "CEPH_S3_ACCESS_KEY",
    "secretKeyRef": "CEPH_S3_SECRET_KEY",
    "pathStyleAccess": true,
    "sslEnabled": false
  },
  "table": {
    "name": "api_user",
    "createIfNotExists": true,
    "primaryKeys": ["id"],
    "partitionKeys": ["dt"]
  },
  "schema": [
    {
      "name": "id",
      "type": "STRING",
      "nullable": false
    },
    {
      "name": "name",
      "type": "STRING",
      "nullable": true
    },
    {
      "name": "updated_time",
      "type": "TIMESTAMP",
      "nullable": true
    },
    {
      "name": "dt",
      "type": "STRING",
      "nullable": false
    }
  ],
  "write": {
    "commitUser": "datafusion-api-plugin",
    "batchSize": 1000,
    "overwritePartition": {
      "enabled": false,
      "partitionKeys": ["dt"],
      "onePartitionPerBatch": true
    }
  }
}
```

## 10. 执行流程

1. 解析 `job.json`。
2. 合并全局 `httpConfig` 与 step 级 `httpConfig`。
3. 解析 `inputVars` 和模板变量。
4. 根据 `trigger.mode` 执行一次、启动本地 cron 或由调度系统触发。
5. 按 `dependsOn` 或数组顺序串行执行 step。
6. HTTP step 执行请求、分页、重试、成功表达式校验和字段映射。
7. 如启用 `redisCache`，将 `valueExpressions` 指定的最小数据写入 Redis。
8. 按批次写入 sink。
9. 返回任务状态、记录数、错误信息和耗时。

## 11. 校验规则

- `job.id`、`steps`、`sink.type`、`sink.loadMode` 必填。
- step id 必须唯一，`dependsOn` 不能循环。
- HTTP step 必须配置 `request.method` 和 `request.url`。
- `httpConfig` 必须是扁平 KV；step 级同名 key 覆盖全局 key。
- `response.recordMode=ARRAY` 时，必须有且只有一个 `isKey=true` 字段。
- 模板引用中的 `${inputVars.xxx}`、`${steps.stepId.outputVars.xxx}` 必须可解析。
- `redis.enabled=true` 时，必须配置 `redis.connectType=REDIS`、`redis.options.host`、`redis.options.port`、`redis.options.database`。
- Redis `loadMode` 必须是 `PUT`、`UPSERT`、`APPEND_LIST`、`HASH`。
- `steps[].redisCache.enabled=true` 时，必须配置 `key` 和非空 `valueExpressions`。
- `sink.loadMode` 必须是 `APPEND`、`UPSERT`、`OVERWRITE_PARTITION`。
- `sink.type=STARROCKS` 时，`connectType` 必须是 `JDBC` 或 `LOAD_STREAM`。
- `STARROCKS + JDBC` 必须配置 `jdbcUrl`、`username`、`database`。
- `STARROCKS + LOAD_STREAM` 必须配置 `loadUrl`、`username`、`database`。
- `sink.type=PAIMON` 时，`connectType` 必须是 `S3`，并配置 `warehouse`、`database`、`endpoint`。
- `loadMode=UPSERT` 时必须配置 `table.primaryKeys`。
- `loadMode=OVERWRITE_PARTITION` 时必须配置分区字段或分区键。

## 12. V1.0 范围

- 支持 `ONCE`、`CRON`、`SCHEDULER` 触发语义。
- 支持 HTTP `GET`、`POST`、`PUT`、`DELETE`。
- 支持 `NONE`、`PAGE`、`OFFSET` 分页。
- 支持 JMESPath 字段映射、`inputVars`、`outputVars` 和模板替换。
- 支持 Redis 中间缓存。
- 支持 StarRocks JDBC / Load Stream 写入。
- 支持 Paimon S3 写入。
