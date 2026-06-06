# scheduler-instance 设计文档

> 数据结构定义见 [scheduler-instance-data-define.md](./scheduler-instance-data-define.md)。本文档只说明数据流、行为、接口、实现边界和验证方式。

## 1. 能力范围

- 能力: 调度运行实例查询，包括流程实例、任务实例、事件实例和任务实例日志读取。
- 模块: `datafusion-manager`。
- Java 后端包: `com.datafusion.manager.scheduler`。
- 前端路径: `datafusion-web/src/modules/scheduler-instance`。
- 路由 / API 前缀: 前端路由建议为 `/scheduler-instance`，后端接口按 `/api/scheduler/{flow|task|event}/instance` 风格提供。
- 调用链路: `*InstanceController` -> `*InstanceService` -> `*InstanceMapper`；流程和任务实例按 `viewType` 路由到实时表或历史表，事件实例查询 `scheduler_event_instance`。

`scheduler-instance` 是运行态查询域，只展示一次调度运行发生了什么、当前状态、运行结果和日志位置。它不承担流程定义、任务定义、事件定义、触发器定义或变量定义的修改能力。

定义修改必须走定义域规则：先取消调度，再取消发布，然后回到 `scheduler-flow`、`scheduler-task`、`scheduler-event` 等定义模块修改。

## 2. 数据流

| 场景 | 来源 | 链路 | 目标 | 数据结构 | 说明 |
|----------|--------|---------|--------|----------------|-------|
| 查询流程实例主列表 | 页面查询区 | `flowInstanceApi.page` -> `FlowInstanceController.page` -> `FlowInstanceService.pageFlowInstance` | `scheduler_flow_instance` / `scheduler_flow_instance_his` | `PageQuery<SchedulerInstanceQueryDto>` / `PageResponse<FlowInstanceDto>` | `viewType` 只决定查询实时表或历史表，不参与终态判断 |
| 按任务名称/ID 过滤流程实例 | 页面查询区 | `FlowInstanceService` -> `TaskInstanceMapper` / `TaskInstanceHisMapper` exists | `scheduler_task_instance` / `scheduler_task_instance_his` + 对应流程实例表 | `SchedulerInstanceQueryDto.taskKeyword` | 按 `viewType` 选择实时或历史表，避免主列表重复 |
| 展开流程实例任务 | 主表展开行 | `taskInstanceApi.listByFlowInstance` -> `TaskInstanceController.listByFlowInstance` | `scheduler_task_instance` / `scheduler_task_instance_his` | `TaskInstanceDto[]` | 按当前 `viewType` 懒加载实时或历史任务 |
| 查询事件实例 | 事件实例页面或详情入口 | `EventInstanceController.page` -> `EventInstanceService.pageEventInstance` | `scheduler_event_instance` | `PageQuery<EventInstanceQueryDto>` | 可独立页面 |
| 查看任务日志 | 任务实例行操作 | `TaskInstanceLogController.content` -> 日志读取 Service | 共享日志目录 | `TaskInstanceLogQueryDto` / `TaskInstanceLogDto` | 日志路径由 agent 写日志后通过 `TaskResult` 回传，不落库 |
| 成功实例归档 | Spring Schedule 定时任务 | `SchedulerInstanceArchiveScheduleJob` -> archive Service -> Mapper | `scheduler_flow_instance_his` / `scheduler_task_instance_his` | 实时表成功实例 | 默认每天执行一次，流程实例和任务实例状态满足 `StatusEnum.isSuccess()` 时，从实时表迁移到历史表 |

## 3. 接口契约

### 3.1 FlowInstanceController

| HTTP 方法 | 路径 | 请求对象 | 响应对象 | 说明 |
|-------------|------|----------------|-----------------|-------|
| `POST` | `/api/scheduler/flow/instance/page` | `PageQuery<SchedulerInstanceQueryDto>` | `Result<PageResponse<FlowInstanceDto>>` | 流程实例主列表 |
| `GET` | `/api/scheduler/flow/instance/{id}` | 路径参数 `UUID id` | `Result<FlowInstanceDto>` | 流程实例详情 |

