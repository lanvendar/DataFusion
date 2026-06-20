# task-definition-register 数据结构定义

> 本文档定义 `TaskDefinitionRegister` 统一业务接入接口的数据结构、`definition` 主结构和 `sync_flag` 语义。它补充 [scheduler-task-data-define.md](./scheduler-task-data-define.md)，不替代现有调度任务定义数据结构。

## 1. 表结构

### 1.1 作用范围

- 目标表: `scheduler_task_info`
- 操作: 轻量改造
- 说明: 不再为业务接入新增 `source_system/source_biz_type/source_biz_id` 顶层列，统一使用 `definition.bizRef` 作为业务定位符，继续复用 `source_route` 和 `sync_flag`。

### 1.2 DDL 变更

推荐只增加 `definition.bizRef` 的唯一索引，不新增额外业务字段列：

```sql
CREATE UNIQUE INDEX uk_scheduler_task_info_definition_biz_ref
    ON scheduler_task_info ((definition ->> 'bizRef'))
    WHERE definition IS NOT NULL
      AND (definition ->> 'bizRef') IS NOT NULL;
```

说明:

- `bizRef` 存在于 `definition` 顶层，作为业务接入唯一键。
- 历史手工创建的调度任务允许没有 `bizRef`。
- `source_route` 继续记录业务页面跳转定位信息。
- `sync_flag` 继续记录调度快照与业务源是否一致。

### 1.3 字段分层

| 分类 | 字段 | 是否由 `TaskDefinitionRegister` 设置 | 说明 |
|------|------|--------------------------------------|------|
| 业务定位属性 | `definition.bizRef` | 是 | 统一业务接入唯一标识 |
| 任务定义属性 | `task_name`、`task_code`、`description`、`task_type_id`、`task_type`、`task_param`、`definition`、`source_route` | 是 | 业务模块提交的调度任务定义主数据 |
| 调度编排属性 | `is_bound`、`flow_id`、`plugin_id`、`view`、`dep_event_ids`、`event_id`、`enabled` | 否 | 由流程编排和发布启停流程维护 |
| 系统属性 | `id`、`sync_flag`、`creator`、`updater`、`create_time`、`update_time` | 后端维护 | 系统生成或维护 |

### 1.4 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `task_name` | `taskName` | `String` | `register` 是 | 无 | 任务名称 |
| `task_code` | `taskCode` | `String` | 否 | 后端可按 `bizRef` 生成 | 调度任务编码 |
| `description` | `description` | `String` | 否 | 无 | 任务描述 |
| `task_type_id` | `taskTypeId` | `String` | `register` 是 | 无 | 任务类型 ID |
| `task_type` | `taskType` | `String` | `register` 是 | 无 | 任务类型，用于解析默认执行插件 |
| `task_param` | `taskParam` | `JsonNode` | 否 | 无 | 任务变量参数 JSON |
| `definition` | `definition` | `JsonNode` | `register` 是 | 无 | 业务提交的任务定义 JSON，顶层必须包含 `bizRef` |
| `source_route` | `sourceRoute` | `String` | 否 | 无 | 原业务页面跳转定位信息 |
| `plugin_id` | `pluginId` | `UUID` | 后端维护 | 按 `taskType` 自动解析 | 执行组件 ID，不由业务方传入 |
| `sync_flag` | `syncFlag` | `Boolean` | 后端维护 | `false` | 定义快照与业务源是否一致，`register=true`，`markUnsynced=false` |

## 2. `definition` 主结构

### 2.1 顶层结构

业务提交的 `definition` 主结构如下：

```json
{
  "bizRef": "bizref:v1:system=INGESTION:bizType=TASK_DEFINITION:bizKey=8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a:versionId=v7",
  "data": {
    "taskId": "8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a",
    "projectId": "p001",
    "jobId": "j002",
    "versionId": "v7"
  },
  "options": {
    "submitter": "INGESTION"
  }
}
```

说明:

- `bizRef` 是必填字符串，作为调度侧唯一定位符。
- `data` 是必填对象，保存业务侧完整定义快照。
- `options` 是可选对象，保存不参与唯一性判断的扩展信息。

### 2.2 顶层字段约束

| key | 类型 | 必填 | 说明 |
|-----|------|------|------|
| `bizRef` | `String` | 是 | 调度侧唯一定位符 |
| `data` | `Object` | 是 | 业务定义快照 |
| `options` | `Object` | 否 | 补充扩展信息 |

### 2.3 `bizRef` 结构

`bizRef` 使用内部协议字符串，而不是标准 URI：

```text
bizref:v1:key=value:key=value
```

固定规则：

- 前缀固定为 `bizref:v1`
- 段分隔符固定为 `:`
- 每段使用 `key=value`
- 若 `value` 中包含 `:` 或 `=`，必须先做 URL 编码

