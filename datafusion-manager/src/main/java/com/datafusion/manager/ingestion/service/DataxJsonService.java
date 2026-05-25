package com.datafusion.manager.ingestion.service;

import com.datafusion.manager.ingestion.dto.DataxJsonVo;

import java.util.UUID;

/**
 * DataX JSON 生成服务.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
public interface DataxJsonService {

    /**
     * 根据任务ID生成 DataX 标准 job JSON.
     *
     * @param taskId 数据同步任务ID
     * @return DataX JSON 响应
     */
    DataxJsonVo buildDataxJson(UUID taskId);
}

