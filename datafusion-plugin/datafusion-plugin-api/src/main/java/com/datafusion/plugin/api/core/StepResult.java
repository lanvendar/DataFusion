package com.datafusion.plugin.api.core;

/**
 * 步骤执行结果.
 *
 * <p>
 * 包含步骤 ID、执行状态、记录数、耗时和错误信息.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class StepResult {
    
    /**
     * 步骤 ID.
     */
    private final String stepId;
    
    /**
     * 执行是否成功.
     */
    private final boolean success;
    
    /**
     * 抽取记录数.
     */
    private final long records;
    
    /**
     * 执行耗时(毫秒).
     */
    private final long elapsedMs;
    
    /**
     * 错误信息.
     */
    private final String errorMessage;

    /**
     * 构造步骤结果.
     *
     * @param stepId 步骤 ID
     * @param success 是否成功
     * @param records 记录数
     * @param elapsedMs 耗时(毫秒)
     * @param errorMessage 错误信息
     */
    public StepResult(String stepId, boolean success, long records, long elapsedMs, String errorMessage) {
        this.stepId = stepId;
        this.success = success;
        this.records = records;
        this.elapsedMs = elapsedMs;
        this.errorMessage = errorMessage;
    }

    /**
     * 获取步骤 ID.
     *
     * @return 步骤 ID
     */
    public String getStepId() {
        return stepId;
    }

    /**
     * 判断执行是否成功.
     *
     * @return true 表示成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取抽取记录数.
     *
     * @return 记录数
     */
    public long getRecords() {
        return records;
    }

    /**
     * 获取执行耗时.
     *
     * @return 耗时(毫秒)
     */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * 获取错误信息.
     *
     * @return 错误信息
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
