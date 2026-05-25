package com.datafusion.scheduler.enums;

/**
 * 动作枚举类.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/3/6
 * @since 2026/3/6
 */
public enum ActionType {
    /**
     * 初始化动作.
     */
    INIT,

    /**
     * 重新初始化动作.
     */
    REINIT,

    /**
     * 依赖检查动作.
     */
    WAIT,

    /**
     * 提交动作.
     */
    SUBMIT,

    /**
     * 运行消息动作.
     */
    RUN,

    /**
     * 停止动作.
     */
    STOP,

    /**
     * 重启动作.
     */
    RESTART,

    /**
     * 强制停止动作.
     */
    KILL,

    /**
     * 强制成功动作.
     */
    ENFORCE_SUCCESS;

    /**
     * 流程消息类型前缀.
     */
    private static final String FLOW_PREFIX = "FLOW_";

    /**
     * 任务消息类型前缀.
     */
    private static final String TASK_PREFIX = "TASK_";

    /**
     * 获取流程消息类型.
     *
     * @return 流程消息类型字符串
     */
    public String flowType() {
        return FLOW_PREFIX + this.name();
    }

    /**
     * 获取任务消息类型.
     *
     * @return 任务消息类型字符串
     */
    public String taskType() {
        return TASK_PREFIX + this.name();
    }
}
