package com.datafusion.scheduler.master.trigger;

import com.datafusion.scheduler.master.trigger.model.TriggerInstance;

/**
 * 调度触发动作接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/9/6
 * @since 2024/9/6
 */
public interface SchedulerTrigger {
    /**
     * 初始化动作.
     *
     * @param triggerInstance 调度器缓存实例
     */
    void fetchInit(TriggerInstance triggerInstance);

    /**
     * 提交动作.
     *
     * @param triggerInstance 泛型实例
     */
    void dispatchSubmit(TriggerInstance triggerInstance);

    /**
     * 覆盖执行策略,停止调度实例.
     *
     * @param payloadId 调度对象id
     * @param delayTime 延迟时间
     */
    void killDelay(String payloadId, long delayTime);

}