核心键约束：

| key | 必填 | 说明 |
|-----|------|------|
| `system` | 是 | 来源系统，如 `INGESTION` |
| `bizType` | 是 | 业务类型，如 `TASK_DEFINITION` |
| `bizKey` | 是 | 业务唯一键 |

可选扩展键：

- `versionId`
- `projectId`
- `jobId`
- 其他稳定定位字段

唯一性判断只认：

- `system`
- `bizType`
- `bizKey`

其余键仅用于补充定位、排查和展示，不参与唯一性判断。

### 2.4 `bizRef` 与 `data` 的关系

| 规则 | 说明 |
|------|------|
| `bizRef` 来源 | 由 `data` 中稳定标识字段拼装而成 |
| 允许入参 | 允许业务方直接传 `bizRef`，后端只做格式校验 |
| 禁止内容 | 不应把 `taskName`、`description` 等易变字段放入 `bizRef` |
| 推荐做法 | 只把稳定身份字段放入 `bizRef`，完整业务快照放入 `data` |

## 3. DTO 定义

### 3.1 `register` 请求 DTO

| DTO | 类型 | 字段 | 字段类型 | 校验 | 说明 |
|-----|------|------|----------|------|------|
| `TaskDefinitionRegisterDto` | `Request` | `taskName` | `String` | `@NotBlank` | 任务名称 |
| `TaskDefinitionRegisterDto` | `Request` | `taskCode` | `String` | 可选 | 业务方自定义调度编码；为空时后端生成 |
| `TaskDefinitionRegisterDto` | `Request` | `description` | `String` | 可选 | 任务描述 |
| `TaskDefinitionRegisterDto` | `Request` | `taskTypeId` | `String` | `@NotBlank` | 任务类型 ID |
| `TaskDefinitionRegisterDto` | `Request` | `taskType` | `String` | `@NotBlank` | 任务类型 |
| `TaskDefinitionRegisterDto` | `Request` | `taskParam` | `JsonNode` | 可选 | 任务变量参数，建议传 JSON 对象 |
| `TaskDefinitionRegisterDto` | `Request` | `definition` | `JsonNode` | `@NotNull` | 顶层必须带 `bizRef/data` |
| `TaskDefinitionRegisterDto` | `Request` | `sourceRoute` | `String` | 可选 | 原业务页面路由或详情 URL |

### 3.2 `register` 响应 DTO

| DTO | 类型 | 字段 | 字段类型 | 说明 |
|-----|------|------|----------|------|
| `TaskDefinitionRegisterResultDto` | `Response` | `taskId` | `UUID` | 调度任务 ID |
| `TaskDefinitionRegisterResultDto` | `Response` | `taskCode` | `String` | 调度任务编码 |
| `TaskDefinitionRegisterResultDto` | `Response` | `created` | `Boolean` | 是否新建 |
| `TaskDefinitionRegisterResultDto` | `Response` | `syncFlag` | `Boolean` | 最新同步标识，固定为 `true` |
| `TaskDefinitionRegisterResultDto` | `Response` | `isBound` | `Boolean` | 当前是否已绑定流程，仅用于界面展示 |
| `TaskDefinitionRegisterResultDto` | `Response` | `flowId` | `UUID` | 已绑定流程 ID，未绑定为空，仅用于界面展示 |

### 3.3 `markUnsynced` 请求 DTO

| DTO | 类型 | 字段 | 字段类型 | 校验 | 说明 |
|-----|------|------|----------|------|------|
| `TaskDefinitionMarkUnsyncedDto` | `Request` | `bizRef` | `String` | `@NotBlank` | 业务定位串 |
| `TaskDefinitionMarkUnsyncedDto` | `Request` | `sourceRoute` | `String` | 可选 | 业务页面最新跳转定位信息 |
| `TaskDefinitionMarkUnsyncedDto` | `Request` | `reason` | `String` | 可选 | 标记原因，如 `BUSINESS_DATA_CHANGED` |

### 3.4 `markUnsynced` 响应 DTO

| DTO | 类型 | 字段 | 字段类型 | 说明 |
|-----|------|------|----------|------|
| `TaskDefinitionMarkUnsyncedResultDto` | `Response` | `taskId` | `UUID` | 调度任务 ID |
| `TaskDefinitionMarkUnsyncedResultDto` | `Response` | `syncFlag` | `Boolean` | 最新同步标识，固定为 `false` |

## 4. 接口示例

### 4.1 `register` 请求示例

