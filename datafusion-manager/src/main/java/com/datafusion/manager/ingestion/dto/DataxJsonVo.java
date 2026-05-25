package com.datafusion.manager.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

/**
 * DataX JSON 生成响应.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Data
@Schema(name = "DataxJsonVo", description = "数据同步任务 DataX JSON 生成响应")
public class DataxJsonVo {

    /**
     * 数据同步任务ID.
     */
    @Schema(name = "taskId", description = "数据同步任务ID")
    private UUID taskId;

    /**
     * 任务编码.
     */
    @Schema(name = "taskCode", description = "任务编码")
    private String taskCode;

    /**
     * DataX 标准 job JSON 字符串.
     */
    @Schema(name = "json", description = "DataX 标准 job JSON 字符串")
    private String json;
}

