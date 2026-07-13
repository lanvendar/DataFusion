# task-definition-register 设计文档

> 数据结构见 [task-definition-register-data-define.md](./task-definition-register-data-define.md)。

## 1. 职责

任务定义登记负责维护业务任务与调度任务的映射、插件执行定义和同步状态，不参与流程编排与运行期提交。

| 接口 | 行为 |
|------|------|
| `POST /api/scheduler/task-definition/register` | 按业务身份新增或更新任务，`sync_flag=true` |
| `POST /api/scheduler/task-definition/mark-unsynced` | 更新业务定位并标记未同步，`sync_flag=false` |

## 2. 数据分层

- `source_route` 保存业务原始唯一定位，Manager 负责结构化对象与协议串的转换。
- `definition` 只保存插件执行参数，顶层不得包含 `bizRef/sourceRoute`。
- `TaskRequest` 不增加 `sourceRoute`，Agent 和插件不处理业务定位信息。
- 复制任务不继承 `source_route`，并将 `sync_flag` 置为 `false`，避免副本占用原业务身份。

## 3. 登记流程

```text
TaskDefinitionRegisterController
  -> 接收 BusinessSourceRoute 和插件执行 definition
  -> 按 bizSystem + bizKey 查询 scheduler_task_info
  -> 不存在：创建任务并解析默认 pluginId
  -> 已存在：校验流程未发布并更新定义
  -> 保存规范化 source_route，sync_flag=true
```

未传 `taskCode` 时，Manager 使用 `bizSystem + bizKey` 生成稳定编码。`bizVersion` 变化只更新同一任务，
不会创建新任务。

`register` 只维护任务定义属性，不修改 `pluginId`、`flowId`、`view`、事件依赖和 `enabled`。

## 4. 未同步流程

业务定义发生变化但尚未重新登记时，业务服务调用 `mark-unsynced`：

1. 按 `bizSystem + bizKey` 查找任务。
2. 保存最新 `bizVersion/bizUrl`。
3. 将 `sync_flag` 置为 `false`。

该接口直接接收 `BusinessSourceRoute`，不保留单独的 `reason` 或旧 `bizRef` DTO。

## 5. 编辑保护

已发布流程中的任务禁止通过 `register` 更新。`mark-unsynced` 只标记业务源变化，不修改运行定义，
因此不受发布状态限制。

## 6. 运行期边界

任务实例初始化读取 `scheduler_task_info.definition` 作为运行数据，Manager 按现有协议构造 `TaskRequest`。
各插件 `TaskDefinitionRegistrar` 按 `TaskDefinitionRegisterDto` 重新构建登记请求，业务定位只写入
`sourceRoute`，不写入 `definition`；Agent 不增加运行期兼容过滤。

## 7. 上线迁移

代码部署前完成一次性数据迁移：

1. 将旧 `definition.bizRef` 映射为新 `source_route`。
2. 从 `definition` 移除顶层 `bizRef/sourceRoute`。
3. 清理复制任务形成的来源追踪 JSON 和重复业务身份。

迁移完成后再发布新代码；新实现不解析旧 `bizRef` 协议。
