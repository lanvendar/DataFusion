package com.datafusion.manager.ingestion.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.datasource.model.DataSourceInfo;
import com.datafusion.manager.ingestion.dao.DatasyncFieldMapper;
import com.datafusion.manager.ingestion.dto.DataxJsonVo;
import com.datafusion.manager.ingestion.po.IngestionDatasyncFieldEntity;
import com.datafusion.manager.ingestion.po.IngestionDatasyncTaskEntity;
import com.datafusion.manager.ingestion.service.DatasyncTaskService;
import com.datafusion.manager.ingestion.service.DataxJsonService;
import com.datafusion.manager.ingestion.service.impl.datax.DataxJobContext;
import com.datafusion.manager.ingestion.service.impl.datax.DataxReaderBuilder;
import com.datafusion.manager.ingestion.service.impl.datax.DataxWriterBuilder;
import com.datafusion.manager.metadata.po.DataSourceInfoEntity;
import com.datafusion.manager.metadata.service.DataSourceInfoService;
import com.datafusion.manager.metadata.support.DatabaseSupportManager;
import com.datafusion.manager.metadata.support.TransformSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DataX JSON 生成服务实现.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/9
 * @since 2026/5/9
 */
@Service
@RequiredArgsConstructor
public class DataxJsonServiceImpl implements DataxJsonService {

    /**
     * 数据同步任务服务.
     */
    private final DatasyncTaskService datasyncTaskService;

    /**
     * 数据同步字段映射 Mapper.
     */
    private final DatasyncFieldMapper datasyncFieldMapper;

    /**
     * 数据源服务.
     */
    private final DataSourceInfoService dataSourceInfoService;

    /**
     * reader 构建器.
     */
    private final List<DataxReaderBuilder> readerBuilders;

    /**
     * writer 构建器.
     */
    private final List<DataxWriterBuilder> writerBuilders;

    /**
     * ObjectMapper.
     */
    private final ObjectMapper objectMapper;

    /**
     * 根据任务ID生成 DataX 标准 job JSON.
     *
     * @param taskId 数据同步任务ID
     * @return DataX JSON 响应
     */
    @Override
    public DataxJsonVo buildDataxJson(UUID taskId) {
        IngestionDatasyncTaskEntity task = datasyncTaskService.getById(taskId);
        if (task == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "任务不存在");
        }

        String sourceDsType = task.getSourceDsType();
        String targetDsType = task.getTargetDsType();
        checkSourceDsType(sourceDsType);

        List<IngestionDatasyncFieldEntity> allFields = datasyncFieldMapper.selectByTaskId(taskId);
        List<IngestionDatasyncFieldEntity> sourceFields = filterFields(allFields, "SOURCE");
        List<IngestionDatasyncFieldEntity> targetFields = filterFields(allFields, "TARGET");

        DataSourceInfoEntity sourceDsEntity = dataSourceInfoService.getWithCheckNonNull(task.getSourceDsId());
        DataSourceInfoEntity targetDsEntity = dataSourceInfoService.getWithCheckNonNull(task.getTargetDsId());
        DataSourceInfo sourceDsInfo = transformIfSupported(sourceDsType, sourceDsEntity);
        DataSourceInfo targetDsInfo = transformIfSupported(targetDsType, targetDsEntity);

        DataxJobContext ctx = DataxJobContext.builder()
                .task(task)
                .sourceFields(sourceFields)
                .targetFields(targetFields)
                .sourceDsEntity(sourceDsEntity)
                .targetDsEntity(targetDsEntity)
                .sourceDsInfo(sourceDsInfo)
                .targetDsInfo(targetDsInfo)
                .sourceConfig(task.getSourceConfig())
                .targetConfig(task.getTargetConfig())
                .objectMapper(objectMapper)
                .build();

        DataxReaderBuilder readerBuilder = getReaderBuilder(sourceDsType);
        DataxWriterBuilder writerBuilder = getWriterBuilder(targetDsType);

        ObjectNode readerNode = readerBuilder.buildReader(ctx);
        ObjectNode writerNode = writerBuilder.buildWriter(ctx);

        ObjectNode jobRoot = objectMapper.createObjectNode();
        ObjectNode job = jobRoot.putObject("job");
        job.set("setting", buildDefaultSetting());
        ArrayNode content = job.putArray("content");
        ObjectNode entry = content.addObject();
        entry.set("reader", readerNode);
        entry.set("writer", writerNode);

        DataxJsonVo vo = new DataxJsonVo();
        vo.setTaskId(task.getId());
        vo.setTaskCode(task.getCode());
        vo.setJson(writeJsonSafely(jobRoot));
        return vo;
    }

    private void checkSourceDsType(String sourceDsType) {
        if (sourceDsType == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "sourceDsType不能为空");
        }
        if (Set.of("starrocks", "hologres").contains(sourceDsType)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, sourceDsType + " 不支持作来源类型");
        }
    }

    private List<IngestionDatasyncFieldEntity> filterFields(List<IngestionDatasyncFieldEntity> allFields, String sourceTarget) {
        if (CollectionUtil.isEmpty(allFields)) {
            return new ArrayList<>();
        }
        return allFields.stream()
                .filter(f -> sourceTarget.equals(f.getSourceTarget()))
                .collect(Collectors.toList());
    }

    private DataSourceInfo transformIfSupported(String dsType, DataSourceInfoEntity entity) {
        if ("tsdb".equals(dsType) || "txtfile".equals(dsType)) {
            return null;
        }
        TransformSupport support = DatabaseSupportManager.getTransformSupport(entity);
        if (support == null) {
            return null;
        }
        return support.transformDataSourceInfo(entity);
    }

    private DataxReaderBuilder getReaderBuilder(String sourceDsType) {
        Map<String, DataxReaderBuilder> map = new HashMap<>();
        for (DataxReaderBuilder b : readerBuilders) {
            map.put(b.supports(), b);
        }
        DataxReaderBuilder builder = map.get(sourceDsType);
        if (builder == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "不支持的来源类型: " + sourceDsType);
        }
        return builder;
    }

    private DataxWriterBuilder getWriterBuilder(String targetDsType) {
        Map<String, DataxWriterBuilder> map = new HashMap<>();
        for (DataxWriterBuilder b : writerBuilders) {
            map.put(b.supports(), b);
        }
        DataxWriterBuilder builder = map.get(targetDsType);
        if (builder == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "不支持的目标类型: " + targetDsType);
        }
        return builder;
    }

    private ObjectNode buildDefaultSetting() {
        ObjectNode setting = objectMapper.createObjectNode();
        setting.putObject("speed").put("channel", 3);
        ObjectNode errorLimit = setting.putObject("errorLimit");
        errorLimit.put("record", 0);
        errorLimit.put("percentage", 0.02);
        return setting;
    }

    private String writeJsonSafely(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "DataX JSON 序列化失败");
        }
    }
}

