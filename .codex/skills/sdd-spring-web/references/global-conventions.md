# DataFusion 全局约定

## 项目结构

- 父工程: `pom.xml`
- 主 Spring Boot 后端: `datafusion-manager`
- 公共 Spring DTO/PO: `datafusion-common-spring`
- 公共异常、枚举、工具: `datafusion-common`
- 数据源能力: `datafusion-datasource`
- 调度核心: `datafusion-scheduler-master`
- 插件父模块: `datafusion-plugin`

## 技术栈

- Java: 使用 JDK 17 做可靠验证。
- Web: Spring Boot。
- ORM: 默认 MyBatis-Plus。
- Mapper: 通常继承 `com.baomidou.mybatisplus.core.mapper.BaseMapper<Entity>`。
- Manager 扫描包: `com.datafusion`。

## 固定对象映射

| 概念 | DataFusion 实际对象 |
|------|---------------------|
| API 响应包装 | `com.datafusion.common.spring.dto.response.Result<T>` |
| 分页请求 | `com.datafusion.common.spring.dto.request.page.PageQuery<T>` |
| 分页响应 | `com.datafusion.common.spring.dto.response.PageResponse<T>` |
| 基础实体 | `com.datafusion.common.spring.po.BaseEntity` |
| ID 基础实体 | `com.datafusion.common.spring.po.BaseIdEntity` |
| 业务异常 | `com.datafusion.common.exception.CommonException` |
| 错误码 | `com.datafusion.common.enums.ErrorCodeEnum` |

## Manager 包组织

优先沿用目标包已有组织，常见目录为：

- `controller`
- `service`
- `service.impl`
- `dao`
- `po`
- `dto`
- `vo`
- `enums`
- `model`
- `constant`

## API 约定

- Controller 返回 `Result<T>`。
- 分页接口通常使用 `PageQuery<QueryDto>` 入参，返回 `Result<PageResponse<ResponseDto>>`。
- 非简单输入使用请求 DTO，不要在 Controller 上堆多个散参数。
- Controller 保持薄，不写业务规则、Mapper 调用或数据源操作。
- 路径命名优先沿用现有 `/api/<domain>/...` 风格。
- 优先显式校验和清晰业务异常，不要依赖 null 驱动流程。

## Service 约定

- Service 负责业务规则、事务边界、状态变化和外部集成编排。
- 数据源、DataX、调度、Redis、OSS、Git、SkyWalking 等集成逻辑应记录在设计文档中。
- 数据源差异优先放在已有 support/strategy 接口背后。
- 避免把数据库类型条件判断散落在 Controller 或无关 Service 中。

## 持久化约定

- Mapper 与 `po` 实体对齐。
- XML Mapper 仅在需要自定义 SQL 时新增或修改。
- 用户输入必须使用 `#{}` 参数绑定，禁止用 `${}` 拼接用户输入。
- 新增 JSON/Properties 字段时，先查找并复用已有 MyBatis 类型处理器。
- 分页、租户过滤和排序规则要显式。
- 已有 DTO 或 Entity 能表达边界时，不要在 Mapper/Service 边界返回原始 `Map`。

## SDD 工作流

- 修改 Controller、Service、持久化或调度集成前，先明确数据结构和行为契约。
- 较大变更写入 `docs/{module}/{feature}-data-define.md` 和 `docs/{module}/{feature}-design.md`。
- 较小变更也要在任务说明或实现总结中明确契约。
- 数据结构文档定义字段、类型、校验、DB 映射和层间转换。
- 设计文档定义 API、Service 行为、Mapper/SQL、集成关系、安全上下文、不实现范围和验证方式。

## 实现顺序

1. 定义或更新数据结构文档。
2. 定义或更新设计文档。
3. 更新 DTO、枚举和校验注解。
4. 更新 Entity/PO、Mapper/XML 和持久化访问。
5. 更新 Service 接口和实现。
6. 最后添加或更新 Controller。
7. 补充聚焦测试。

## Checkstyle 和验证

- 代码风格文件: `style/codeStyle.md`
- Checkstyle 文件: `style/CheckStyle-13.0.0.xml`
- 不允许用 `@SuppressWarnings` 或 suppression 配置绕过 Checkstyle。
- Javadoc、字段空行、import、命名、缩进、行长都按项目规则真实修复。
- 最小验证命令: `mvn -DskipTests compile -pl <module> -am`
- 测试命令: `mvn test -pl <module> -am`

## 测试约定

- 仅 Controller 变更时，优先写 Service 测试；请求绑定或校验复杂时再补窄范围 Web 测试。
- Service 变更覆盖正常流程、缺失数据、非法状态和外部集成失败。
- SQL/数据源相关工作覆盖不支持的数据源类型和空结果。
- 调度相关工作覆盖状态转换，以及重复 start/stop/publish 的幂等性。

## 构建风险

- 不要把已有 `target/` 产物当作 clean build 证明。
- 仓库里存在已跟踪的生成产物，不要扩大这个问题。
- 如果 Maven 当前绑定 JDK 低于 17，临时设置 `JAVA_HOME` 后再验证。
