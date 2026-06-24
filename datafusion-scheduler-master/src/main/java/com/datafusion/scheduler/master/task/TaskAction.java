package com.datafusion.scheduler.master.task;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.constant.SystemConstant;
import com.datafusion.common.graph.DagResolver;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterStorage;
import com.datafusion.scheduler.master.actor.Actor;
import com.datafusion.scheduler.master.actor.ActorProxy;
import com.datafusion.scheduler.master.actor.ActorSystem;
import com.datafusion.scheduler.master.event.GlobalEventOperator;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.task.handler.TaskEnforceSuccessMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskInitMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskKillMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskMsgHandlerRegister;
import com.datafusion.scheduler.master.task.handler.TaskRestartMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskRunMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskStopMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskSubmitMsgHandler;
import com.datafusion.scheduler.master.task.handler.TaskWaitMsgHandler;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.model.TaskLink;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.TaskResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 任务动作处理类.
 *
 * <p>
 * 1.实现 worker 执行端的状态上报处理
 * 2.实现用户手动指令操作
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/28
 * @since 2026/2/28
 */
@Slf4j
public class TaskAction implements TaskResultHandler {
    /**
     * 创建 Actor 系统.
     */
    private final ActorSystem actorSystem;

    /**
     * 综合存储.
     */
    private final MasterStorage masterStorage;

    /**
     * 全局事件操作类.
     *
     */
    private final GlobalEventOperator eventOperator;

    /**
     * 任务执行器.
     */
    private final MasterTaskOperator masterTaskOperator;

    /**
     * 创建任务消息处理器注册.
     */
    private final TaskMsgHandlerRegister msgHandlerRegister;

    /**
     * 构造函数.
     *
     * @param actorSystem   Actor 系统
     * @param eventOperator 全局事件操作类
     * @param masterTaskOperator  任务执行器
     * @param masterStorage 综合存储
     *
     */
    public TaskAction(ActorSystem actorSystem, GlobalEventOperator eventOperator, MasterTaskOperator masterTaskOperator,
                      MasterStorage masterStorage) {
        this.actorSystem = actorSystem;
        this.eventOperator = eventOperator;
        this.masterTaskOperator = masterTaskOperator;
        this.masterStorage = masterStorage;
        this.msgHandlerRegister = initTaskMsgHandlerRegister();
    }

