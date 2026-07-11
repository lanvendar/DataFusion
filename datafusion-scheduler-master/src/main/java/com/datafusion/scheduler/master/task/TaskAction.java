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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
        // 处理任务实例上下游的依赖关系（单任务流程可能无连线）
        List<TaskLink> taskInfoLink = masterStorage.getTaskStorage().getTaskInfoLink(flowIns.getFlowId());
        if (CollectionUtil.isEmpty(taskInfoLink)) {
            taskInfoLink = Collections.emptyList();
        }

        initEnabledTasks(triggerInstance, flowIns, taskInfos, taskInfoLink);

        // 更新流程实例状态
        flowIns.setState(StatusEnum.INIT_SUCCESS);
        masterStorage.getFlowStorage().saveInstance(flowIns);
    }

    /**
     * 初始化启用任务实例及其依赖关系.
     *
     * @param triggerInstance 调度器缓存实例
     * @param flowIns         流程实例
     * @param taskInfos       任务定义
     * @param taskLinks       任务连线
     */
    private void initEnabledTasks(TriggerInstance triggerInstance, FlowInstance flowIns, List<TaskInfo> taskInfos,
                                  List<TaskLink> taskLinks) {
        // 先收缩禁用节点, 确保实例依赖只引用本次实际创建的启用任务.
        DagResolver<String> dagResolver = buildEnabledTaskDag(taskInfos, taskLinks);
        // 实例ID由流程实例和任务定义稳定生成, 前后依赖可按需转换, 无需维护额外映射.
        Function<String, String> taskInstanceIdResolver = taskId -> UUID.nameUUIDFromBytes((
                flowIns.getInstanceId() + SystemConstant.UNDER_LINE + taskId).getBytes(StandardCharsets.UTF_8)).toString();
        for (TaskInfo taskInfo : taskInfos) {
            if (Boolean.FALSE.equals(taskInfo.getIsAble())) {
                log.info("跳过禁用任务, flowInstanceId={}, taskId={}", flowIns.getInstanceId(), taskInfo.getTaskId());
                continue;
            }
            String taskInsId = taskInstanceIdResolver.apply(taskInfo.getTaskId());
            TaskMsg msg = TaskMsg.builder()//
                    .flowInstanceId(triggerInstance.getInstanceId())//
                    .version(triggerInstance.getVersion())//
                    .scheduleTime(triggerInstance.getScheduleTime())//
                    .flowParamData(flowIns.getFlowParam())//
                    .taskId(taskInfo.getTaskId())
                    .taskInstanceId(taskInsId)//
                    .actionType(ActionType.INIT)//
                    .isManualAction(false)//
                    .build();
            TaskMsgHandler handler = msgHandlerRegister.getHandler(ActionType.INIT);
            if (handler != null) {
                handler.handle(msg, null);
            }
            TaskInstance taskIns = new TaskInstance();
            taskIns.setInstanceId(taskInsId);
            taskIns.setFlowInstanceId(triggerInstance.getInstanceId());
            Set<String> preTaskIds = dagResolver.getPreNodeSet(taskInfo.getTaskId());
            if (CollectionUtil.isNotEmpty(preTaskIds)) {
                taskIns.setLastInstanceIds(preTaskIds.stream()
                        .map(taskInstanceIdResolver)
                        .collect(Collectors.toSet()));
            }
            Set<String> postTaskIds = dagResolver.getPostNodeSet(taskInfo.getTaskId());
            if (CollectionUtil.isNotEmpty(postTaskIds)) {
                taskIns.setNextInstanceIds(postTaskIds.stream()
                        .map(taskInstanceIdResolver)
                        .collect(Collectors.toSet()));
            }
            taskIns.setState(StatusEnum.INIT_SUCCESS);
            // 保存任务实例
            masterStorage.getTaskStorage().saveInstance(taskIns);
        }
    }

    /**
     * 收缩禁用任务并构建启用任务 DAG.
     *
     * @param taskInfos 任务定义
     * @param taskLinks 原始任务连线
     * @return 启用任务 DAG
     */
    private DagResolver<String> buildEnabledTaskDag(List<TaskInfo> taskInfos, List<TaskLink> taskLinks) {
        Set<String> enabledTaskIds = taskInfos.stream()
                .filter(taskInfo -> !Boolean.FALSE.equals(taskInfo.getIsAble()))
                .map(TaskInfo::getTaskId)
                .collect(Collectors.toSet());
        // 保留原始邻接关系, 用于穿透连续禁用节点.
        Map<String, Set<String>> nextTaskIds = new HashMap<>(Math.max(taskLinks.size(), 2));
        for (TaskLink taskLink : taskLinks) {
            if (taskLink.getStartId() != null && taskLink.getEndId() != null) {
                nextTaskIds.computeIfAbsent(taskLink.getStartId(), key -> new HashSet<>()).add(taskLink.getEndId());
            }
        }

        List<TaskLink> effectiveLinks = new ArrayList<>();
        for (String enabledTaskId : enabledTaskIds) {
            Deque<String> pendingTaskIds = new ArrayDeque<>(nextTaskIds.getOrDefault(enabledTaskId, Collections.emptySet()));
            Set<String> visitedTaskIds = new HashSet<>();
            visitedTaskIds.add(enabledTaskId);
            while (!pendingTaskIds.isEmpty()) {
                String nextTaskId = pendingTaskIds.removeFirst();
                if (!visitedTaskIds.add(nextTaskId)) {
                    continue;
                }
                if (enabledTaskIds.contains(nextTaskId)) {
                    // 找到最近的启用下游后停止该分支, 避免跨过有效依赖.
                    effectiveLinks.add(new TaskLink(null, enabledTaskId, nextTaskId));
                } else {
                    pendingTaskIds.addAll(nextTaskIds.getOrDefault(nextTaskId, Collections.emptySet()));
                }
            }
        }
        return new DagResolver<>(enabledTaskIds, effectiveLinks);
    }

    /**
     * 任务提交动作.
     *
     * @param triggerInstance 触发器缓存实例
     * @param taskInstances   任务实例
     */
    public void dispatchSubmit(TriggerInstance triggerInstance, List<TaskInstance> taskInstances) {
        String flowInsId = triggerInstance.getInstanceId();
        for (TaskInstance taskIns : taskInstances) {
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
            case UNKNOWN:
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
