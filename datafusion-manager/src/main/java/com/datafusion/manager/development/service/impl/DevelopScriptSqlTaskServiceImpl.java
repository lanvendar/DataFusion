package com.datafusion.manager.development.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.development.dao.DevelopScriptSqlTaskMapper;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskQueryDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskSaveDto;
import com.datafusion.manager.development.dto.DevelopScriptSqlTaskUpdateDto;
import com.datafusion.manager.development.po.DevelopScriptSqlTaskEntity;
import com.datafusion.manager.development.service.DevelopScriptSqlTaskService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.manager.utils.TaskSerialCodeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 数据开发-SQL脚本任务服务实现.
 *
 * @author weibowen
 * @version 1.0.0, 2026/5/13
 * @since 2026/5/13
 */
@Service
public class DevelopScriptSqlTaskServiceImpl
        extends ServiceImpl<DevelopScriptSqlTaskMapper, DevelopScriptSqlTaskEntity>
        implements DevelopScriptSqlTaskService {

    /**
     * 删除标识：正常.
     */
    private static final int MODEL_STATUS_NORMAL = 0;

    /**
     * 删除标识：已软删.
     */
    private static final int MODEL_STATUS_DELETED = 1;

    @Override
    public PageResponse<DevelopScriptSqlTaskDto> pageTask(PageQuery<DevelopScriptSqlTaskQueryDto> query) {
        List<DevelopScriptSqlTaskDto> dataList = baseMapper.pageTaskList(query);
        Integer total = baseMapper.pageTaskCount(query);
        int totalCount = total != null ? total : 0;
        return new PageResponse<>(dataList, query.getSize(), query.getCurrent(), totalCount);
    }

    @Override
    public List<DevelopScriptSqlTaskDto> listTask(DevelopScriptSqlTaskQueryDto query) {
        return baseMapper.listTask(query);
    }

    @Override
    public DevelopScriptSqlTaskDto getTaskById(UUID id) {
        DevelopScriptSqlTaskEntity entity = getAndCheckActive(id);
        return toDto(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addTask(DevelopScriptSqlTaskSaveDto dto) {
        DevelopScriptSqlTaskEntity entity = new DevelopScriptSqlTaskEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(dto.getName());
        String code = StrUtil.isNotBlank(dto.getCode()) ? dto.getCode().trim() : generateTaskCode();
        entity.setCode(code);
        entity.setDescription(dto.getDescription());
        entity.setScript(dto.getScript());
        entity.setVariables(dto.getVariables());
        entity.setGroupId(dto.getGroupId());
        entity.setPublishStatus(Boolean.FALSE);
        entity.setPublishTime(null);
        entity.setModelStatus(MODEL_STATUS_NORMAL);
        String user = HttpUtils.getCurrentUserName();
        Date now = new Date();
        entity.setCreator(user);
        entity.setCreateTime(now);
        entity.setUpdater(user);
        entity.setUpdateTime(now);
        super.save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTask(DevelopScriptSqlTaskUpdateDto dto) {
        DevelopScriptSqlTaskEntity entity = getAndCheckActive(dto.getId());
        if (StrUtil.isNotBlank(dto.getName())) {
            entity.setName(dto.getName());
        }
        if (dto.getCode() != null) {
            entity.setCode(dto.getCode());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getScript() != null) {
            entity.setScript(dto.getScript());
        }
        if (dto.getVariables() != null) {
            entity.setVariables(dto.getVariables());
        }
        if (dto.getGroupId() != null) {
            entity.setGroupId(dto.getGroupId());
        }
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean softDeleteTask(UUID id) {
        DevelopScriptSqlTaskEntity entity = getAndCheckActive(id);
        entity.setModelStatus(MODEL_STATUS_DELETED);
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishTask(UUID id) {
        DevelopScriptSqlTaskEntity entity = getAndCheckActive(id);
        entity.setPublishStatus(Boolean.TRUE);
        entity.setPublishTime(new Date());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return super.updateById(entity);
    }

    /**
     * 查询并校验记录存在且未软删.
     *
     * @param id 主键
     * @return 实体
     */
    private DevelopScriptSqlTaskEntity getAndCheckActive(UUID id) {
        DevelopScriptSqlTaskEntity entity = super.getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "任务不存在");
        }
        if (!Objects.equals(entity.getModelStatus(), MODEL_STATUS_NORMAL)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_BUSINESS_ERROR_C0505, "任务已删除");
        }
        return entity;
    }

    /**
     * Generates serial task code for this entity type.
     */
    private String generateTaskCode() {
        String prefix = TaskSerialCodeUtils.buildDatedPrefix(
                TaskSerialCodeUtils.PREFIX_DEVELOP_SCRIPT_SQL_TASK, LocalDate.now());
        String maxCode = baseMapper.selectMaxTaskCodeSeq(prefix);
        return TaskSerialCodeUtils.nextSerialCode(prefix, maxCode);
    }

    /**
     * Entity 转 DTO.
     *
     * @param entity 实体
     * @return DTO
     */
    private DevelopScriptSqlTaskDto toDto(DevelopScriptSqlTaskEntity entity) {
        DevelopScriptSqlTaskDto dto = new DevelopScriptSqlTaskDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setDescription(entity.getDescription());
        dto.setScript(entity.getScript());
        dto.setVariables(entity.getVariables());
        dto.setPublishStatus(entity.getPublishStatus());
        dto.setModelStatus(entity.getModelStatus());
        dto.setPublishTime(entity.getPublishTime());
        dto.setGroupId(entity.getGroupId());
        dto.setCreator(entity.getCreator());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdater(entity.getUpdater());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
