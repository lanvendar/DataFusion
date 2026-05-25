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
