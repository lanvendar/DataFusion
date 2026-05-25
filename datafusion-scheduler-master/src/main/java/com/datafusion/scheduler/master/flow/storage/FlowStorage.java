package com.datafusion.scheduler.master.flow.storage;

import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;

import java.util.List;

/**
 * 流程存储接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
public interface FlowStorage {

    //region flowInfo处理

    /**
     * 根据流程ID获取流程信息.
     *
     * @param flowId 流程ID
     * @return 流程信息
     */
    FlowInfo getFlowInfo(String flowId);

    /**
     * 根据所有已发布流程调度信息.
     *
     * @return 已发布流程调度信息
     */
    List<FlowInfo> getAllFlowInfo();
    //endregion
    //region flowInstance处理

    /**
     * 获取流程实例.
     *
     * @param flowInsId 流程实例ID
     * @return 流程实例
     */
    FlowInstance getInstanceById(String flowInsId);

    /**
     * 更新流程实例.
     *
     * @param flowIns 流程实例
     */
    void saveInstance(FlowInstance flowIns);

    /**
     * 删除流程实例.
     *
     * @param flowInsId 流程实例id
     */
    void removeInstanceById(String flowInsId);

    /**
     * 获取所有可用流程实例.
     *
     * @param flowId 流程id,当flowId为空则获取所有流程实例
     * @return 所有可重新加载的实例
     */
    List<FlowInstance> getAvailableInstance(String flowId);

    /**
     * 获取流程实例.
     *
     * @param flowId  流程信息ID
     * @param version 流程版本
     * @return 流程实例
     */
    FlowInstance getLastInstance(String flowId, String version);
    //endregion
}
