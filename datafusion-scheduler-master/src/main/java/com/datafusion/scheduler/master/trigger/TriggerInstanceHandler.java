package com.datafusion.scheduler.master.trigger;

import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;

/**
 * 调度实例处理器接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/12/25
 * @since 2024/12/25
 */
public interface TriggerInstanceHandler extends TriggerInstanceGenerator {

    /**
     * 获取调度触发器.
     *
     * @return 调度触发器
     */
    SchedulerTrigger getSchedulerTrigger();

    /**
     * 校验调度是否可用.
     *
     * @param triggerInstance 调度实例
     * @return true表示可用, false表示不可用
     */
    boolean checkScheduleAvailable(TriggerInstance triggerInstance);

    /**
     * 获取最新调度实例状态.
     *
     * @param scheduleInsId 调度实例id
     * @return 调度实例状态
     */
    StatusEnum getScheduleInstanceState(String scheduleInsId);

    /**
     * 保存上一次调度实例.
     *
     * @param lastTriggerInstance 调度实例
     */
    void saveLastScheduleInstance(TriggerInstance lastTriggerInstance);

    /**
     * 保存调度实例.
     *
     * @param triggerInstance 调度实例
     */
    void saveTriggerInstance(TriggerInstance triggerInstance);

    /**
     * 获取调度策略信息.
     *
     * @param payloadId 调度载体id
     * @return 调度策略信息
     */
    TriggerInfo getTriggerInfo(String payloadId);
}
