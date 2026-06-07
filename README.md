# 项目概览
DataFusion 是一个企业级数据集成平台。提供元数据管理、数据集成、数据开发、数据资产、数据治理和数据调度等工具的能力。

# 开发说明文档
## 项目模块结构说明
### 1. dataFusion 项目模块结构
```
    |- datafusion
        |- datafusion-common (公共模块:轻量级工具包,形成模块后可抽出独立模块,例如:datafusion-datasource) 
        |- datafusion-common-data (公共对象模块:命名规则为一级包名为[模块名],类路径与模块中保持一致)
            | - datasource 
            | - scheduler
            | - manager
            | - agent
            | - api
        |- datafusion-common-spring (Spring相关依赖模块)
        |- datafusion-datasource (数据源核心框架)
        |- datafusion-scheduler-master (数据调度—调度节点)
        |- datafusion-scheduler-worker (数据调度—执行节点)
        |- datafusion-manager (管理页面-后端)
            | - metadata (元数据) 
                | - controller
                | - service
            | - ingestion (数据集成) 
                | - controller
                | - service
            | - development (数据开发) 
                | - controller
                | - service                        
            | - asset (数据资产) 
                | - controller
                | - service           
            | - quality (数据质量) 
                | - controller
                | - service
            | - scheduler (调度中心) 
                | - controller
                | - service  
                | - master (调度引擎实现) 
            | - system (系统管理) 
                | - controller
                | - service
        |- datafusion-web (管理页面-前端)   
        |- datafusion-agent (管理插件的工作节点)
        |- datafusion-plugin (插件)
            |- datafusion-plugin-api (API接口查询插件)
            |- datafusion-plugin-flink (Flink插件)
            |- datafusion-plugin-spark (Spark插件)
            |- datafusion-plugin-datax (DataX插件)
```
### 2. 内部服务模块间依赖关系(待补充)
```
```

-------

## 包结构和命名规范

1. 说明公共对象包com.datafusion
   - com.datafusion.***.po 数据访问层对象包名(一般与数据库表结构保持一致)
   - com.datafusion.***.dao 数据访问层接口包名
   - com.datafusion.***.dto 传输层对象包名(微服务传输层对象,前端使用,各模块传输使用)
   - com.datafusion.***.vo 展示层对象包名(前端使用,目前暂时不用后期可扩展)
   - com.datafusion.***.constant 常量定义包名
   - com.datafusion.***.enums 枚举定义包名
   - com.datafusion.***.model 框架数据结构定义包名
2. 层级模块说明
   - controller 控制层接口与页面保持一致(多个页面可以共用一个controller，一个页面不要出现多个controller)
   - service 业务逻辑层与业务接口分类保持一致(推荐的做法，在Service层中使用其他Service接口)
   - dao 数据访问层与数据库表结构保持一致

-------

## 启动配置和运行方式

`datafusion-manager` 和 `datafusion-agent` 使用同一套 profile 约定支持本地单体模式和 Nacos 微服务模式。

### 1. Profile 说明

| 启动 profile | 运行模式 | 说明 |
| --- | --- | --- |
| `local` | Spring Boot 单体本地模式 | 关闭 Nacos 配置中心和注册中心，服务间调用使用固定 HTTP 地址 |
| `test` | Spring Cloud + Nacos test 模式 | 开启 Nacos 配置中心和注册中心，适合测试环境 |
| `prod` | Spring Cloud + Nacos prod 模式 | 开启 Nacos 配置中心和注册中心，适合生产环境 |

### 2. 配置文件职责

`datafusion-manager/src/main/resources` 和 `datafusion-agent/src/main/resources` 均使用以下配置文件结构：

| 文件 | 说明 |
| --- | --- |
| `application.yml` | 公共业务配置，例如端口默认值、线程池、调度参数、日志目录等 |
| `application-local.yml` | 本地环境配置；同时显式关闭 Nacos |
| `bootstrap.yml` | bootstrap 公共配置，声明 `spring.application.name`，默认 profile 为 `local`，默认关闭 Nacos |
| `bootstrap-test.yml` | test 环境 Nacos 地址、namespace、group、账号等配置 |
| `bootstrap-prod.yml` | prod 环境 Nacos 地址、namespace、group、账号等配置 |

### 3. 本地单体启动

本地单体模式不依赖 Nacos。先启动 `datafusion-manager`，再启动 `datafusion-agent`。

```bash
mvn -pl datafusion-manager -am spring-boot:run -Dspring-boot.run.profiles=local
```

```bash
mvn -pl datafusion-agent -am spring-boot:run -Dspring-boot.run.profiles=local
```

也可以使用 jar 启动：

```bash
java -jar datafusion-manager/target/datafusion-manager-1.0.0.jar --spring.profiles.active=local
```

```bash
java -jar datafusion-agent/target/datafusion-agent-1.0.0.jar --spring.profiles.active=local
```

local 模式下，`datafusion-agent` 默认使用 `http://127.0.0.1:8080` 注册和心跳到 `datafusion-manager`。如需覆盖：

```bash
DATAFUSION_MANAGER_URL=http://127.0.0.1:8080 java -jar datafusion-agent/target/datafusion-agent-1.0.0.jar --spring.profiles.active=local
```

### 4. Nacos 模式启动

test 环境：

```bash
java -jar datafusion-manager/target/datafusion-manager-1.0.0.jar --spring.profiles.active=test
```

```bash
java -jar datafusion-agent/target/datafusion-agent-1.0.0.jar --spring.profiles.active=test
```

prod 环境：

```bash
java -jar datafusion-manager/target/datafusion-manager-1.0.0.jar --spring.profiles.active=prod
```

```bash
java -jar datafusion-agent/target/datafusion-agent-1.0.0.jar --spring.profiles.active=prod
```

Nacos 模式下，`datafusion-agent` 默认使用服务名 `http://datafusion-manager` 访问 manager，并通过 Spring Cloud LoadBalancer 解析服务实例。

prod 模式可以通过环境变量覆盖 Nacos 连接参数：

```bash
NACOS_SERVER_ADDR=127.0.0.1:8848 \
NACOS_NAMESPACE=prod \
NACOS_GROUP=DEFAULT_GROUP \
NACOS_USERNAME=nacos \
NACOS_PASSWORD=nacos \
java -jar datafusion-manager/target/datafusion-manager-1.0.0.jar --spring.profiles.active=prod
```

### 5. 编译验证

```bash
mvn -DskipTests compile -pl datafusion-manager -am
```

```bash
mvn -DskipTests compile -pl datafusion-agent -am
```
