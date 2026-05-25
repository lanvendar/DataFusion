package com.datafusion.scheduler.master.task.storage;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.MasterConfigOptions;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.model.TaskLink;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 任务存储缓存装饰器.
 * 基于 Caffeine 缓存提供高性能的查询能力.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class CachedTaskStorage implements TaskStorage {

    /**
     * task info 的缓存.
     */
    private final LoadingCache<String, TaskInfo> taskInfoCache;

    /**
     * task link 缓存, key = flowId.
     */
    private final LoadingCache<String, List<TaskLink>> taskLinkCache;

    /**
     * task instance 的缓存.
     */
    private final LoadingCache<String, TaskInstance> taskInstanceCache;

    /**
     * Task实例持久化服务.
     */
    private final TaskStorage taskStorage;

    /**
     * 默认构造函数.
     */
    public CachedTaskStorage() {
        this(new TaskStorageMem(), new Options());
    }

    /**
     * 带参数的构造函数.
     *
     * @param taskStorage 任务持久化服务
     * @param options     配置信息
     */
    public CachedTaskStorage(TaskStorage taskStorage, Options options) {
        this.taskStorage = taskStorage;
        int taskCacheMaxSize = options.get(MasterConfigOptions.TASK_INSTANCE_CACHE_MAX_SIZE);
        this.taskInstanceCache = Caffeine.newBuilder().maximumSize(taskCacheMaxSize).build(taskStorage::getInstanceById);
        this.taskInfoCache = Caffeine.newBuilder().maximumSize(taskCacheMaxSize).build(taskStorage::getTaskInfo);
        this.taskLinkCache = Caffeine.newBuilder().maximumSize(taskCacheMaxSize)
                .build(taskStorage::getTaskInfoLink);
    }

    @Override
    public TaskInfo getTaskInfo(String taskId) {
        return this.taskInfoCache.get(taskId);
    }

    @Override
    public List<TaskLink> getTaskInfoLink(String flowId) {
        return this.taskLinkCache.get(flowId);
    }

    @Override
    public List<TaskInfo> getTaskInfoByFlowId(String flowId) {
        List<TaskInfo> taskInfoByFlowId = taskStorage.getTaskInfoByFlowId(flowId);
        if (CollectionUtil.isNotEmpty(taskInfoByFlowId)) {
            for (TaskInfo taskInfo : taskInfoByFlowId) {
                if (taskInfo != null) {
                    //caffeine 二级缓存
                    this.taskInfoCache.asMap().putIfAbsent(taskInfo.getTaskId(), taskInfo);
                }
            }
        }
        return taskInfoByFlowId;
    }

    @Override
    public TaskInstance getInstanceById(String taskInsId) {
        return this.taskInstanceCache.get(taskInsId);
    }

    @Override
    public void saveInstance(TaskInstance taskInstance) {
        //原子性的持久化task instance并更新缓存
        this.taskInstanceCache.asMap().compute(taskInstance.getInstanceId(), (k, v) -> {
            this.taskStorage.saveInstance(taskInstance);
            if (v != null) {
                BeanUtil.copyProperties(taskInstance, v, CopyOptions.create().setIgnoreNullValue(true));
                return v;
            } else {
                return taskStorage.getInstanceById(k);
            }
        });
    }

    @Override
    public void removeInstanceById(String taskInsId) {
        taskStorage.removeInstanceById(taskInsId);
        taskInstanceCache.invalidate(taskInsId);
    }

    @Override
    public List<TaskInstance> getTaskInsIdsByFlowInsId(String flowInsId) {
        List<TaskInstance> taskInstances = taskStorage.getTaskInsIdsByFlowInsId(flowInsId);
        if (CollectionUtil.isNotEmpty(taskInstances)) {
            for (TaskInstance taskInstance : taskInstances) {
                if (taskInstance != null) {
                    //caffeine 二级缓存
                    this.taskInstanceCache.asMap().putIfAbsent(taskInstance.getInstanceId(), taskInstance);
                }
            }
        }
        return taskInstances;
    }

    @Override
    public void removeTaskInsByFlowInsId(String flowInsId) {
        List<TaskInstance> taskInstances = this.taskStorage.getTaskInsIdsByFlowInsId(flowInsId);
        this.taskStorage.removeTaskInsByFlowInsId(flowInsId);
        //清理缓存
        if (CollectionUtil.isNotEmpty(taskInstances)) {
            for (TaskInstance taskInstance : taskInstances) {
                this.taskInstanceCache.invalidate(taskInstance.getInstanceId());
            }
        }
    }
}
