package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;

/**
 * API 抽取任务执行器接口.
 *
 * <p>
 * 负责根据配置执行完整的 API 数据抽取流程,包括 HTTP 请求、响应解析、数据转换和落表写入.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ApiExtractRunner {
    
    /**
     * 执行 API 抽取任务.
     *
     * @param config 抽取任务配置
     * @return 抽取结果,包含执行状态、记录数和错误信息
     */
    ApiExtractResult run(ApiExtractJobConfig config);
}
