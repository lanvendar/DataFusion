# scheduler-worker 数据结构定义

> 本文档是 WorkerRegistry 字段、类型、校验和层间映射的唯一事实源。实现不得自行增减字段或更改类型。

## 1. 表结构

### 1.1 表信息

- 表名: `scheduler_worker_registry`
- 操作: 已存在
- 主键: `id uuid`
- 候选键 / 唯一业务键: `worker_code`、`host + port`
- 分区策略: 无
- 说明: 调度 worker 注册表，记录 worker 注册状态、心跳时间、插件能力和运行元信息。

### 1.2 DDL

```sql
-- 调度 worker 信息
-- DROP TABLE scheduler_worker_registry;

CREATE TABLE scheduler_worker_registry (
id uuid NOT NULL,
worker_code varchar(128) NOT NULL, -- worker编码
host_name varchar(128) NOT NULL, -- 主机名称
host varchar(45) NOT NULL, -- IP地址
port int4 NOT NULL, -- 端口
status int4 NOT NULL, -- 状态：0-下线 1-上线
"zone" varchar(64) NULL, -- 区域/分组，预留字段
plugins varchar(256) NULL, -- 组件类型列表，逗号分隔
register_time timestamp(6) NULL, -- 注册时间
last_heartbeat_time timestamp(6) NULL, -- 最近心跳时间
log_dir text NULL, -- 日志根路径
is_active int2 NOT NULL, -- 是否有效：1-有效 0-无效
remark text NULL, -- 资源说明
creator varchar(100) NOT NULL, -- 创建人
updater varchar(100) NOT NULL, -- 修改人
create_time timestamp(6) NOT NULL, -- 创建时间
update_time timestamp(6) NOT NULL, -- 修改时间
tenant_id uuid NULL, -- 租户ID
CONSTRAINT scheduler_worker_registry_host_port_uk UNIQUE (host, port),
CONSTRAINT scheduler_worker_registry_pkey PRIMARY KEY (id),
CONSTRAINT scheduler_worker_registry_worker_code_uk UNIQUE (worker_code)
);
COMMENT ON TABLE scheduler_worker_registry IS '调度 worker 注册表，记录 worker 的注册状态、心跳时间、插件能力和运行元信息';
```

完整字段注释以 `datafusion-manager/src/main/resources/init_db/init_ddl.sql` 为准。

### 1.3 字段定义

| DB 列 | Java 字段 | Java 类型 | 必填 | 默认值 | 说明 |
|-------|-----------|-----------|------|--------|------|
| `id` | `id` | `UUID` | 是 | 由 `workerCode` 字节生成 | 主键，继承自 `BaseIdEntity`，对应 scheduler `Worker.id` 和任务链路 `workerId`；生成规则为 `UUID.nameUUIDFromBytes(workerCode.getBytes(UTF_8))` |
| `worker_code` | `workerCode` | `String` | 是 | agent 上报或人工填写 | worker 稳定编码，对应 scheduler `Worker.workerCode` |
| `host_name` | `hostName` | `String` | 是 | 无 | 主机名称 |
| `host` | `host` | `String` | 是 | 无 | IP 地址或可访问主机地址 |
| `port` | `port` | `Integer` | 是 | 无 | worker HTTP 端口 |
| `status` | `status` | `Integer` | 是 | 新增时 `0` | `0` 下线、`1` 上线；仅由系统注册、心跳、下线和超时扫描维护 |
| `zone` | `zone` | `String` | 否 | 无 | 区域/分组，预留字段 |
| `plugins` | `plugins` | `String` | 否 | 无 | 插件类型列表，逗号分隔，最大 256 字符 |
| `register_time` | `registerTime` | `Date` | 否 | agent 注册时写入 | 注册时间 |
| `last_heartbeat_time` | `lastHeartbeatTime` | `Date` | 否 | agent 心跳时写入 | 最近心跳时间 |
| `log_dir` | `workerLogDir` | `String` | 否 | agent 注册时写入 | worker 服务日志目录；用于定位 `datafusion-agent*.log` 和 `datafusion-agent.error*.log` |
| `is_active` | `isActive` | `Integer` | 是 | 新增时 `1` | `1` 有效、`0` 无效；调度查找必须过滤 `1` |
| `remark` | `remark` | `String` | 否 | 无 | 资源说明 |
| `creator` | `creator` | `String` | 是 | 当前用户或 `system` | 创建人，继承自 `BaseEntity` |
| `updater` | `updater` | `String` | 是 | 当前用户或 `system` | 修改人，继承自 `BaseEntity` |
| `create_time` | `createTime` | `Date` | 是 | 当前时间 | 创建时间，继承自 `BaseEntity` |
| `update_time` | `updateTime` | `Date` | 是 | 当前时间 | 修改时间，继承自 `BaseEntity` |
| `tenant_id` | `tenantId` | `UUID` | 否 | 无 | 租户 ID，当前不参与过滤 |

