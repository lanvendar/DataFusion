# DataFusion Plugin API - API 接口抽数设计

## 1. 背景

`datafusion-plugin-api` 需要提供一个基于配置驱动的接口抽数程序，用于从 API 接口抽取数据，并将结果写入分析型表。V1.0 可以通过主函数直接启动，读取 JSON 配置文件执行；后续接入调度后，调度系统可以把同一份 JSON 配置作为任务参数传给插件执行器。

该设计需要支持：

- 临时 cron 定时触发，后续可切换为调度触发。
- HTTP 请求配置，包括请求方式、请求头、请求体、响应体解析、超时、失败重试等。
- 目标落表 schema 定义，支持 StarRocks 和 Paimon。
- 一个抽数任务内存在多个 API 调用，并支持接口之间的依赖。
- 中间接口结果可暂存 Redis，供后续接口使用。

## 2. 设计目标

- 配置驱动：一份 JSON 配置描述完整抽数任务。
- 调度友好：既支持本地主函数按 cron 运行，也支持被调度系统触发后只执行一次。
- 支持多步骤：一个任务可以包含多个 API 请求步骤，并按依赖顺序执行。
- 流式处理：大结果集按分页和批次处理，避免一次性加载全部数据。
- 抽取与写入解耦：核心抽数逻辑不直接绑定 StarRocks 或 Paimon。
- V1.0 具备基本可恢复能力：支持 HTTP 重试，并保留必要运行结果用于排查。

## 3. 不处理的内容

- 完整工作流编排引擎。
- 复杂 OAuth2 refresh token 生命周期管理。
- HTTP 源、Redis、目标表之间的严格 exactly-once 语义。
- 多实例并发运行同一任务时的分布式锁。
- 可视化配置页面。

这些能力可以在基础 API 抽数链路稳定后逐步补充。

## 4. 运行模式

### 4.1 本地 Cron 模式

主函数接收 JSON 文件路径或 JSON 字符串，解析 `trigger.cron`，在进程内启动一个临时 cron 定时器。

启动示例：

```bash
java -jar datafusion-plugin-api.jar --config D:/tmp/api-job.json
```

### 4.2 调度触发模式

调度系统把同一份 JSON 作为 `pluginParam` 传入。此时 `trigger.mode` 可以为 `SCHEDULER`，也可以由 worker 忽略 `trigger` 配置。插件每次被调度触发时只执行一次，并返回任务结果。

## 5. 顶层 JSON 结构

```json
{
  "job": {
    "id": "api_user_sync",
    "name": "User API Sync",
    "description": "Extract users from upstream API and load into StarRocks",
    "version": "1.0.0"
  },
  "trigger": {
    "mode": "CRON",
    "cron": "0 */10 * * * ?",
    "timezone": "Asia/Shanghai"
  },
  "runtime": {
    "loopCount": 1,
    "loopIntervalMs": 0,
    "timeout": {
      "connectMs": 5000,
      "readMs": 30000,
      "writeMs": 30000
    },
    "retry": {
      "maxAttempts": 3,
      "intervalMs": 1000,
      "backoffMultiplier": 2.0,
      "retryOnStatus": [429, 500, 502, 503, 504]
    },
    "failurePolicy": {
      "onHttpError": "FAIL",
      "onEmptyData": "SUCCESS",
      "onParseError": "FAIL"
    }
  },
  "redis": {
    "enabled": true,
    "host": "172.26.185.208",
    "port": 6379,
    "database": 0,
    "passwordRef": "REDIS_PASSWORD",
    "keyPrefix": "datafusion:plugin:api",
    "ttlSeconds": 3600
  },
  "steps": [],
  "sink": {}
}
```

顶层字段说明：

- `job`：任务基础信息。
- `trigger`：触发配置。临时本地运行时使用，后续调度触发时可以忽略。
- `runtime`：运行时控制参数。
- `redis`：中间数据缓存配置。
- `steps`：接口调用步骤列表。
- `sink`：最终落表配置。

### 5.1 runtime 含义

