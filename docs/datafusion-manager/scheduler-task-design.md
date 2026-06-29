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
| `POST` | `/copy` | 复制单条任务定义 |
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

- 新增任务要求 `taskName/taskCode/taskTypeId/taskType` 必填，`taskName/taskCode` 不超过 235 个字符，且 `taskCode` 唯一。
- 任务 ID 使用 `UUID.nameUUIDFromBytes(taskCode)` 生成，因此新增前必须先校验编码唯一。
- 新增默认写入 `isBound=false`、`flowId=null`、`enabled=true`、`syncFlag=false`。
- 复制任务时前端只提交原任务 ID；后端生成副本名称、编码和主键。
- 复制任务只复制任务定义属性和系统属性，不复制调度编排属性。
- 副本 `taskName/taskCode` 使用同一个毫秒级 15 位时间后缀生成，后缀格式为 `yyMMddHHmmssSSS`；原值已以 `_` + 15 位数字结尾时替换该后缀，否则追加新后缀。
- 原任务 `taskName/taskCode` 去掉已有复制后缀后的基础值超过 235 个字符时拒绝复制，避免生成值超过表字段长度。
- 副本 `id` 基于新 `taskCode` 重新生成；`syncFlag` 照搬原任务；审计字段使用当前用户和当前时间。
- 副本 `sourceRoute` 存储为 JSON 字符串，格式为 `{"sourceRoute":"<sourceRoute>","copy_task_id":"<sourceId>","copy_task_name":"<sourceTaskName>"}`。
- 副本固定为未绑定状态：`isBound=false`、`flowId=null`、`view=null`、`depEventIds=null`、`eventId=null`、`enabled=true`；`pluginId` 按 `taskType` 解析默认执行插件。
- 已绑定流程的任务允许复制，但副本保持未绑定，后续由流程编排页重新拖入流程。
- 修改任务先查询旧实体，再合并非空字段；`taskName/taskCode` 不超过 235 个字符；任务定义页面不应提交调度编排字段。
- 修改后置 `syncFlag=false`，表示定义已变更。
- 删除任务前必须确认任务未绑定流程；已绑定时拒绝删除。
- `clearEventId=true` 仅用于流程编排中的事件关闭场景，不属于普通任务定义编辑入口。

## 复制设计

调用链：

```text
TaskController.copy -> TaskInfoService.copyTaskInfo -> TaskInfoServiceImpl -> scheduler_task_info
```

后端新增 `TaskInfoCopyDto`，字段见 `scheduler-task-data-define.md`。Controller 只接收原任务 ID 并返回新任务 ID；复制校验、名称编码生成和字段取舍全部放在 Service。

Service 处理步骤：

1. 根据 `sourceId` 查询原任务，不存在时报“任务不存在”。
2. 生成副本名称和编码：生成一个毫秒级 15 位时间后缀，格式为 `yyMMddHHmmssSSS`；若 `source.taskName/source.taskCode` 已以 `_` + 15 位数字结尾，先移除旧后缀，再追加新后缀；同一个新后缀同时用于 `taskName` 和 `taskCode`。
3. 校验去掉已有复制后缀后的 `taskName/taskCode` 基础值不超过 235 个字符，超出时报“任务名称过长, 无法复制”或“任务编码过长, 无法复制”；生成后校验 `taskCode` 唯一。
4. 构造新 `TaskInfoEntity`：新 `id` 由新 `taskCode` 生成；`taskName/taskCode` 使用第 2 步生成值。
5. 复制任务定义属性：`description/taskTypeId/taskType/taskParam/definition`。
6. 复制系统属性中的 `syncFlag`；`sourceRoute` 写入来源追踪 JSON：`{"sourceRoute":"<sourceRoute>","copy_task_id":"<sourceId>","copy_task_name":"<sourceTaskName>"}`。其中 `sourceRoute` 保留原任务的业务页面路由，`copy_task_id/copy_task_name` 记录被复制任务。
7. 审计字段写入当前用户和当前时间。
8. 重置编排字段：`isBound=false`、`flowId=null`、`view=null`、`depEventIds=null`、`eventId=null`、`enabled=true`；`pluginId` 按新任务 `taskType` 解析默认执行插件。
9. 保存后返回新任务 ID。

前端在任务管理列表行操作中新增“复制”。点击后弹出确认框，确认后调用 `/copy`，请求体只提交 `sourceId`。提交成功后刷新任务列表；副本名称和编码以后端返回后的列表数据为准，不在前端预生成。

## 集成点

- `TaskStorageImpl` 将任务定义转换为 master 的 `TaskInfo`。
- `FlowInfoServiceImpl` 查询流程 DAG 时读取绑定任务。
- `TaskLinkService` 负责流程 DAG 依赖连线，不由任务定义域维护。
- 当前用户通过 `HttpUtils.getCurrentUserName()` 写入审计字段。
- 任务复制不触发 `TaskStorageImpl`、`TaskLinkService`、流程发布或实例运行链路。

## 非目标

- 不在 `TaskController` 中处理 worker 提交、停止、重启或实例状态。
- 不在任务定义页编辑流程绑定、画布坐标、依赖事件、生成事件或节点启停。
- 不复制流程绑定、流程连线、实例、日志、运行结果或事件实例。
- 不调整 `scheduler_task_info` 表结构。

## 预期文件变更

| 文件 | 变更 |
|------|------|
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/TaskInfoCopyDto.java` | 新增复制请求 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/TaskController.java` | 新增 `POST /copy` |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/TaskInfoService.java` | 新增 `copyTaskInfo` 方法 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/TaskInfoServiceImpl.java` | 实现复制、名称编码生成、唯一性校验复用、来源追踪、审计填充 |
| `datafusion-web/src/modules/scheduler-task/api.ts` | 新增 `copy` API client |
| `datafusion-web/src/modules/scheduler-task/dto.ts` | 新增 `TaskCopyReq` |
| `datafusion-web/src/modules/scheduler-task/components/list-table/columns.tsx` | 新增行级复制操作 |
| `datafusion-web/src/modules/scheduler-task/index.tsx` | 处理复制动作、调用复制接口并刷新列表 |

验证命令：

- `mvn -DskipTests compile -pl datafusion-manager -am`
- `npm run build`
