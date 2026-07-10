# plugin-spark-sql 设计

> 数据结构见 [spark-sql-data-define.md](./spark-sql-data-define.md)。

## 1. 能力

- Module: `datafusion-plugin/datafusion-plugin-spark-sql`
- Main class: `com.datafusion.plugin.spark.sql.SparkSqlApplication`
- CLI: `--job-file <path>`
- Call chain: `SparkSqlApplication` -> `SparkSqlJobConfig` -> `SparkSessionFactory` -> `SparkSqlRunner`
- Responsibility: 加载作业配置、创建 SparkSession、切换默认 Catalog/Database、顺序执行 SQL。

插件不创建 `SparkApplication`，不访问 Kubernetes API，不解析或改写 SQL。

## 2. 执行流程

| Step | Input | Behavior | Output |
|------|-------|----------|--------|
| 加载配置 | `--job-file` | Jackson 读取 `spark-sql-job.json` | `SparkSqlJobConfig` |
| 校验配置 | job config | 校验目标类型、Catalog、Database、statements 和配置 map | 有效配置 |
| 创建 Session | `sparkConf`, `paimonConf` | Spark 配置先写入，Paimon 配置后写入 | `SparkSession` |
| 设置 Hadoop | `hadoopConf` | 写入 SparkContext Hadoop Configuration | Hadoop runtime |
| 切换上下文 | `catalogName`, `databaseName` | `useDatabase=true` 时设置当前 Catalog 和 Database | SQL 默认命名空间 |
| 执行 SQL | `statements[]` | 按数组顺序调用 `spark.sql` | 成功/失败计数 |
| 输出结果 | 有返回列的 Dataset | 最多输出 100 行 | driver log |
| 关闭 Session | 执行完成或异常 | `SparkSession.stop()` | driver 退出码 |

## 3. Java 结构

| Class | Responsibility |
|-------|----------------|
| `SparkSqlApplication` | 解析 `--job-file`、加载配置、管理 SparkSession 生命周期 |
| `SparkSqlJobConfig` | JSON 模型、默认值和配置校验 |
| `SparkSessionFactory` | 合并 Spark/Paimon 配置并创建 SparkSession |
| `SparkSqlRunner` | 切换 Catalog/Database、执行 statements、输出结果和汇总 |

不单独创建 SQL splitter、result DTO 或只有一次调用的包装类。

## 4. 业务规则

| Scenario | Rule |
|----------|------|
| SQL 执行对象 | `sqlTargetType` 仅接受 `PAIMON` |
| Catalog 配置 | `paimonConf` 必须包含 `spark.sql.catalog.{catalogName}` 和 warehouse |
| Paimon 扩展 | `paimonConf` 必须包含 `spark.sql.extensions` |
| 配置冲突 | 同名 key 使用 `paimonConf`，并输出一条简短 warn |
| 默认 Database | `useDatabase=true` 时 database 必须已存在；插件不自动建库 |
| SQL 数组 | 每个元素只承载一条 SQL；页面或 Manager 负责按分号拆分 |
| SQL 日志 | `enableSqlLogging=true` 时输出 SQL 文本；执行结果和汇总始终输出 |
| 失败处理 | 不允许失败时立即终止；允许失败时继续并成功退出 |
| SELECT 结果 | 对所有有返回列的语句统一最多输出 100 行 |

插件不提供跨 statements 事务。每条 SQL 的提交和回滚语义由 Spark 与目标 Catalog 决定。

## 5. 构建发布

`maven-shade-plugin` 直接生成 `target/plugin-spark-sql.jar`，构建清单通过 `resourceFiles` 发布该文件，
并通过 `resourceDirs` 发布 `conf/` 和 `jobs/`。
fat jar 包含 Paimon Spark 4.0 和 Paimon S3，Spark、Scala、Jackson、SLF4J 使用官方镜像运行时依赖。

Agent 将 jar 复制到 driver 和 executor 的 `/opt/datafusion/spark/jars`，并以
`local:///opt/datafusion/spark/jars/plugin-spark-sql.jar` 作为 `mainApplicationFile`。

## 6. Paimon 与 Ceph

Paimon 使用原生 S3 文件系统：

```json
{
  "spark.sql.catalog.paimon.warehouse": "s3://data-lake-warehouse/paimon",
  "spark.sql.catalog.paimon.s3.endpoint": "http://ceph-rgw.example:7480",
  "spark.sql.catalog.paimon.s3.path.style.access": "true"
}
```

Ceph AK/SK 不写入示例或日志，由 Kubernetes Secret 向 driver 和 executor 注入。

## 7. 文件

| File | Purpose |
|------|---------|
| `src/main/java/com/datafusion/plugin/spark/sql/SparkSqlApplication.java` | 启动入口 |
| `src/main/java/com/datafusion/plugin/spark/sql/config/SparkSqlJobConfig.java` | 配置模型 |
| `src/main/java/com/datafusion/plugin/spark/sql/runtime/SparkSessionFactory.java` | Session 创建 |
| `src/main/java/com/datafusion/plugin/spark/sql/runtime/SparkSqlRunner.java` | SQL 执行 |
| `src/main/resources/builder/plugin-build-manifest.json` | 插件发布清单 |
| `src/main/resources/plugins/spark/conf/logback.xml` | 控制台日志配置 |
| `src/main/resources/plugins/spark/jobs/spark-sql-job-example.json` | 作业示例 |

## 8. 验证

- 配置测试：必填字段、默认值、PAIMON 类型、空 SQL、Paimon 配置。
- 执行测试：顺序执行、失败中断、允许失败、结果行数限制。
- Build: `mvn -pl datafusion-plugin/datafusion-plugin-spark-sql -am package`
- Checkstyle: `mvn -pl datafusion-plugin/datafusion-plugin-spark-sql -am validate -Dskip.checkStyle=false`
- K8S: driver 读取 `spark-sql-job.json`，Ceph 建表、写入、查询均成功。