`runtime` 是插件执行器的全局运行控制参数，不属于 HTTP 请求参数，也不属于落表 schema。

```json
{
  "loopCount": 1,
  "loopIntervalMs": 0,
  "timeout": {
    "connectMs": 5000,
    "readMs": 30000,
    "writeMs": 30000
  },
  "retry": {
    "maxAttempts": 3,
    "intervalMs": 1000,
    "backoffMultiplier": 2.0,
    "retryOnStatus": [429, 500, 502, 503, 504]
  },
  "failurePolicy": {
    "onHttpError": "FAIL",
    "onEmptyData": "SUCCESS",
    "onParseError": "FAIL"
  }
}
```

字段说明：

- `loopCount`：单次触发后循环执行次数。默认 `1` 表示只执行一轮；大于 `1` 时，同一任务会重复执行多轮，适合接口数据需要短周期轮询补齐的场景。
- `loopIntervalMs`：多轮循环之间的等待间隔，单位毫秒。`loopCount` 为 `1` 时该配置无效。
- `timeout`：全局默认 HTTP 超时配置。step 未配置自己的 `timeout` 时使用该配置。
- `retry`：全局默认 HTTP 重试配置。step 未配置自己的 `retry` 时使用该配置。
- `failurePolicy`：全局默认失败处理策略。step 未配置自己的 `failurePolicy` 时使用该配置。

运行约束：

- `steps` 是一个有向无环图，但只允许单链路 DAG。
- step 按 `dependsOn` 推导出的拓扑顺序串行执行;如果未配置 `dependsOn`，则按数组顺序串行执行;`dependsOn`要么不配置,要么都配。
- 不支持同一 job 内多分支并发；多分支场景拆成多个 job 处理。
- 任一关键 step 失败后任务直接失败。
- 批量写入大小只保留在 `sink.write.batchSize` 中。

## 6. 触发配置

```json
{
  "mode": "CRON",
  "cron": "0 */10 * * * ?",
  "timezone": "Asia/Shanghai"
}
```

字段说明：

- `mode`：支持 `CRON`、`ONCE`，补充 `ONCE`就是用来调试的。
- `cron`：临时本地运行使用的 cron 表达式。
- `timezone`：cron 使用的时区。

调度系统接入后，应由调度系统负责定时，插件只负责执行单次任务。

## 7. 步骤模型

每一次 API 调用都定义为一个 step。step 可以依赖上游 step，并通过变量或 Redis 使用上游输出。

```json
{
  "id": "fetch_users",
  "type": "HTTP",
  "dependsOn": ["fetch_token"],
  "enabled": true,
  "request": {},
  "pagination": {},
  "response": {},
  "cache": {},
  "output": {}
}
```

字段说明：

- `id`：步骤唯一标识。
- `type`：步骤类型，V1.0 支持 `HTTP`。
- `dependsOn`：依赖的上游步骤 ID。
- `enabled`：是否启用该步骤。
- `request`：HTTP 请求定义。
- `pagination`：分页定义。
- `response`：响应解析与校验定义。
- `cache`：中间数据缓存定义。
- `output`：步骤输出定义，可供下游步骤引用。

## 8. HTTP 请求配置

```json
{
  "method": "GET",
  "url": "https://example.com/api/users",
  "headers": {
    "Accept": "application/json",
    "Authorization": "Bearer ${steps.fetch_token.output.accessToken}"
  },
  "queryParams": {
    "status": "active"
  },
  "bodyType": "JSON",
  "body": {
    "tenantId": "${vars.tenantId}"
  }
}
```

支持的请求方式：

- `GET`
- `POST`
- `PUT`
- `DELETE`

支持的请求体类型：

- `NONE`
- `JSON`
- `FORM`
- `RAW`

敏感信息不建议直接写入 JSON。优先使用 `passwordRef`、环境变量或后续的密钥管理引用。

`timeout`、`retry`、`failurePolicy` 默认从顶层 `runtime` 继承。如果某个 step 有特殊要求，可以在 step 的 `request` 中局部覆盖同名配置。