    /**
     * 初始化任务消息处理器注册.
     *
     * @return 任务消息处理器注册
     */
    private TaskMsgHandlerRegister initTaskMsgHandlerRegister() {
        TaskStorage taskStorage = masterStorage.getTaskStorage();
        TaskMsgHandlerRegister register = new TaskMsgHandlerRegister();
        register.registerHandler(new TaskInitMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskWaitMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskSubmitMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskRunMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskStopMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskRestartMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskKillMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        register.registerHandler(new TaskEnforceSuccessMsgHandler(taskStorage, eventOperator, masterTaskOperator));
        return register;
    }

    /**
     * 任务初始化动作.
     *
     * @param triggerInstance 调度器缓存实例
     */
    public void fetchInit(TriggerInstance triggerInstance) {
        // 获取流程实例
        FlowInstance flowIns = masterStorage.getFlowStorage().getInstanceById(triggerInstance.getInstanceId());
        List<TaskInfo> taskInfos = masterStorage.getTaskStorage().getTaskInfoByFlowId(flowIns.getFlowId());
        // key: taskId ,value: taskInsId
        Map<String, String> taskIdToInsIdMap = new HashMap<>(2);
        for (TaskInfo taskInfo : taskInfos) {
            // flowInstanceId = payloadId(flowId) + "_" + scheduleTime + "_" + version 生成 uuid
            // taskInstanceId = flowInstanceId + "_" + taskId 生成 uuid
            String taskInsId = UUID.nameUUIDFromBytes((//
                    flowIns.getInstanceId() + SystemConstant.UNDER_LINE + taskInfo.getTaskId()).getBytes(StandardCharsets.UTF_8)).toString();
            // 构建消息
            TaskMsg msg = TaskMsg.builder()//
                    .flowInstanceId(triggerInstance.getInstanceId())//
                    .version(triggerInstance.getVersion())//
                    .scheduleTime(triggerInstance.getScheduleTime())//
                    .taskId(taskInfo.getTaskId())
                    .taskInstanceId(taskInsId)//
                    .actionType(ActionType.INIT)//
                    .isManualAction(false)//
                    .build();
            TaskMsgHandler handler = msgHandlerRegister.getHandler(ActionType.INIT);
            if (handler != null) {
                handler.handle(msg, null);
            }
            taskIdToInsIdMap.put(taskInfo.getTaskId(), taskInsId);
        }

        // 处理任务实例上下游的依赖关系（单任务流程可能无连线）
        List<TaskLink> taskInfoLink = masterStorage.getTaskStorage().getTaskInfoLink(flowIns.getFlowId());
        if (CollectionUtil.isEmpty(taskInfoLink)) {
            taskInfoLink = Collections.emptyList();
        }

        // 将 taskInfoLink (taskId -> taskId) 转换为 taskInsId -> taskInsId
        List<TaskLink> taskInsLinks = taskInfoLink.stream()
                .map(link -> new TaskLink(
                        taskIdToInsIdMap.get(link.getId()),
                        taskIdToInsIdMap.get(link.getStartId()),
                        taskIdToInsIdMap.get(link.getEndId())))
                .collect(Collectors.toList());

        // 构建 节点和边都是 taskInsId 的 DAG
        Set<String> taskInsIds = new HashSet<>(taskIdToInsIdMap.values());
        DagResolver<String> dagResolver = new DagResolver<>(taskInsIds, taskInsLinks);
        dagResolver.breadthFirstWalk(taskInsId -> {
            TaskInstance taskIns = new TaskInstance();
            taskIns.setInstanceId(taskInsId);
            taskIns.setFlowInstanceId(triggerInstance.getInstanceId());
            Set<String> preNode = dagResolver.getPreNodeSet(taskInsId);
            if (CollectionUtil.isNotEmpty(preNode)) {
                taskIns.setLastInstanceIds(preNode);
            }
            Set<String> postNode = dagResolver.getPostNodeSet(taskInsId);
            if (CollectionUtil.isNotEmpty(postNode)) {
                taskIns.setNextInstanceIds(postNode);
            }
            taskIns.setState(StatusEnum.INIT_SUCCESS);
            // 保存任务实例
            masterStorage.getTaskStorage().saveInstance(taskIns);
        });

        // 更新流程实例状态
        flowIns.setState(StatusEnum.INIT_SUCCESS);
        masterStorage.getFlowStorage().saveInstance(flowIns);
    }

    /**
     * 任务提交动作.
     *
     * @param triggerInstance 触发器缓存实例
     */
    public void dispatchSubmit(TriggerInstance triggerInstance) {
        String flowInsId = triggerInstance.getInstanceId();
        List<TaskInstance> ids = masterStorage.getTaskStorage().getTaskInsIdsByFlowInsId(flowInsId);
        for (TaskInstance taskIns : ids) {
            TaskMsg msg = TaskMsg.builder()//
                    .flowInstanceId(flowInsId)//
                    .taskInstanceId(taskIns.getInstanceId())//
                    .actionType(ActionType.WAIT)//
                    .isManualAction(false)//
                    .build();
            // 创建 TaskActor 并发送消息
            ActorProxy taskActor = createTaskActor(taskIns.getInstanceId(), flowInsId);
            // 第一次发送消息
            taskActor.notify(msg);
        }
    }

    /**
     * 恢复流程下任务实例的 Actor 运行态.
     *
     * @param flowInsId 流程实例ID
     */
    public void reloadTasks(String flowInsId) {
        List<TaskInstance> taskInstances = masterStorage.getTaskStorage().getTaskInsIdsByFlowInsId(flowInsId);
        for (TaskInstance taskInstance : taskInstances) {
            if (taskInstance == null) {
                continue;
            }
            StatusEnum taskState = taskInstance.getState();
            if (taskState != null && taskState.isSuccess()) {
                continue;
            }
            try {
                ActorProxy taskActor = createTaskActor(taskInstance.getInstanceId(), flowInsId);
                recoverTask(taskActor, taskInstance);
            } catch (Exception e) {
                log.warn("恢复任务实例运行态失败,taskInstanceId={}",
                        taskInstance == null ? null : taskInstance.getInstanceId(), e);
            }
        }
        log.info("恢复任务运行态数量: {}, flowInstanceId={}", taskInstances.size(), flowInsId);
    }

    private void recoverTask(ActorProxy taskActor, TaskInstance taskInstance) {
        ActionType actionType = getRecoverAction(taskInstance.getState());
        if (actionType == null) {
            return;
        }
        TaskMsg msg = TaskMsg.builder()
                .flowInstanceId(taskInstance.getFlowInstanceId())
                .taskInstanceId(taskInstance.getInstanceId())
                .taskId(taskInstance.getTaskId())
                .flowParamData(getFlowParamData(taskInstance))
                .actionType(actionType)
                .isManualAction(false)
                .build();
        taskActor.notify(msg);
    }

    private ParamData getFlowParamData(TaskInstance taskInstance) {
        FlowInstance flowInstance = masterStorage.getFlowStorage().getInstanceById(taskInstance.getFlowInstanceId());
        return flowInstance == null ? null : flowInstance.getFlowParam();
    }

    private ActionType getRecoverAction(StatusEnum state) {
        if (state == null) {
            return null;
        }
        switch (state) {
            case INIT_SUCCESS:
            case WAIT_DEPENDENT:
                return ActionType.WAIT;
            case SUBMITTING:
                return ActionType.RUN;
            case RESTARTING:
                return ActionType.SUBMIT;
            case STOPPING:
                return ActionType.STOP;
            case KILLING:
                return ActionType.KILL;
            case ENFORCING_SUCCESS:
                return ActionType.ENFORCE_SUCCESS;
            default:
                return null;
        }
    }

    /**
     * 创建 TaskActor.
     *
     * @param taskInsId 任务实例ID
     * @param flowInsId 流程实例ID
     * @return ActorProxy
     */
    public ActorProxy createTaskActor(String taskInsId, String flowInsId) {
        // 创建 TaskActor
        return actorSystem.createChildActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return taskInsId;
            }

            @Override
            public Actor createActor() {
                return new TaskActor(taskInsId, msgHandlerRegister);
            }
        }, flowInsId);
    }

    /**
     * 任务上报状态处理.
     *
     * @param result 任务结果
     * @return 上报结果
     */
    @Override
    public boolean asyncHandle(TaskResult result) {
        if (result == null || result.getTaskState() == null) {
            return false;
        }

        ActionType actionType = null;
        switch (result.getTaskState()) {
            case SUBMITTING:
            case SUBMIT_FAILURE:
            case SUBMIT_SUCCESS:
            case RUNNING:
            case RUN_SUCCESS:
            case RUN_FAILURE:
                actionType = ActionType.RUN;
                break;
            case STOP_SUCCESS:
            case STOP_FAILURE:
                actionType = ActionType.STOP;
                break;
            case KILLED:
                actionType = ActionType.KILL;
                break;
            case ENFORCE_SUCCESS:
                actionType = ActionType.ENFORCE_SUCCESS;
                break;
            default:
                log.warn("收到worker无法处理的任务状态, taskInstanceId={}, taskState={}",
                        result.getTaskInstanceId(), result.getTaskState());
                return false;
        }

        TaskMsg msg = TaskMsg.builder()
                .taskInstanceId(result.getTaskInstanceId())
                .actionType(actionType)
                .isManualAction(false)
                .taskResult(result)
                .build();
        String actorId = result.getTaskInstanceId();
        actorSystem.notify(actorId, msg);
        return true;
    }

    //region 用户手动指令操作

    /**
     * 任务重新初始化.
     *
     * @param instance 流程实例
     */
    public void taskReInit(FlowInstance instance) {
        //TODO 参见 fetchInit
    }

    /**
     * 立即提交任务(忽略依赖提交).
     *
     * @param instance 流程实例
     */
    public void taskSubmit(TaskInstance instance) {
        this.manualAction(instance, ActionType.SUBMIT);
    }

    /**
     * 停止任务.
     *
     * @param instance 流程实例
     */
    public void taskStop(TaskInstance instance) {
        this.manualAction(instance, ActionType.STOP);
    }

    /**
     * 强制停止任务.
     *
     * @param instance 流程实例
     */
    public void taskKill(TaskInstance instance) {
        this.manualAction(instance, ActionType.KILL);
    }

    /**
     * 重启任务.
     *
     * @param instance 流程实例
     */
    public void taskRestart(TaskInstance instance) {
        this.manualAction(instance, ActionType.RESTART);
    }

    /**
     * 强制成功任务.
     *
     * @param instance 流程实例
     */
    public void taskEnforceSuccess(TaskInstance instance) {
        this.manualAction(instance, ActionType.ENFORCE_SUCCESS);
    }

    /**
     * 手动触发流程动作.
     *
     * @param instance   流程实例
     * @param actionType 动作
     */
    private void manualAction(TaskInstance instance, ActionType actionType) {
        TaskMsg msg = TaskMsg.builder()
                .flowInstanceId(instance.getFlowInstanceId())
                .taskInstanceId(instance.getInstanceId())
                .actionType(actionType)
                .isManualAction(true)
                .build();
        String actorId = instance.getInstanceId();
        actorSystem.notify(actorId, msg);
    }
    //endregion
}
