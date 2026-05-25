package com.datafusion.scheduler.master;

import com.datafusion.common.options.Options;
import com.datafusion.common.threadpool.ThreadPoolBuilder;
import com.datafusion.scheduler.master.actor.ActorSystem;
import com.datafusion.scheduler.master.actor.core.DefaultActorSystem;
import com.datafusion.scheduler.master.event.DefaultGlobalEventOperator;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.event.storage.CachedEventStorage;
import com.datafusion.scheduler.master.event.storage.EventStorageMem;
import com.datafusion.scheduler.master.flow.FlowAction;
import com.datafusion.scheduler.master.flow.storage.CachedFlowStorage;
import com.datafusion.scheduler.master.flow.storage.FlowStorageMem;
import com.datafusion.scheduler.master.task.TaskAction;
import com.datafusion.scheduler.master.task.TaskExecutor;
import com.datafusion.scheduler.master.task.storage.CachedTaskStorage;
import com.datafusion.scheduler.master.task.storage.TaskStorageMem;
import com.datafusion.scheduler.master.trigger.SchedulerTrigger;
import com.datafusion.scheduler.master.trigger.TriggerInstanceHandler;
import com.datafusion.scheduler.master.trigger.TriggerInstanceHandlerWithPolicy;
import com.datafusion.scheduler.master.trigger.core.DispatchTriggerThread;
import com.datafusion.scheduler.master.trigger.core.GenerateTriggerThread;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.storage.CachedTriggerStorage;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorageMem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 调度的 master 入口服务.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/3/9
 * @since 2026/3/9
 */
@Slf4j
public class MasterService {
    /**
     * 流程动作处理类.
     */
    @Getter
    private final FlowAction flowAction;

    /**
     * 任务动作处理类.
     */
    @Getter
    private final TaskAction taskAction;

    /**
     * 全局事件操作类.
     */
    private final GlobalEventOperator eventOperator;

    /**
     * 任务执行器.
     */
    private final TaskExecutor taskExecutor;

    /**
     * 调度上下文.
     */
    @Getter
    private final MasterStorage masterStorage;

    /**
     * 调度实例处理器.
     */
    private final TriggerInstanceHandler triggerHandler;

    /**
     * 产生调度实例触发线程.
     */
    private final GenerateTriggerThread generateTriggerThread;

    /**
     * 分发调度实例触发线程.
     */
    private final DispatchTriggerThread dispatchTriggerThread;

    /**
     * 构造函数.
     *
     * @param taskExecutor  任务执行器
     * @param masterStorage 调度上下文（null 时使用内存存储）
     * @param options       调度配置（null 时使用默认配置）
     */
    public MasterService(TaskExecutor taskExecutor, MasterStorage masterStorage, Options options) {
        Options resolvedOptions = (options != null) ? options : new Options();

        // 按域初始化（顺序由依赖关系决定）
        this.masterStorage = initStorages(masterStorage, resolvedOptions);
        this.taskExecutor = initWorker(taskExecutor);
        this.eventOperator = initEvent(resolvedOptions);
        // 基础设施
        ActorSystem actorSystem = initActorSystem(resolvedOptions);
        this.taskAction = initTask(actorSystem);
        this.flowAction = initFlow(actorSystem);
        // 触发器
        this.triggerHandler = initTrigger(this.flowAction, resolvedOptions);
        ThreadPoolExecutor triggerThreadPool = initTriggerThreadPool(resolvedOptions);
        this.generateTriggerThread = new GenerateTriggerThread(triggerThreadPool, this.triggerHandler);
        this.dispatchTriggerThread = new DispatchTriggerThread(triggerThreadPool, this.triggerHandler);
    }

    /**
     * 便捷构造函数（无外部存储）.
     *
     * @param taskExecutor 任务执行器
     * @param options      调度配置
     */
    public MasterService(TaskExecutor taskExecutor, Options options) {
        this(taskExecutor, null, options);
    }

    // ========================= 域初始化方法 =========================

    /**
     * 初始化 Actor 基础设施.
     */
    private ActorSystem initActorSystem(Options options) {
        ThreadPoolExecutor actorThreadPool = ThreadPoolBuilder.create()
                .setCorePoolSize(options.get(MasterConfigOptions.ACTOR_POOL_CORE_SIZE))
                .setMaxPoolSize(options.get(MasterConfigOptions.ACTOR_POOL_MAX_SIZE))
                .setKeepAliveSeconds(options.get(MasterConfigOptions.ACTOR_POOL_KEEP_ALIVE_TIME))
                .setQueueCapacity(options.get(MasterConfigOptions.ACTOR_POOL_CAPACITY))
                .setPoolName("actorThreadPool")
                .build();
        return new DefaultActorSystem(actorThreadPool, options);
    }

    /**
     * 初始化存储层：为每种存储包装缓存代理.
     *
     * @param externalStorage 外部存储
     * @param options         配置
     * @return 存储层
     */
    private MasterStorage initStorages(MasterStorage externalStorage, Options options) {
        CachedTriggerStorage cachedTriggerStorage = new CachedTriggerStorage(
                (externalStorage != null) ? externalStorage.getTriggerStorage() : new TriggerStorageMem(), options);
        CachedFlowStorage cachedFlowStorage = new CachedFlowStorage(
                (externalStorage != null) ? externalStorage.getFlowStorage() : new FlowStorageMem(), options);
        CachedTaskStorage cachedTaskStorage = new CachedTaskStorage(
                (externalStorage != null) ? externalStorage.getTaskStorage() : new TaskStorageMem(), options);
        CachedEventStorage cachedEventStorage = new CachedEventStorage(
                (externalStorage != null) ? externalStorage.getEventStorage() : new EventStorageMem(), options);
        return new MasterStorage(cachedTriggerStorage, cachedFlowStorage, cachedTaskStorage, cachedEventStorage);
    }