## 9. 分页配置

V1.0 支持 `NONE`、`PAGE`、`OFFSET`。

### 9.1 无分页

```json
{
  "type": "NONE"
}
```

### 9.2 页码分页

```json
{
  "type": "PAGE",
  "pageParam": "page",
  "pageSizeParam": "pageSize",
  "startPage": 1,
  "pageSize": 100,
  "maxPages": 1000,
  "stopWhenEmpty": true
}
```

### 9.3 Offset 分页

```json
{
  "type": "OFFSET",
  "offsetParam": "offset",
  "limitParam": "limit",
  "startOffset": 0,
  "limit": 100,
  "maxRequests": 1000,
  "stopWhenEmpty": true
}
```

后续可扩展：

- `CURSOR`：从响应中提取下一页游标。
- `NEXT_URL`：从响应中提取下一页 URL。

## 10. 响应配置

```json
{
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
      "name": "extra_json",
      "type": "JSON",
      "expression": "data.list[].extra",
      "nullable": true
    }
  ]
}
```

字段说明：

- `contentType`：V1.0 支持 `JSON`。
- `successStatus`：允许的 HTTP 状态码。
- `successExpression`：可选的业务成功表达式，使用 JMESPath 表达式。
- `messageExpression`：错误消息表达式，使用 JMESPath 表达式。
- `recordMode`：记录模式，支持 `OBJECT` 和 `ARRAY`。
- `fields`：字段映射规则，字段内的 `expression` 始终基于完整响应 JSON 执行，使用 JMESPath 表达式。

字段映射规则：

- `recordMode = OBJECT`：每个字段表达式应返回单个值，最终生成一条记录。
- `recordMode = ARRAY`：字段表达式可以返回数组或标量。返回数组的字段按下标组合成多条记录；返回标量的字段会广播到每条记录。
- `recordMode = ARRAY` 时，必须有且只能有一个字段配置 `isKey=true`。该字段表达式必须返回数组，用于决定记录条数。
- `isKey=true` 字段的每个元素不能为空；其它数组字段长度必须与 key 字段一致。
- 字段表达式返回对象或数组时，字段类型应配置为 `JSON`，用于落入 StarRocks/Paimon 的 JSON、STRING 或等价复杂类型字段。

数组响应示例，接口返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 2,
    "list": [
      {
        "id": "1",
        "name": "Alice",
        "extra": {
          "roles": ["admin", "editor"]
        }
      },
      {
        "id": "2",
        "name": "Bob",
        "extra": {
          "roles": ["viewer"]
        }
      }
    ]
  }
}
```

此时使用 `recordMode = ARRAY`：

```json
{
  "recordMode": "ARRAY",
  "fields": [
    {
      "name": "id",
      "type": "STRING",
      "expression": "data.list[].id",
      "isKey": true
    },
    {
      "name": "name",
      "type": "STRING",
      "expression": "data.list[].name"
    },
    {
      "name": "extra_json",
      "type": "JSON",
      "expression": "data.list[].extra"
    }
  ]
}
```

- `data.list[].id` 返回 `["1", "2"]`，且 `id` 配置了 `isKey=true`，因此它决定最终生成 2 条记录。
- `data.list[].extra` 返回对象数组，每个对象按下标写入对应行的 `extra_json` 字段。

如果接口直接返回数组，例如：

```json
[
  {"id": "1", "name": "Alice"},
  {"id": "2", "name": "Bob"}
]
```

配置为：

```json
{
  "recordMode": "ARRAY",
  "fields": [
    {
      "name": "id",
      "type": "STRING",
      "expression": "[].id",
      "isKey": true
    },
    {
      "name": "name",
      "type": "STRING",
      "expression": "[].name"
    }
  ]
}
```

分页停止不依赖总数字段。V1.0 按以下规则判断结束：

- 当前页返回空数组。
- 当前页返回条数小于 `pageSize` 或 `limit`。
- 达到 `maxPages` 或 `maxRequests`。

单条嵌套对象响应示例，接口返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "user": {
      "id": "1",
      "profile": {
        "name": "Alice",
        "email": "alice@example.com"
      },
      "updatedTime": "2026-05-27 10:00:00"
    }
  }
}
```

