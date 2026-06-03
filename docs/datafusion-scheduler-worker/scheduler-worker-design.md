# 调度 Worker 框架设计

> 数据结构定义见 [scheduler-worker-data-define.md](./scheduler-worker-data-define.md)，本文档不重复字段定义。

`datafusion-scheduler-worker` 是 worker 侧调度框架层，只作为 jar 包提供任务操作接口、插件路由、结果上报接口、运行中任务上下文和可复用服务。worker 的真实运行时由 `datafusion-agent` 承载。

## 边界

`datafusion-scheduler-master` / `datafusion-scheduler-worker` 是调度框架层，仅提供核心能力、接口契约、状态机、模型和默认实现。

`datafusion-manager` / `datafusion-agent` 是运行时应用层，负责 Spring Boot 启动、HTTP 接口、配置加载、持久化实现、鉴权、部署和外部系统集成。

禁止反向依赖：框架层不得依赖运行时应用层；运行时应用层可以依赖并装配框架层。

`datafusion-scheduler-worker` 不依赖 Spring 或 `datafusion-common-spring`，不定义 Controller、Spring Bean 配置、Web DTO 或 HTTP 响应包装。

## 职责

- 定义 worker 侧任务操作接口。
- 定义插件执行器和插件路由抽象。
- 定义插件加载接口。
- 定义任务结果上报接口。
- 管理运行中任务上下文。
- 统一同步提交和异步提交语义。
- 支持任务提交、停止、强制停止和结果上报的幂等处理。

## 模块依赖

```text
datafusion-scheduler-worker
    -> datafusion-common-data
    -> datafusion-plugin-api
```

`lombok` 仅作为 `provided` 编译期依赖，用于模型类生成访问器，不作为运行时框架依赖。

通信模型归属：

- `TaskRequest`、`TaskResult`、worker 注册/心跳相关 DTO 统一从 `datafusion-common-data` 复用。
- `WorkerManager`、`WorkerStorage` 和 worker 选择策略属于 master 侧调度管理能力，不进入 `datafusion-scheduler-worker`。
- `datafusion-scheduler-worker` 不依赖 `datafusion-scheduler-master`。

## 设计评审结论

- 当前实现中 `datafusion-scheduler-worker` 仍是占位模块，缺少 worker 侧契约、插件路由、上下文管理和默认服务。
- `TaskRequest`、`Worker` 当前位于 `datafusion-scheduler-master`，会导致 worker 侧为了复用通信模型依赖 master，必须下沉到 `datafusion-common-data`。
- `TaskResult.isSync` 命名容易被理解为“任务同步执行完成”，应替换为 `submitMode`，只表达“提交方式”。
- `datafusion-scheduler-worker` 不应引入 Spring、Controller、HTTP、Nacos 或 manager/agent 运行时实现。
- 插件发现只定义 `WorkerPluginLoader` 契约；classpath、SPI、插件目录扫描等实现由 `datafusion-agent` 装配。
- 参考旧 worker 实现后保留其核心经验：提交防重、状态上下文、异步执行、结果上报和任务级执行资源释放；HTTP、Nacos、Feign、具体插件实现仍留在应用层或插件层。

## 核心接口

### WorkerTaskOperator

```java
public interface WorkerTaskOperator {
    TaskResult submitTask(TaskRequest request);
    TaskResult stopTask(TaskRequest request);
    TaskResult killTask(TaskRequest request);
    TaskResult finishTask(TaskRequest request);
}
```

与 master 侧对应关系：

```text
MasterTaskOperator.submitTask(TaskInstance) -> WorkerTaskOperator.submitTask(TaskRequest)
MasterTaskOperator.stopTask(TaskInstance)   -> WorkerTaskOperator.stopTask(TaskRequest)
MasterTaskOperator.killTask(TaskInstance)   -> WorkerTaskOperator.killTask(TaskRequest)
MasterTaskOperator.finishTask(TaskInstance) -> WorkerTaskOperator.finishTask(TaskRequest)
```

