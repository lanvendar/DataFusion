# scheduler-flow 设计文档

> 数据结构见 [scheduler-flow-data-define.md](./scheduler-flow-data-define.md)。本文只保留流程定义、DAG 编排和发布调度的行为规则。

## 定位

`scheduler-flow` 管理流程定义、任务编排和发布/调度启停。它不直接创建流程实例，也不直接提交 worker；运行实例由 `datafusion-scheduler-master` 通过 manager 的 storage 适配创建和推进。

核心链路：

```text
FlowController -> FlowInfoService -> FlowInfoServiceImpl -> FlowInfoMapper -> scheduler_flow_info
```

流程定义承担三类职责：

| 分类 | 落点 | 说明 |
|------|------|------|
| 基础定义 | `scheduler_flow_info` | 流程名称、编码、类型、变量、触发器、事件依赖 |
| DAG 编排 | `scheduler_task_info`、`scheduler_task_link` | 流程任务绑定、上下游依赖和画布视图 |
| 发布调度 | `publish_state`、`publish_version`、`enabled` | 控制流程是否可进入 master 调度队列 |

## 接口

API 前缀：`/api/scheduler/flow`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询流程 |
| `POST` | `/list` | 查询流程列表 |
| `GET` | `/detail/{id}` | 查询流程详情 |
| `POST` | `/add` | 新增流程 |
| `POST` | `/update` | 修改流程 |
| `POST` | `/delete/{id}` | 删除流程 |
| `GET` | `/dag/detail/{id}` | 查询流程 DAG |
| `POST` | `/dag/save` | 保存流程 DAG |
| `POST` | `/publish` | 发布流程，可勾选同时开始调度 |
| `POST` | `/unpublish/{id}` | 取消发布，并停用调度 |
| `POST` | `/enable/{id}` | 开始调度 |
| `POST` | `/disable/{id}` | 取消调度 |

流程编排任务池复用 `POST /api/scheduler/task/list`，查询未绑定任务并按 `updateTime desc` 排序。

## 业务规则

- 新增流程要求 `flowName/flowCode/flowType/triggerId` 必填，且 `flowCode` 唯一。
- 流程 ID 使用 `UUID.nameUUIDFromBytes(flowCode)` 生成，因此新增前必须先校验编码唯一。
- 新增默认 `enabled=false`、`publishState=false`、`publishVersion=0`。
- 修改、删除、保存 DAG 只允许在未发布且未调度状态下进行。
- 删除流程前需要解绑任务并删除流程连线，失败时整体回滚。
- 保存 DAG 采用全量替换：解绑旧任务，绑定新任务，重建连线。
- 任务已绑定其他流程时不能加入当前 DAG。
- 发布流程生成新的 `publishVersion`；勾选“开始调度”时同时置 `enabled=true` 并加入 master 调度。
- 取消发布时不二次确认；先取消调度，再取消发布，最终 `enabled=false`、`publishState=false`。
- 开始调度要求流程已发布且存在有效触发器。
- 取消调度只修改当前流程调度启用状态，并调用 master 停止后续调度生成。

## DAG 页面规则

- 画布使用左侧任务池、中间 DAG 画布、右侧节点面板。
- 任务池只展示未绑定任务；支持关键字按任务名称或编码查询。
- 画布支持拖拽任务、移动节点、连线、删除节点/边、缩放和保存。
- 节点坐标写入 `nodeView`，连线样式和 handle 信息写入 `edgeView`。
- 已发布或调度中的流程只能查看 DAG，不允许拖拽、连线、删除或保存。
- 右侧“调度信息”维护节点级 `pluginId`、依赖事件、生成事件开关和任务变量。

## 调度集成

- `FlowStorageImpl` 将流程定义转换为 master 的 `FlowInfo`。
- `TriggerStorageImpl` 使用流程上的 `triggerId` 读取触发器定义。
- `TaskStorageImpl` 读取流程绑定任务和依赖供 master 生成实例。
- 发布/启用/停用/取消发布必须同步 master 调度队列，数据库更新与 master 操作需要保持事务语义一致。

## 非目标

- 不在定义域创建或操作流程实例。
- 不在 DAG 页面直接执行任务。
- 不修改任务定义本体；节点配置只维护流程内调度属性。
