# scheduler-instance 设计文档

> 数据结构见 [scheduler-instance-data-define.md](./scheduler-instance-data-define.md)。本文只描述运行实例查询、操作、日志和归档规则。

## 定位

`scheduler-instance` 是调度运行态查询域，用来查看一次调度运行的流程实例、任务实例、事件实例、结果和日志，并承载实例级手工操作。它不修改流程、任务、事件、触发器等定义。

定义修改必须回到定义域，并遵循“取消调度 -> 取消发布 -> 修改定义”的顺序。

## 查询链路

| 场景 | 链路 | 数据源 |
|------|------|--------|
| 流程实例主列表 | `FlowInstanceController.page` -> `FlowInstanceService.pageFlowInstance` | 实时表或历史表 |
| 按任务过滤流程实例 | 流程分页 SQL 中使用任务实例 `exists` | 与 `viewType` 一致的任务实例表 |
| 展开流程下任务 | `TaskInstanceController.listByFlowInstance` | 实时表或历史表 |
| 任务实例分页 | `TaskInstanceController.page` | 实时表或历史表 |
| 事件实例分页 | `EventInstanceController.page` | `scheduler_event_instance` |
| 任务日志 | `TaskInstanceLogController.content` | agent 写入的日志目录或插件日志文件 |
| 任务运行目录 | `TaskInstanceLogController.fileBrowser` | 后端重定向到 FileBrowser 对应目录 |
| 实例操作 | `FlowInstanceController.action` / `TaskInstanceController.action` | 实时实例 + master actor |

`viewType=REALTIME` 查询实时表，`viewType=HISTORY` 查询历史表；数据归属不根据状态临时判断。

## 接口

| 前缀 | 方法 | 路径 | 说明 |
|------|------|------|------|
| `/api/scheduler/flow/instance` | `POST` | `/page` | 流程实例分页 |
| `/api/scheduler/flow/instance` | `GET` | `/{id}` | 流程实例详情 |
| `/api/scheduler/flow/instance` | `POST` | `/action` | 流程实例操作 |
| `/api/scheduler/task/instance` | `POST` | `/page` | 任务实例分页 |
| `/api/scheduler/task/instance` | `POST` | `/listByFlowInstance` | 查询流程下任务实例 |
| `/api/scheduler/task/instance` | `GET` | `/{id}` | 任务实例详情 |
| `/api/scheduler/task/instance` | `POST` | `/action` | 任务实例操作 |
| `/api/scheduler/event/instance` | `POST` | `/page` | 事件实例分页 |
| `/api/scheduler/event/instance` | `GET` | `/{id}` | 事件实例详情 |
| `/api/scheduler/task/instance/log` | `POST` | `/content` | 读取任务日志 |

## 业务规则

- 查询条件支持流程名称/ID、任务名称/ID、状态、调度时间、开始时间、结束时间。
- 流程主列表按任务过滤时使用 `exists`，避免一条流程因多个任务命中而重复。
- 展开任务时按当前 `viewType` 查询同一归属的数据表。
- 实例详情优先按指定视图查询；需要兜底时可先实时、后历史。
- 实例操作只允许实时表实例，历史实例只读。
- 操作前后端都要按状态计算可用操作；前端展示不能替代后端校验。
- 流程行刷新只刷新当前流程实例和该流程下任务实例，不刷新整页。

## 实例操作

流程实例第一版开放：

| 状态 | 操作 |
|------|------|
| `INIT_SUCCESS`、`WAIT_DEPENDENT` | `SUBMIT` |
| `RUNNING` | `STOP` |

任务实例第一版开放：

| 状态 | 操作 |
|------|------|
| `INIT_SUCCESS`、`WAIT_DEPENDENT` | `SUBMIT`、`STOP` |
| `INIT_FAILURE` | `STOP`、`ENFORCE_SUCCESS` |
| `SUBMIT_SUCCESS`、`RUNNING` | `STOP` |
| `SUBMIT_FAILURE`、`RUN_FAILURE`、`STOP_SUCCESS`、`KILLED` | `RESTART`、`ENFORCE_SUCCESS` |
| `STOP_FAILURE` | `KILL`、`RESTART`、`ENFORCE_SUCCESS` |
| `UNKNOWN` | `ENFORCE_SUCCESS` |

`REINIT` 暂不在页面开放；流程和任务实例操作均通过 `MasterService.getFlowAction()` / `getTaskAction()` 进入 actor 状态机。

## 日志

标准任务日志只使用 `TaskResult.workerResult.workDirPath` 定位，不按 `startTime` 或 `${modules}` 兜底推导路径。
日志类型固定映射：

```text
LOG    -> {workDirPath}/stdout.log
ERROR  -> {workDirPath}/stderr.log
STATUS -> {workDirPath}/state.log
PLUGIN -> workerResult.pluginLogUri
```

`stdout.log`、`stderr.log`、`state.log` 的文件名由 `TaskRuntimeFiles` 统一定义。`PLUGIN` 只读取当前任务
`workDirPath` 目录下的本地插件日志文件。`workerResult` 缺失路径或目标文件不存在时，
接口返回空内容和实际尝试路径；日志读取不新增数据库表。

任务实例“返回结果”按文本行展示 `workerResultText`、`workerResult.message`、`appId`、`workerId`、`workDirPath`
和 `pluginLogUri` 等后端返回字段；长字段页面可截断，鼠标悬浮展示完整内容。返回结果列不承载跳转操作。
鼠标移入返回结果区域时，以气泡展示当前任务返回结果的完整多行文本。
插件日志通过任务“查看日志”抽屉中的“插件日志”tab 查看，使用 `logType=PLUGIN` 分段读取日志内容。
日志抽屉中的 `LOG`、`ERROR`、`STATUS`、`PLUGIN` 四种日志均支持打开独立页面，独立页复用同一日志读取接口和
“加载更多”能力。

当任务实例存在 `workDirPath` 时，操作列展示“打开目录”入口，前端调用
`/api/scheduler/task/instance/log/filebrowser/{taskInstanceId}` 并由后端重定向到 FileBrowser。前端不拼接
FileBrowser 地址，也不保存或展示 FileBrowser 用户名密码。

## 归档

`SchedulerInstanceArchiveScheduleJob` 定时把成功流程实例从实时表迁移到历史表，并按流程实例 ID 同步迁移其任务实例。

当前归档约束：

- 只迁移 `StatusEnum.isSuccess()` 的流程实例。
- 非成功流程实例不迁移，任务实例不独立判断状态。
- 当 `scheduler_flow_info` 中仍存在对应 `id + publish_version` 的流程定义版本时，按
  `flow_id + publish_version` 保留实时表中 `schedule_time` 最新的一条流程实例。
- 当流程实例的 `flow_id + publish_version` 在 `scheduler_flow_info` 中已不存在时，该成功实例不再参与
  “保留最新一条”规则，需要迁移到历史表。
- 只处理 `schedule_time is not null` 的调度实例。
- 同一批归档需要在事务中完成历史表插入和实时表删除。

该约束保证仍存在定义版本的流程，master 重启恢复能从实时表最新 `scheduleTime` 继续调度；已删除或不存在的
定义版本不再保留实时实例。

## 非目标

- 不新增人工操作审计表。
- 不新增 `TaskInstanceLog` 表。
- 不在实例页面编辑 DAG、变量、插件参数或定义信息。
- 不查询历史表参与 actor 恢复。