```json
{
  "taskName": "Ingestion Task Definition",
  "taskCode": "optional-task-code",
  "description": "数据集成任务定义同步到调度",
  "taskTypeId": "INGESTION_TASK",
  "taskType": "INGESTION",
  "definition": {
    "bizRef": "bizref:v1:system=INGESTION:bizType=TASK_DEFINITION:bizKey=8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a:versionId=v7",
    "data": {
      "taskId": "8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a",
      "projectId": "p001",
      "jobId": "j002",
      "versionId": "v7"
    }
  },
  "taskParam": {
    "submitter": "INGESTION"
  },
  "sourceRoute": "/ingestion/task-definition/detail/8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a"
}
```

### 4.2 `register` 响应示例

```json
{
  "code": "000000",
  "msg": "success",
  "data": {
    "taskId": "5f5e1d7c-8e4d-4e9f-9910-3cb9c1b8a2c5",
    "taskCode": "INGESTION_TASK_5F5E1D7C",
    "created": true,
    "syncFlag": true,
    "isBound": false,
    "flowId": null
  }
}
```

### 4.3 `markUnsynced` 请求示例

```json
{
  "bizRef": "bizref:v1:system=INGESTION:bizType=TASK_DEFINITION:bizKey=8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a:versionId=v7",
  "sourceRoute": "/ingestion/task-definition/detail/8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a",
  "reason": "BUSINESS_DATA_CHANGED"
}
```

### 4.4 `markUnsynced` 响应示例

```json
{
  "code": "000000",
  "msg": "success",
  "data": {
    "taskId": "5f5e1d7c-8e4d-4e9f-9910-3cb9c1b8a2c5",
    "syncFlag": false
  }
}
```

## 5. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/task-definition/register` | `TaskDefinitionRegisterDto` | `TaskDefinitionRegisterResultDto` | `Result<T>` | 新增或更新调度任务定义 |
| `POST /api/scheduler/task-definition/mark-unsynced` | `TaskDefinitionMarkUnsyncedDto` | `TaskDefinitionMarkUnsyncedResultDto` | `Result<T>` | 标记定义与业务源不一致 |

说明:

- `register` 使用 `definition.bizRef` 做幂等查找，不区分 `add/update` 两套接口。
- `markUnsynced` 不提交最新 `definition`，只负责把 `sync_flag` 置为 `false`。
- 业务模块不得直接拼装 `TaskInfoSaveDto` 或 `TaskInfoUpdateDto` 访问调度定义域。

## 6. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `TaskDefinitionRegisterDto` -> existing `TaskInfoEntity` | 根据 `definition.bizRef` 查询并更新 | 保留调度编排字段，尤其不更新 `flowId`；成功后置 `syncFlag=true` |
| `TaskDefinitionRegisterDto` -> new `TaskInfoEntity` | 根据 `definition.bizRef` 新建任务定义 | `pluginId` 按 `taskType` 解析默认值；默认 `isBound/enabled=false`；成功后置 `syncFlag=true` |
| `TaskDefinitionMarkUnsyncedDto` -> existing `TaskInfoEntity` | 根据 `bizRef` 定位后只更新 `syncFlag/sourceRoute` | 不更新 `definition`、`taskParam` 和调度编排字段；成功后置 `syncFlag=false` |
| `TaskInfoEntity` -> `TaskDefinitionRegisterResultDto` | 复制关键结果字段 | `created` 由 Service 判定 |
| `TaskInfoEntity` -> `TaskDefinitionMarkUnsyncedResultDto` | 返回任务 ID 与同步状态 | `syncFlag=false` |

## 7. 业务规则

| 规则 | 说明 |
|------|------|
| 幂等键 | 统一使用 `definition.bizRef` 作为业务任务接入唯一键 |
| `taskCode` 生成 | 当请求未显式传 `taskCode` 时，后端按 `bizRef` 生成稳定且可重复的编码 |
| 默认插件解析 | 创建任务时根据 `taskType` 调用 `TaskTypeConfigService#getDefaultPluginIdByTaskType` 解析默认插件 |
| `syncFlag` 置位 | `register` 成功后写 `true`；`markUnsynced` 成功后写 `false` |
| 定义变更保护 | 流程已发布时，拒绝 `register` 更新定义；流程未发布时允许 `register`；允许 `markUnsynced` 标脏 |
| 调度字段隔离 | 统一业务接口不能修改 `pluginId`、`flowId`、`eventId`、`depEventIds`、`view`、`enabled` |

## 8. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `TaskInfoEntity` | `datafusion-manager/scheduler/po` | 复用现有主实体 | 不增加来源顶层列 |
| `TaskTypeConfigService` | `datafusion-manager/system/service` | 创建任务时解析默认插件 | 隔离业务方对 `pluginId` 的感知 |
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `CommonException` / `ErrorCodeEnum` | `datafusion-common` | 统一业务异常 | 接口校验和状态拦截 |
