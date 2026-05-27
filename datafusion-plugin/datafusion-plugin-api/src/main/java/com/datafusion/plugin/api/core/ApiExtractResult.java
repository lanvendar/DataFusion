package com.datafusion.plugin.api.core;

import java.util.ArrayList;
import java.util.List;

/**
 * API 抽取任务执行结果.
 *
 * <p>
 * 包含任务执行状态、记录数、耗时和错误信息等.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ApiExtractResult {
    
    /**
     * 执行是否成功.
     */
    private boolean success;
    
    /**
     * 任务 ID.
     */
    private String jobId;
    
    /**
     * 运行 ID.
     */
    private String runId;
    
    /**
     * 抽取记录总数.
     */
    private long records;
    
    /**
     * 执行耗时(毫秒).
     */
    private long elapsedMs;
    
    /**
     * 错误信息.
     */
    private String errorMessage;
    
    /**
     * 步骤执行结果列表.
     */
    private final List<StepResult> steps = new ArrayList<>();

    /**
     * 创建成功结果.
     *
     * @param jobId 任务 ID
     * @param runId 运行 ID
     * @param records 记录数
     * @param elapsedMs 耗时(毫秒)
     * @param steps 步骤结果列表
     * @return 成功结果对象
     */
    public static ApiExtractResult success(String jobId, String runId, long records, long elapsedMs, List<StepResult> steps) {
        ApiExtractResult result = new ApiExtractResult();
        result.success = true;
        result.jobId = jobId;
        result.runId = runId;
        result.records = records;
        result.elapsedMs = elapsedMs;
        result.steps.addAll(steps);
        return result;
    }

    /**
     * 创建失败结果.
     *
     * @param jobId 任务 ID
     * @param runId 运行 ID
     * @param records 记录数
     * @param elapsedMs 耗时(毫秒)
     * @param errorMessage 错误信息
     * @param steps 步骤结果列表
     * @return 失败结果对象
     */
    public static ApiExtractResult failure(String jobId, String runId, long records, long elapsedMs, String errorMessage,
            List<StepResult> steps) {
        ApiExtractResult result = new ApiExtractResult();
        result.success = false;
        result.jobId = jobId;
        result.runId = runId;
        result.records = records;
        result.elapsedMs = elapsedMs;
        result.errorMessage = errorMessage;
        result.steps.addAll(steps);
        return result;
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
     * 获取任务 ID.
     *
     * @return 任务 ID
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * 获取运行 ID.
     *
     * @return 运行 ID
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 获取抽取记录总数.
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

    /**
     * 获取步骤执行结果列表.
     *
     * @return 步骤结果列表
     */
    public List<StepResult> getSteps() {
        return steps;
    }
}
