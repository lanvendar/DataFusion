package com.datafusion.manager.scheduler.master.task;

import com.datafusion.scheduler.exception.SchedulerException;
import com.datafusion.scheduler.master.task.MasterTaskOperator;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.model.TaskResult;

/**
 * Load balance master task operator.
 *
 * @author david
 * @version 3.6.4, 2024/10/29
 * @since 3.6.4, 2024/10/29
 */
public class LoadBalanceMasterTaskOperator implements MasterTaskOperator {

    @Override
    public TaskResult submitTask(TaskInstance taskIns) throws SchedulerException {
        return null;
    }

    @Override
    public TaskResult stopTask(TaskInstance taskIns) throws SchedulerException {
        return null;
    }

    @Override
    public TaskResult killTask(TaskInstance taskIns) throws SchedulerException {
        return null;
    }

    @Override
    public boolean finishTask(TaskInstance taskIns) throws SchedulerException {
        return true;
    }
}
