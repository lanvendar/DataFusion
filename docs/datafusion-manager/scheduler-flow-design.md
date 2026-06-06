# scheduler-flow 设计文档

> 数据结构定义见 [scheduler-flow-data-define.md](./scheduler-flow-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 调度流程定义的分页查询、列表查询、详情、新增、修改、删除、DAG 编排、发布、取消发布、启用调度和停用调度。
- 所属模块: `datafusion-manager`
- 包路径: `com.datafusion.manager.scheduler`
- 路径前缀: `/api/scheduler/flow`
- 调用链: `FlowController` -> `FlowInfoService` -> `FlowInfoServiceImpl` -> `FlowInfoMapper` -> `scheduler_flow_info`

流程定义承担三类职责：

| 分类 | 说明 | 当前落点 |
|------|------|----------|
| 基础定义 | 流程名称、编码、类型、参数、调度窗口、触发器、事件依赖 | `scheduler_flow_info` |
| DAG 编排 | 流程包含哪些任务，以及任务上下游关系和前端画布视图 | `scheduler_task_info.flow_id/view`、`scheduler_task_link` |
| 调度发布 | 将流程标记为已发布、维护版本并控制是否进入调度扫描 | `publish_state`、`publish_version`、`enabled` |

`FlowController` 是流程定义管理入口，不直接创建流程实例，也不直接提交 worker 任务。流程实例创建、触发和执行由 `datafusion-scheduler-master` 通过 manager 侧 `FlowStorageImpl`、`TriggerStorageImpl`、`TaskStorageImpl` 等适配类完成。

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<FlowInfoQueryDto>` | `Result<PageResponse<FlowInfoDto>>` | 分页查询流程 |
| `POST` | `/list` | `FlowInfoQueryDto` | `Result<List<FlowInfoDto>>` | 查询流程列表 |
| `GET` | `/detail/{id}` | path `UUID id` | `Result<FlowInfoDto>` | 查询流程详情 |
| `POST` | `/add` | `FlowInfoSaveDto` | `Result<UUID>` | 新增流程 |
| `POST` | `/update` | `FlowInfoUpdateDto` | `Result<Boolean>` | 修改流程 |
| `POST` | `/delete/{id}` | path `UUID id` | `Result<Boolean>` | 删除流程 |
| `GET` | `/dag/detail/{id}` | path `UUID id` | `Result<FlowDagDto>` | 查询流程 DAG |
| `POST` | `/dag/save` | `DagSaveDto` | `Result<Boolean>` | 保存流程 DAG |
| `POST` | `/publish` | `FlowPublishDto` | `Result<Boolean>` | 发布流程 |
| `POST` | `/unpublish/{id}` | path `UUID id` | `Result<Boolean>` | 取消发布 |
| `POST` | `/enable/{id}` | path `UUID id` | `Result<Boolean>` | 启用调度 |
| `POST` | `/disable/{id}` | path `UUID id` | `Result<Boolean>` | 停用调度 |

流程编排任务池复用任务列表接口：

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/api/scheduler/task/list` | `TaskInfoQueryDto` | `Result<List<TaskInfoDto>>` | 查询可加入当前流程的未绑定任务池 |

任务池查询契约：

- 入参支持 `keyword`，用于任务名称和任务编码共用一个搜索框。
- 查询条件为 `isBound=false AND (taskName ILIKE %keyword% OR taskCode ILIKE %keyword%)`。
- 结果按 `updateTime desc` 排序。
- 返回字段至少包含 `id/taskName/taskCode/taskType/syncFlag`，用于任务池卡片和拖拽节点展示。

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/scheduler-flow-data-define.md` | 调度流程字段、类型、校验和层间映射定义 |
| `docs/datafusion-manager/scheduler-flow-design.md` | 调度流程 API、Service、Mapper、DAG 编排、调度集成和验证设计 |

### 3.2 修改

无。

### 3.3 复用

| 对象 | 路径 | 说明 |
|------|------|------|
| `FlowController` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/FlowController.java` | HTTP 入口 |
| `FlowInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/FlowInfoService.java` | Service 契约 |
| `FlowInfoServiceImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/FlowInfoServiceImpl.java` | 业务实现 |
| `FlowInfoMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/FlowInfoMapper.java` | MyBatis-Plus Mapper |
| `FlowInfoMapper.xml` | `datafusion-manager/src/main/resources/mapper/FlowInfoMapper.xml` | `getFlowInfo` 和 `listAllEnabled` 自定义查询 |
| `FlowInfoEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/FlowInfoEntity.java` | 流程定义实体 |
| `TaskInfoService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/TaskInfoService.java` | DAG 节点任务查询和绑定 |
| `TaskLinkService` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/TaskLinkService.java` | DAG 连线查询和保存 |
| `FlowStorageImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/FlowStorageImpl.java` | master `FlowStorage` 适配 |
| `TriggerStorageImpl` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/TriggerStorageImpl.java` | master `TriggerStorage` 适配 |
| `Result<T>` | `datafusion-common-spring` | API 响应包装 |
| `PageQuery<T>` / `PageResponse<T>` | `datafusion-common-spring` | 分页请求和响应 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `getFlowInfo` | `UUID flowId` | `FlowInfoEntity` | 按流程 ID 查询实体，供调度存储适配使用 |
| `listAllEnabled` | 无 | `List<FlowInfoEntity>` | 查询已发布且启用的流程，供触发器和流程存储适配使用 |
| `pageFlowInfo` | `PageQuery<FlowInfoQueryDto>` | `PageResponse<FlowInfoDto>` | 按条件分页查询 |
| `listFlowInfo` | `FlowInfoQueryDto` | `List<FlowInfoDto>` | 按条件查询列表 |
| `getFlowInfoById` | `UUID id` | `FlowInfoDto` | 查询详情，不存在时抛业务异常 |
| `addFlowInfo` | `FlowInfoSaveDto` | `UUID` | 新增流程，校验 `flowCode` 唯一并初始化发布/启用状态 |
| `updateFlowInfo` | `FlowInfoUpdateDto` | `boolean` | 按非空字段合并更新 |
| `deleteFlowInfo` | `UUID id` | `boolean` | 删除未发布、未启用的流程，并解绑任务、删除连线 |
| `getDag` | `UUID flowId` | `FlowDagDto` | 从绑定任务和连线组装流程 DAG |
| `saveDag` | `DagSaveDto` | `boolean` | 重新保存流程 DAG：解绑旧任务、绑定新任务、重建连线 |
| `publish` | `FlowPublishDto` | `boolean` | 发布流程，生成发布版本，可同时启用调度 |
| `unpublish` | `UUID id` | `boolean` | 取消发布，同时停用调度 |
| `enable` | `UUID id` | `boolean` | 已发布流程启用调度 |
| `disable` | `UUID id` | `boolean` | 停用调度 |

### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `flowName` 模糊匹配，`flowType/enabled/publishState` 精确匹配，按 `createTime desc` 排序 | 返回 `PageResponse<FlowInfoDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<FlowInfoDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "流程不存在")` |
| 新增流程 | `flowName/flowCode/flowType` 必填；`flowCode` 唯一 | 重复编码时抛 `流程编码已存在` |
| 新增流程 | `triggerId` 必填 | 缺失时在 DTO 校验阶段失败，避免落到数据库 `trigger_id NOT NULL` 约束 |
| 新增流程 | `id` 由 `UUID.nameUUIDFromBytes(flowCode)` 生成 | `flowCode` 变化会导致 ID 语义变化，新增前必须先校验唯一 |
| 新增流程 | 默认 `enabled=false`、`publishState=false`、`publishVersion=0` | 返回流程 ID |
| 修改流程 | 只允许修改未发布且未启用的流程；先查询旧实体，再合并非空字段 | 不存在时抛 `流程不存在`；已发布或已启用时拒绝编辑；`flowCode` 非空时重新校验唯一 |
| 删除流程 | 只允许删除未启用、未发布的流程 | 启用时抛 `流程调度中, 无法删除`；已发布时抛 `流程已发布, 无法删除` |
| 删除流程 | 删除前解绑所有任务并删除连线 | 事务内失败整体回滚 |
| 查询 DAG | 流程必须存在 | 不存在时抛 `流程不存在` |
| 查询 DAG | 从 `TaskInfoService.listByFlowId` 取节点，从 `TaskLinkService.listByFlowId` 取连线 | 节点 `data` 由后端补齐；节点/连线视图从 JSON 解析 |
| 保存 DAG | 流程必须存在；节点 ID 必须能解析为 `UUID` 且任务存在 | 不存在任务时抛 `任务不存在: {nodeId}` |
| 保存 DAG | 只允许保存未发布且未启用的流程 | 已发布或已启用流程只能查看编排，不允许编辑、拖拽、连线或保存 |
| 保存 DAG | 任务若已绑定其他流程，不允许复用 | 抛 `任务[{taskCode}]已被其他流程引用` |
| 保存 DAG | 先解绑旧任务，再绑定新任务并重建所有连线 | 不做增量更新 |
| 发布流程 | 流程必须存在；发布后 `publishState=true`，`publishVersion=System.currentTimeMillis()` | `enableSchedule=true` 时同时 `enabled=true` |
| 取消发布 | 流程必须存在；若已启用则先停用 | `publishState=false`，`enabled=false` |
| 启用调度 | 流程必须存在且已发布 | 未发布时抛 `流程未发布, 无法启用调度` |
| 停用调度 | 流程必须存在 | 置 `enabled=false` |

### 4.2 事务边界

- 是否需要事务: 部分写操作已显式声明事务。
- 事务方法: `deleteFlowInfo`、`saveDag` 当前使用 `@Transactional(rollbackFor = Exception.class)`。
- 建议补充事务: `addFlowInfo`、`updateFlowInfo`、`publish`、`unpublish`、`enable`、`disable` 均为单表写入，当前未显式声明事务；后续可统一补齐。
- 回滚条件: `RuntimeException`、`CommonException` 和 DAG 保存中的任意数据异常均应回滚。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 列表查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除、发布、启停前检查 |
| `selectCount` | 查询 | MyBatis-Plus `BaseMapper` | `flowCode` 唯一性校验 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增流程 |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改流程、发布、取消发布、启停 |
| `deleteById` / `removeById` | 写入 | MyBatis-Plus `ServiceImpl` | 删除流程 |
| `getFlowInfo` | 查询 | `FlowInfoMapper.xml` | `select * from scheduler_flow_info where id = #{flowId}` |
| `listAllEnabled` | 查询 | `FlowInfoMapper.xml` | 查询 `enabled=true and publish_state=true` 的流程 |
| `TaskInfoMapper.listByFlowId` | 查询 | `TaskInfoMapper.xml` | DAG 节点查询，按 `create_time asc` |
| `TaskLinkMapper.listByFlowId` | 查询 | `TaskLinkMapper.xml` | DAG 连线查询 |

自定义 SQL 均使用 `#{}` 参数绑定。

## 6. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD、分页、条件查询和批量保存 |
| `HttpUtils` | 静态工具调用 | 获取当前用户名，写入 `creator` 和 `updater` |
| `JacksonUtils` | 工具调用 | `flowParam`、节点视图、连线视图与 `JsonNode` 互转 |
| `TaskInfoService` | Service 注入 | 保存 DAG 时绑定/解绑任务；查询 DAG 时组装节点 |
| `TaskLinkService` | Service 注入 | 保存 DAG 时重建连线；查询 DAG 时组装边 |
| `FlowStorageImpl` | scheduler master 适配 | 将 `FlowInfoEntity` 转为调度框架 `FlowInfo` |
| `TriggerStorageImpl` | scheduler master 适配 | 使用已发布且启用的流程生成触发器调度输入 |
| `TaskStorageImpl` | scheduler master 适配 | 读取流程绑定任务和任务依赖供 master 执行 |

## 7. 前端设计

### 7.1 流程编排布局

流程编排使用三栏布局：

| 区域 | 职责 | 说明 |
|------|------|------|
| 左侧任务池 | 展示可加入当前流程的任务定义 | 复用 `POST /api/scheduler/task/list`；默认查询 `scheduler_task_info.is_bound=false` 的任务；支持 `keyword` 对任务名称和任务编码做 OR 模糊搜索；展示任务类型标签；后端按 `update_time desc` 倒排；支持拖拽到画布 |
| 中间画布 | DAG 编排主工作区 | 使用 `@xyflow/react`；支持从任务池拖拽生成节点、节点移动、节点连线、删除节点、删除边、缩放、适配视图和保存 |
| 右侧面板 | 节点信息和调度信息 | 默认展示【基本信息】标签页；选中节点后展示当前节点信息；第二个标签页为【调度信息】 |

左侧任务池除拖拽外，可保留“添加”按钮作为备选入口，但拖拽是主要交互。任务池列表只展示未绑定任务，已绑定其他流程的任务不作为可添加任务出现。任务卡片至少展示 `taskName/taskCode/taskType/syncFlag`。

### 7.2 画布交互

中间画布以 `@xyflow/react` 为核心实现：

- 从左侧任务池拖拽任务到画布，落点位置生成节点。
- 节点可拖动，拖动后的坐标保存到 `NodeDto.nodeView`。
- 节点默认提供 4 个居中连接点，上、下、左、右各一个 handle。
- 拖拽节点 handle 到其他节点生成依赖边。
- 删除节点时同步删除与该节点相关的边。
- 支持删除选中的边。
- 支持缩放、平移、适配视图和小地图。
- 保存时提交 `DagSaveDto`，即 `flowId + nodes + edges`；前端需要持久化的节点样式、位置和展示信息进入 `nodeView`，边样式、展示信息和 `sourceHandle/targetHandle` 等连线锚点进入 `edgeView`。
- 已发布或已启用流程只能查看编排，前端需要禁用拖拽、连线、删除和保存操作。

### 7.3 右侧节点面板

右侧面板包含两个标签页：

| 标签页 | 数据来源 | 展示内容 |
|--------|----------|----------|
| 基本信息 | 不额外请求后端，直接使用 DAG 节点 `data` 中的 task 信息 | 任务名称、任务编码、任务类型、任务描述、`syncFlag` 同步状态、`taskParam.vars` 摘要、`definition` 摘要或 JSON |
| 调度信息 | 选中节点并切换到该标签页时调用 `GET /api/scheduler/task/detail/{id}` 补齐节点调度字段 | 可编辑 `pluginId`、`depEventIds`、`eventId`、`enabled`、`taskParam.vars` |

`ParamData` 只承载调度变量集合：

- `flowParam.vars` 表示流程级调度运行时变量输入和表达式替换配置。
- `taskParam.vars` 表示任务级调度运行时变量输入和表达式替换配置。
- `definition` 表示任务定义本体。

`taskParam.vars` 第一版使用 JSON 编辑器承载，保存时保持 `ParamData.vars` 结构并只替换 `vars` 部分。后续如果变量结构稳定，可以升级为可增删行的表格编辑。

调度信息在流程编排页支持编辑。`pluginId` 通过 `/api/system/plugin/list` 加载插件配置选项，`depEventIds/eventId` 通过 `/api/scheduler/event/list` 加载事件选项，保存时复用 `POST /api/scheduler/task/update` 提交节点调度字段。已发布或已启用流程仍只能查看，不允许编辑调度信息。

### 7.4 同步状态警示

节点和任务池需要展示 `scheduler_task_info.sync_flag` 状态：

- 当 `syncFlag=false` 时，在任务名称附近展示明显的“未同步”标识，可放在任务名称后、任务名称前或节点 label 内。
- 任务池中的任务卡也展示“未同步”标识，避免用户把未同步任务拖入流程时无感知。
- 右侧【基本信息】展示同步状态。
- `syncFlag=true` 时默认不在画布节点上展示额外标签，减少 DAG 噪音。

建议使用 warning 颜色表达 `syncFlag=false`，不要使用过强的 error 样式，避免和执行失败状态混淆。

### 7.5 第一版范围

第一版前端实现范围：

- 左侧任务池查询 `isBound=false` 的任务。
- 一个搜索框同时模糊搜索任务名称和任务编码。
- 任务类型标签和 `syncFlag=false` 警示标签。
- 拖拽任务到画布生成节点。
- 节点移动、4 个 handle 连线、删除节点、删除边。
- 右侧【基本信息】和【调度信息】标签页。
- 右侧【调度信息】可编辑执行插件、依赖事件、产出事件、启用状态和 `taskParam.vars`。
- 保存 `DagSaveDto`。
- 已发布或已启用流程只读查看，不允许编辑流程基础信息或 DAG 编排。

暂不实现：

- 自动布局。
- DAG 环检测。
- 孤立节点校验。
- 发布快照对比。
- `taskParam.vars` 的表格化增删行编辑。

## 8. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户/项目/App Header: 当前链路未显式处理。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除、发布和启停依赖外层统一鉴权。

## 9. 不实现的部分

- 不在 `FlowController` 中创建流程实例或任务实例。
- 不在 `FlowController` 中直接提交 worker 任务。
- 不修改 `scheduler_flow_info`、`scheduler_task_info` 或 `scheduler_task_link` 表结构。
- 不实现 DAG 环检测、孤立节点校验、重复边校验和起止节点合法性校验。
- 不实现发布快照表；当前发布只更新 `publishState/publishVersion`。
- 不在本设计中修复 `TriggerStorageImpl` 的触发器查询字段问题，只记录风险。

## 10. 风险和建议

- `trigger_id` 在 DDL 中为 `NOT NULL`，设计已要求 `FlowInfoSaveDto.triggerId` 使用 `@NotNull`；实现时需补齐 DTO 校验，避免新增流程落到数据库层才失败。
- `flow_code` 唯一性只靠 Service 查询校验，并发新增时可能绕过；建议补数据库唯一索引。
- `flowType` 当前是字符串，保存时未校验是否能被 `FlowTypeEnum.fromString` 解析；发布或 master 装载时可能失败。
- `flowParam` 使用 `JacksonUtils.tryStr2JsonNode`，非法 JSON 的处理语义需要确认，建议保存时返回明确业务错误。
- `saveDag` 保存连线时不校验 `source/target` 是否都在 `nodes` 中，也不校验是否形成环；后续 master 运行前应补 DAG 合法性校验。
- `saveDag` 采用全量解绑和重建连线，适合第一版，但并发编辑同一流程时可能覆盖他人修改；建议后续引入版本或更新时间校验。
- `publish` 只更新流程状态和版本，没有固化 DAG 快照；任务或连线发布后继续修改时，历史版本不可追溯。
- `TriggerStorageImpl` 当前使用 `flow.getId()` 查询触发器，需确认应改为 `flow.getTriggerId()`，否则流程配置的 `triggerId` 可能不生效。
- `TriggerStorageImpl.getAllScheduledTriggerInfo` 依赖 `listAllEnabled`，发布/启停后是否需要刷新调度线程缓存，需结合 `SchedulerConfig` 和 master 生命周期补充。
- `pageFlowInfo` 未防御 `PageQuery` 为 `null` 的情况。

## 11. 验证

- 单元测试: 建议覆盖新增流程、重复 `flowCode`、修改后唯一性检查、删除已发布/已启用流程、保存 DAG 绑定任务、保存 DAG 拒绝已绑定其他流程任务、发布并启用、未发布启用失败。
- 编译命令: `mvn -DskipTests compile -pl datafusion-manager -am`
- Checkstyle: 本次只新增文档，不涉及 Java 代码。
- 手工检查: 使用 `/page`、`/list`、`/detail/{id}`、`/add`、`/update`、`/delete/{id}`、`/dag/detail/{id}`、`/dag/save`、`/publish`、`/unpublish/{id}`、`/enable/{id}`、`/disable/{id}` 验证返回和异常。