### 3.2 TaskInstanceController

| HTTP 方法 | 路径 | 请求对象 | 响应对象 | 说明 |
|-------------|------|----------------|-----------------|-------|
| `POST` | `/api/scheduler/task/instance/page` | `PageQuery<SchedulerInstanceQueryDto>` | `Result<PageResponse<TaskInstanceDto>>` | 任务实例独立分页 |
| `POST` | `/api/scheduler/task/instance/listByFlowInstance` | `FlowInstanceTaskQueryDto` | `Result<List<TaskInstanceDto>>` | 展开流程实例任务，按 `viewType` 查询实时或历史任务表 |
| `GET` | `/api/scheduler/task/instance/{id}` | 路径参数 `UUID id` | `Result<TaskInstanceDto>` | 任务实例详情 |

### 3.3 EventInstanceController

| HTTP 方法 | 路径 | 请求对象 | 响应对象 | 说明 |
|-------------|------|----------------|-----------------|-------|
| `POST` | `/api/scheduler/event/instance/page` | `PageQuery<EventInstanceQueryDto>` | `Result<PageResponse<EventInstanceDto>>` | 事件实例分页 |
| `GET` | `/api/scheduler/event/instance/{id}` | 路径参数 `UUID id` | `Result<EventInstanceDto>` | 事件实例详情 |

### 3.4 TaskInstanceLogController

| HTTP 方法 | 路径 | 请求对象 | 响应对象 | 说明 |
|-------------|------|----------------|-----------------|-------|
| `POST` | `/api/scheduler/task/instance/log/content` | `TaskInstanceLogQueryDto` | `Result<TaskInstanceLogDto>` | 按偏移读取日志 |

## 4. 文件变更

### 4.1 新增文件

| 文件 | 说明 |
|------|-------|
| `docs/datafusion-manager/scheduler-instance-data-define.md` | 数据库、后端、前端数据结构定义 |
| `docs/datafusion-manager/scheduler-instance-design.md` | 数据流、接口、后端、前端、集成和验证设计 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/FlowInstanceController.java` | 流程实例查询入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/TaskInstanceController.java` | 任务实例查询入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/EventInstanceController.java` | 事件实例查询入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/TaskInstanceLogController.java` | 任务实例日志入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/jobs/SchedulerInstanceArchiveScheduleJob.java` | 成功实例归档 Spring Schedule 定时任务 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/SchedulerInstanceArchiveService.java` | 成功实例归档 Service |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/SchedulerInstanceQueryDto.java` | 流程和任务实例查询条件 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/FlowInstanceTaskQueryDto.java` | 流程展开任务实例查询条件 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/EventInstanceQueryDto.java` | 事件实例查询条件 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/FlowInstanceDto.java` | 流程实例响应 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/TaskInstanceDto.java` | 任务实例响应 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/EventInstanceDto.java` | 事件实例响应 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/TaskInstanceLogQueryDto.java` | 日志读取请求 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/TaskInstanceLogDto.java` | 日志读取响应 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/FlowInstanceHisEntity.java` | 流程实例历史表 PO |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/TaskInstanceHisEntity.java` | 任务实例历史表 PO |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/FlowInstanceHisMapper.java` | 流程实例历史表 Mapper |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/TaskInstanceHisMapper.java` | 任务实例历史表 Mapper |
| `datafusion-manager/src/main/resources/mapper/FlowInstanceHisMapper.xml` | 流程实例历史表分页和归档 SQL |
| `datafusion-manager/src/main/resources/mapper/TaskInstanceHisMapper.xml` | 任务实例历史表分页、展开和归档 SQL |
| `datafusion-web/src/modules/scheduler-instance/*` | 前端实例查询页面、API client、DTO、组件 |

### 4.2 修改文件

