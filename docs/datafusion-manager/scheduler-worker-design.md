# scheduler-worker 设计文档

> 数据结构定义见 [scheduler-worker-data-define.md](./scheduler-worker-data-define.md)，本文档不重复字段定义。

## 1. 能力说明

- 能力: 调度 worker 注册信息的分页查询、列表查询、新增、修改、详情查询和逻辑删除。
- 所属模块: `datafusion-manager`、`datafusion-web`
- 后端包路径: `com.datafusion.manager.scheduler`
- 前端模块路径: `datafusion-web/src/modules/scheduler-worker`
- API 路径前缀: `/api/scheduler/worker`
- 调用链: `WorkerRegistryController` -> `WorkerRegistryService` -> `WorkerRegistryServiceImpl` -> `WorkerRegistryMapper` -> `scheduler_worker_registry`
- 调度适配链路: `WorkerManager` -> `WorkerStorage` -> `WorkerStorageImpl` -> `WorkerRegistryService`

## 2. 接口契约

| HTTP 方法 | 路径 | 请求 | 响应 | 说明 |
|-----------|------|------|------|------|
| `POST` | `/page` | `PageQuery<WorkerRegistryQueryDto>` | `Result<PageResponse<WorkerRegistryDto>>` | 分页查询 worker |
| `POST` | `/list` | `WorkerRegistryQueryDto` | `Result<List<WorkerRegistryDto>>` | 查询 worker 列表 |
| `POST` | `/add` | `WorkerRegistrySaveDto` | `Result<UUID>` | 新增 worker |
| `POST` | `/update` | `WorkerRegistryUpdateDto` | `Result<Boolean>` | 修改 worker |
| `GET` | `/{id}` | path `UUID id` | `Result<WorkerRegistryDto>` | 根据 ID 查询 worker |
| `DELETE` | `/{id}` | path `UUID id` | `Result<Boolean>` | 逻辑删除 worker |

## 3. 文件变更

