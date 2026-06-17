package com.datafusion.scheduler.enums;

import lombok.Getter;

import java.util.EnumSet;

/**
 * 状态枚举.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/5/25
 * @since 2022/5/6
 */
public enum StatusEnum {
    /**
     * 初始化中(过渡状态:无锁/同步).
     */
    INITIALIZING("00", StatusPhaseEnum.INITIALIZATION, EnableState.FLOW_ENABLED),
    /**
     * 初始化成功/未运行(内部状态).
     */
    INIT_SUCCESS("01", StatusPhaseEnum.INITIALIZATION, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 初始化失败(内部状态).
     */
    INIT_FAILURE("02", StatusPhaseEnum.INITIALIZATION, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    
    /**
     * 等待依赖(过渡状态:有锁/异步).
     */
    WAIT_DEPENDENT("11", StatusPhaseEnum.SUBMISSION, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 等待资源(过渡状态:有锁/异步).
     */
    WAIT_RESOURCES("14", StatusPhaseEnum.SUBMISSION, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 提交中(过渡状态:有锁/异步).
     */
    SUBMITTING("20", StatusPhaseEnum.SUBMISSION, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 提交成功(内部状态).
     */
    SUBMIT_SUCCESS("21", StatusPhaseEnum.SUBMISSION, EnableState.TASK_ENABLED),
    /**
     * 提交失败(内部状态).
     */
    SUBMIT_FAILURE("22", StatusPhaseEnum.SUBMISSION, EnableState.TASK_ENABLED),
    
    /**
     * 正在运行(内部状态).
     */
    RUNNING("30", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 运行成功(内部状态).
     */
    RUN_SUCCESS("31", StatusPhaseEnum.SUCCESS, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 运行失败(内部状态).
     */
    RUN_FAILURE("32", StatusPhaseEnum.FAILURE, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 强制成功(内部状态).
     */
    ENFORCE_SUCCESS("33", StatusPhaseEnum.SUCCESS, EnableState.TASK_ENABLED),
    /**
     * 强制成功中(过渡状态:有锁/异步).
     */
    ENFORCING_SUCCESS("34", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED),
    /**
     * 停止中(过渡状态:有锁/异步).
     */
    STOPPING("40", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 正常停止(内部状态).
     */
    STOP_SUCCESS("41", StatusPhaseEnum.FAILURE, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED),
    /**
     * 正常停止(内部状态).
     */
    STOP_FAILURE("42", StatusPhaseEnum.FAILURE, EnableState.TASK_ENABLED),
    /**
     * 强制停止中(过渡状态:有锁/异步).
     */
    KILLING("43", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED),
    /**
     * 强制停止(内部状态).
     */
    KILLED("44", StatusPhaseEnum.FAILURE, EnableState.TASK_ENABLED),
    /**
     * 重启中.
     */
    RESTARTING("50", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED),
    // 重试中(过渡状态:有锁/异步).
    //RETRYING("60", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED),
    // 重试失败(内部状态).
    //RETRY_FAILURE("62", StatusPhaseEnum.FAILURE, EnableState.TASK_ENABLED),
    /**
     * 失败转移.
     */
    FAIL_OVER("61", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED),
    /**
     * 重新加载中(过渡状态:有锁/异步).
     */
    RELOADING("70", StatusPhaseEnum.RUNNING, EnableState.TASK_ENABLED),
    /**
     * 未知.
     */
    UNKNOWN("99", StatusPhaseEnum.FAILURE, EnableState.TASK_ENABLED | EnableState.FLOW_ENABLED);
    
    /**
     * 状态枚举.
     */
    @Getter
    private final String stateType;
    
    /**
     * 状态阶段.
     */
    private final StatusPhaseEnum statusPhaseEnum;
    
    /**
     * 有效状态.
     */
    private final int enableState;
    
    /**
     * 构造方法.
     *
     * @param stateType      状态枚举
     * @param statusPhaseEnum 状态阶段
     * @param enableState    有效状态
     */
    StatusEnum(String stateType, StatusPhaseEnum statusPhaseEnum, int enableState) {
        this.stateType = stateType;
        this.statusPhaseEnum = statusPhaseEnum;
        this.enableState = enableState;
    }
    
    /**
     * 字符串转化枚举类型.
     *
     * @param target 目标字符串
     * @return 返回枚举
     */
    public static StatusEnum fromString(String target) {
        if (target == null) {
            return null;
        }
        return EnumSet.allOf(StatusEnum.class).stream()
                .filter(s -> s.getStateType().equals(target)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Invalid StateEnum: " + target));
    }
    
    /**
     * 判断是否成功.
     *
     * @return true：成功 false：失败
     */
    public boolean isSuccess() {
        return this == RUN_SUCCESS || this == ENFORCE_SUCCESS;
    }
    
    /**
     * 判断是否失败.
     *
     * @return true：成功 false：失败
     */
    public boolean isFailure() {
        return this == SUBMIT_FAILURE || this == RUN_FAILURE || this == STOP_FAILURE;
    }
    
    /**
     * 判断是否停止.
     *
     * @return true：成功 false：失败
     */
    public boolean isStopped() {
        return this == STOP_SUCCESS || this == KILLED;
    }
    
    /**
     * 判断是否最终状态.
     *
     * @return true：成功 false：失败
     */
    public boolean isFinalState() {
        return isSuccess() || isFailure() || isStopped();
    }
    
    /**
     * 判断是否运行.
     *
     * @return true：成功 false：失败
     */
    public boolean isNoRun() {
        return this == INITIALIZING || this == INIT_SUCCESS || this == INIT_FAILURE;
    }
    
    /**
     * 判断是否可取消.
     *
     * @return true：成功 false：失败
     */
    public boolean isCancel() {
        return isFinalState() || isNoRun();
    }
    
    /**
     * 判断是否等待.
     *
     * @return true：成功 false：失败
     */
    public boolean isWait() {
        return this == WAIT_DEPENDENT || this == WAIT_RESOURCES;
    }
    
    /**
     * 判断是否可以重启的状态.
     *
     * @return true：成功 false：失败
     */
    public boolean isRun() {
        return this == SUBMITTING || this == RUNNING || this == STOPPING || this == STOP_SUCCESS || this == RUN_FAILURE;
    }
    
    private interface EnableState {
        /**
         * 任务有效状态.
         */
        int TASK_ENABLED = 1;

        /**
         * 流程有效状态.
         */
        int FLOW_ENABLED = 2;

        /**
         * 共有状态.
         */
        int ALL_ENABLED = 3;
    }
}
