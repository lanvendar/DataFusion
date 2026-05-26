package com.datafusion.manager.ingestion.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.ingestion.dao.DatasyncFieldMapper;
import com.datafusion.manager.ingestion.dao.DatasyncTaskMapper;
import com.datafusion.manager.ingestion.dto.DatasyncFieldDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskQueryDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskSaveDto;
import com.datafusion.manager.ingestion.dto.DatasyncTaskUpdateDto;
import com.datafusion.manager.ingestion.po.IngestionDatasyncFieldEntity;
import com.datafusion.manager.ingestion.po.IngestionDatasyncTaskEntity;
import com.datafusion.manager.ingestion.service.DatasyncTaskService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.manager.utils.TaskSerialCodeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 数据同步任务服务实现.
 *
 * @author weibowen
 * @version 1.0.0, 2026/4/23
 * @since 2026/4/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasyncTaskServiceImpl
        extends ServiceImpl<DatasyncTaskMapper, IngestionDatasyncTaskEntity>
        implements DatasyncTaskService {

    /**
     * 从表Mapper.
     */
    private final DatasyncFieldMapper fieldMapper;

    @Override
    public PageResponse<DatasyncTaskDto> pageDatasyncTask(PageQuery<DatasyncTaskQueryDto> query) {
        List<DatasyncTaskDto> dataList = baseMapper.pageTaskList(query);
        Integer total = baseMapper.pageTaskCount(query);
        return new PageResponse<>(dataList, query.getSize(), query.getCurrent(), total);
    }

    @Override
    public List<DatasyncTaskDto> listDatasyncTask(DatasyncTaskQueryDto query) {
        return baseMapper.listTask(query);
    }

    @Override
    public DatasyncTaskDto getDatasyncTaskById(UUID id) {
        IngestionDatasyncTaskEntity entity = getAndCheckExist(id);
        DatasyncTaskDto dto = toDto(entity);
        List<IngestionDatasyncFieldEntity> fieldEntities = fieldMapper.selectByTaskId(id);
        dto.setFields(toFieldDtoList(fieldEntities));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addDatasyncTask(DatasyncTaskSaveDto dto) {
        String prefix = TaskSerialCodeUtils.buildDatedPrefix(
                TaskSerialCodeUtils.PREFIX_INGESTION_DATASYNC_TASK, LocalDate.now());
        String code = TaskSerialCodeUtils.nextSerialCode(prefix, baseMapper.selectMaxTaskCodeSeq(prefix));
        IngestionDatasyncTaskEntity entity = new IngestionDatasyncTaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(dto.getName());
        entity.setCode(code);
        entity.setDescription(dto.getDescription());
        entity.setSourceDsType(dto.getSourceDsType());
        entity.setSourceDsId(dto.getSourceDsId());
        entity.setSourceEntityId(dto.getSourceEntityId());
        entity.setSourceConfig(dto.getSourceConfig());
        entity.setTargetDsType(dto.getTargetDsType());
        entity.setTargetDsId(dto.getTargetDsId());
        entity.setTargetEntityId(dto.getTargetEntityId());
        entity.setTargetConfig(dto.getTargetConfig());
        entity.setPublishStatus(false);
        entity.setPublishTime(null);
        entity.setVariables(dto.getVariables());
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setCreateTime(new Date());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        super.save(entity);
        if (dto.getFields() != null && !dto.getFields().isEmpty()) {
            List<IngestionDatasyncFieldEntity> fieldEntities = toFieldEntityList(dto.getFields(), entity.getId());
            fieldMapper.batchInsert(fieldEntities);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDatasyncTask(DatasyncTaskUpdateDto dto) {
        IngestionDatasyncTaskEntity entity = getAndCheckExist(dto.getId());
        if (StrUtil.isNotBlank(dto.getName())) {
            entity.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (StrUtil.isNotBlank(dto.getSourceDsType())) {
            entity.setSourceDsType(dto.getSourceDsType());
        }
        if (dto.getSourceDsId() != null) {
            entity.setSourceDsId(dto.getSourceDsId());
        }
        if (dto.getSourceEntityId() != null) {
            entity.setSourceEntityId(dto.getSourceEntityId());
        }
        if (dto.getSourceConfig() != null) {
            entity.setSourceConfig(dto.getSourceConfig());
        }
        if (StrUtil.isNotBlank(dto.getTargetDsType())) {
            entity.setTargetDsType(dto.getTargetDsType());
        }
        if (dto.getTargetDsId() != null) {
            entity.setTargetDsId(dto.getTargetDsId());
        }
        if (dto.getTargetEntityId() != null) {
            entity.setTargetEntityId(dto.getTargetEntityId());
        }
        if (dto.getTargetConfig() != null) {
            entity.setTargetConfig(dto.getTargetConfig());
        }
        if (dto.getVariables() != null) {
            entity.setVariables(dto.getVariables());
        }
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        super.updateById(entity);
        if (dto.getFields() != null) {
            fieldMapper.deleteByTaskId(dto.getId());
            if (!dto.getFields().isEmpty()) {
                List<IngestionDatasyncFieldEntity> fieldEntities = toFieldEntityList(dto.getFields(), dto.getId());
                fieldMapper.batchInsert(fieldEntities);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDatasyncTask(UUID id) {
        getAndCheckExist(id);
        fieldMapper.deleteByTaskId(id);
        return super.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishDatasyncTask(UUID id) {
        IngestionDatasyncTaskEntity entity = getAndCheckExist(id);
        entity.setPublishStatus(true);
        entity.setPublishTime(new Date());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean offlineDatasyncTask(UUID id) {
        IngestionDatasyncTaskEntity entity = getAndCheckExist(id);
        if (!entity.getPublishStatus()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "任务未发布,无法下线");
        }
        entity.setPublishStatus(false);
        entity.setPublishTime(null);
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    /**
     * 查询并校验记录存在.
     *
     * @param id 主键
     * @return 实体
     */
    private IngestionDatasyncTaskEntity getAndCheckExist(UUID id) {
        IngestionDatasyncTaskEntity entity = super.getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "任务不存在");
        }
        return entity;
    }

    /**
     * 主表Entity转Dto(JSON字段反序列化).
     *
     * @param entity 主表实体
     * @return DTO
     */
    private DatasyncTaskDto toDto(IngestionDatasyncTaskEntity entity) {
        DatasyncTaskDto dto = new DatasyncTaskDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setDescription(entity.getDescription());
        dto.setSourceDsType(entity.getSourceDsType());
        dto.setSourceDsId(entity.getSourceDsId());
        dto.setSourceEntityId(entity.getSourceEntityId());
        dto.setTargetDsType(entity.getTargetDsType());
        dto.setTargetDsId(entity.getTargetDsId());
        dto.setTargetEntityId(entity.getTargetEntityId());
        dto.setPublishStatus(entity.getPublishStatus());
        dto.setPublishTime(entity.getPublishTime());
        dto.setCreator(entity.getCreator());
        dto.setCreateTime(entity.getCreateTime());
        dto.setSourceConfig(entity.getSourceConfig());
        dto.setTargetConfig(entity.getTargetConfig());
        dto.setVariables(entity.getVariables());
        return dto;
    }

    /**
     * 从表Entity列表转Dto列表.
     *
     * @param entities 从表实体列表
     * @return DTO列表
     */
    private List<DatasyncFieldDto> toFieldDtoList(List<IngestionDatasyncFieldEntity> entities) {
        List<DatasyncFieldDto> dtoList = new ArrayList<>();
        for (IngestionDatasyncFieldEntity entity : entities) {
            DatasyncFieldDto fieldDto = new DatasyncFieldDto();
            fieldDto.setSourceTarget(entity.getSourceTarget());
            fieldDto.setColumnName(entity.getColumnName());
            fieldDto.setDataType(entity.getDataType());
            fieldDto.setColumnIndex(entity.getColumnIndex());
            dtoList.add(fieldDto);
        }
        return dtoList;
    }

    /**
     * FieldDto列表转从表Entity列表.
     *
     * @param fields DTO列表
     * @param taskId 任务id
     * @return Entity列表
     */
    private List<IngestionDatasyncFieldEntity> toFieldEntityList(List<DatasyncFieldDto> fields, UUID taskId) {
        List<IngestionDatasyncFieldEntity> entities = new ArrayList<>();
        for (DatasyncFieldDto fieldDto : fields) {
            IngestionDatasyncFieldEntity entity = new IngestionDatasyncFieldEntity();
            entity.setId(UUID.randomUUID());
            entity.setTaskId(taskId);
            entity.setSourceTarget(fieldDto.getSourceTarget());
            entity.setColumnName(fieldDto.getColumnName());
            entity.setDataType(fieldDto.getDataType());
            entity.setColumnIndex(fieldDto.getColumnIndex() != null ? fieldDto.getColumnIndex() : 0);
            entity.setCreator(HttpUtils.getCurrentUserName());
            entity.setCreateTime(new Date());
            entity.setUpdater(HttpUtils.getCurrentUserName());
            entity.setUpdateTime(new Date());
            entities.add(entity);
        }
        return entities;
    }
}