| 文件 | 说明 |
|------|-------|
| `FlowInstanceService` / `FlowInstanceServiceImpl` | 补充页面分页和详情查询 |
| `TaskInstanceService` / `TaskInstanceServiceImpl` | 补充页面分页、详情、按流程实例查询 |
| `EventInstanceService` / `EventInstanceServiceImpl` | 补充页面分页和详情查询 |
| `FlowInstanceMapper` / `TaskInstanceMapper` / `EventInstanceMapper` | 必要时补充关联分页查询 |
| `FlowInstanceMapper.xml` / `TaskInstanceMapper.xml` / `EventInstanceMapper.xml` | 必要时补充自定义 SQL |
| `init_ddl.sql` | 补充 `scheduler_flow_instance_his`、`scheduler_task_instance_his` 历史表 |
| `FlowStorageImpl` / `TaskStorageImpl` | 状态落库与读取改为 `StatusEnum.stateType` / `StatusEnum.fromString()` |
| `TaskResult` | 补充 agent 回传日志文件路径的显式字段，供 manager 读取共享日志目录 |
| `datafusion-web/src/router/routes.tsx` | 增加 `scheduler-instance` 菜单 |

### 4.3 复用对象

| 对象 | 路径 | 说明 |
|--------|------|-------|
| `FlowInstanceEntity` / `TaskInstanceEntity` / `EventInstanceEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po` | 复用现有实时表 PO |
| `FlowStorageImpl` / `TaskStorageImpl` / `EventStorageImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage` | master 运行态落库适配 |
| `Result<T>`、`PageQuery<T>`、`PageResponse<T>` | `datafusion-common-spring` | API 响应和分页模型 |
| `StatusEnum` | `datafusion-common-data` | 状态存储、展示和成功归档判断 |
| `TaskResult` | `datafusion-common-data` | worker 返回结果和日志路径 |

## 5. Java 后端设计

### 5.1 Controller

| 方法 | 输入 | 输出 | 说明 |
|--------|-------|--------|-------|
| `FlowInstanceController.page` | `PageQuery<SchedulerInstanceQueryDto>` | `Result<PageResponse<FlowInstanceDto>>` | 主列表 |
| `FlowInstanceController.getById` | `UUID id` | `Result<FlowInstanceDto>` | 详情 |
| `TaskInstanceController.page` | `PageQuery<SchedulerInstanceQueryDto>` | `Result<PageResponse<TaskInstanceDto>>` | 任务实例分页 |
| `TaskInstanceController.listByFlowInstance` | `FlowInstanceTaskQueryDto` | `Result<List<TaskInstanceDto>>` | 展开行懒加载，`viewType` 决定任务表 |
| `TaskInstanceController.getById` | `UUID id` | `Result<TaskInstanceDto>` | 详情 |
| `EventInstanceController.page` | `PageQuery<EventInstanceQueryDto>` | `Result<PageResponse<EventInstanceDto>>` | 事件实例分页 |
| `EventInstanceController.getById` | `UUID id` | `Result<EventInstanceDto>` | 详情 |
| `TaskInstanceLogController.content` | `TaskInstanceLogQueryDto` | `Result<TaskInstanceLogDto>` | 日志读取 |

### 5.2 Service

| 方法 | 输入 | 输出 | 说明 |
|--------|-------|--------|-------|
| `pageFlowInstance` | `PageQuery<SchedulerInstanceQueryDto>` | `PageResponse<FlowInstanceDto>` | 按 `viewType` 选择实时表或历史表，支持流程/任务关键字 |
| `getFlowInstanceById` | `UUID id` | `FlowInstanceDto` | 先查实时表，未命中再查历史表，不存在抛业务异常 |
| `pageTaskInstance` | `PageQuery<SchedulerInstanceQueryDto>` | `PageResponse<TaskInstanceDto>` | 按 `viewType` 选择实时表或历史表，独立任务实例分页 |
| `listByFlowInstanceId` | `FlowInstanceTaskQueryDto` | `List<TaskInstanceDto>` | 按 `viewType` 展开实时或历史流程实例 |
| `getTaskInstanceById` | `UUID id` | `TaskInstanceDto` | 先查实时表，未命中再查历史表，不存在抛业务异常 |
| `pageEventInstance` | `PageQuery<EventInstanceQueryDto>` | `PageResponse<EventInstanceDto>` | 事件实例分页 |
| `getEventInstanceById` | `UUID id` | `EventInstanceDto` | 不存在抛业务异常 |
| `readTaskInstanceLog` | `TaskInstanceLogQueryDto` | `TaskInstanceLogDto` | 按偏移读取日志 |
| `archiveSuccessInstances` | 归档批次参数 | `int` | 定时迁移成功流程实例和任务实例 |

