package com.datafusion.scheduler.master.example;

import com.datafusion.common.google.BeanTableConverter;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.google.common.collect.Table;

/**
 * 简单流程夹具：单触发器 + 单任务.
 *
 * <pre>
 * 触发器策略: SERIAL_WAIT（顺序执行）
 * CRON: 每分钟执行一次
 * DAG:  [TaskA]  （无依赖，单独执行）
 * </pre>
 */
public final class SimpleFlowExample {

    public static final String FLOW_ID = "simple_flow_id";
    public static final String FLOW_NAME = "简单流程";
    public static final String VERSION = "v1";

    public static final String TRIGGER_ID = "simple_trigger_id";
    public static final String CRON_EXPRESSION = "0 0/1 * * * ?";

    public static final String TASK_A_ID = "simple_task_a";
    public static final String TASK_A_NAME = "任务A";
    public static final String TASK_A_TYPE = "SHELL";

    private SimpleFlowExample() {
    }

    /**
     * 向存储表中写入简单流程的全部测试数据.
     *
     * @param triggerInfoTable 触发器信息表
     * @param flowInfoTable    流程信息表
     * @param taskInfoTable    任务信息表
     * @param triggerFactory   触发器工厂方法
     * @param flowFactory      流程工厂方法
     * @param taskFactory      任务工厂方法
     * @param scheduleFlag     是否启用调度
     */
    public static void load(Table<String, String, Object> triggerInfoTable,
                            Table<String, String, Object> flowInfoTable,
                            Table<String, String, Object> taskInfoTable,
                            TriggerFactory triggerFactory,
                            FlowFactory flowFactory,
                            TaskFactory taskFactory,
                            boolean scheduleFlag) {
        // 触发器：CRON类型，策略为 SERIAL_WAIT（顺序执行）
        TriggerInfo triggerInfo = triggerFactory.create(
                FLOW_ID, TRIGGER_ID, VERSION,
                TriggerTypeEnum.CRON, CRON_EXPRESSION, TriggerPolicyEnum.SERIAL_WAIT);
        triggerInfo.setScheduleFlag(scheduleFlag);
        BeanTableConverter.addData(triggerInfoTable, triggerInfo, "payloadId");

        // 流程
        FlowInfo flowInfo = flowFactory.create(FLOW_ID, FLOW_NAME, VERSION);
        BeanTableConverter.addData(flowInfoTable, flowInfo, "flowId");

        // 任务A（单任务，无依赖）
        TaskInfo taskA = taskFactory.create(TASK_A_ID, FLOW_ID, TASK_A_NAME, TASK_A_TYPE, "");
        BeanTableConverter.addData(taskInfoTable, taskA, "taskId");
    }
}
