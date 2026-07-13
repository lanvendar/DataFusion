# DataFusion 项目约定

> 本文档记录 DataFusion 跨功能稳定约定。功能字段、接口、状态机和页面行为应写入各功能的 `*-data-define.md` 与 `*-design.md`，不要写入本文档。

## 1. 项目概览

DataFusion 是 Java/Maven 多模块数据集成平台，覆盖元数据管理、数据集成、SQL 开发、数据资产、血缘治理和调度能力。

主后端应用是 `datafusion-manager`。部分模块仍可能是框架壳或占位实现，修改前必须读取真实代码和相邻实现，不能只按模块名推断能力。

## 2. 模块边界

| 模块 | 职责 | 说明 |
|------|------|------|
| `datafusion-common` | 公共工具、类型系统、cron、SQL 模板、通用异常 | 不放 Spring Web 运行时能力 |
| `datafusion-common-data` | 跨模块共享 DTO、枚举和领域模型 | 调度通信 DTO 必要时下沉到这里 |
| `datafusion-common-spring` | Spring/Web DTO、分页对象、基础实体、MyBatis 类型处理器 | 后端 API 和持久化通用对象优先复用 |
| `datafusion-datasource` | 动态数据源、连接器、SQL 执行、结果集映射 | 新增数据源前检查 `DatabaseTypeEnum` 和 `ConnectorFactory` |
| `datafusion-manager` | 主 Spring Boot 后端应用 | 包含 metadata、ingestion、development、asset、scheduler、system 等域 |
| `datafusion-web` | 管理端前端应用 | Vite、React、Ant Design、React Query |
| `datafusion-scheduler-master` | 调度 master 框架层 | 仅作为 jar 提供调度核心能力 |
| `datafusion-scheduler-worker` | 调度 worker 框架层 | 仅作为 jar 提供 worker 侧契约和复用组件 |
| `datafusion-agent` | worker 运行时应用层 | Spring Boot 启动、注册、插件加载、任务执行与日志记录 |
| `datafusion-plugin` | 插件父模块 | 当前包含 `datafusion-plugin-api` 等公共插件能力，以及业务定制插件 |

框架层不得反向依赖运行时应用层；运行时应用层可以依赖并装配框架层。

`datafusion-plugin` 下以 `datafusion-plugin-*` 命名的模块才按项目公共插件模块理解；没有 `datafusion-plugin` 前缀的 Maven 模块是业务定制模块插件，不作为跨业务公共模块沉淀。

## 3. SDD 文档约定

- 项目索引: `docs/.sdd/project-index.yml`。
- 项目级唯一约定: `docs/.sdd/project-conventions.md`。
- 单模块功能数据定义: `docs/{feature}-data-define.md`。
- 单模块功能设计: `docs/{feature}-design.md`。
- 多模块功能数据定义: `docs/{module}/{feature}-data-define.md`。
- 多模块功能设计: `docs/{module}/{feature}-design.md`。
- 如果功能文档直接位于 `docs/` 根目录，按单模块模式处理，此时 `project-index.yml` 可以只维护根目录。
- 如果存在 `docs/{module}/{feature}-*.md`，按多模块模式处理，必须通过 `project-index.yml` 索引功能文档根目录、源码根目录和验证命令。
- `project-index.yml` 的 `modules` 以 Maven reactor 和实际源码目录为准；暂时没有独立功能文档的模块也要保留索引，`feature_docs_root` 写 `无`，避免把“无文档”误判为“模块不存在”。
- `datafusion-plugin` 下没有 `datafusion-plugin` 前缀的业务定制模块插件，功能文档放在 `docs/datafusion-plugin/{plugin-module}/`，例如 `docs/datafusion-plugin/plugin-flink-schema-paimon/`。
- 数据结构定义是 Database、Backend、Frontend 模型的唯一事实源。
- 设计文档只说明数据流、行为、接口、事务、集成、验证和不实现范围，不重复完整字段表。
- 外部运行时或资源引入型插件如果不定义数据库、Java 后端模型、前端模型或任务数据结构，可以只保留 `*-design.md`；设计文档必须明确资源所有权、创建来源、生命周期、任务数据关系和不持有持久化数据的边界。例如 `docs/datafusion-plugin/datafusion-plugin-spider/spider-plugin-design.md`。

