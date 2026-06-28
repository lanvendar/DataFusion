# scheduler-task 设计文档

> 数据结构见 [scheduler-task-data-define.md](./scheduler-task-data-define.md)。本文只保留任务定义域的行为边界和接口规则。

## 定位

`scheduler-task` 管理任务定义，不负责任务实例执行、worker 提交或实例状态维护。运行期执行由 `datafusion-scheduler-master` 通过 manager 侧 `TaskStorageImpl` 读取任务定义后推进。

核心链路：

```text
TaskController -> TaskInfoService -> TaskInfoServiceImpl -> TaskInfoMapper -> scheduler_task_info
```

## 接口

API 前缀：`/api/scheduler/task`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询任务定义 |
| `POST` | `/list` | 查询任务定义列表 |
| `POST` | `/add` | 新增任务定义 |
| `POST` | `/update` | 修改任务定义 |
| `GET` | `/detail/{id}` | 查询任务详情 |
| `POST` | `/delete/{id}` | 删除任务定义 |

## 字段边界

`scheduler_task_info` 同时包含任务定义属性和流程编排属性，但任务定义页面只维护任务自身属性。

| 分类 | 字段 | 规则 |
|------|------|------|
| 任务定义 | `taskName`、`taskCode`、`description`、`taskTypeId`、`taskType`、`taskParam`、`definition` | 新增、修改、详情页可维护 |
| 调度编排 | `isBound`、`flowId`、`pluginId`、`view`、`depEventIds`、`eventId`、`enabled` | 流程编排或调度节点配置维护 |
| 系统属性 | `id`、`syncFlag`、`sourceRoute`、审计字段 | 后端生成或维护 |

`pluginId` 由后端按 `taskType` 解析默认执行插件并写入，用于满足表结构非空约束；任务定义页面不展示、不提交该字段。

## 业务规则

- 新增任务要求 `taskName/taskCode/taskTypeId/taskType` 必填，且 `taskCode` 唯一。
- 任务 ID 使用 `UUID.nameUUIDFromBytes(taskCode)` 生成，因此新增前必须先校验编码唯一。
- 新增默认写入 `isBound=false`、`flowId=null`、`enabled=false`、`syncFlag=false`。
- 修改任务先查询旧实体，再合并非空字段；任务定义页面不应提交调度编排字段。
- 修改后置 `syncFlag=false`，表示定义已变更。
- 删除任务前必须确认任务未绑定流程；已绑定时拒绝删除。
- `clearEventId=true` 仅用于流程编排中的事件关闭场景，不属于普通任务定义编辑入口。

## 集成点

- `TaskStorageImpl` 将任务定义转换为 master 的 `TaskInfo`。
- `FlowInfoServiceImpl` 查询流程 DAG 时读取绑定任务。
- `TaskLinkService` 负责流程 DAG 依赖连线，不由任务定义域维护。
- 当前用户通过 `HttpUtils.getCurrentUserName()` 写入审计字段。

## 非目标

- 不在 `TaskController` 中处理 worker 提交、停止、重启或实例状态。
- 不在任务定义页编辑流程绑定、画布坐标、依赖事件、生成事件或节点启停。
- 不调整 `scheduler_task_info` 表结构。
