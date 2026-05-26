# AGENTS.md

## 项目概览

DataFusion 是一个 Java/Maven 多模块企业级数据集成平台，提供元数据管理、数据集成、SQL 开发、数据资产、血缘/治理和调度等后端能力。

主要实现集中在 `datafusion-manager`。部分模块目前仍是占位实现或框架壳，因此不要只根据模块名判断其生产可用程度，修改前应先确认真实代码。

## 仓库结构

- `pom.xml`：父级 Maven 工程，`packaging` 为 `pom`。
- `datafusion-common`：公共工具、类型系统、cron 工具、SQL 模板、线程池工具、通用异常。
- `datafusion-common-data`：调度/领域共享 DTO 和枚举。
- `datafusion-common-spring`：Spring/Web 共享 DTO、分页对象、基础实体、MyBatis 类型处理器。
- `datafusion-datasource`：动态数据源注册、JDBC/Cassandra 连接器、SQL 执行、结果集映射、SQL Mapper 扫描。
- `datafusion-manager`：主 Spring Boot 后端，包含 metadata、ingestion、development、asset、scheduler、auth、config 等包。
- `datafusion-scheduler-master`：调度引擎核心，包含 Actor 系统、Flow/Task/Event/Trigger 模型、存储接口、内存和缓存实现。
- `datafusion-scheduler-worker`：目前只有 `Hello World` 风格的占位代码。
- `datafusion-agent`：目前只有 `Hello World` 风格的占位代码。
- `datafusion-plugin`：插件父模块；当前只有 `datafusion-plugin-api`，也偏占位。
- `datafusion-web`：只有 Maven 壳，当前没有前端源码。
- `style`：checkstyle 和代码风格文档。
- `docs`：项目文档目录。

## 构建注意事项

父 POM 声明 Java 21：