### 3.1 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-manager/scheduler-worker-data-define.md` | WorkerRegistry 字段、类型、校验和层间映射定义 |
| `docs/datafusion-manager/scheduler-worker-design.md` | WorkerRegistry API、Service、页面和调度适配设计 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/controller/WorkerRegistryController.java` | WorkerRegistry HTTP 入口 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/WorkerRegistryService.java` | WorkerRegistry Service 契约 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/service/impl/WorkerRegistryServiceImpl.java` | WorkerRegistry 业务实现 |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/WorkerRegistryDto.java` | 查询响应 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/WorkerRegistryQueryDto.java` | 查询条件 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/WorkerRegistrySaveDto.java` | 新增请求 DTO |
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dto/WorkerRegistryUpdateDto.java` | 修改请求 DTO |
| `datafusion-web/src/modules/scheduler-worker/**` | WorkerRegistry 查询维护页面 |

### 3.2 修改

| 文件 | 说明 |
|------|------|
| `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/storage/WorkerStorageImpl.java` | 改为通过 `WorkerRegistryService` 适配调度核心 |
| `datafusion-manager/src/main/java/com/datafusion/manager/config/SchedulerMasterConfig.java` | 注入 `WorkerRegistryService` 创建 `WorkerStorageImpl` |
| `datafusion-web/src/router/routes.tsx` | 调度中心新增“Worker 管理”菜单入口 |

### 3.3 复用

| 对象 | 路径 | 说明 |
|------|------|------|
| `WorkerRegistryEntity` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/po/WorkerRegistryEntity.java` | 表实体 |
| `WorkerRegistryMapper` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/dao/WorkerRegistryMapper.java` | MyBatis-Plus Mapper |
| `WorkerRpcProvider` | `datafusion-manager/src/main/java/com/datafusion/manager/scheduler/rpc/WorkerRpcProvider.java` | agent 注册、心跳、下线入口 |
| `WorkerManager` | `datafusion-scheduler-master` | 调度核心 worker 管理器 |
| `Worker` | `datafusion-common-data` | 调度 worker 模型 |

## 4. Service 设计

| 方法 | 入参 | 出参 | 说明 |
|------|------|------|------|
| `pageWorkerRegistry` | `PageQuery<WorkerRegistryQueryDto>` | `PageResponse<WorkerRegistryDto>` | 按条件分页查询，按 `updateTime desc` 排序 |
| `listWorkerRegistry` | `WorkerRegistryQueryDto` | `List<WorkerRegistryDto>` | 按条件查询全量列表 |
| `getWorkerRegistryById` | `UUID id` | `WorkerRegistryDto` | 查询详情，不存在时抛业务异常 |
| `addWorkerRegistry` | `WorkerRegistrySaveDto` | `UUID` | 新增 worker 记录，校验唯一键和字段值 |
| `updateWorkerRegistry` | `WorkerRegistryUpdateDto` | `boolean` | 修改 worker 记录，合并后重新校验 |
| `deleteWorkerRegistry` | `UUID id` | `boolean` | 逻辑删除，设置 `status=2`、`isActive=0` |
| `getSchedulableWorkerByCode` | `String workerCode` | `WorkerRegistryEntity` | 查询 `isActive=1` 且 `status=1` 的可调度 worker |
| `getSchedulableWorkerByHostAndPort` | `String host, Integer port` | `WorkerRegistryEntity` | 查询 `isActive=1` 且 `status=1` 的可调度 worker |
| `listSchedulableWorkers` | 无 | `List<WorkerRegistryEntity>` | 查询调度可用 worker |
| `findForHeartbeat` | `String workerCode, String host, Integer port` | `WorkerRegistryEntity` | agent 注册/心跳定位旧记录，优先 `workerCode`，回退 `host + port` |
| `saveOrUpdateFromWorker` | `Worker worker` | `void` | 将 scheduler `Worker` upsert 到 DB |

### 4.1 业务规则

| 场景 | 规则 | 异常/返回 |
|------|------|-----------|
| 分页查询 | `workerCode/hostName/host` 模糊匹配，`status/zone/isActive` 精确匹配 | 返回 `PageResponse<WorkerRegistryDto>` |
| 列表查询 | 与分页查询条件一致，不分页 | 返回 `List<WorkerRegistryDto>` |
| 查询详情 | 根据 `id` 查询 | 不存在时抛 `CommonException(SERVICE_ERROR_C0300, "worker不存在")` |
| 新增 worker | `workerCode` 唯一，`host + port` 唯一 | 冲突时抛业务异常 |
| 新增默认值 | `status` 为空时写 `0`，`isActive` 为空时写 `1` | 返回新增 ID |
| 修改 worker | 先查询旧实体，再合并字段，合并后校验唯一键和值域 | 不存在、唯一键冲突或值非法时抛业务异常 |
| 删除 worker | 逻辑删除，不物理删除 | 设置 `status=2`、`isActive=0`，刷新审计字段 |
| 调度查找 worker | 必须满足 `isActive=1` 且 `status=1` | 不满足时调度核心视为不可用 |
| agent 注册/心跳 | 定位已有记录不要求 `status=1`，优先 `workerCode`，回退 `host + port` | 找不到则新增 |

### 4.2 事务边界

- 写操作使用 `@Transactional(rollbackFor = Exception.class)`。
- 事务方法: `addWorkerRegistry`、`updateWorkerRegistry`、`deleteWorkerRegistry`、`saveOrUpdateFromWorker`。
- 回滚条件: `Exception`。

## 5. Mapper / SQL

| 方法 | 类型 | SQL 来源 | 说明 |
|------|------|----------|------|
| `selectPage` | 查询 | MyBatis-Plus `BaseMapper` | 分页查询 |
| `selectList` | 查询 | MyBatis-Plus `BaseMapper` | 列表查询、调度查询 |
| `selectById` / `getById` | 查询 | MyBatis-Plus `ServiceImpl` | 详情、修改、删除前检查 |
| `selectOne` | 查询 | MyBatis-Plus `BaseMapper` | 唯一键查询、心跳定位 |
| `selectCount` | 查询 | MyBatis-Plus `BaseMapper` | 唯一性校验 |
| `insert` / `save` | 写入 | MyBatis-Plus `ServiceImpl` | 新增 worker |
| `updateById` | 写入 | MyBatis-Plus `ServiceImpl` | 修改、逻辑删除、心跳 upsert |

不新增 XML SQL。

## 6. 前端设计

### 6.1 页面入口

- 路由: `/scheduler-worker`
- 菜单: 调度中心 / Worker 管理
- 页面模块: `datafusion-web/src/modules/scheduler-worker`

### 6.2 页面能力

| 区域 | 能力 |
|------|------|
| 查询区 | 按 worker 编码、主机名称、IP、状态、区域、有效标记筛选 |
| 表格 | 展示 worker 编码、主机、地址、端口、状态、有效标记、插件、区域、心跳时间、更新时间 |
| 操作列 | 编辑、删除 |
| 新增按钮 | 打开 Drawer 新增 worker |
| 编辑 Drawer | 修改 worker 元信息和有效标记 |
| 删除确认 | 二次确认后调用逻辑删除 API |

### 6.3 前端数据处理

- API client 前缀为 `/api/scheduler/worker`。
- `status` 显示为 Tag: `0` 下线、`1` 上线、`2` 清除。
- `isActive` 显示为 Tag: `1` 有效、`0` 无效。
- `plugins` 以逗号分隔文本维护和展示，不在前端拆数组。
- 开发环境接口失败时使用 demo 数据，保持现有模块体验一致。

## 7. 集成关系

| 集成对象 | 方式 | 说明 |
|----------|------|------|
| MyBatis-Plus | `BaseMapper` / `ServiceImpl` | 提供 CRUD、分页和条件查询 |
| `HttpUtils` | 静态工具调用 | 获取当前用户名，写入 `creator` 和 `updater` |
| `WorkerStorageImpl` | Service 注入 | 将 manager 表数据适配为 scheduler `WorkerStorage` |
| `CachedWorkerStorage` | 外层包装 | 保持 scheduler master 原缓存能力 |
| `WorkerRpcProvider` | 调用 `WorkerManager` | agent 注册、心跳、下线通过 `WorkerManager` 间接写入 registry |

## 8. 安全和上下文

- 当前用户: `HttpUtils.getCurrentUserName()`。
- 租户/项目/App Header: 第一版不做租户隔离，`tenant_id` 不由 API 维护。
- 密码/Token 处理: 无。
- 权限边界: Controller 未显式声明权限注解；是否可新增、修改、删除依赖外层统一鉴权。

## 9. 不实现的部分

- 不实现租户隔离和区域调度策略。
- 不实现 worker 运行任务数、资源水位、负载权重展示。
- 不实现对 agent 的主动探活、重启或下发配置。
- 不在前端拆分插件数组选择器，第一版使用逗号分隔文本。
- 不修改 `scheduler_worker_registry` DDL。

## 10. 风险和建议

- `worker_code` 由 agent 上报，但 agent/worker 端的稳定 ID 生成规则仍需明确。
- 当前外层 `CachedWorkerStorage` 可能缓存已被页面改为无效的 worker，后续需要增加缓存失效或管理操作刷新机制。
- 人工编辑在线 worker 的 `host/port/workerCode` 可能被后续 agent 心跳覆盖。
- `plugins` 使用逗号字符串，缺少插件类型字典约束。

## 11. 验证

- 后端编译: `mvn -DskipTests compile -pl datafusion-manager -am`
- 前端构建: `npm run build`
- 空白检查: `git diff --check -- docs/datafusion-manager datafusion-manager/src/main/java datafusion-web/src`
- 手工接口检查: `/page`、`/list`、`/add`、`/update`、`/{id}`、`DELETE /{id}`
- 手工页面检查: 新增、编辑、删除、筛选、分页、状态显示、插件长文本展示。
