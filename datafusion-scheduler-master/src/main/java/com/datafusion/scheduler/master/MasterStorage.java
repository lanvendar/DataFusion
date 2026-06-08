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
     * 失效触发器信息及其派生依赖的流程信息缓存.
     *
     * @param payloadId 调度载体id（即 flowId）
     */
    public void invalidateTriggerInfo(String payloadId) {
        triggerStorage.invalidateTriggerInfo(payloadId);
        flowStorage.invalidateFlowInfo(payloadId);
    }
}
