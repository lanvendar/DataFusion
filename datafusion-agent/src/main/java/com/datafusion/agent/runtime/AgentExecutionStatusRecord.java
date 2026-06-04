package com.datafusion.agent.runtime;

import lombok.Builder;
import lombok.Data;

/**
 * Agent 执行状态记录.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/3
 * @since 1.0.0
 */
@Data
@Builder
public class AgentExecutionStatusRecord {

    /**
     * 流程执行ID.
     */
    private String flowInstanceId;

    /**
     * 执行实例ID.
     */
    private String executionId;

    /**
     * 外部应用ID.
     */
    private String appId;

    /**
     * 本地进程ID.
     */
    private String pid;

    /**
     * 工作节点ID.
     */
    private String workId;

    /**
     * 执行状态.
     */
    private String status;

    /**
     * 执行结果说明.
     */
    private String result;
}
