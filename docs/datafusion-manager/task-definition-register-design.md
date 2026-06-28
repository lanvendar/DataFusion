# task-definition-register 设计文档

> 数据结构见 [task-definition-register-data-define.md](./task-definition-register-data-define.md)。本文定义 `TaskDefinitionRegister` 的接口职责、`sync_flag` 时序和 `definition.bizRef` 的使用边界。

## 1. 能力边界

`TaskDefinitionRegister` 为业务模块提供统一任务定义接入能力。业务方通过 `register` 提交最新任务定义快照，通过 `markUnsynced` 显式通知业务源已变化但尚未重新登记。

本能力只维护 `scheduler_task_info` 中的定义快照和同步状态，不直接参与流程编排、实例执行或 worker 提交。

## 2. 接口与链路

API 前缀：`/api/scheduler/task-definition`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/register` | 按 `definition.bizRef` 新增或更新任务定义，成功后 `sync_flag=true` |
| `POST` | `/mark-unsynced` | 按 `bizRef` 标记任务定义未同步，成功后 `sync_flag=false` |

核心链路：

```text
TaskDefinitionRegisterController
    -> TaskDefinitionRegisterService
    -> TaskDefinitionRegisterServiceImpl
    -> TaskInfoMapper.getTaskInfoByBizRef
    -> scheduler_task_info
```

## 3. 行为概览

业务侧始终提交或标记业务定义，不需要判断调度任务当前是新增还是更新：

```text
业务模块
   |
   +--> TaskDefinitionRegister.register
   |         |
   |         +--> 按 definition.bizRef 查找
   |         +--> 不存在则新增
   |         +--> 存在则更新
   |         +--> 成功后 sync_flag=true
   |         +--> 返回 taskId
   |
   +--> TaskDefinitionRegister.markUnsynced
             |
             +--> 按 bizRef 查找
             +--> 只更新 sync_flag=false
             +--> 返回 taskId