### 5.3 业务规则

| 场景 | 规则 | 错误 / 返回 |
|----------|------|----------------|
| 实时查询 | `viewType=REALTIME` 查询 `scheduler_flow_instance` / `scheduler_task_instance`，不依据状态决定数据归属 | 返回实时表中的实例 |
| 历史查询 | `viewType=HISTORY` 查询 `scheduler_flow_instance_his` / `scheduler_task_instance_his`，不依据状态决定数据归属 | 返回历史表中的实例 |
| 流程关键字 | 支持流程名称模糊匹配；关键字可解析为 UUID 时同时匹配流程实例 ID | 返回分页结果 |
| 任务关键字过滤流程 | 按 `viewType` 使用 `exists` 关联对应任务实例表，避免流程主列表重复 | 返回流程分页结果 |
| 展开任务实例 | 按 `viewType` 选择 `scheduler_task_instance` 或 `scheduler_task_instance_his`，再按 `flow_instance_id` 查询，默认按 `create_time asc` | 返回任务列表 |
| worker 结果展示 | 保留 `worker_result` 原始 JSON，额外派生摘要文本和日志路径 | 为空显示 `-` |
| 日志读取 | 不新增表，优先使用 `TaskResult` 回传的日志文件路径；缺失时使用任务 `startTime` 派生 `yyyyMMdd` 并按约定目录兜底定位 | 文件不存在时返回空内容或明确业务错误 |
| 成功实例归档 | Spring Schedule 默认每天凌晨 2 点扫描实时表中成功态 `31` / `33` 的流程实例和任务实例，插入对应 `*_his` 表后删除实时表记录；cron 可通过 `datafusion.scheduler.instance.archive.cron` 覆盖 | 同一批次内插入历史表和删除实时表需要事务；重复归档按主键幂等处理 |
| 定义修改 | 实例域不允许修改定义 | 引导用户取消调度、取消发布后回定义域修改 |
| 操作入口 | 原型中的重启、停止、重跑、强制成功等写操作第一版不实现；刷新仅作为重新查询当前行的读操作 | 不提供写接口 |

### 5.4 事务边界

- 是否需要事务: 是，查询和日志读取不需要事务；成功实例归档需要事务。
- 事务方法: `archiveSuccessInstances`。
- 回滚条件: 插入历史表、删除实时表任一失败时回滚当前批次，避免实例同时缺失或重复迁移。

### 5.5 Mapper / DAO / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|--------|------|------------|-------|
| `FlowInstanceMapper.getInstanceById` | 查询 | `FlowInstanceMapper.xml` | 已存在 |
| `TaskInstanceMapper.listByFlowInsId` | 查询 | `TaskInstanceMapper.xml` | 已存在，展开任务实例 |
| `TaskInstanceMapper.getInstanceById` | 查询 | `TaskInstanceMapper.xml` | 已存在 |
| `EventInstanceMapper.loadByEventId` / `loadByEventKey` | 查询 | `EventInstanceMapper.xml` | 已存在，供调度事件存储使用 |
| `pageFlowInstance` | 查询 | XML 或 MyBatis-Plus wrapper | 待实现，按 `viewType` 路由实时表 / 历史表，包含 `exists` 关联任务 |
| `pageTaskInstance` | 查询 | XML 或 MyBatis-Plus wrapper | 待实现，按 `viewType` 路由实时表 / 历史表 |
| `pageEventInstance` | 查询 | XML 或 MyBatis-Plus wrapper | 待实现 |
| `listTaskByFlowInstance` | 查询 | XML 或 MyBatis-Plus wrapper | 待实现，按 `viewType` 查询对应任务实例表 |
| `archiveSuccessFlowInstances` / `archiveSuccessTaskInstances` | 写入 + 删除 | XML | 待实现，先插入 `*_his`，再删除实时表成功实例 |

