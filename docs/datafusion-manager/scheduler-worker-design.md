# scheduler-worker 设计文档

> 数据结构见 [scheduler-worker-data-define.md](./scheduler-worker-data-define.md)。本文描述 manager 侧执行节点管理和调度适配。

## 定位

`scheduler-worker` 在 manager 中承载执行节点注册表。页面名称使用“执行节点管理”，字段文案使用“节点编码”。调度核心仍使用 `Worker` / `WorkerManager` 作为框架模型。

核心链路：

```text
WorkerRegistryController -> WorkerRegistryService -> WorkerRegistryMapper -> scheduler_worker_registry
WorkerManager -> WorkerStorageImpl -> WorkerRegistryMapper -> scheduler_worker_registry
```

## 接口

API 前缀：`/api/scheduler/worker`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询执行节点 |
| `POST` | `/list` | 查询执行节点列表 |
| `POST` | `/add` | 新增执行节点 |
| `POST` | `/update` | 修改执行节点 |
| `GET` | `/{id}` | 查询执行节点详情 |
| `DELETE` | `/{id}` | 真删除执行节点，前端必须二次确认 |

agent 注册、心跳、下线走内部接口：`/internal/schedule/worker/*`。

## 业务规则

- `workerCode` 表示节点编码，必须唯一，`Worker.id` 由 `workerCode` 字符串二进制稳定生成 UUID。
- `host + port` 必须唯一。
- 删除执行节点为真删除记录；同一 `workerCode` 后续重新注册会生成相同 UUID 并插入新记录。
- 调度可用节点必须满足 `isActive=1`、`status=1`，并且插件能力包含任务需要的 `pluginType`。
- agent 注册只按 `workerCode` 定位旧记录；如果记录存在，不论 `isActive=1/0`，都刷新运行信息并置为上线，但不覆盖已有 `isActive`。
- agent 心跳和主动下线只按 `id` 定位节点，避免重启后错误复活其他节点。
- agent 注册可以刷新节点状态、插件能力和心跳时间；人工维护在线节点的地址或编码可能被后续注册覆盖。
- `isActive` 是人工调度开关，注册和心跳不得覆盖已有记录的 `isActive`。
- `status` 是系统运行态，不允许页面新增或修改；仅由注册、心跳、主动下线、manager 启动下线和心跳超时扫描维护。
- `WorkerHeartbeatMonitorJob` 每 `datafusion.scheduler.worker.heartbeat-check-interval-ms` 扫描在线节点，默认 30000ms；当 `lastHeartbeatTime` 早于当前时间减 `datafusion.scheduler.worker.heartbeat-timeout-ms` 时标记为下线，默认超时 180000ms。
- `workerLogDir` 是 worker 级服务日志目录，由 agent 注册时写入；UI 不修改，后续心跳不覆盖。

## 页面规则

- 菜单：调度中心 / 执行节点管理。
- 查询支持节点编码、主机名称、IP、状态、区域和有效标记。
- 表格展示节点编码、主机、地址、端口、状态、插件能力、区域、心跳时间和更新时间。
- `plugins` 第一版使用逗号分隔文本维护和展示，不拆成多选字典。
- 页面通过新增/编辑表单维护 `isActive`，用于控制节点是否可执行任务。
- 页面不允许手动修改 `status`。
- 删除操作必须二次确认，确认后真删除数据库记录。

## 调度集成

- `WorkerStorageImpl` 将数据库注册表适配为 master 的 `WorkerStorage`。
- `WorkerRpcProvider` 通过 `WorkerManager` 处理 agent 注册、心跳和下线。
- `WorkerOperator` 处理 UI 删除操作，不与 agent 生命周期接口混用。
- `WorkerRegistryService` 只服务执行节点管理页面；master 运行时生命周期不经过该 UI 服务。
- manager 启动时调用 `WorkerManager.offlineAllWorkers()` 将已有上线节点置为下线，并由运行中心跳超时扫描兜底；节点需要等待 agent 注册或心跳后再参与调度。
- agent 注册成功后按返回的 `Worker.id` 请求属于自己的未完成任务清单，再结合本地 `.snap/.state` 恢复监听；agent 不扫描全部 `taskRuntimeDir` 作为恢复来源。

## 非目标

- 不实现租户隔离、区域调度、负载权重或资源水位。
- 不主动探活、重启 agent 或下发配置。
- 不修改 `scheduler_worker_registry` DDL。
