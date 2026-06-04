package com.datafusion.agent.runtime;

/**
 * Agent 执行状态记录器.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/3
 * @since 1.0.0
 */
public interface AgentExecutionStatusRecorder {

    /**
     * 记录执行状态.
     *
     * @param record 执行状态记录
     */
    void record(AgentExecutionStatusRecord record);
}
