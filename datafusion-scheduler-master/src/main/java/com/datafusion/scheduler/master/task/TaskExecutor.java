package com.datafusion.scheduler.master.task;

import com.datafusion.scheduler.exception.SchedulerException;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.model.TaskResult;

/**
 * 任务动作执行接口(master/manager端实现).
 * 对应 TaskExecutorHandler
 *
 * @author lanvendar
 * @version 3.0.0, 2022/8/5
 * @since 2022/8/5
 */
public interface TaskExecutor {

    /**
     * 提交任务.
     *
     * @param taskIns task实例
     * @return task执行结果
     */
    TaskResult runTask(TaskInstance taskIns) throws SchedulerException;

    /**
     * 停止任务.
     *
     * @param taskIns task任务实例
     * @return task执行结果
     */
    TaskResult stopTask(TaskInstance taskIns) throws SchedulerException;

    /**
     * kill任务.
     *
     * @param taskIns task任务实例
     * @return task执行结果
     */
    TaskResult killTask(TaskInstance taskIns) throws SchedulerException;

    /**
     * 任务运行成功之后的一些动作,例如:清理日志,关闭第三方的接口等操作.
     *
     * @param taskIns task任务实例
     * @return task执行结果
     */
    TaskResult finishTask(TaskInstance taskIns) throws SchedulerException;
}
