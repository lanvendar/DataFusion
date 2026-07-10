# plugin-spark-sql 数据结构定义

> 本文档是 `datafusion-plugin-spark-sql` 作业配置和运行结果的数据结构事实源。
> Agent 提交契约见 [../../datafusion-agent/plugin-spark-data-define.md](../../datafusion-agent/plugin-spark-data-define.md)。

## 1. 边界

- 不新增 DataFusion 数据库表、HTTP API 或前端模型。
- 作业配置由 Agent 写入 `spark-sql-job.json`，插件在 Spark driver 中只读加载。
- SQL 读写的 Paimon 表由业务 SQL 管理，不属于 DataFusion 持久化模型。

## 2. 作业配置

### 2.1 `SparkSqlJobConfig`

| Field | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `job` | `JobConfig` | Yes | 无 | 作业元信息 |
| `sqlTargetType` | `String` | Yes | 无 | SQL 执行对象，当前仅支持 `PAIMON` |
| `catalogName` | `String` | Yes | 无 | Spark Catalog 名称，同时限定 Paimon 配置前缀 |
| `databaseName` | `String` | Conditional | 无 | `useDatabase=true` 时必填 |
| `useDatabase` | `Boolean` | No | `true` | 是否在执行 statements 前切换 Catalog 和 Database |
| `enableHiveSupport` | `Boolean` | No | `false` | 是否调用 `SparkSession.Builder.enableHiveSupport()` |
| `enableSqlLogging` | `Boolean` | No | `true` | 是否输出待执行 SQL 文本 |
| `allowSqlFailure` | `Boolean` | No | `false` | 是否允许单条 SQL 失败后继续并保持任务成功 |
| `statements` | `List<SqlStatement>` | Yes | 无 | 按数组顺序执行的 SQL，至少一条 |
| `paimonConf` | `Map<String, String>` | Yes | 无 | Paimon 官方 Spark 配置，保留官方完整 key |
| `sparkConf` | `Map<String, String>` | No | 空对象 | Spark 官方配置 |
| `hadoopConf` | `Map<String, String>` | No | 空对象 | Hadoop 官方配置 |

### 2.2 `JobConfig`

| Field | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `id` | `String` | Yes | 无 | 作业唯一标识 |
| `name` | `String` | No | 无 | 作业名称 |
| `description` | `String` | No | 无 | 作业说明 |
| `version` | `String` | No | 无 | 业务配置版本 |

### 2.3 `SqlStatement`

| Field | Type | Required | Default | Notes |
|-------|------|----------|---------|-------|
| `comment` | `String` | No | 无 | 页面 SQL 注释或语句说明 |
| `sql` | `String` | Yes | 无 | 单条 SQL，不在插件内按分号再次切分 |

### 2.4 内部运行参数

| Constant | Value | Notes |
|----------|-------|-------|
| `SELECT_LOG_ROW_LIMIT` | `100` | 有列的 SQL 结果最多输出 100 行，不作为 JSON 字段 |

## 3. 配置映射

| Source | Target | Rule |
|--------|--------|------|
| `sparkConf` | `SparkSession.Builder` | 先写入 Spark 配置 |
| `paimonConf` | `SparkSession.Builder` | 后写入，覆盖 `sparkConf` 中的同名 key |
| `hadoopConf` | `SparkContext.hadoopConfiguration` | SparkSession 创建后逐项写入 |
| `catalogName`, `databaseName` | Spark Catalog | `useDatabase=true` 时依次设置当前 Catalog 和 Database |
| `statements[]` | `SparkSession.sql` | 按数组顺序逐条执行 |

`paimonConf` 与 `sparkConf` 存在同名 key 时输出 warn，格式为：

```text
参数[spark.sql.catalog.paimon.warehouse]失效，使用paimonConf
```

## 4. 执行结果

| Scenario | Behavior |
|----------|----------|
| SQL 无返回列 | 完成 Spark action 后记录成功 |
| SQL 有返回列 | 触发计算并在 driver 日志输出最多 100 行 |
| `allowSqlFailure=false` | 第一条失败立即抛出异常，SparkApplication 失败 |
| `allowSqlFailure=true` | 记录失败并继续，全部 statements 完成后进程成功退出 |
| 任务结束 | 输出 `total`, `succeeded`, `failed` 汇总 |

## 5. 依赖

| Dependency | Version / scope | Notes |
|------------|-----------------|-------|
| Spark | `4.0.2`, Scala `2.13`, `provided` | 由官方 Spark 镜像提供 |
| Paimon Spark | `paimon-spark-4.0_2.13:1.4.1` | 打入插件 fat jar |
| Paimon S3 | `paimon-s3:1.4.1` | Ceph RGW 使用原生 `s3://` |
| Jackson | `provided` | 使用 Spark 运行时版本解析 JSON |
| SLF4J | `provided` | 使用 Spark 运行时日志实现 |
