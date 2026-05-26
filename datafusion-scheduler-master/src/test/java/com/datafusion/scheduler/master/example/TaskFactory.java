package com.datafusion.scheduler.master.example;

import com.datafusion.scheduler.master.task.model.TaskInfo;

/**
 * 任务创建工厂接口，由测试类实现以复用已有的工厂方法.
 */
@FunctionalInterface
public interface TaskFactory {

    /**
     * 创建任务信息.
     */
    TaskInfo create(String taskId, String flowId, String taskName, String taskType, String eventId);
}
