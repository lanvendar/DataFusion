# task-definition-register 数据定义

## 1. 数据边界

`scheduler_task_info` 将业务定位与执行定义分开保存：

| 数据 | 存储位置 | 说明 |
|------|----------|------|
| 业务来源定位 | `source_route` | Manager 内部协议串，不进入 Agent |
| 插件执行定义 | `definition` | 只包含插件可执行参数，禁止顶层 `bizRef`、`sourceRoute` |
| 同步状态 | `sync_flag` | `register=true`，`mark-unsynced=false` |

`source_route` 的持久化格式固定为：

```text
bizref:v1:bizSystem=<url-encoded>:bizKey=<url-encoded>:bizVersion=<url-encoded>[:bizUrl=<url-encoded>]
```

协议段顺序固定，值使用 UTF-8 URL 编码。业务任务身份只由 `bizSystem + bizKey` 决定，
`bizVersion` 和 `bizUrl` 可以随业务定义更新。

## 2. BusinessSourceRoute

API 使用结构化对象，不接收业务方拼接的协议串：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `bizSystem` | `String` | 是 | 来源业务系统 |
| `bizKey` | `String` | 是 | 来源业务主键 |
| `bizVersion` | `String` | 是 | 业务定义版本或更新时间 |
| `bizUrl` | `String` | 否 | 业务页面地址 |

`v1` 是 Manager 内部固定的 `sourceRoute` 协议版本，不需要业务方传入。

## 3. register

### 3.1 请求

```json
{
  "taskName": "SQL 采集任务",
  "taskCode": "optional-task-code",
  "taskTypeId": "SPARK_SQL",
  "taskType": "SPARK",
  "sourceRoute": {
    "bizSystem": "SQL_DEVELOPMENT",
    "bizKey": "job-001",
    "bizVersion": "20260713153000",
    "bizUrl": "/sql/job/job-001"
  },
  "definition": {
    "job": {
      "id": "job-001",
      "version": "20260713153000"
    },
    "sql": "select 1"
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `taskName` | 是 | 任务名称 |
| `taskCode` | 否 | 为空时按业务任务身份生成稳定编码 |
| `taskTypeId` | 是 | 任务类型 ID |
| `taskType` | 是 | 任务类型，用于解析默认插件 |
| `taskParam` | 否 | 任务变量参数 |
| `sourceRoute` | 是 | 结构化业务定位对象 |
| `definition` | 是 | 纯插件执行定义 |

### 3.2 响应

`TaskDefinitionRegisterResultDto` 返回 `taskId`、`taskCode`、`created`、`syncFlag`、
`isBound` 和 `flowId`。

## 4. mark-unsynced

请求体直接复用 `BusinessSourceRoute`：

```json
{
  "bizSystem": "SQL_DEVELOPMENT",
  "bizKey": "job-001",
  "bizVersion": "20260713154000",
  "bizUrl": "/sql/job/job-001"
}
```

接口按 `bizSystem + bizKey` 定位任务，更新 `source_route` 并将 `sync_flag` 置为 `false`，
不修改 `definition` 和调度编排属性。

## 5. 数据库约束

`source_route` 保持 `text` 类型，暂不增加业务身份唯一索引。Manager 按 `bizSystem + bizKey`
查询并完成登记幂等。

历史数据必须在上线前完成迁移：将旧 `definition.bizRef` 转换为新 `source_route`，移除
`definition` 顶层的 `bizRef/sourceRoute`，并处理重复业务身份。代码不承担旧协议兼容。

## 6. 层间转换

| 方向 | 规则 |
|------|------|
| `BusinessSourceRoute` -> `source_route` | Manager 按固定协议编码 |
| `source_route` -> `BusinessSourceRoute` | Manager 查询 DTO 解码 |
| `TaskDefinitionRegisterDto` -> `TaskInfoEntity` | 按业务身份新增或更新，成功后 `sync_flag=true` |
| `BusinessSourceRoute` -> `TaskInfoEntity` | `mark-unsynced` 更新定位信息并置 `sync_flag=false` |
| `definition` -> `TaskRequest.taskData` | 原样进入执行链路，不包含业务定位字段 |