此时使用 `recordMode = OBJECT`：

```json
{
  "recordMode": "OBJECT",
  "fields": [
    {
      "name": "id",
      "type": "STRING",
      "expression": "data.user.id",
      "nullable": false
    },
    {
      "name": "name",
      "type": "STRING",
      "expression": "data.user.profile.name",
      "nullable": true
    },
    {
      "name": "email",
      "type": "STRING",
      "expression": "data.user.profile.email",
      "nullable": true
    }
  ]
}
```

- 字段表达式直接基于完整响应 JSON，因此可以直接写 `data.user.profile.name`。

## 11. Redis 中间缓存

Redis 用于多接口任务中保存上游接口结果，供后续接口引用。

当前决策：Redis 只用于多接口任务的中间数据缓存。单接口直接抽数落表的任务不强制使用 Redis，也不要求把所有运行中间状态统一写入 Redis。

```json
{
  "enabled": true,
  "key": "token:${job.id}",
  "ttlSeconds": 1800,
  "mode": "UPSERT",
  "valueExpression": "data"
}
```

字段说明：

- `enabled`：是否启用缓存。
- `key`：Redis key 模板。
- `ttlSeconds`：缓存过期时间。
- `mode`：支持 `PUT`、`UPSERT`、`APPEND_LIST`、`HASH`。
- `valueExpression`：要写入 Redis 的响应数据表达式，使用 JMESPath。

推荐 Redis key 格式：

```text
{keyPrefix}:{jobId}:{runId}:{stepId}:{customKey}
```

如果缓存的是 token 这类跨运行可复用数据，可以不包含 `runId`，但需要设置较短 TTL。

## 12. 步骤输出与变量引用

每个步骤可以声明部分输出字段，供下游步骤引用。

```json
{
  "output": {
    "accessToken": "data.access_token",
    "tenantIds": "data.tenants[*].id"
  }
}
```

引用示例：

```text
${job.id}
${run.id}
${vars.bizDate}
${steps.fetch_token.output.accessToken}
${redis.token}
```

V1.0 实现一个简单模板解析器，在每次请求发送前完成变量替换。

## 13. 落表配置

`sink` 定义最终记录写入的位置。

```json
{
  "type": "STARROCKS",
  "mode": "APPEND",
  "connection": {},
  "table": {},
  "schema": []
}
```

当前决策：sink 写入属于该插件职责，但必须通过独立 `SinkWriter` 接口解耦。API 抽数、字段转换、批次提交不应直接依赖 StarRocks 或 Paimon 具体实现，后续如果 sink 迁移，只替换 writer 实现。

支持的落表类型：

- `STARROCKS`
- `PAIMON`

支持的写入模式：

- `APPEND`
- `UPSERT`
- `OVERWRITE_PARTITION`

建表策略：

- 允许插件自动建表。
- 如果目标表不存在，根据 `sink.schema` 和 `sink.table` 自动创建。
- 如果目标表已存在，复用现有表，并在写入前做 schema 兼容性校验。
- 如果目标表已存在但缺少配置中的字段，或现有表字段类型、主键、分区与配置冲突，任务直接失败；V1.0 不自动变更已有表结构。

## 14. StarRocks 落表配置

```json
{
  "type": "STARROCKS",
  "mode": "APPEND",
  "connection": {
    "jdbcUrl": "jdbc:mysql://starrocks-fe:9030/dwd",
    "loadUrl": "http://starrocks-fe:8030",
    "username": "root",
    "passwordRef": "STARROCKS_PASSWORD",
    "database": "dwd"
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
      "nullable": false,
      "comment": "User id"
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
      "nullable": false,
      "defaultValue": "${vars.bizDate}"
    }
  ],
  "write": {
    "format": "JSON",
    "batchSize": 1000,
    "flushIntervalMs": 5000
  }
}
```