    /**
     * 初始化 Worker 组件：校验 taskExecutor.
     */
    private TaskExecutor initWorker(TaskExecutor taskExecutor) {
        if (taskExecutor == null) {
            log.warn("未指定任务执行分发器,若是测试或者示例运行,请使用 DummyTaskExecutor .");
            throw new IllegalArgumentException("未指定任务执行分发器.");
        }
        return taskExecutor;
    }

    /**
     * 初始化事件子系统.
     */
    private GlobalEventOperator initEvent(Options options) {
        ThreadPoolExecutor eventThreadPool = ThreadPoolBuilder.create()
                .setCorePoolSize(options.get(MasterConfigOptions.EVENT_POOL_CORE_SIZE))
                .setMaxPoolSize(options.get(MasterConfigOptions.EVENT_POOL_MAX_SIZE))
                .setKeepAliveSeconds(options.get(MasterConfigOptions.EVENT_POOL_KEEP_ALIVE_TIME))
                .setQueueCapacity(options.get(MasterConfigOptions.EVENT_POOL_CAPACITY))
                .setPoolName("eventThreadPool")
                .build();
        return new DefaultGlobalEventOperator(this.masterStorage.getEventStorage(), eventThreadPool, options);
    }

    /**
     * 初始化任务子系统.
     */
    private TaskAction initTask(ActorSystem actorSystem) {
        return new TaskAction(actorSystem, this.eventOperator, this.taskExecutor, this.masterStorage);
    }

    /**
     * 初始化流程子系统.
     */
    private FlowAction initFlow(ActorSystem actorSystem) {
        FlowAction flow = new FlowAction(actorSystem, this.eventOperator, this.masterStorage);
        flow.setTaskAction(this.taskAction);
        return flow;
    }

    /**
     * 初始化触发器线程池.
     */
    private ThreadPoolExecutor initTriggerThreadPool(Options options) {
        return ThreadPoolBuilder.create()
                .setCorePoolSize(options.get(MasterConfigOptions.TRIGGER_POOL_CORE_SIZE))
                .setMaxPoolSize(options.get(MasterConfigOptions.TRIGGER_POOL_MAX_SIZE))
                .setKeepAliveSeconds(options.get(MasterConfigOptions.TRIGGER_POOL_KEEP_ALIVE_TIME))
                .setQueueCapacity(options.get(MasterConfigOptions.TRIGGER_POOL_CAPACITY))
                .setPoolName("triggerThreadPool")
                .build();
    }

    /**
     * 初始化触发器子系统.
     */
    private TriggerInstanceHandler initTrigger(SchedulerTrigger schedulerTrigger, Options options) {
        TriggerStorage triggerStorage = this.masterStorage.getTriggerStorage();
        return new TriggerInstanceHandlerWithPolicy(schedulerTrigger, triggerStorage, options);
    }

    /**
     * 添加调度,根据 TriggerInfo 生成调度实例并加入缓存.
     *
     * <p>baseTime 会由 TriggerInfo.calScheduleTime 内部兜底：
     * 若 baseTime 早于 startTime，则自动以 startTime 为基准；
     * 若 baseTime 晚于 startTime，则以传入的 baseTime 为基准。
     * 这样触发器可在过去或未来的任意时间点触发.
     *
     * @param triggerInfo 触发器信息
     * @param baseTime    调度基准时间（毫秒时间戳）
     * @param included    是否包含基准时间点
     */
    public void addSchedule(TriggerInfo triggerInfo, long baseTime, boolean included) {
        if (triggerInfo != null) {
            triggerHandler.generateInstance(triggerInfo, baseTime, included);
        }
    }

    /**
     * 停止调度,将 triggerInfo 的 scheduleFlag 设置为 false.
     * 触发器线程在下一轮检查时会跳过该调度.
     *
     * @param payloadId 调度载体id（即 flowId）
     */
    public void stopSchedule(String payloadId) {
        TriggerInfo triggerInfo = masterStorage.getTriggerStorage().getTriggerInfo(payloadId);
        if (triggerInfo != null) {
            triggerInfo.setScheduleFlag(false);
            masterStorage.getTriggerStorage().saveTriggerInfo(triggerInfo);
            log.info("停止调度: payloadId={}", payloadId);
        } else {
            log.warn("停止调度失败,未找到调度信息: payloadId={}", payloadId);
        }
    }

    /**
     * 启动调度线程.
     */
    public void start() {
        this.generateTriggerThread.start();
        this.dispatchTriggerThread.start();
        log.info("调度线程启动成功");
    }

    /**
     * 停止调度线程.
     *
     * @return 是否停止成功
     */
    public boolean stop() {
        this.generateTriggerThread.close();
        this.dispatchTriggerThread.close();
        log.info("调度线程停止成功");
        return true;
    }
}