## 2. Entity / PO 映射

| Java 字段 | DB 列 | Java 类型 | 注解/处理器 | 说明 |
|-----------|-------|-----------|-------------|------|
| `id` | `id` | `UUID` | `@TableId("id")` | 来自 `BaseIdEntity` |
| `workerCode` | `worker_code` | `String` | `@TableField("worker_code")` | worker 稳定编码 |
| `hostName` | `host_name` | `String` | `@TableField("host_name")` | 主机名称 |
| `host` | `host` | `String` | `@TableField("host")` | IP 地址或可访问主机地址 |
| `port` | `port` | `Integer` | `@TableField("port")` | worker HTTP 端口 |
| `status` | `status` | `Integer` | `@TableField("status")` | worker 状态 |
| `zone` | `zone` | `String` | `@TableField("zone")` | 区域/分组 |
| `plugins` | `plugins` | `String` | `@TableField("plugins")` | 插件类型列表 |
| `registerTime` | `register_time` | `Date` | `@TableField("register_time")` | 注册时间 |
| `lastHeartbeatTime` | `last_heartbeat_time` | `Date` | `@TableField("last_heartbeat_time")` | 最近心跳时间 |
| `logDir` | `log_dir` | `String` | `@TableField("log_dir")` | worker 服务日志目录 |
| `isActive` | `is_active` | `Integer` | `@TableField("is_active")` | 是否有效 |
| `remark` | `remark` | `String` | `@TableField("remark")` | 资源说明 |
| `tenantId` | `tenant_id` | `UUID` | `@TableField("tenant_id")` | 租户 ID |
| `creator` | `creator` | `String` | 继承字段 | 创建人 |
| `updater` | `updater` | `String` | 继承字段 | 修改人 |
| `createTime` | `create_time` | `Date` | 继承字段 | 创建时间 |
| `updateTime` | `update_time` | `Date` | 继承字段 | 修改时间 |

## 3. DTO 定义

