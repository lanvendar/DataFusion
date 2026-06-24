package com.datafusion.manager.config;

import com.datafusion.common.options.ConfigOption;
import com.datafusion.common.options.Options;
import com.datafusion.manager.scheduler.dao.TaskInstanceMapper;
import com.datafusion.manager.scheduler.dao.WorkerRegistryMapper;
import com.datafusion.manager.scheduler.master.task.HttpMasterTaskOperator;
import com.datafusion.manager.scheduler.storage.EventStorageImpl;
import com.datafusion.manager.scheduler.storage.FlowStorageImpl;
import com.datafusion.manager.scheduler.storage.TaskStorageImpl;
import com.datafusion.manager.scheduler.storage.TriggerStorageImpl;
import com.datafusion.manager.scheduler.storage.WorkerStorageImpl;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.MasterService;
import com.datafusion.scheduler.master.MasterStorage;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.worker.WorkerListener;
import com.datafusion.scheduler.worker.WorkerManager;
import com.datafusion.scheduler.worker.WorkerOperator;
import com.datafusion.scheduler.worker.storage.CachedWorkerStorage;
import com.datafusion.scheduler.worker.storage.WorkerStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/**
 * 调度 master Spring 配置.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class SchedulerMasterConfig {

    /**
     * Spring 环境配置.
     */
    private final Environment environment;

    /**
     * 流程存储实现.
     */
    private final FlowStorageImpl flowStorage;

    /**
     * 任务存储实现.
     */
    private final TaskStorageImpl taskStorage;

    /**
     * 触发器存储实现.
     */
    private final TriggerStorageImpl triggerStorage;

    /**
     * 事件存储实现.
     */
    private final EventStorageImpl eventStorage;

    /**
     * 组装 master 存储.
     *
     * @return MasterStorage
     */
    @Bean
    public MasterStorage masterStorage() {
        return new MasterStorage(triggerStorage, flowStorage, taskStorage, eventStorage);
    }

    /**
     * 创建调度配置.
     *
     * @return 调度配置
     */
    @Bean
    public Options masterOptions() {
        Options options = new Options();
        setIfPresent(options, MasterConfigOptions.ACTOR_POOL_CORE_SIZE);
        setIfPresent(options, MasterConfigOptions.ACTOR_POOL_MAX_SIZE);
        setIfPresent(options, MasterConfigOptions.ACTOR_POOL_KEEP_ALIVE_TIME);
        setIfPresent(options, MasterConfigOptions.ACTOR_POOL_CAPACITY);
        setIfPresent(options, MasterConfigOptions.ACTOR_MSG_POLL_NUM);
        setIfPresent(options, MasterConfigOptions.ACTOR_INIT_MAX_ATTEMPTS);
        setIfPresent(options, MasterConfigOptions.EVENT_POOL_CORE_SIZE);
        setIfPresent(options, MasterConfigOptions.EVENT_POOL_MAX_SIZE);
        setIfPresent(options, MasterConfigOptions.EVENT_POOL_KEEP_ALIVE_TIME);
        setIfPresent(options, MasterConfigOptions.EVENT_POOL_CAPACITY);
        setIfPresent(options, MasterConfigOptions.EVENT_RETAIN_NUM);
        setIfPresent(options, MasterConfigOptions.EVENT_RETAIN_TIME);
        setIfPresent(options, MasterConfigOptions.TRIGGER_POOL_CORE_SIZE);
        setIfPresent(options, MasterConfigOptions.TRIGGER_POOL_MAX_SIZE);
        setIfPresent(options, MasterConfigOptions.TRIGGER_POOL_KEEP_ALIVE_TIME);
        setIfPresent(options, MasterConfigOptions.TRIGGER_POOL_CAPACITY);
        setIfPresent(options, MasterConfigOptions.PREPARED_MS);
        setIfPresent(options, MasterConfigOptions.BATCH_READ_COUNT);
        setIfPresent(options, MasterConfigOptions.POLL_INTERVAL);
        setIfPresent(options, MasterConfigOptions.FLOW_INSTANCE_CACHE_MAX_SIZE);
        setIfPresent(options, MasterConfigOptions.TASK_INSTANCE_CACHE_MAX_SIZE);
        setIfPresent(options, MasterConfigOptions.EVENT_INSTANCE_CACHE_MAX_SIZE);
        return options;
    }

    /**
     * 创建 WorkerStorage.
     *
     * @param options 调度配置
     * @return WorkerStorage
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkerStorage schedulerWorkerStorage(WorkerRegistryMapper workerRegistryMapper,
                                                TaskInstanceMapper taskInstanceMapper, Options options) {
        return new CachedWorkerStorage(new WorkerStorageImpl(workerRegistryMapper, taskInstanceMapper), options);
    }

    /**
     * 创建 WorkerManager.
     *
     * @param schedulerWorkerStorage worker 存储
     * @return WorkerManager
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkerManager workerManager(WorkerStorage schedulerWorkerStorage) {
        return new WorkerManager(schedulerWorkerStorage);
    }

    /**
     * 创建 WorkerListener.
     *
     * @param workerManager worker 管理器
     * @return WorkerListener
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(WorkerListener.class)
    public WorkerListener workerListener(WorkerManager workerManager) {
        return workerManager;
    }

    /**
     * 创建 WorkerOperator.
     *
     * @param workerManager worker 管理器
     * @return WorkerOperator
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(WorkerOperator.class)
    public WorkerOperator workerOperator(WorkerManager workerManager) {
        return workerManager;
    }

    /**
     * 创建 MasterTaskOperator.
     *
     * @param workerListener worker 运行时服务
     * @return MasterTaskOperator
     */
    @Bean
    @ConditionalOnMissingBean
    public MasterTaskOperator masterTaskOperator(WorkerListener workerListener) {
        return new HttpMasterTaskOperator(workerListener);
    }

    /**
     * 创建 MasterService 调度引擎入口.
     *
     * @param masterTaskOperator 任务执行器
     * @param masterStorage      综合存储
     * @param options            调度配置
     * @return MasterService
     */
    @Bean(destroyMethod = "stop")
    public MasterService masterService(MasterTaskOperator masterTaskOperator, MasterStorage masterStorage,
                                       Options options) {
        return new MasterService(masterTaskOperator, masterStorage, options);
    }

    private void setIfPresent(Options options, ConfigOption<?> option) {
        String value = environment.getProperty(option.key());
        if (value != null) {
            options.setString(option.key(), value);
        }
    }
}
