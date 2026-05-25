package com.datafusion.scheduler.master.flow.storage;

import com.datafusion.common.google.BeanTableConverter;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

/**
 * 流程内存存储实现.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class FlowStorageMem implements FlowStorage {

    /**
     * flow instance 的 table.
     */
    private final Table<String, String, Object> flowInstanceTable;

    /**
     * flow flowInfo 的 table.
     */
    private final Table<String, String, Object> flowInfoTable;

    /**
     * 调度信息管理存储.
     */
    private TriggerStorage triggerStorage;

    /**
     * 流程和调度相结合的的构造函数.
     *
     * @param triggerStorage   存储器
     * @param flowInstanceTable flow instance 的 table.
     * @param flowInfoTable     flow flowInfo 的 table.
     */
    public FlowStorageMem(TriggerStorage triggerStorage, Table<String, String, Object> flowInstanceTable,
                          Table<String, String, Object> flowInfoTable) {
        this.triggerStorage = triggerStorage;
        this.flowInstanceTable = flowInstanceTable;
        this.flowInfoTable = flowInfoTable;
    }

    /**
     * 默认构造函数.
     */
    public FlowStorageMem() {
        this(HashBasedTable.create(), HashBasedTable.create());
    }

    /**
     * 构造函数.
     *
     * @param flowInstanceTable flow instance 的 table.
     * @param flowInfoTable     flow flowInfo 的 table.
     */
    public FlowStorageMem(Table<String, String, Object> flowInstanceTable, Table<String, String, Object> flowInfoTable) {
        this.flowInstanceTable = flowInstanceTable;
        this.flowInfoTable = flowInfoTable;
    }

    @Override
    public FlowInfo getFlowInfo(String flowId) {
        return BeanTableConverter.queryData(flowInfoTable, FlowInfo.class, flowId);
    }

    @Override
    public List<FlowInfo> getAllFlowInfo() {
        return BeanTableConverter.queryAllData(flowInfoTable, FlowInfo.class);
    }

    @Override
    public FlowInstance getInstanceById(String flowInsId) {
        return BeanTableConverter.queryData(flowInstanceTable, FlowInstance.class, flowInsId);
    }

    @Override
    public void saveInstance(FlowInstance flowIns) {
        FlowInstance instance =
                BeanTableConverter.queryData(flowInstanceTable, FlowInstance.class, flowIns.getInstanceId());
        if (instance == null) {
            BeanTableConverter.addData(flowInstanceTable, flowIns, "instanceId");
        } else {
            BeanTableConverter.updateData(flowInstanceTable, flowIns, flowIns.getInstanceId(), "instanceId");
        }
        // also save to trigger storage
        triggerStorage.saveTriggerInstance(flowIns2ScheduleIns(flowIns));
    }

    /**
     * 将FlowInstance转换为ScheduleInstance.
     *
     * @param flowIns 流程实例
     * @return 调度实例
     */
    private TriggerInstance flowIns2ScheduleIns(FlowInstance flowIns) {
        TriggerInstance triggerInstance = new TriggerInstance();
        triggerInstance.setPayloadId(flowIns.getFlowId());
        triggerInstance.setInstanceId(flowIns.getInstanceId());
        triggerInstance.setScheduleTime(flowIns.getScheduleTime());
        triggerInstance.setVersion(flowIns.getVersion());
        //triggerInstance.setScheduleSign(flowIns.getScheduleSign());
        triggerInstance.setState(flowIns.getState());
        return triggerInstance;
    }

    @Override
    public void removeInstanceById(String flowInsId) {
        BeanTableConverter.removeRow(flowInstanceTable, flowInsId);
        //TODO 删除调度实例
    }

    @Override
    public List<FlowInstance> getAvailableInstance(String flowId) {
        if (flowId == null) {
            return BeanTableConverter.queryAllData(flowInstanceTable, FlowInstance.class);
        } else {
            return BeanTableConverter.queryDataByOneColumn(flowInstanceTable, FlowInstance.class, "flowId", flowId);
        }
    }

    @Override
    public FlowInstance getLastInstance(String flowId, String version) {
        List<FlowInstance> instances = BeanTableConverter.queryDataByOneColumn(flowInstanceTable, FlowInstance.class, "flowId", flowId);
        //取第二大的scheduleTime
        // 按scheduleTime降序排序
        instances.sort(Comparator.comparing(FlowInstance::getScheduleTime).reversed());

        // 确保至少有两个实例才能获取第二大的scheduleTime
        if (instances.size() < 1) {
            log.info("Not enough instances[{}]", instances.size());
            return null;
        }
        //获取倒数第二个实例（即第二大scheduleTime对应的实例）
        FlowInstance ins = instances.get(0);
        return ins;
    }
}