## 4. 数据库约定

- DDL 必须明确主键、候选键或唯一业务键、分区策略、表注释和字段注释。
- 没有候选键或分区时，也要在数据定义文档中显式写“无”。
- 审计字段如由 `BaseEntity` 或 `BaseIdEntity` 继承，Entity 不重复声明，但 DDL 中必须明确。
- 自定义 SQL 必须使用 `#{}` 绑定用户输入，禁止用 `${}` 拼接用户输入。
- 新增 JSON/Properties 字段时，优先查找并复用已有 MyBatis 类型处理器。

## 5. 后端约定

- Java 使用 JDK 17 做可靠验证。
- 主应用入口: `datafusion-manager/src/main/java/com/datafusion/manager/ManagerApplication.java`。
- Manager 扫描包: `com.datafusion`。
- Controller 返回 `Result<T>`。
- 分页请求优先使用 `PageQuery<T>`，分页响应优先使用 `PageResponse<T>`。
- Controller 保持薄，不写业务规则、Mapper 调用或外部系统编排。
- Service 负责业务规则、事务边界、状态变化和外部集成编排。
- Mapper 通常继承 MyBatis-Plus `BaseMapper<Entity>`。
- 优先复用 `BaseEntity`、`BaseIdEntity`、`CommonException`、`ErrorCodeEnum`。
- 常见包结构为 `controller`、`service`、`service.impl`、`dao`、`po`、`dto`、`vo`、`enums`、`model`、`constant`。

## 6. 前端约定

- 前端源码位于 `datafusion-web/src`。
- 技术栈为 Vite、React、TypeScript、Ant Design、Axios、React Query、React Router。
- 页面模块优先放在 `datafusion-web/src/modules/{feature}`。
- 模块内常见文件为 `api.ts`、`dto.ts`、`constants.ts`、`index.tsx`，复杂页面可补充 `components/`、`utils.ts`。
- 统一 HTTP client 位于 `datafusion-web/src/api/http.ts`。
- 路由集中维护在 `datafusion-web/src/router/routes.tsx`。
- 新增页面应复用已有 page layout、表格、表单、抽屉、菜单和 API client 风格。

## 7. 调度边界

- `datafusion-scheduler-master` / `datafusion-scheduler-worker` 是调度框架层，仅作为 jar 包提供核心能力、接口契约、状态机、模型和默认实现。
- `datafusion-manager` / `datafusion-agent` 是运行时应用层，负责 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、部署和外部系统集成。
- worker/agent 需要复用 master 侧通信 DTO 时，应将必要 DTO 下沉到 `datafusion-common-data`，避免 worker 框架层或 agent 运行时依赖 master 框架模块。
- `datafusion-scheduler-worker` 不应依赖 Spring，但应定义插件加载、任务执行、上下文和结果上报契约。
- 任务定义修改必须先取消调度，再取消发布，然后回到定义域修改；运行实例域不承担任务定义修改能力。

## 8. 构建和验证

| 场景 | 命令 |
|------|------|
| 全量后端编译 | `mvn -DskipTests compile` |
| Manager 编译 | `mvn -DskipTests compile -pl datafusion-manager -am` |
| Maven 测试 | `mvn test` |
| Web 开发 | `npm run dev` |
| Web 构建 | `npm run build` |
| Web lint | `npm run lint` |
| Web 测试 | `npm run test` |

不要依赖已有 `target/` 产物证明构建成功。仓库中已有被跟踪的生成产物，不要扩大这个问题。

## 9. 运行时依赖和风险

`datafusion-manager` 没有本地完整 `application.*`，运行配置预期来自外部。外部配置通常包括：

- `spring.datasource.*`
- `datafusion.datasource.*`
- Redis / Redisson
- Nacos config
- `oss.*`
- `git.url`、`git.branch`、`git.username`、`git.password`
- `etl.*`
- `skywalking.graphql.url`
- `datafusion.resource.sync.*`

已知构建风险：

- 父 POM 中存在重复依赖声明风险，例如 `calcite-linq4j`。
- `datafusion-datasource/pom.xml` 曾多次声明 `spring-context`。
- MySQL 驱动应逐步从 `mysql:mysql-connector-java` 迁移到 `com.mysql:mysql-connector-j`。
