package com.datafusion.manager.scheduler.model;

import com.datafusion.manager.scheduler.dto.SchedulerInstanceAvailableActionDto;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 调度实例可用操作策略.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/6
 * @since 1.0.0
 */
public final class SchedulerInstanceActionPolicy {

    /**
     * 所有实例操作均需要二次确认.
     */
    private static final Boolean CONFIRM_REQUIRED = Boolean.TRUE;

    /**
     * 可提交流程状态.
     */
    private static final Set<StatusEnum> FLOW_SUBMIT_STATES = EnumSet.of(StatusEnum.INIT_SUCCESS,
            StatusEnum.WAIT_DEPENDENT);

    /**
     * 可停止流程状态.
     */
    private static final Set<StatusEnum> FLOW_STOP_STATES = EnumSet.of(StatusEnum.RUNNING);

    /**
     * 可提交任务状态.
     */
    private static final Set<StatusEnum> TASK_SUBMIT_STATES = EnumSet.of(StatusEnum.INIT_SUCCESS,
            StatusEnum.WAIT_DEPENDENT);

    /**
     * 可停止任务状态.
     */
    private static final Set<StatusEnum> TASK_STOP_STATES = EnumSet.of(StatusEnum.INIT_SUCCESS,
            StatusEnum.INIT_FAILURE, StatusEnum.WAIT_DEPENDENT, StatusEnum.SUBMIT_SUCCESS,
            StatusEnum.SUBMIT_FAILURE, StatusEnum.RUNNING);

    /**
     * 可强制停止任务状态.
     */
    private static final Set<StatusEnum> TASK_KILL_STATES = EnumSet.of(StatusEnum.STOP_FAILURE);

    /**
     * 可重启任务状态.
     */
    private static final Set<StatusEnum> TASK_RESTART_STATES = EnumSet.of(StatusEnum.SUBMIT_FAILURE,
            StatusEnum.RUN_FAILURE, StatusEnum.STOP_SUCCESS, StatusEnum.STOP_FAILURE, StatusEnum.KILLED);

    /**
     * 可强制成功任务状态.
     */
    private static final Set<StatusEnum> TASK_ENFORCE_SUCCESS_STATES = EnumSet.of(StatusEnum.INIT_FAILURE,
            StatusEnum.SUBMIT_FAILURE, StatusEnum.RUN_FAILURE, StatusEnum.STOP_SUCCESS, StatusEnum.STOP_FAILURE,
            StatusEnum.KILLED, StatusEnum.UNKNOWN);

    private SchedulerInstanceActionPolicy() {
    }

    /**
     * 获取流程实例可用操作.
     *
     * @param status 状态值
     * @return 可用操作
     */
    public static List<SchedulerInstanceAvailableActionDto> flowActions(String status) {
        StatusEnum statusEnum = parseStatus(status);
        if (statusEnum == null) {
            return Collections.emptyList();
        }
        List<SchedulerInstanceAvailableActionDto> actions = new ArrayList<>();
        if (FLOW_SUBMIT_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.SUBMIT, "提交"));
        }
        if (FLOW_STOP_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.STOP, "停止"));
        }
        return actions;
    }

    /**
     * 获取任务实例可用操作.
     *
     * @param status 状态值
     * @return 可用操作
     */
    public static List<SchedulerInstanceAvailableActionDto> taskActions(String status) {
        StatusEnum statusEnum = parseStatus(status);
        if (statusEnum == null) {
            return Collections.emptyList();
        }
        List<SchedulerInstanceAvailableActionDto> actions = new ArrayList<>();
        if (TASK_SUBMIT_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.SUBMIT, "提交"));
        }
        if (TASK_STOP_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.STOP, "停止"));
        }
        if (TASK_KILL_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.KILL, "强制停止"));
        }
        if (TASK_RESTART_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.RESTART, "重启"));
        }
        if (TASK_ENFORCE_SUCCESS_STATES.contains(statusEnum)) {
            actions.add(action(ActionType.ENFORCE_SUCCESS, "强制成功"));
        }
        return actions;
    }

    /**
     * 判断流程操作是否可用.
     *
     * @param status     状态值
     * @param actionType 操作类型
     * @return 是否可用
     */
    public static boolean canFlowAction(String status, ActionType actionType) {
        return containsAction(flowActions(status), actionType);
    }

    /**
     * 判断任务操作是否可用.
     *
     * @param status     状态值
     * @param actionType 操作类型
     * @return 是否可用
     */
    public static boolean canTaskAction(String status, ActionType actionType) {
        return containsAction(taskActions(status), actionType);
    }

    private static boolean containsAction(List<SchedulerInstanceAvailableActionDto> actions, ActionType actionType) {
        return actions.stream().anyMatch(action -> action.getActionType().equals(actionType.name()));
    }

    private static SchedulerInstanceAvailableActionDto action(ActionType actionType, String label) {
        SchedulerInstanceAvailableActionDto action = new SchedulerInstanceAvailableActionDto();
        action.setActionType(actionType.name());
        action.setLabel(label);
        action.setConfirmRequired(CONFIRM_REQUIRED);
        return action;
    }

    private static StatusEnum parseStatus(String status) {
        try {
            return StatusEnum.fromString(status);
        } catch (Exception e) {
            return null;
        }
    }
}
