package com.datafusion.manager.ingestion.service.impl.datax;

import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.ingestion.po.IngestionDatasyncFieldEntity;
import com.datafusion.manager.ingestion.po.IngestionDatasyncTaskEntity;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DataX job 组装上下文（Service 内部使用）.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Data
@Builder
public class DataxJobContext {

    /**
     * 任务主表实体.
     */
    private IngestionDatasyncTaskEntity task;

    /**
     * source 端字段（按 columnIndex 升序）.
     */
    private List<IngestionDatasyncFieldEntity> sourceFields;

    /**
     * target 端字段（按 columnIndex 升序）.
     */
    private List<IngestionDatasyncFieldEntity> targetFields;

    /**
     * 来源数据源实体（密文存储）.
     */
    private DataSourceInfoEntity sourceDsEntity;

    /**
     * 目标数据源实体（密文存储）.
     */
    private DataSourceInfoEntity targetDsEntity;

    /**
     * 来源数据源连接信息（解密后）.
     */
    private DataSourceInfo sourceDsInfo;

    /**
     * 目标数据源连接信息（解密后）.
     */
    private DataSourceInfo targetDsInfo;

    /**
     * 来源数据源配置（task.sourceConfig）.
     */
    private JsonNode sourceConfig;

    /**
     * 目标数据源配置（task.targetConfig）.
     */
    private JsonNode targetConfig;

    /**
     * 共享 ObjectMapper.
     */
    private ObjectMapper objectMapper;
}