```

核心规则：

- 业务方永远提交最新 `definition` 快照，而不是自己决定调用 `add` 还是 `update`。
- `sync_flag` 的变化由接口显式驱动，不依赖调度侧猜测。
- 调度侧只依赖 `definition.bizRef` 定位任务，不要求拆业务主键字段入表。

## 4. `definition` 设计原则

### 4.1 主结构

`definition` 顶层至少保留一个稳定定位概念：

- `bizRef`

并允许两个通用扩展概念：

- `data`
- `options`

其中：

- `bizRef` 负责唯一定位
- `data` 负责保存完整业务定义快照，可选
- `options` 负责可选扩展
- 其他字段由具体插件解释为运行定义，例如 DataX 的 `job.content`

### 4.2 为什么运行定义可以有插件字段

调度侧只理解 `bizRef`，不理解复杂业务结构。`definition` 中除 `bizRef/data/options` 外的字段应视为插件运行定义，由执行插件自行解释。这样既保留统一登记能力，也避免把 DataX、Shell 等插件定义强行套进同一业务快照结构。

### 4.3 `bizRef` 的边界

`bizRef` 不是完整业务数据，只是定位串。

应该放：

- 稳定身份字段
- 稳定版本或分区字段

不应该放：

- 任务名称
- 描述
- 可编辑展示文案
- 容易变化但不影响身份的业务属性

## 5. `sync_flag` 语义与触发

### 5.1 字段语义

| 值 | 含义 |
|----|------|
| `true` | `scheduler_task_info.definition` 已与业务源一致 |
| `false` | 业务源已变更，但最新 `definition` 尚未重新登记到调度 |

### 5.2 接口触发规则

| 接口 | 行为 | `sync_flag` 结果 |
|------|------|------------------|
| `register` | 提交最新 `definition` 快照 | `true` |
| `markUnsynced` | 通知业务源已变化但未重新提交定义 | `false` |

### 5.3 触发时机

主链路：

- 业务主服务在事务提交成功后，主动调用 `markUnsynced`
- 当业务侧生成最新调度定义并准备提交时，再调用 `register`

补偿链路：

- 可使用 outbox / MQ / 领域事件异步触发 `markUnsynced`
- 定时 job 只做兜底对账，不作为主链路

不作为主链路：

- 让调度后台定时全表扫描业务表作为主机制

原因是：

- 成本高
- 延迟大
- 难以判断复杂聚合业务何时真正发生影响定义的变化

## 6. `register` 行为

### 6.1 幂等规则

`register` 通过 `definition.bizRef` 判断新增还是更新：

1. 查不到 `bizRef` 对应任务时，执行新增
2. 查到对应任务时，执行更新
3. 成功后统一返回 `taskId`

业务侧不需要感知新增还是更新。

### 6.2 更新边界

`register` 允许更新：

- `taskName`
- `taskCode`
- `description`
- `taskTypeId`
- `taskType`
- `taskParam`
- `definition`
- `sourceRoute`

典型请求主结构：

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

`register` 不允许更新：

- `pluginId`
- `flowId`
- `eventId`
- `depEventIds`
- `view`
- `enabled`

### 6.3 状态保护

| 场景 | 处理规则 |
|------|----------|
| 未发布 | 允许 `register`，成功后 `sync_flag=true` |
| 已发布 | 拒绝 `register` 更新定义 |

说明:

- 当前状态机不考虑“已启用但未发布”的独立场景。
- `register` 不更新 `flowId`，因此任务是否绑定流程不作为定义更新的单独拦截条件。

## 7. `markUnsynced` 行为

`markUnsynced` 只做三件事：

1. 根据 `bizRef` 查找调度任务
2. 更新 `sync_flag=false`
3. 可选更新 `source_route`

典型请求主结构：

```json
{
  "bizRef": "bizref:v1:system=INGESTION:bizType=TASK_DEFINITION:bizKey=8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a:versionId=v7",
  "sourceRoute": "/ingestion/task-definition/detail/8f3c2c6a-7d2e-4c2b-a8c1-1b2f3d4e5f6a",
  "reason": "BUSINESS_DATA_CHANGED"
}
```

它不做：

- `definition` 更新
- `taskParam` 更新
- 调度编排属性更新

因此它适合作为“业务数据已变脏”的轻量通知接口。

## 8. 与现有执行链路的关系

本方案不改运行期 master/worker 提交协议：

- `TaskStorageImpl` 仍从 `scheduler_task_info.definition` 读取任务定义
- `HttpMasterTaskOperator` 仍负责向 worker 发起 `/internal/scheduler/submitTask`
- worker 仍消费转换后的 `TaskRequest`

`TaskDefinitionRegister` 只负责维护定义快照和同步状态，不直接参与实例执行。

## 9. 文件和对象

当前实现包含以下对象：

| 对象 | 路径 | 职责 |
|------|------|------|
| `TaskDefinitionRegisterController` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller` | 暴露 `register` 和 `markUnsynced` |
| `TaskDefinitionRegisterService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service` | 定义统一登记服务接口 |
| `TaskDefinitionRegisterServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl` | 提取 `bizRef`、幂等查找、状态保护和 `sync_flag` 更新 |
| `TaskDefinitionRegisterDto` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto` | `register` 请求 |
| `TaskDefinitionRegisterResultDto` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto` | `register` 响应 |
| `TaskDefinitionMarkUnsyncedDto` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto` | `markUnsynced` 请求 |
| `TaskDefinitionMarkUnsyncedResultDto` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto` | `markUnsynced` 响应 |
| `TaskInfoMapper.getTaskInfoByBizRef` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao` | 按 `bizRef` 核心字段查找任务定义 |

## 10. 非目标

- 不调整 worker 执行协议。
- 不开放业务方直接维护流程绑定或事件编排。
- 不要求调度侧理解业务多表主键结构。
- 不要求业务方必须持久化 `taskId` 后再进行同步状态通知。