V1.0 建议：

- 使用 Stream Load 做批量写入。
- JDBC 只用于元数据检查和可选建表。
- `UPSERT` 写入模式要求目标表为 StarRocks 主键模型表，并且 `sink.table.primaryKeys` 必须配置。

## 15. Paimon 落表配置

```json
{
  "type": "PAIMON",
  "mode": "APPEND",
  "connection": {
    "warehouse": "s3://ceph-bucket/warehouse/paimon",
    "catalogType": "filesystem",
    "database": "dwd",
    "options": {
      "s3.endpoint": "http://ceph-rgw:7480",
      "s3.access-key": "${secrets.CEPH_S3_ACCESS_KEY}",
      "s3.secret-key": "${secrets.CEPH_S3_SECRET_KEY}",
      "s3.path.style.access": "true"
    }
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
      "nullable": false,
      "defaultValue": "${vars.bizDate}"
    }
  ],
  "write": {
    "commitUser": "datafusion-api-plugin",
    "batchSize": 1000
  }
}
```

当前决策：Paimon writer 直接使用 Paimon API 写入，底层存储使用 Ceph S3。Paimon writer 仍然通过独立 `SinkWriter` 接口隔离，避免 API 抽数主流程直接依赖 Paimon 的运行时细节。V1.0 暂不支持 Paimon branch、tag、snapshot 管理。

## 16. 完整示例

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
    "loopIntervalMs": 0,
    "timeout": {
      "connectMs": 5000,
      "readMs": 30000,
      "writeMs": 30000
    },
    "retry": {
      "maxAttempts": 3,
      "intervalMs": 1000,
      "backoffMultiplier": 2.0,
      "retryOnStatus": [429, 500, 502, 503, 504]
    },
    "failurePolicy": {
      "onHttpError": "FAIL",
      "onEmptyData": "SUCCESS",
      "onParseError": "FAIL"
    }
  },
  "redis": {
    "enabled": true,
    "host": "172.26.185.208",
    "port": 6379,
    "database": 0,
    "passwordRef": "REDIS_PASSWORD",
    "keyPrefix": "datafusion:plugin:api",
    "ttlSeconds": 3600
  },
  "vars": {
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
      "cache": {
        "enabled": true,
        "key": "token:${job.id}",
        "ttlSeconds": 1800,
        "mode": "UPSERT",
        "valueExpression": "data"
      },
      "output": {
        "accessToken": "data.access_token"
      }
    },
    {
      "id": "fetch_users",
      "type": "HTTP",
      "dependsOn": ["fetch_token"],
      "enabled": true,
      "request": {
        "method": "GET",
        "url": "https://example.com/api/users",
        "headers": {
          "Accept": "application/json",
          "Authorization": "Bearer ${steps.fetch_token.output.accessToken}"
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
            "value": "${vars.bizDate}",
            "nullable": false
          }
        ]
      }
    }
  ],
  "sink": {
    "type": "STARROCKS",
    "mode": "APPEND",
    "connection": {
      "jdbcUrl": "jdbc:mysql://starrocks-fe:9030/dwd",
      "loadUrl": "http://starrocks-fe:8030",
      "username": "root",
      "passwordRef": "STARROCKS_PASSWORD",
      "database": "dwd"
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
      "flushIntervalMs": 5000
    }
  }
}
```

## 17. 执行流程

1. 解析 JSON 配置。
2. 校验 `job`、`trigger`、`steps`、`redis`、`sink` 配置。
3. 解析运行时变量。
4. 如果 `trigger.mode` 为 `CRON`，启动本地 cron，每次触发创建一次 run。
5. 如果 `trigger.mode` 为 `ONCE` 或 `SCHEDULER`，立即创建一次 run。
6. 根据 `steps[].dependsOn` 构建步骤 DAG。
7. 按依赖顺序执行步骤。
8. 对每个 HTTP 步骤执行：
   - 解析请求模板。
   - 应用分页参数。
   - 按超时和重试策略发送 HTTP 请求。
   - 校验 HTTP 状态码和业务成功表达式。
   - 使用 JMESPath 解析响应。
   - 将配置的中间结果写入 Redis。
   - 按字段映射转换最终记录。
9. 按批次写入 sink。
10. 返回运行结果，包括记录数、状态、错误信息和耗时。

## 18. 核心接口建议

```java
public interface ApiExtractRunner {
    ApiExtractResult run(ApiExtractJobConfig config);
}
```

```java
public interface StepExecutor {
    StepResult execute(ApiExtractContext context, StepConfig step);
}
```

```java
public interface SinkWriter extends AutoCloseable {
    void open(SinkConfig sink);

