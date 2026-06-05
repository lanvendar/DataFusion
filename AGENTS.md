# AGENTS.md

## 项目概览

DataFusion 是 Java/Maven 多模块数据集成平台，覆盖元数据管理、数据集成、SQL 开发、数据资产、血缘治理和调度能力。

主后端实现集中在 `datafusion-manager`。部分模块仍是占位实现或框架壳，修改前必须先确认真实代码，不要只根据模块名判断可用程度。

## 模块地图

- `datafusion-common`：公共工具、类型系统、cron、SQL 模板、通用异常。
- `datafusion-common-data`：调度/领域共享 DTO 和枚举。
- `datafusion-common-spring`：Spring/Web DTO、分页对象、基础实体、MyBatis 类型处理器。
- `datafusion-datasource`：动态数据源、JDBC/Cassandra 连接器、SQL 执行、结果集映射。
- `datafusion-manager`：主 Spring Boot 后端，包含 metadata、ingestion、development、asset、scheduler、auth、config。
- `datafusion-scheduler-master`：调度核心。
- `datafusion-scheduler-worker`、`datafusion-agent`：当前主要是占位代码。
- `datafusion-plugin`：插件父模块，当前包含 `datafusion-plugin-api`。
- `datafusion-web`：Maven 壳，当前没有前端源码。

## 构建

父 POM 当前声明 Java 17。可靠验证使用 JDK 17。

常用命令：

```powershell
mvn -DskipTests compile
mvn -DskipTests compile -pl datafusion-manager -am
mvn test
```

不要依赖已有 `target/` 产物证明构建成功。仓库中已有大量被跟踪的生成产物，不要扩大这个问题。

## 已知风险

- `datafusion-manager/pom.xml` 曾出现内部 artifact 与当前 reactor 模块不一致的情况，改构建文件时优先保证 reactor 自包含。
- 父 POM 中存在重复依赖声明风险，例如 `calcite-linq4j`。
- `datafusion-datasource/pom.xml` 曾多次声明 `spring-context`。
- `mysql:mysql-connector-java` 已迁移到 `com.mysql:mysql-connector-j`。
- `datafusion-manager` 没有本地 `application.*`，运行配置预期来自外部。

## 主应用和关键上下文

Spring Boot 入口：

- `datafusion-manager/src/main/java/com/datafusion/manager/ManagerApplication.java`

Manager 扫描 `com.datafusion`，MyBatis Mapper 包主要包括：

- `com.datafusion.manager.asset.dao`
- `com.datafusion.manager.ingestion.dao`
- `com.datafusion.manager.metadata.dao`
- `com.datafusion.manager.scheduler.dao`
- `com.datafusion.manager.development.dao`

外部运行配置至少包括：

- `spring.datasource.*`
- `datafusion.datasource.*`
- Redis/Redisson
- Nacos config
- `oss.*`
- `git.url`、`git.branch`、`git.username`、`git.password`
- `etl.*`
- `skywalking.graphql.url`
- `datafusion.resource.sync.*`

## 业务域提示

- 元数据：`datafusion-manager/src/main/java/com/datafusion/manager/metadata`，重点关注 `DatabaseSupportManager`、`MetaDataSupport`、`TransformSupport`。
- 数据集成：`datafusion-manager/src/main/java/com/datafusion/manager/ingestion`，DataX JSON 生成链路在 `DataxJsonServiceImpl` 和 `ingestion/service/impl/datax/*Builder`。
- SQL 开发：`datafusion-manager/src/main/java/com/datafusion/manager/development`，重点关注 `DevelopmentSqlServiceImpl`、`SqlScriptUtils`、`SqlExecutorRouter`。
- 资产和血缘：`datafusion-manager/src/main/java/com/datafusion/manager/asset`，SkyWalking、OSS、Git/ETL 集成风险较高。
- 调度：核心在 `datafusion-scheduler-master`，Manager 侧在 `datafusion-manager/src/main/java/com/datafusion/manager/scheduler`。`SchedulerConfig.java` 目前调度集成不完整。
- 数据源层：`datafusion-datasource`，新增数据源前至少检查 `DatabaseTypeEnum` 和 `ConnectorFactory#createConnector`。

## Skills

Web 功能设计、前端页面/API client、Spring Web、Controller、Service、DTO、Mapper、Entity/PO、持久化和调度集成类变更，使用项目内 skill：

- `.codex/skills/sdd-java-web`

Java 风格和 Checkstyle 处理，使用项目内 skill：

- `.codex/skills/checkstyle`
