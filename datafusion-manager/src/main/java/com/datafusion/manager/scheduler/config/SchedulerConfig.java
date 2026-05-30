package com.datafusion.manager.scheduler.config;

import com.datafusion.manager.scheduler.storage.EventStorageImpl;
import com.datafusion.manager.scheduler.storage.FlowStorageImpl;
import com.datafusion.manager.scheduler.storage.TaskStorageImpl;
import com.datafusion.manager.scheduler.storage.TriggerStorageImpl;
import com.datafusion.scheduler.master.MasterStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 调度引擎Spring配置, 组装MasterStorage和MasterService Bean.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {
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
     * 组装MasterStorage, 聚合所有Storage实现.
     *
     * @return MasterStorage
     */
    @Bean
    public MasterStorage masterStorage() {
        return new MasterStorage(triggerStorage, flowStorage, taskStorage, eventStorage);
    }

    /**
     * 创建MasterService调度引擎入口.
     * initMethod=start 容器启动时自动开始调度.
     * destroyMethod=stop 容器关闭时优雅停止.
     *
     * @param masterTaskOperator  任务执行器
     * @param masterStorage 综合存储
     * @return MasterService
     */
    /*@Bean(initMethod = "start", destroyMethod = "stop")
    public MasterService masterService(MasterTaskOperator masterTaskOperator, MasterStorage masterStorage) {
        return new MasterService(masterTaskOperator, masterStorage, new Options());
    }*/
}
