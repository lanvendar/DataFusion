# scheduler-trigger 设计文档

> 数据结构见 [scheduler-trigger-data-define.md](./scheduler-trigger-data-define.md)。本文只说明触发器定义域的接口、规则和调度边界。

## 定位

`scheduler-trigger` 维护触发器定义。触发器本身不直接启动流程，只有被已发布且已启用的流程引用后，才会通过 `TriggerStorageImpl` 转换为 master 的 `TriggerInfo` 并进入调度队列。

核心链路：

```text
TriggerController -> TriggerInfoService -> TriggerInfoServiceImpl -> TriggerInfoMapper -> scheduler_trigger_info
```

## 接口

API 前缀：`/api/scheduler/trigger`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询触发器 |
| `POST` | `/list` | 查询触发器列表 |
| `POST` | `/add` | 新增触发器 |
| `POST` | `/update` | 修改触发器 |
| `POST` | `/cron/preview` | 预览 Java cron 后续运行时间 |
| `GET` | `/{id}` | 查询触发器详情 |
| `DELETE` | `/{id}` | 删除触发器 |

## 业务规则

- `name` 在 Service 层校验唯一。
- `type`、`policy` 由枚举 name 解析，DB 仍保存 ordinal 字符串。
- `CRON` 类型必须填写 `cron`。
- 前端在 `type=CRON` 时显示“运行查看”，调用 Java cron 预览接口展示后续运行时间；切换为 `INTERVAL` 时隐藏。
- `INTERVAL` 类型必须填写大于 0 的 `interval`；manager API 中单位为分钟，进入 master 前转换为毫秒。
- 修改触发器时先查询旧实体，再合并非空字段，并校验合并后的类型参数。
- 删除触发器前必须检查流程引用。
- 被已发布或调度中的流程引用时，后端应拒绝修改触发器调度语义，前端只负责收口入口。

## 调度集成

- `TriggerStorageImpl` 将 `scheduler_trigger_info` 和引用它的流程定义转换为 master `TriggerInfo`。
- 流程发布并勾选开始调度时，流程发布和调度启用一起完成。
- 单独启用调度时，后端更新流程启用状态后调用 `MasterService.addSchedule`。
- 停用调度时，后端更新流程启用状态后调用 `MasterService.stopSchedule`。

## 当前约束

- `type/policy` 使用 ordinal 存储，枚举顺序变化会影响历史数据语义。
- `CRON` 保存目前只做非空校验；“运行查看”使用 Java cron 解析器提前暴露明显语法错误。
- `name` 唯一性只靠 Service 层校验，当前 DDL 没有数据库唯一约束。