    void write(List<Record> records);

    void flush();
}
```

```java
public interface IntermediateCache {
    void put(String key, Object value, long ttlSeconds);

    Object get(String key);
}
```

## 19. 配置校验规则

执行任何 API 请求前应先完成配置校验：

- `job.id` 不能为空。
- `steps` 不能为空。
- step id 必须唯一。
- `dependsOn` 不能出现循环依赖。
- HTTP step 必须定义 `request.method` 和 `request.url`。
- `response.recordMode` 必须为 `OBJECT` 或 `ARRAY`。
- `response.recordMode` 为 `OBJECT` 时，每个字段表达式应返回单值；如果返回对象或数组，字段类型必须支持 JSON 或字符串序列化。
- `response.recordMode` 为 `ARRAY` 时，必须有且只能有一个字段配置 `isKey=true`。
- `response.recordMode` 为 `ARRAY` 时，`isKey=true` 字段表达式必须返回数组，并决定记录条数。
- `response.recordMode` 为 `ARRAY` 时，`isKey=true` 字段的每个元素不能为空。
- `response.recordMode` 为 `ARRAY` 时，多个数组字段长度必须与 key 字段一致；标量字段会广播到每一条记录。
- sink schema 字段名必须唯一。
- sink schema 应覆盖最终响应映射出的字段，除非字段被显式忽略。
- 如果任一 step 启用了 cache，则必须配置 Redis。

## 20. V1.0 实现范围

- 定义 JSON 配置类。
- 实现配置校验。
- 实现支持 `ONCE` 和 `CRON` 模式的主函数。
- 实现 HTTP `GET`、`POST`、`PUT`、`DELETE`。
- 实现 `NONE`、`PAGE`、`OFFSET` 分页。
- 实现 JMESPath 响应解析和字段映射。
- 实现 Redis 中间缓存抽象。
- 实现 StarRocks Stream Load writer。
- 实现 Paimon API writer。
- 通过 `SinkWriter` 接口隔离 StarRocks 和 Paimon 写入实现。

## 21. 已确认设计决策

- 插件允许自动建表：目标表不存在时自动创建，存在时复用并做 schema 兼容性校验。
- 目标表已存在但缺少配置字段，或字段类型、主键、分区不一致时，任务直接失败，不自动 `ALTER TABLE`。
- Redis 只用于多接口任务的中间数据缓存，不作为所有任务的统一运行状态存储。
- Redis 中间缓存 V1.0 不做加密或脱敏。
- sink 写入当前属于插件职责，但必须通过 `SinkWriter` 接口解耦，方便后续迁移为独立 sink。
- StarRocks `UPSERT` 模式要求目标表为主键模型表，并要求配置 `primaryKeys`。
- Paimon writer 直接使用 Paimon API，底层数据存储在 Ceph S3。
- Paimon V1.0 不支持 branch、tag、snapshot 管理。
- 响应提取表达式使用 JMESPath，不使用 JSONPath。

## 22. 后续演进方向

- 接入调度 worker，通过 `pluginType=HTTP_API` 分发执行。
- 增加运行历史和指标。
- 增加页面配置生成能力。
- 增加增量游标持久化。
- 增加 Redis 中间缓存加密或脱敏。
- 增加 Paimon branch、tag、snapshot 管理。
- 增加已存在表的自动 schema 演进能力。