用户输入必须使用 `#{}` 参数绑定，禁止 `${}` 拼接。涉及 `like` 时由 Service 组装安全条件或 MyBatis-Plus wrapper。

## 6. 前端设计

### 6.1 页面结构

页面参考原型采用“上方查询区 + 实例列表主表 + 展开任务子表”的结构。原型中偏数据集成任务的文案只作为布局参考，正式页面统一使用调度实例、流程实例、任务实例等通用文案。

| 页面 / 组件 | 职责 | 输入数据 | 输出事件 | 说明 |
|------------------|----------------|------------|--------------|-------|
| `SchedulerInstancePage` | 页面容器、查询状态、实时/历史切换 | `SchedulerInstanceQueryDto` | 查询、重置、切换 tab | 首屏为实例查询页，`viewType` 切换放在列表标题右侧 |
| `InstanceFilterBar` | 查询区 | 表单状态 | `onSearch` / `onReset` | 流程名称/ID、任务名称/ID、实例状态、调度时间范围、开始时间范围、结束时间范围 |
| `FlowInstanceTable` | 流程实例主表 | `FlowInstanceDto[]` | 展开、刷新、打开详情 | 支持展开任务实例；起止时间按开始/结束两行展示 |
| `TaskInstanceSubTable` | 展开行任务表 | `TaskInstanceDto[]` | 查看依赖图、查看日志 | 懒加载；返回结果列展示 worker 摘要 |
| `TaskInstanceDependencyDrawer` | 任务依赖图展示 | `FlowInstanceDto` + `TaskInstanceDto` | 关闭 | 只读展示，优先使用 `flowDagSnapshot` 和任务上下游实例 ID |
| `TaskInstanceLogDrawer` | 日志内容展示 | `TaskInstanceLogDto` | 加载更多、关闭 | 等宽字体展示 |
| `EventInstancePage` | 事件实例查询 | `EventInstanceDto[]` | 查询、详情 | 可后续加入独立菜单或详情页 |

主表列:

| 表格 | 列 | 说明 |
|------|------|-------|
| 流程实例主表 | 展开、流程名称、流程实例ID、实例状态、调度时间、起止时间、操作 | 对齐原型中的实例列表；操作列第一版仅提供刷新当前行数据 |
| 任务实例子表 | 任务实例、任务实例ID、任务状态、返回结果、起止时间、操作 | 展开流程后加载；操作列提供查看依赖图、查看日志；重启不在第一版实现 |

### 6.2 交互行为

| 场景 | 触发 | 数据变化 | API 调用 | 结果处理 |
|----------|---------|-------------|----------|-----------------|
| 查询实例 | 点击查询 | 更新分页条件，页码回到 1 | `flowInstanceApi.page` | 按 `viewType` 刷新实时或历史主列表；流程/任务名称 ID 输入框均支持名称模糊和 UUID 精确匹配 |
| 重置查询 | 点击重置 | 清空条件，`viewType=REALTIME` | `flowInstanceApi.page` | 刷新实时主列表 |
| 切换实时/历史 | 点击 segmented/tab | 更新 `viewType` | `flowInstanceApi.page` | 切换查询实时表或历史表 |
| 展开流程 | 展开行 | 记录展开 ID 和当前 `viewType` | `taskInstanceApi.listByFlowInstance` | 按同一 `viewType` 渲染任务子表 |
| 刷新流程行 | 点击流程行刷新 | 保留查询条件和展开状态 | `flowInstanceApi.getById` + `taskInstanceApi.listByFlowInstance` | 刷新当前流程行和已展开任务列表 |
| 查看依赖图 | 点击任务行查看依赖图 | 设置当前流程和任务实例 | 无新增写接口 | 打开只读依赖图抽屉 |
| 查看日志 | 点击任务行日志 | 设置当前日志查询 | `taskInstanceLogApi.content` | 打开日志抽屉 |
| 加载更多日志 | 点击加载更多 | 更新 offset | `taskInstanceLogApi.content` | 追加日志内容 |
| 重启任务 | 原型中的任务行重启 | 第一版不提供 | 无 | 隐藏或禁用；后续如实现需新增权限、幂等和审计设计 |

