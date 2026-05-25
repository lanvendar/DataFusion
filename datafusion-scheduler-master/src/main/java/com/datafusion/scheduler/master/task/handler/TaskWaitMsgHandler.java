package com.datafusion.scheduler.master.task.handler;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.lang.mutable.MutablePair;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import com.datafusion.scheduler.master.event.GlobalEventListener;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.task.TaskExecutor;
import com.datafusion.scheduler.master.task.TaskMsg;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 任务等待消息处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
@Slf4j
public class TaskWaitMsgHandler extends AbstractTaskMsgHandler {

    /**
     * 构造函数.
     *
     * @param taskStorage   任务存储
     * @param eventOperator 全局事件操作
     * @param taskExecutor  任务执行器
     */
    public TaskWaitMsgHandler(TaskStorage taskStorage, GlobalEventOperator eventOperator, TaskExecutor taskExecutor) {
        super(taskStorage, eventOperator, taskExecutor);
    }

    @Override
    public ActionType getActionType() {
        return ActionType.WAIT;
    }

    @Override
    public EnumSet<StatusEnum> getPreState() {
        //自动流转前置状态
        return EnumSet.of(StatusEnum.INIT_SUCCESS, StatusEnum.WAIT_DEPENDENT);
    }

    @Override
    public EnumSet<StatusEnum> getManualPreState() {
        //不支持手动触发
        return null;
    }

    @Override
    protected void handleAction(TaskMsg msg, ActorSysContext context) {
        TaskInstance taskIns = super.getTaskInstance(msg.getTaskInstanceId());
        //更新等待依赖状态
        StatusEnum state = taskIns.getState();
        if (StatusEnum.INIT_SUCCESS == state) {
            taskIns.setState(StatusEnum.WAIT_DEPENDENT);
            super.saveTaskInstance(taskIns);
        }

        //依赖不满足
        if (!checkLinkEvent(taskIns) || !checkGlobalEvent(taskIns, msg, context)) {
            return;
        }

        //TODO 资源等待逻辑,待实现
        /*taskIns.setState(StateEnum.WAIT_RESOURCES);
        super.saveTaskInstance(taskIns);
        super.noticeFlowActor(taskIns.getFlowInstanceId(), taskIns.getInstanceId(), StateEnum.WAIT_RESOURCES);*/

        //进入提交阶段
        TaskMsg submitMsg = TaskMsg.builder()//
                .flowInstanceId(taskIns.getFlowInstanceId())
                .taskInstanceId(taskIns.getInstanceId())//
                .actionType(ActionType.SUBMIT)//
                .isManualAction(false)//
                .build();
        context.notify(submitMsg);
    }

    @Override
    protected void handleManualAction(TaskMsg msg, ActorSysContext context) {
        log.error("不可能发生!!!程序异常!!!");
    }

    /**
     * 检查流程依赖.
     *
     * @param taskIns 任务实例
     * @return 检查结果
     */
    private boolean checkLinkEvent(TaskInstance taskIns) {
        log.debug("[{}] - 任务实例, 任务依赖事件检查", taskIns.getInstanceId());
        List<Pair<String, Boolean>> internalDependents = new ArrayList<>();

        Set<String> preInstances = taskIns.getLastInstanceIds();
        if (CollectionUtil.isNotEmpty(preInstances)) {
            for (String preInsId : preInstances) {
                MutablePair<String, Boolean> pair = new MutablePair<>(preInsId, true);
                TaskInstance preIns = super.getTaskInstance(preInsId);
                StatusEnum state = preIns.getState();
                if (null == state || !state.isSuccess()) {
                    pair.setValue(false);
                }
                internalDependents.add(pair);
            }
        }

        if (CollectionUtil.isEmpty(internalDependents)) {
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (Pair<String, Boolean> pair : internalDependents) {
            sb.append(String.format("| %-32s | %5b |\n", pair.getKey(), pair.getValue()));
        }
        log.debug("任务实例:taskIns=[{}],依赖检查\n {}", taskIns.getInstanceId(), sb);
        return internalDependents.stream().allMatch(Pair::getValue);
    }

    /**
     * 检查任务实例全局依赖.
     *
     * @param taskIns 任务实例
     * @param msg     消息
     * @param context actor 上下文
     * @return 检查加过
     */
    private boolean checkGlobalEvent(TaskInstance taskIns, TaskMsg msg, ActorSysContext context) {
        if (taskIns.eventTime() == null || taskIns.eventTime() <= 0 || CollectionUtil.isEmpty(taskIns.getDepEventIds())) {
            log.debug("[{}] - 任务检查全局事件条件不满足，跳过检查", taskIns.getInstanceId());
            return true;
        }

        for (String eventId : taskIns.getDepEventIds()) {
            Pair<String, Long> eventKey = Pair.of(eventId, taskIns.eventTime());
            if (!super.eventOperator.checkEvents(eventKey, taskIns.eventTime())) {
                log.debug("[{}] - 事件还未发生,eventKey = {} ", taskIns.getInstanceId(), eventKey);
                GlobalEventListener listener = (event) -> {
                    log.debug("[{}] -  事件已发生,通知自身重新处理TASK_WAIT消息", taskIns.getInstanceId());
                    context.notify(msg);
                };
                super.eventOperator.registerListener(eventKey, listener);
                return false;
            }
        }
        return true;
    }
}