### PluginTaskExecutor

`PluginTaskExecutor` 是具体插件执行器，负责真正执行某类任务。

```java
public interface PluginTaskExecutor {
    String pluginType();
    TaskResult submitTask(TaskRequest request);
    TaskResult stopTask(TaskRequest request);
    TaskResult killTask(TaskRequest request);
    TaskResult finishTask(TaskRequest request);
}
```

每个插件执行器必须记录终端任务 ID，例如本地进程 `PID`、Flink Job ID、Yarn Application ID 或插件返回的外部应用 ID，用于状态查询、停止、强制停止和启动恢复。

`PluginTaskExecutor` 提供默认 `destroyTask(TaskRequest)` 钩子，供插件释放任务级执行器、进程句柄、客户端连接或临时资源。

### WorkerTaskOperatorRouter

`WorkerTaskOperatorRouter` 根据 `pluginType` 路由到对应 `PluginTaskExecutor`。未匹配到插件时返回明确失败结果，不应静默忽略。

### WorkerPluginLoader

```java
public interface WorkerPluginLoader {
    List<PluginTaskExecutor> loadPlugins();
}
```

`WorkerPluginLoader` 只定义纯 Java 契约。classpath、SPI、配置文件或插件目录扫描等加载方式由 `datafusion-agent` 运行时实现。

### TaskResultReporter

```java
public interface TaskResultReporter {
    boolean report(TaskResult result);
}
```

框架层只定义上报契约。HTTP 调用 manager 的实现放在 `datafusion-agent`。

### WorkerTaskService

`WorkerTaskService` 是 `WorkerTaskOperator` 的默认实现，负责:

- 校验 `TaskRequest`。
- 根据 `pluginType` 获取 `PluginTaskExecutor`。
- 使用 `taskInstanceId` 维护运行中任务上下文。
- 对同一上下文上的 `submitTask` 做串行化，避免瞬时重复提交重复启动任务。
- 按 `submitMode` 区分同步提交和异步提交。
- 对重复 `submitTask`、`stopTask`、`killTask`、`finishTask` 做幂等处理。
- 调用 `TaskResultReporter` 异步上报插件执行结果。

### WorkerTaskContextStorage

`WorkerTaskContextStorage` 保存 worker 本地运行中任务上下文，默认实现为内存实现 `CachedWorkerTaskContextStorage`。

第一版只保证单 agent 进程内幂等和恢复入口，跨进程恢复由 `datafusion-agent` 结合 `${modules}/task-status/` 和外部终端任务 ID 实现。

## 状态语义

异步提交：

```text
接收 submitTask
    -> 创建或复用 RunningTaskContext
    -> 返回 SUBMIT_SUCCESS + submitMode=ASYNC
    -> 后台执行 PluginTaskExecutor.submitTask
    -> 异步上报 RUNNING / RUN_SUCCESS / RUN_FAILURE
```

同步提交：

```text
接收 submitTask
    -> 调用 PluginTaskExecutor.submitTask 并确认任务进入运行态
    -> 返回 RUNNING + submitMode=SYNC
    -> 异步上报 RUN_SUCCESS / RUN_FAILURE
```

两种模式都不能在提交响应中返回 `RUN_SUCCESS` 或 `RUN_FAILURE`。

停止和强制停止：

```text
stopTask -> 正常停止本地任务或外部应用 -> 上报 STOP_SUCCESS / STOP_FAILURE
killTask -> 强制停止本地任务或外部应用 -> 上报 KILLED
```

## 幂等

任务状态保存与实际执行任务无法保证原子性，worker 框架必须支持幂等处理。

推荐幂等键：

```text
taskInstanceId + actionType
```

规则：

- 重复 `submitTask` 不重复启动同一任务实例。
- 重复 `stopTask` / `killTask` 返回当前控制结果或当前终态。
- 重复 `TaskResultReporter.report` 允许 manager 幂等消费。
- 本地运行中任务上下文必须保存终端任务 ID 和最近状态。

