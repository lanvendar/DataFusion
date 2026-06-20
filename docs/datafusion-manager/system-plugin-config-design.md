# system-plugin-config 设计文档

> 数据结构见 [system-plugin-config-data-define.md](./system-plugin-config-data-define.md)。本文描述插件配置和任务类型默认插件绑定。

## 定位

本模块只管理插件配置和任务类型到默认插件的绑定，不负责插件 jar 上传、动态加载、代码执行、插件市场或版本发布。

核心链路：

```text
PluginConfigController -> PluginConfigService -> PluginConfigMapper -> system_plugin_config
TaskTypeConfigController -> TaskTypeConfigService -> TaskTypeConfigMapper -> system_task_type_config
```

## 接口

插件配置 API 前缀：`/api/system/plugin`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询插件配置 |
| `POST` | `/list` | 查询插件配置列表 |
| `POST` | `/add` | 新增插件配置 |
| `POST` | `/copy` | 复制插件配置 |
| `POST` | `/update` | 修改插件配置 |
| `GET` | `/{id}` | 查询插件配置详情 |
| `DELETE` | `/{id}` | 软删除插件配置 |

任务类型配置 API 前缀：`/api/system/task-type`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询任务类型配置 |
| `POST` | `/list` | 查询任务类型配置列表 |
| `POST` | `/add` | 新增任务类型配置 |
| `POST` | `/update` | 修改默认插件绑定 |
| `GET` | `/{id}` | 查询任务类型详情 |
| `DELETE` | `/{id}` | 删除任务类型配置 |

## 字段边界

| 对象 | 关键字段 | 规则 |
|------|----------|------|
| 插件配置 | `pluginName`、`pluginType`、`runMode`、`description`、`pluginParam`、`isTemplate` | 描述插件身份、运行模式和配置模板；接口不修改 `isTemplate` |
| 任务类型 | `taskType`、`defaultPluginId`、`pluginType` | 维护任务类型和默认执行插件绑定；`taskType` 新增后不修改 |
| 租户 | `tenantId` | 当前使用固定默认租户，后续接入正式租户上下文 |

`runMode` 是 `pluginType` 的第二维，例如 `FLINK + YARN`、`FLINK + K8S` 可对应不同配置结构。

## 业务规则

- 插件配置新增时，`pluginName`、`pluginType` 必填；同租户下 `pluginType + runMode + pluginName` 应唯一。
- 新增和复制出的插件配置 `isTemplate=false`。
- 复制接口由前端提交当前行配置，后端生成新名称和新主键。
- 修改插件配置时不允许修改 `tenantId`、`isDel`、`creator`、`createTime`、`isTemplate`。
- 删除插件配置为软删除：`isDel=1`。
- 任务类型新增时，`taskType` 标准化为大写，且同租户唯一。
- 任务类型 `id` 由标准化后的 `taskType` 稳定生成。
- 任务类型修改只维护 `defaultPluginId` 和 `pluginType`。
- 新增任务定义时，若请求未显式传 `pluginId`，后端按 `taskType` 查询默认插件。

## 集成点

- `TaskInfoServiceImpl` 通过任务类型配置解析默认 `pluginId`。
- 插件运行参数由 `system_plugin_config.plugin_param` 提供给调度任务的 `pluginParam`。
- 当前用户通过 `HttpUtils.getCurrentUserName()` 写入审计字段。

## 初始化数据

`init_data.sql` 初始化三条插件配置模板：

| 插件配置 | 运行组合 | 说明 |
|----------|----------|------|
| `DataX LOCAL 模板` | `DATAX + LOCAL` | 作为 `DATAX` 任务类型默认插件 |
| `DataX K8S 模板` | `DATAX + K8S` | 仅作为模板初始化；提交前需要补充 `kubernetes.image` |
| `Shell LOCAL 模板` | `SHELL + LOCAL` | 作为 `SHELL` 任务类型默认插件 |

`system_task_type_config` 只初始化当前 agent 已实现执行器的默认绑定：

| 任务类型 | 默认插件 |
|----------|----------|
| `DATAX` | `DataX LOCAL 模板` |
| `SHELL` | `Shell LOCAL 模板` |

不初始化 `FLINK`、`SPARK`、`API` 或旧数据集成枚举中的非 agent 执行器类型，避免任务定义能创建但 worker 无法路由执行。

## 非目标

- 不实现插件上传、热加载、签名校验或运行器生命周期。
- 不实现插件版本、灰度、回滚和跨租户共享。
- 不把 `system_plugin_config` 当作运行时任务实例表。
- 不提供任务类型软删除，当前表没有删除状态字段。

## 风险

- 当前固定默认租户只是本地调试兼容方案，正式多租户接入时需要替换。
- `plugin_param` 是宽松 JSONB，必须由插件协议约定结构，否则会出现能存不能用。
- 唯一性主要靠 Service 层校验，并发写入仍建议补数据库约束。
