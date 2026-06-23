package com.datafusion.scheduler.master;

import com.datafusion.scheduler.master.event.storage.EventStorage;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 综合对象存储类包装,包含触发器存储, 流程存储, 任务存储, 事件存储.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/28
 * @since 2026/2/28
 */
@Data
@AllArgsConstructor
public class MasterStorage {
    /**
     * 触发器存储.
     */
    private final TriggerStorage triggerStorage;

    /**
     * 流程存储.
     */
    private final FlowStorage flowStorage;

    /**
     * 任务存储.
     */
    private final TaskStorage taskStorage;

    /**
     * 事件存储.
     */
    private final EventStorage eventStorage;

    /**
     * 失效调度定义信息缓存.
     *
     * @param flowId 流程id
     */
    public void invalidateSchedulerInfo(String flowId) {
        triggerStorage.invalidateTriggerInfo(flowId);
        flowStorage.invalidateFlowInfo(flowId);
        taskStorage.invalidateTaskInfoByFlowId(flowId);
    }
}