## 文件变更

### 新建

| 文件 | 说明 |
|------|------|
| `docs/datafusion-scheduler-worker/scheduler-worker-data-define.md` | worker 通信 DTO、上下文模型和层间映射定义 |
| `datafusion-common-data/src/main/java/com/datafusion/scheduler/enums/SubmitModeEnum.java` | 提交模式枚举 |
| `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/TaskRequest.java` | master/manager 到 worker 的任务操作请求 |
| `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/Worker.java` | worker 节点模型 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/WorkerTaskOperator.java` | worker 侧任务操作接口 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/WorkerTaskService.java` | worker 侧任务操作默认实现 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/plugin/PluginTaskExecutor.java` | 插件任务执行器接口 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/plugin/WorkerPluginLoader.java` | 插件加载接口 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/plugin/WorkerTaskOperatorRouter.java` | 插件路由器 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/reporter/TaskResultReporter.java` | 任务结果上报接口 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/reporter/NoopTaskResultReporter.java` | 空上报实现 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/context/RunningTaskContext.java` | 运行中任务上下文 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/context/WorkerTaskContextStorage.java` | 运行中任务上下文存储接口 |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/scheduler/worker/context/CachedWorkerTaskContextStorage.java` | 运行中任务上下文内存实现 |

### 修改

| 文件 | 说明 |
|------|------|
| `datafusion-common-data/src/main/java/com/datafusion/scheduler/model/TaskResult.java` | 使用 `submitMode` 替代 `isSync` |
| `datafusion-scheduler-worker/pom.xml` | 增加 `datafusion-common-data` 和 `datafusion-plugin-api` 依赖 |
| `datafusion-scheduler-master` / `datafusion-manager` 相关类 | 引用 common-data 中的 `TaskRequest` 和 `Worker` |

### 删除

| 文件 | 说明 |
|------|------|
| `datafusion-scheduler-master/src/main/java/com/datafusion/scheduler/worker/model/TaskRequest.java` | 通信 DTO 下沉到 `datafusion-common-data` |
| `datafusion-scheduler-master/src/main/java/com/datafusion/scheduler/worker/model/Worker.java` | worker 节点模型下沉到 `datafusion-common-data` |
| `datafusion-scheduler-worker/src/main/java/com/datafusion/App.java` | Maven archetype 占位类 |

## 实现清单

- 定义并实现 `WorkerTaskOperator`。
- 定义 `PluginTaskExecutor`、`WorkerTaskOperatorRouter`、`WorkerPluginLoader`。
- 定义 `TaskResultReporter`。
- 实现 `WorkerTaskService`，负责请求校验、幂等判断、上下文管理和插件路由。
- 支持 `SYNC` / `ASYNC` 两种提交模式。
- 不引入 Spring、Controller 或 `datafusion-common-spring`。

## 不实现的部分

- 不实现 HTTP Controller，留给 `datafusion-agent`。
- 不实现 Nacos、Kubernetes、Consul 或 Redis 注册发现，留给 `datafusion-agent` / `datafusion-manager`。
- 不实现插件目录扫描或 SPI 发现，只定义 `WorkerPluginLoader` 契约。
- 不实现跨进程任务状态恢复，只提供内存上下文和恢复所需字段。
- 不实现具体 DataX、Flink、Shell、SQL 等插件执行逻辑。

## 验证

- `WorkerTaskOperatorRouter` 按 `pluginType` 路由。
- 插件加载后正确注册到路由。
- `WorkerTaskService` 正确处理同步提交、异步提交和重复提交。
- stop / kill 能根据运行中上下文找到终端任务。
- `TaskResultReporter` 在异常场景下可重试且不破坏幂等。

```powershell
mvn -DskipTests compile -pl datafusion-scheduler-worker -am
```