| DTO | 类型 | 使用场景 | 字段 | 字段类型 | 校验/查询方式 | 说明 |
|-----|------|----------|------|----------|---------------|------|
| `WorkerRegistryQueryDto` | `Query` | 分页和列表查询 | `workerCode` | `String` | `like` | 节点编码 |
| `WorkerRegistryQueryDto` | `Query` | 分页和列表查询 | `hostName` | `String` | `like` | 主机名称 |
| `WorkerRegistryQueryDto` | `Query` | 分页和列表查询 | `host` | `String` | `like` | IP 地址 |
| `WorkerRegistryQueryDto` | `Query` | 分页和列表查询 | `status` | `Integer` | `eq` | worker 状态 |
| `WorkerRegistryQueryDto` | `Query` | 分页和列表查询 | `zone` | `String` | `eq` | 区域/分组 |
| `WorkerRegistryQueryDto` | `Query` | 分页和列表查询 | `isActive` | `Integer` | `eq` | 是否有效 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `workerCode` | `String` | `@NotBlank` | 节点编码 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `hostName` | `String` | `@NotBlank` | 主机名称 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `host` | `String` | `@NotBlank` | IP 地址 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `port` | `Integer` | `@NotNull`，大于 0 | 端口 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `zone` | `String` | 可空 | 区域/分组 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `plugins` | `String` | 长度不超过 256 | 插件类型列表 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `isActive` | `Integer` | 可空，默认 `1` | 是否有效 |
| `WorkerRegistrySaveDto` | `Request` | 新增 worker | `remark` | `String` | 可空 | 资源说明 |
| `WorkerRegistryUpdateDto` | `Request` | 修改 worker | `id` | `UUID` | `@NotNull` | worker 注册记录 ID |
| `WorkerRegistryUpdateDto` | `Request` | 修改 worker | `zone` | `String` | 非 `null` 时合并 | 区域/分组 |
| `WorkerRegistryUpdateDto` | `Request` | 修改 worker | `remark` | `String` | 非 `null` 时合并 | 资源说明 |
| `WorkerRegistryActiveDto` | `Request` | 启用或禁用 worker | `id` | `UUID` | `@NotNull` | worker 注册记录 ID |
| `WorkerRegistryActiveDto` | `Request` | 启用或禁用 worker | `isActive` | `Integer` | `@NotNull`，只允许 `0/1` | 人工调度开关 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `id` | `UUID` | 无 | 主键 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `workerCode` | `String` | 无 | 节点编码 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `hostName` | `String` | 无 | 主机名称 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `host` | `String` | 无 | IP 地址 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `port` | `Integer` | 无 | 端口 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `status` | `Integer` | 无 | worker 状态 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `zone` | `String` | 无 | 区域/分组 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `plugins` | `String` | 无 | 插件类型列表 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `registerTime` | `Date` | 无 | 注册时间 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `lastHeartbeatTime` | `Date` | 无 | 最近心跳时间 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `workerLogDir` | `String` | 无 | worker 服务日志目录 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `isActive` | `Integer` | 无 | 是否有效 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `remark` | `String` | 无 | 资源说明 |
| `WorkerRegistryDto` | `Response` | 查询响应 | `creator/updater/createTime/updateTime` | `String/Date` | 无 | 审计字段 |

## 4. API 数据映射

| API | 请求对象 | 响应 data | 响应包装 | 说明 |
|-----|----------|-----------|----------|------|
| `POST /api/scheduler/worker/page` | `PageQuery<WorkerRegistryQueryDto>` | `PageResponse<WorkerRegistryDto>` | `Result<T>` | 分页查询 worker |
| `POST /api/scheduler/worker/list` | `WorkerRegistryQueryDto` | `List<WorkerRegistryDto>` | `Result<T>` | 列表查询 worker |
| `POST /api/scheduler/worker/add` | `WorkerRegistrySaveDto` | `UUID` | `Result<T>` | 新增 worker |
| `POST /api/scheduler/worker/update` | `WorkerRegistryUpdateDto` | `Boolean` | `Result<T>` | 修改区域和备注 |
| `POST /api/scheduler/worker/active` | `WorkerRegistryActiveDto` | `Boolean` | `Result<T>` | 启用或禁用 worker 调度 |
| `GET /api/scheduler/worker/{id}` | path `UUID id` | `WorkerRegistryDto` | `Result<T>` | 查询详情 |
| `DELETE /api/scheduler/worker/{id}` | path `UUID id` | `Boolean` | `Result<T>` | 真删除 worker，前端必须二次确认 |

## 5. 层间转换规则

