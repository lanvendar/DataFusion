package com.datafusion.scheduler.master.trigger.storage;

import com.datafusion.common.google.BeanTableConverter;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 触发器存储接口内存实现(默认).
 *
 * @author lanvendar
 * @version 1.0.0, 2024/10/29
 * @since 2024/10/29
 */
@Slf4j
@Getter
public class TriggerStorageMem implements TriggerStorage {
    /**
     * flow instance 的 table.
     */
    private final Table<String, String, Object> triggerInstanceTable;

    /**
     * flow flowInfo 的 table.
     */
    private final Table<String, String, Object> triggerInfoTable;

    /**
     * 默认构造函数.
     */
    public TriggerStorageMem() {
        this(HashBasedTable.create(), HashBasedTable.create());
    }

    /**
     * 构造函数.
     *
     * @param triggerInstanceTable schedule instance 的 table.
     * @param triggerInfoTable     schedule scheduleInfo 的 table.
     */
    public TriggerStorageMem(Table<String, String, Object> triggerInstanceTable, Table<String, String, Object> triggerInfoTable) {
        this.triggerInstanceTable = triggerInstanceTable;
        this.triggerInfoTable = triggerInfoTable;
    }

    @Override
    public List<TriggerInfo> getAllScheduledTriggerInfo() {
        return null;
    }

    @Override
    public TriggerInfo getTriggerInfo(String payloadId) {
        return BeanTableConverter.queryData(triggerInfoTable, TriggerInfo.class, payloadId);
    }

    @Override
    public void saveTriggerInfo(TriggerInfo triggerInfo) {
        TriggerInfo instance =
                BeanTableConverter.queryData(triggerInfoTable, TriggerInfo.class, triggerInfo.getPayloadId());
        if (instance == null) {
            BeanTableConverter.addData(triggerInfoTable, triggerInfo, "payloadId");
        } else {
            BeanTableConverter.updateData(triggerInfoTable, triggerInfo, triggerInfo.getPayloadId(), "payloadId");
        }
    }

    @Override
    public TriggerInstance getTriggerInstance(String scheduleInsId) {
        return BeanTableConverter.queryData(triggerInstanceTable, TriggerInstance.class, scheduleInsId);
    }

    @Override
    public TriggerInstance getLastTriggerInstance(String payloadId, String version) {
        List<TriggerInstance> list = BeanTableConverter.queryDataByOneColumn(triggerInstanceTable, TriggerInstance.class, "payloadId", payloadId);
        if (null == list) {
            log.info("Not enough instances[{}]", 0);
            return null;
        }
        list = list.stream().filter(scheduleInstance -> scheduleInstance.getVersion().equals(version))
                .sorted(Comparator.comparing(TriggerInstance::getScheduleTime).reversed())
                .collect(Collectors.toList());
        //返回倒数第一条
        return list.get(0);
    }

    @Override
    public void saveTriggerInstance(TriggerInstance triggerInstance) {
        TriggerInstance instance =
                BeanTableConverter.queryData(triggerInstanceTable, TriggerInstance.class, triggerInstance.getInstanceId());
        if (instance == null) {
            BeanTableConverter.addData(triggerInstanceTable, triggerInstance, "instanceId");
        } else {
            BeanTableConverter.updateData(triggerInstanceTable, triggerInstance, triggerInstance.getInstanceId(), "instanceId");
        }
    }
}