### 6.3 路由 / 菜单

| 路由 / 菜单 | 路径 | 组件 | 权限 / 展示规则 | 说明 |
|--------------|------|-----------|---------------------------|-------|
| 调度中心 / 实例查询 | `scheduler-instance` | `SchedulerInstancePage` | 复用现有前端菜单规则 | 对应原型主页面 |
| 调度中心 / 事件实例 | `scheduler-event-instance` | `EventInstancePage` | 可后续开放 | 第一版可不进菜单 |

## 7. 集成关系

| 集成目标 | 方式 | 说明 |
|--------------------|--------|-------|
| `datafusion-scheduler-master` | `FlowStorageImpl` / `TaskStorageImpl` / `EventStorageImpl` | master 负责运行态创建和状态推进，manager 查询实例 |
| `datafusion-agent` | 共享日志目录 + `TaskResult` | 任务日志由 agent 产生并写入共享目录，agent 在 `TaskResult` 中回传日志文件路径，manager 按路径只读 |
| MyBatis-Plus | Mapper / ServiceImpl | 查询实例表 |
| `SchedulerInstanceArchiveScheduleJob` | Spring Schedule 定时任务 | 默认每天凌晨 2 点将成功流程实例、任务实例从实时表迁移到历史表 |
| `StatusEnum` | 枚举判断 | 状态落库使用 `stateType`，读取使用 `fromString()`，归档成功态使用 `isSuccess()` |

日志目录约定：

```text
${modules}/logs/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/*.log
${modules}/logs/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/*.err.log
${modules}/task-status/{yyyyMMdd}/{flowInstanceId}/{taskInstanceId}/taskStatus.log
```

`yyyyMMdd` 由任务实例 `startTime` 派生；当 `TaskResult` 已回传明确日志文件路径时，优先使用回传路径。

## 8. 安全与上下文

- 当前用户: 查询接口不写审计字段；日志读取只读文件。
- 租户 / 项目 / 应用上下文: 现有实例表无租户或项目字段，第一版不做租户过滤。
- 密码 / token 处理: 无。
- 权限边界: 第一版只读。若后续增加停止、重跑、强制成功等操作，必须补充权限、幂等和操作审计设计。

## 9. 不实现范围

- 不修改流程定义、任务定义、事件定义、触发器定义或变量定义。
- 不新增人工操作记录表。
- 不新增 `TaskInstanceLog` 表。
- 不实现重启、停止、重跑、强制成功等写操作。
- 不修改 `scheduler_flow_instance`、`scheduler_task_instance`、`scheduler_event_instance` 实时表结构；仅新增流程和任务实例历史表。
- 不在实例页面直接编辑 DAG、任务变量参数或插件参数。

## 10. 验证方式

- 单元测试: 覆盖 `REALTIME/HISTORY` 表路由、流程名称/ID 查询、任务名称/ID 关联查询、展开任务实例、事件实例分页、成功实例归档、日志文件不存在。
- 编译 / 构建命令: `mvn -DskipTests compile -pl datafusion-manager -am`。
- 前端验证: `cd datafusion-web && npm run build`，必要时补充 `npm run lint`。
- 风格 / lint: Java 按 `style/codeStyle.md` 和 `style/CheckStyle-13.0.0.xml`；前端按现有 ESLint。
- 手工检查: 使用原型页面条件验证 `/flow/instance/page`、`/task/instance/listByFlowInstance`、`/task/instance/log/content`。
