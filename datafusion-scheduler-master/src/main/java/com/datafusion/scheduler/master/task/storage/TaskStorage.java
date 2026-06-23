package com.datafusion.scheduler.master.task.storage;

import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.model.TaskLink;

import java.util.List;

/**
 * 任务存储接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
public interface TaskStorage {

    //region taskInfo处理

    /**
     * 获取Task信息.
     *
     * @param taskId 任务主键id
     * @return TaskInfo
     */
    TaskInfo getTaskInfo(String taskId);

    /**
     * 根据flow id获取Task依赖关系的集合.
     *
     * @param flowId 流程主键id
     * @return TaskInfo集合
     */
    List<TaskLink> getTaskInfoLink(String flowId);

    /**
     * 根据flow id获取Task信息.
     *
     * @param flowId 流程主键id
     * @return TaskInfo集合
     */
    List<TaskInfo> getTaskInfoByFlowId(String flowId);

    /**
     * 失效指定流程的任务定义缓存.
     *
     * @param flowId 流程主键id
     */
    default void invalidateTaskInfoByFlowId(String flowId) {
    }
    //endregion
    //region taskInstance处理

    /**
     * 根据task instance id获取Task instance.
     *
     * @param taskInsId task instance id
     * @return Task instance
     */
    TaskInstance getInstanceById(String taskInsId);

    /**
     * 保存Task实例.
     *
     * @param taskInstance Task实例
     */
    void saveInstance(TaskInstance taskInstance);

    /**
     * 删除任务实例.
     *
     * @param taskInsId 任务实例id
     */
    void removeInstanceById(String taskInsId);
    //endregion
    //region flowInstance与taskInstance关系处理

    /**
     * 根据流程实例id获取任务实例id的集合.
     *
     * @param flowInsId 流程实例id
     * @return 获取任务实例id的set集合
     */
    List<TaskInstance> getTaskInsIdsByFlowInsId(String flowInsId);

    /**
     * 删除flow instance对应的task instance.
     *
     * @param flowInsId flowInsId
     */
    void removeTaskInsByFlowInsId(String flowInsId);
    //endregion
}
