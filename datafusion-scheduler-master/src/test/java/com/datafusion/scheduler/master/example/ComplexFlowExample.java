package com.datafusion.scheduler.master.example;

import com.datafusion.common.google.BeanTableConverter;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.google.common.collect.Table;

/**
 * 复杂流程夹具：单触发器 + 4任务.
 *
 * <pre>
 * 触发器策略: SERIAL_WAIT（顺序执行）
 * CRON: 每分钟执行一次
 * DAG:  A → B → C
 *             ↘ D
 * </pre>
 */
public final class ComplexFlowExample {

    public static final String FLOW_ID = "complex_flow_id";
    public static final String FLOW_NAME = "复杂流程";
    public static final String VERSION = "v1";

    public static final String TRIGGER_ID = "complex_trigger_id";
    public static final String CRON_EXPRESSION = "0 0/1 * * * ?";

    public static final String TASK_A_ID = "complex_task_a";
    public static final String TASK_A_NAME = "任务A";
    public static final String TASK_B_ID = "complex_task_b";
    public static final String TASK_B_NAME = "任务B";
    public static final String TASK_C_ID = "complex_task_c";
    public static final String TASK_C_NAME = "任务C";
    public static final String TASK_D_ID = "complex_task_d";
    public static final String TASK_D_NAME = "任务D";

    public static final String TASK_TYPE = "SHELL";

    public static final String LINK_A_TO_B_ID = "complex_link_a_b";
    public static final String LINK_B_TO_C_ID = "complex_link_b_c";
    public static final String LINK_B_TO_D_ID = "complex_link_b_d";

    private ComplexFlowExample() {
    }

    /**
     * 向存储表中写入复杂流程的全部测试数据.
     *
     * @param triggerInfoTable 触发器信息表
     * @param flowInfoTable    流程信息表
     * @param taskInfoTable    任务信息表
     * @param taskLinkTable    任务DAG边表
     * @param triggerFactory   触发器工厂方法
     * @param flowFactory      流程工厂方法
     * @param taskFactory      任务工厂方法
     * @param scheduleFlag     是否启用调度
     */
    public static void load(Table<String, String, Object> triggerInfoTable,
                            Table<String, String, Object> flowInfoTable,
                            Table<String, String, Object> taskInfoTable,
                            Table<String, String, Object> taskLinkTable,
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

        // 任务A（根节点）
        TaskInfo taskA = taskFactory.create(TASK_A_ID, FLOW_ID, TASK_A_NAME, TASK_TYPE, "");
        BeanTableConverter.addData(taskInfoTable, taskA, "taskId");

        // 任务B（A的下游）
        TaskInfo taskB = taskFactory.create(TASK_B_ID, FLOW_ID, TASK_B_NAME, TASK_TYPE, "");
        BeanTableConverter.addData(taskInfoTable, taskB, "taskId");

        // 任务C（B的下游，叶子节点）
        TaskInfo taskC = taskFactory.create(TASK_C_ID, FLOW_ID, TASK_C_NAME, TASK_TYPE, "");
        BeanTableConverter.addData(taskInfoTable, taskC, "taskId");

        // 任务D（B的下游，叶子节点）
        TaskInfo taskD = taskFactory.create(TASK_D_ID, FLOW_ID, TASK_D_NAME, TASK_TYPE, "");
        BeanTableConverter.addData(taskInfoTable, taskD, "taskId");

        // DAG: A → B
        BeanTableConverter.addData(taskLinkTable,
                new TaskLinkDto(LINK_A_TO_B_ID, TASK_A_ID, TASK_B_ID, FLOW_ID), "id");
        // DAG: B → C
        BeanTableConverter.addData(taskLinkTable,
                new TaskLinkDto(LINK_B_TO_C_ID, TASK_B_ID, TASK_C_ID, FLOW_ID), "id");
        // DAG: B → D
        BeanTableConverter.addData(taskLinkTable,
                new TaskLinkDto(LINK_B_TO_D_ID, TASK_B_ID, TASK_D_ID, FLOW_ID), "id");
    }
}
