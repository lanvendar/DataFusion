package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 抽取上下文,存储任务运行时的状态信息.
 *
 * <p>
 * 包含配置、运行 ID 和步骤输出等运行时数据.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ApiExtractContext {
    
    /**
     * 任务配置.
     */
    private final ApiExtractJobConfig config;
    
    /**
     * 运行 ID.
     */
    private final String runId;
    
    /**
     * 步骤输出映射,key 为步骤 ID,value 为输出数据.
     */
    private final Map<String, Object> stepOutputs = new LinkedHashMap<>();

    /**
     * 构造抽取上下文.
     *
     * @param config 任务配置
     * @param runId 运行 ID
     */
    public ApiExtractContext(ApiExtractJobConfig config, String runId) {
        this.config = config;
        this.runId = runId;
    }

    /**
     * 获取任务配置.
     *
     * @return 任务配置
     */
    public ApiExtractJobConfig getConfig() {
        return config;
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
     * 存储步骤输出.
     *
     * @param stepId 步骤 ID
     * @param output 步骤输出数据
     */
    public void putStepOutput(String stepId, Map<String, Object> output) {
        stepOutputs.put(stepId, output);
    }

    /**
     * 获取步骤输出.
     *
     * @param stepId 步骤 ID
     * @return 步骤输出数据,不存在时返回空 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStepOutput(String stepId) {
        Object output = stepOutputs.get(stepId);
        if (output instanceof Map) {
            return (Map<String, Object>) output;
        }
        return Map.of();
    }
}
