# system-variable 设计文档

> 数据结构见 [system-variable-data-define.md](./system-variable-data-define.md)。本文只说明系统变量配置的接口和业务规则。

## 定位

`system-variable` 维护系统变量配置。当前只提供配置 CRUD，不实现变量解析、表达式求值或任务引用检查。

核心链路：

```text
VariableController -> VariableInfoService -> VariableInfoServiceImpl -> VariableInfoMapper -> system_variable_info
```

## 接口

API 前缀：`/api/system/variable`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page` | 分页查询变量 |
| `POST` | `/list` | 查询变量列表 |
| `POST` | `/add` | 新增变量，服务端固定创建 `CUSTOM` |
| `POST` | `/update` | 修改变量 |
| `GET` | `/{id}` | 查询变量详情 |
| `DELETE` | `/{id}` | 删除变量 |

## 业务规则

- 查询支持 `name/code` 模糊匹配，`type/valueType` 精确匹配。
- 新增变量时 `code` 全局唯一，`type` 固定为 `CUSTOM`。
- `SYSTEM` 变量只允许修改 `value`，不允许删除。
- `CUSTOM` 变量支持合并修改非空字段；修改 `code` 时重新校验唯一。
- 删除变量当前不检查任务、脚本或其他配置引用。
- 当前用户通过 `HttpUtils.getCurrentUserName()` 写入审计字段。

## 风险

- `code` 唯一性只在 Service 层校验，并发写入建议补数据库唯一约束。
- `id` 由 `code` 稳定生成，修改 `code` 后 `id` 不再可由新编码推导。
- `type/valueType` 是字符串，后续应收敛为枚举和白名单校验。
