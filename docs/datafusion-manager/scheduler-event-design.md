# scheduler-event 设计文档

> 数据结构见 [scheduler-event-data-define.md](./scheduler-event-data-define.md)。本文只说明事件定义域和 master 事件存储适配。

## 定位

`scheduler-event` 管理流程、任务可产生或依赖的调度事件；运行期事件实例由 `EventStorageImpl` 适配 master 的 `EventStorage` 后写入 `scheduler_event_instance`。

核心链路：

```text
EventController -> EventInfoService -> EventInfoServiceImpl -> EventInfoMapper -> scheduler_event_info
MasterStorage -> EventStorageImpl -> EventInstanceService -> scheduler_event_instance
```

## 接口

API 前缀：`/api/scheduler/event`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询事件 |
| `POST` | `/list` | 查询事件列表 |
| `POST` | `/add` | 新增事件 |
| `POST` | `/update` | 修改事件 |
| `GET` | `/detail/{id}` | 查询事件详情 |
| `POST` | `/delete/{id}` | 删除事件 |

事件实例查询在实例域提供：`/api/scheduler/event/instance`。

## 业务规则

- `eventName`、`eventType` 必填。
- `eventType="1"` 表示任务事件，必须关联 `taskId`。
- `eventType="2"` 表示流程事件，必须关联 `flowId`。
- 修改事件时先查询旧实体，再合并非空字段，并校验合并后的关联关系。
- 删除事件前必须检查流程和任务的产出事件、依赖事件引用。
- `dep_event_ids` 先用 `LIKE` 预筛选，再按英文逗号拆分精确确认，避免 UUID 子串误判。

## 调度集成

- `GlobalEventOperator` 在流程或任务成功后产生全局事件。
- `EventStorageImpl` 将 `GlobalEvent` 持久化为事件实例，并供等待依赖的任务查询。
- `CachedEventStorage` 可缓存 `loadByEventKey` 结果，但数据库仍是运行期事实来源。

## 风险

- `eventType` 当前是字符串编码，应继续收敛到枚举校验。
- 新增/修改只校验关联 ID 是否传入，是否校验实体真实存在由后续业务规则补齐。
- `eventName` 是否唯一未形成数据库约束。
- 事件实例字段与 `EventStorageImpl` 的转换需保持一致，避免运行期保存失败。