| 方向 | 转换规则 | 特殊处理 |
|------|----------|----------|
| `WorkerRegistrySaveDto` -> `WorkerRegistryEntity` | 复制主机、端口、分组、插件、有效标记和备注 | `id` 使用 `workerCode` 生成稳定 UUID；`status` 默认 `0`；`isActive` 默认 `1`；不接收 `workerLogDir`；审计字段由 Service 设置 |
| `WorkerRegistryUpdateDto` -> existing `WorkerRegistryEntity` | 仅合并 `zone` 和 `remark` | 注册信息由 agent 维护；不接收 `workerCode`、主机、端口、插件、`status`、`isActive` 和 `workerLogDir` 修改 |
| `WorkerRegistryActiveDto` -> `WorkerOperator` | `isActive=1` 调用 `active`，`isActive=0` 调用 `inactive` | 只修改人工调度开关，不改变 agent 在线状态 |
| `WorkerRegistryService.delete` | 按 `id` 真删除记录 | 删除后同一 `workerCode` 再次注册会按稳定 UUID 重新插入记录 |
| `WorkerRegistryEntity` -> `WorkerRegistryDto` | 字段逐一复制 | 不转换 `status/isActive`，由前端映射展示 |
| `WorkerRegistryDto` -> 前端表格 | `registerTime`、`lastHeartbeatTime`、`updateTime` 按本地时区格式化为 `YYYY-MM-DD HH:mm:ss` | 后端可返回带 offset 的 ISO 时间字符串，前端不原样展示 |
| `WorkerRegistryQueryDto` -> `LambdaQueryWrapper` | 字符串字段按定义 `like/eq`；数值字段 `eq` | 默认 `updateTime desc` |
| `WorkerRegistryEntity` -> scheduler `Worker` | `id` -> `id`，`workerCode` -> `workerCode`，`host` -> `ip`，`plugins` 逗号拆分为 `pluginTypes`，`logDir` -> `workerLogDir` | `timestamp(6)` 转毫秒时间戳 |
| scheduler `Worker` -> `WorkerRegistryEntity` | `id` 由 `workerCode` 稳定生成，`workerCode` -> `workerCode`，`ip` -> `host`，`pluginTypes` -> `plugins` 逗号字符串，首次非空 `workerLogDir` -> `logDir` | 注册由 `WorkerStorageImpl` 按 `workerCode` upsert；注册和心跳都会置 `status=1`；已有记录保留 `isActive`；`logDir` 已存在时不被覆盖 |
| `WorkerStorage.getWorker` | `workerId` 只查 `id` | scheduler 框架层不按 `workerCode` 查询 |
| `WorkerStorage.getTaskInsByWorkerId` | 按 `workerId` 查询属于该 worker 的未完成任务清单 | 供 agent 注册成功后恢复本 worker 任务状态，不扫描全部任务运行目录 |

## 6. 枚举 / 特殊字段

| 字段 | 存储类型 | Java 类型 | 取值 | 说明 |
|------|----------|-----------|------|------|
| `status` | `int4` | `Integer` | `0` 下线、`1` 上线 | 对应 `Worker.STATUS_DOWN/STATUS_UP` |
| `is_active` | `int2` | `Integer` | `0` 无效、`1` 有效 | 调度查找必须过滤 `1` |
| `plugins` | `varchar(256)` | `String` | 逗号分隔字符串 | 新增时可录入；注册后由 agent 维护，前端编辑时只读 |
| `log_dir` | `text` | `String` | 目录路径 | worker 服务日志目录，不指向单个滚动日志文件 |
| `tenant_id` | `uuid` | `UUID` | 无 | 暂无租户功能，不参与 API 维护和查询过滤 |

## 7. 复用对象

| 对象 | 路径 | 复用方式 | 说明 |
|------|------|----------|------|
| `Result<T>` | `datafusion-common-spring` | Controller 响应包装 | 统一 API 返回 |
| `PageQuery<T>` | `datafusion-common-spring` | 分页请求 | `/page` 入参 |
| `PageResponse<T>` | `datafusion-common-spring` | 分页响应 | `/page` 出参 |
| `BaseIdEntity` | `datafusion-common-spring` | Entity 基类 | 提供 `id` |
| `BaseEntity` | `datafusion-common-spring` | Entity 基类 | 提供审计字段 |
| `CommonException` | `datafusion-common` | 业务异常 | worker 不存在、唯一键冲突、状态非法 |
| `ErrorCodeEnum` | `datafusion-common` | 错误码 | 当前使用 `SERVICE_ERROR_C0300` |
| `Worker` | `datafusion-common-data` | 调度 worker 模型 | worker 注册、心跳和调度查找 |
| `WorkerStorage` | `datafusion-scheduler-master` | 调度核心存储接口 | `WorkerStorageImpl` 适配 manager 表 |
