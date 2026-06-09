# scheduler-worker 设计文档

> 数据结构见 [scheduler-worker-data-define.md](./scheduler-worker-data-define.md)。本文描述 manager 侧执行节点管理和调度适配。

## 定位

`scheduler-worker` 在 manager 中承载执行节点注册表。页面名称使用“执行节点管理”，字段文案使用“节点编码”。调度核心仍使用 `Worker` / `WorkerManager` 作为框架模型。

核心链路：

```text
WorkerRegistryController -> WorkerRegistryService -> WorkerRegistryMapper -> scheduler_worker_registry
WorkerManager -> WorkerStorageImpl -> WorkerRegistryService
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
| `DELETE` | `/{id}` | 逻辑删除执行节点 |

agent 注册、心跳、下线走内部接口：`/internal/schedule/worker/*`。

## 业务规则

- `workerCode` 表示节点编码，必须唯一。
- `host + port` 必须唯一。
- 删除执行节点为逻辑删除：`status=2`、`isActive=0`。
- 调度可用节点必须满足 `isActive=1`、`status=1`，并且插件能力包含任务需要的 `pluginType`。
- agent 注册/心跳定位旧记录时优先使用 `workerCode`，找不到再按 `host + port` 回退。
- agent 心跳可以刷新节点状态、插件能力和心跳时间；人工维护在线节点的地址或编码可能被后续心跳覆盖。

## 页面规则

- 菜单：调度中心 / 执行节点管理。
- 查询支持节点编码、主机名称、IP、状态、区域和有效标记。
- 表格展示节点编码、主机、地址、端口、状态、插件能力、区域、心跳时间和更新时间。
- `plugins` 第一版使用逗号分隔文本维护和展示，不拆成多选字典。

## 调度集成

- `WorkerStorageImpl` 将数据库注册表适配为 master 的 `WorkerStorage`。
- `WorkerRpcProvider` 通过 `WorkerManager` 处理 agent 注册、心跳和下线。
- manager 重启后从数据库恢复有效节点，但节点需要等待 agent 新心跳后再参与调度。

## 非目标

- 不实现租户隔离、区域调度、负载权重或资源水位。
- 不主动探活、重启 agent 或下发配置。
- 不修改 `scheduler_worker_registry` DDL。
