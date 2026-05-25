package com.datafusion.scheduler.master.task.storage;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.google.BeanTableConverter;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.model.TaskLink;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 任务内存存储实现.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskStorageMem implements TaskStorage {

    /**
     * task instance 的 table.
     */
    private final Table<String, String, Object> taskInstanceTable;

    /**
     * task info 的 table.
     */
    private final Table<String, String, Object> taskInfoTable;

    /**
     * task info 的 table.
     */
    private final Table<String, String, Object> taskLinkTable;

    /**
     * 默认构造器.
     */
    public TaskStorageMem() {
        this(HashBasedTable.create(), HashBasedTable.create(), HashBasedTable.create());
    }

    /**
     * 自定义构造器.
     *
     * @param taskInstanceTable task instance 的 table.
     * @param taskInfoTable     task info 的 table.
     * @param taskLinkTable     task link 的 table.
     */
    public TaskStorageMem(Table<String, String, Object> taskInstanceTable, Table<String, String, Object> taskInfoTable,
                          Table<String, String, Object> taskLinkTable) {
        this.taskInstanceTable = taskInstanceTable;
        this.taskInfoTable = taskInfoTable;
        this.taskLinkTable = taskLinkTable;
    }

    @Override
    public TaskInfo getTaskInfo(String taskId) {
        return BeanTableConverter .queryData(taskInfoTable, TaskInfo.class, taskId);
    }

    @Override
    public List<TaskLink> getTaskInfoLink(String flowId) {
        return BeanTableConverter.queryDataByOneColumn(taskLinkTable, TaskLink.class, "flowId", flowId);
    }

    @Override
    public List<TaskInfo> getTaskInfoByFlowId(String flowId) {
        return BeanTableConverter.queryDataByOneColumn(taskInfoTable, TaskInfo.class, "flowId", flowId);
    }

    @Override
    public TaskInstance getInstanceById(String taskInsId) {
        return BeanTableConverter.queryData(taskInstanceTable, TaskInstance.class, taskInsId);
    }

    @Override
    public void saveInstance(TaskInstance taskInstance) {
        TaskInstance instance =
                BeanTableConverter.queryData(taskInstanceTable, TaskInstance.class, taskInstance.getInstanceId());
        if (instance == null) {
            BeanTableConverter.addData(taskInstanceTable, taskInstance, "instanceId");
        } else {
            BeanTableConverter.updateData(taskInstanceTable, taskInstance, taskInstance.getInstanceId(), "instanceId");
        }
    }

    @Override
    public void removeInstanceById(String taskInsId) {
        BeanTableConverter.removeRow(taskInstanceTable, taskInsId);
    }

    @Override
    public List<TaskInstance> getTaskInsIdsByFlowInsId(String flowInsId) {
        return BeanTableConverter.queryDataByOneColumn(taskInstanceTable, TaskInstance.class, "flowInstanceId", flowInsId);
    }

    @Override
    public void removeTaskInsByFlowInsId(String flowInsId) {
        List<TaskInstance> taskInstances = getTaskInsIdsByFlowInsId(flowInsId);
        if (CollectionUtil.isNotEmpty(taskInstances)) {
            for (TaskInstance taskInstance : taskInstances) {
                BeanTableConverter.removeRow(taskInstanceTable, taskInstance.getInstanceId());
            }
        }
    }
}
