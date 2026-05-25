package com.datafusion.scheduler.master.trigger.storage;

import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;

import java.util.List;

/**
 * 触发器存储接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/10/29
 * @since 2024/10/29
 */
public interface TriggerStorage {
    /**
     * 获取已调度的所有调度.
     *
     * @return 已调度流程的集合.
     */
    List<TriggerInfo> getAllScheduledTriggerInfo();

    /**
     * 获取调度策略信息.
     *
     * @param payloadId 调度载体id
     * @return 调度策略信息
     */
    TriggerInfo getTriggerInfo(String payloadId);

    /**
     * 保存调度策略信息.
     *
     * @param triggerInfo 调度策略信息
     */
    void saveTriggerInfo(TriggerInfo triggerInfo);

    /**
     * 获取调度器实例.
     *
     * @param scheduleInsId 调度器实例id
     * @return 调度器实例
     */
    TriggerInstance getTriggerInstance(String scheduleInsId);

    /**
     * 获取调度器实例.
     *
     * @param payloadId 调度载体id
     * @param version   调度载体版本
     * @return 调度器实例
     */
    TriggerInstance getLastTriggerInstance(String payloadId, String version);

    /**
     * 保存调度实例.
     *
     * @param triggerInstance 调度实例.
     */
    void saveTriggerInstance(TriggerInstance triggerInstance);
}