```xml
<maven.compiler.release>21</maven.compiler.release>
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

为了获得可靠构建结果，请使用 JDK 21。即使某些本地增量构建在低版本 JDK 上看起来能通过，也不要把它当作完整验证。

常用命令：

```powershell
mvn -DskipTests compile
mvn -DskipTests compile -pl datafusion-manager -am
mvn test
```

验证变更时不要依赖已有 `target/` 输出。当前仓库里存在大量已跟踪的 `target/` 产物，因此一次成功的增量编译不一定能证明 clean build 可用。

## 已知构建和依赖风险

- `datafusion-manager/pom.xml` 依赖 `com.datafusion:datafusion-common-web`，但当前仓库中存在的是 `datafusion-common-spring`。
- `datafusion-manager/pom.xml` 依赖 `com.datafusion:datafusion-scheduler`，但当前仓库中存在的是 `datafusion-scheduler-master` 和 `datafusion-scheduler-worker`。
- 这些坐标可能会从开发者本地 Maven 仓库解析成功，但仅靠当前检出的 reactor 无法复现。
- 父 POM 中 `calcite-linq4j` 的 dependencyManagement 存在重复声明。
- `datafusion-datasource/pom.xml` 多次声明了 `spring-context`。
- `mysql:mysql-connector-java` 已迁移到 `com.mysql:mysql-connector-j`。

如果修改构建文件，优先目标是让当前 reactor 自包含、可复现。

## 主后端应用

Spring Boot 入口：

- `datafusion-manager/src/main/java/com/datafusion/manager/ManagerApplication.java`

Manager 扫描 `com.datafusion`，启用调度，并扫描以下 MyBatis Mapper 包：

- `com.datafusion.manager.asset.dao`
- `com.datafusion.manager.ingestion.dao`
- `com.datafusion.manager.metadata.dao`
- `com.datafusion.manager.scheduler.dao`
- `com.datafusion.manager.development.dao`

当前 `datafusion-manager` 没有 `src/main/resources/application.*` 文件。运行配置预期来自外部，包括 datasource、Redis/Redisson、Nacos、OSS、Git、ETL 和 SkyWalking 相关配置。

## 主要业务域

### 元数据

包路径：

- `datafusion-manager/src/main/java/com/datafusion/manager/metadata`

职责包括数据源注册、表/字段元数据、元数据比对、DDL 生成、元数据刷新、SQL 执行和导出。

数据库特定能力通过以下类路由：

- `metadata/support/DatabaseSupportManager.java`
- `metadata/support/MetaDataSupport.java`
- `metadata/support/TransformSupport.java`

已有实现覆盖 MySQL、PostgreSQL、MaxCompute、Hologres、DM 以及 StarRocks 相关类。

### 数据集成

包路径：

- `datafusion-manager/src/main/java/com/datafusion/manager/ingestion`

DataX JSON 生成链路主要集中在：

- `ingestion/service/impl/DataxJsonServiceImpl.java`
- `ingestion/service/impl/datax/*ReaderBuilder.java`
- `ingestion/service/impl/datax/*WriterBuilder.java`

新增源端或目标端类型时，优先扩展现有 `DataxReaderBuilder` 和 `DataxWriterBuilder` 策略模式。

### SQL 开发

包路径：

- `datafusion-manager/src/main/java/com/datafusion/manager/development`

SQL 执行通过数据源元数据支持和脚本工具路由。重点查看：

- `development/service/impl/DevelopmentSqlServiceImpl.java`
- `development/service/sql/SqlScriptUtils.java`
- `development/service/sql/SqlExecutorRouter.java`

处理 SQL 执行时要特别注意权限、语句拆分、结果集大小和数据源差异。

### 数据资产和血缘

包路径：

- `datafusion-manager/src/main/java/com/datafusion/manager/asset`

包含资产资源、血缘节点/边、API 资源、指标、菜单资源、ETL 导入/解析、SkyWalking 链路处理、Git/OSS 集成。

高风险外部集成：

- SkyWalking GraphQL：`asset/service/SkywalkingGraphqlClient.java`
- 链路处理：`asset/service/SkywalkingTraceProcessingService.java`
- ETL Git 处理：`asset/service/impl/EtlProcessServiceImpl.java`
- OSS 操作：`manager/utils/AliyunOssUtils.java`

### 调度

调度引擎核心：

- `datafusion-scheduler-master/src/main/java/com/datafusion/scheduler/master`

Manager 侧调度持久化和 API：

- `datafusion-manager/src/main/java/com/datafusion/manager/scheduler`

`SchedulerConfig.java` 当前只装配了 `MasterStorage`，`MasterService` Bean 被注释掉。在显式启用并测试前，应把调度能力视为“部分集成”。

## 数据源层

包路径：

- `datafusion-datasource/src/main/java/com/datafusion/datasource`

关键类：

- `ConnectorFactory.java`：动态数据源注册和连接器创建。
- `AbstractJdbcConnector.java`：JDBC 连接器基类。
- `JdbcExecutor.java`：JDBC 执行逻辑。
- `resultset/*`：结果集映射。
- `spring/*`：SQL Mapper 扫描和代理集成。

新增数据源类型前，至少检查：

- `com.datafusion.common.enums.DatabaseTypeEnum`
- `ConnectorFactory#createConnector`

如果该数据源要暴露给 manager API，还需要在 `datafusion-manager` 中补充元数据 transform/support 实现。

## 测试现状

当前测试覆盖有限：

- `datafusion-common` 有主要可用测试。
- `datafusion-manager`、`datafusion-datasource`、`datafusion-scheduler-master` 基本没有测试覆盖。
- `datafusion-agent` 和 `datafusion-scheduler-worker` 只有占位测试。

有实际行为变更时，应围绕受影响业务路径补充聚焦测试。高优先级测试目标：

- DataX JSON 构建输出。
- 元数据 support 路由和数据源转换。
- SQL 脚本拆分和执行保护。
- 调度 Flow/Task/Trigger 状态转换。
- 数据源连接器刷新和结果集映射。

## 编码指南

- 遵循现有包组织：`controller`、`service`、`service.impl`、`dao`、`po`、`dto`、`constant`、`enums`、`model`。
- 优先复用已有策略/管理器模式，不要新增平行的路由逻辑。
- 保持模块边界清晰。纯公共工具放在 `datafusion-common`；Spring 专属 DTO/类型处理器放在 `datafusion-common-spring`；业务逻辑放在 `datafusion-manager`。
- 不要轻易新增依赖。当前项目依赖面已经很宽。
- 除非确实符合现有风格，否则避免继续引入新的静态可变注册表。
- 修改附近代码时，优先把 `printStackTrace`/`System.out.println` 替换为结构化日志。
- 谨慎处理用户凭证和数据源密码。多个 DTO/Entity 携带 password 字段。
- 不要提交生成构建产物。当前仓库已经跟踪了大量 `target/` 文件，不要扩大这个问题。

## SDD Spring Web 工作流

在本仓库实现 Spring Web 功能时，使用 SDD 风格流程：在修改 Controller、Service、持久化或调度集成前，先明确预期行为。对于较大变更，应将规格说明放在 `docs/` 中；对于较小变更，也应在任务描述中明确契约。

每个后端功能在实现前先定义：

- 能力：支持哪个用户或系统动作。
- 范围：由哪个模块/包负责。
- API 契约：接口路径、HTTP 方法、请求 DTO、响应 DTO、校验和错误场景。
- 领域契约：涉及的 Entity、DTO、枚举、状态转换和不变量。
- 持久化契约：Mapper 方法、表/列、查询过滤、排序、分页和事务边界。
- 集成契约：数据源连接、DataX、SkyWalking、Redis/Redisson、OSS、Git、Nacos、调度 master 等外部系统。
- 安全和身份：必需 Header、租户/用户字段、token 透传、密码/密钥处理。
- 验证：单元测试、集成测试、编译命令和手工 API 检查。

Spring Web 变更建议按以下顺序实现：

1. 定义或更新 DTO 和校验注解。
2. 更新 Service 接口契约。
3. 实现 Service 逻辑和持久化访问。
4. 最后添加或更新 Controller，保持 Controller 薄。
5. 在大范围重构前，优先补充服务行为和边界场景测试。
6. 使用 `-pl <module> -am` 编译最小受影响 Maven 模块。

Controller 指南：

- 返回项目已有的 `Result` 和 `PageResponse` 结构。
- 路径命名保持与现有 `/api/<domain>/...` 风格一致。
- 不要在 Controller 中写业务逻辑、Mapper 调用或数据源操作。
- 非简单输入使用请求 DTO，不要堆大量散参数。
- 优先显式校验和清晰业务异常，不要依赖 null 驱动流程。

Service 指南：

- Service 负责业务规则、事务、状态变化和集成编排。
- 复用现有 `DatabaseSupportManager`、数据源 transform、DataX builder、调度存储适配器、资产血缘服务等。
- 数据源特定行为尽量放在 support/strategy 接口背后。
- 避免把数据库类型条件判断散落在 Controller 或无关 Service 中。

持久化指南：

- Mapper 接口应与面向表的 `po` 实体对齐。
- 分页、租户过滤和排序规则要显式。
- 已有 DTO 或 Entity 能表达边界时，不要在 Mapper/Service 边界返回原始 Map。
- 新增 JSON 或 `Properties` 字段时，复用现有 MyBatis 类型处理器。

测试指南：

- 仅 Controller 变更时，优先写 Service 测试；如果请求绑定或校验复杂，再补窄范围 Web 测试。
- Service 变更应覆盖正常流程、缺失数据、非法状态和外部集成失败。
- SQL/数据源相关工作应覆盖不支持的数据源类型和空/空值结果。
- 调度相关工作应覆盖状态转换，以及重复 start/stop/publish 的幂等性。

轻量 Feature Spec 模板：

```markdown
# Feature Spec: <name>

## Capability
<新增或修改的行为。>

## API
- Method/path:
- Request DTO:
- Response DTO:
- Validation:
- Errors:

## Domain and Persistence
- Entities/tables:
- State changes:
- Transaction boundary:

## Integrations
- Datasource/DataX/Scheduler/Redis/OSS/Git/SkyWalking:

## Verification
- Tests:
- Compile command:
- Manual checks:
```

## 运行配置预期

后端至少预期外部提供以下配置：

- `spring.datasource.*`
- `datafusion.datasource.*`
- Redis/Redisson
- Nacos config
- `oss.*`
- `git.url`、`git.branch`、`git.username`、`git.password`
- `etl.*`
- `skywalking.graphql.url`
- `datafusion.resource.sync.*`

如果新增必需配置，应同步写入项目文档或示例配置。

## Agent 实用工作流

1. 编辑前先阅读相关模块 POM。
2. 确认目标模块是否真实参与 reactor 构建。
3. 新增类或模式前先用 `rg` 搜索现有实现。
4. 对 Spring Web 功能，修改代码前先写出或推断 SDD 契约。
5. 后端变更使用 `-pl <module> -am` 编译最小受影响模块。
6. 修改 manager 代码时，注意 `datafusion-common-web` 与 `datafusion-common-spring` 的依赖错位。
7. 除非用户明确要求，不要对已跟踪的 `target/` 文件执行破坏性清理。
8. 清楚报告构建结果，并说明是 clean 构建、增量构建还是被环境阻塞。
