package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.TaskInfoMapper;
import com.datafusion.manager.scheduler.dto.TaskInfoDto;
import com.datafusion.manager.scheduler.dto.TaskInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TaskInfoUpdateDto;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-任务信息Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TaskInfoServiceImpl extends ServiceImpl<TaskInfoMapper, TaskInfoEntity>
        implements TaskInfoService {

    @Override
    public TaskInfoEntity getTaskInfo(UUID taskId) {
        return baseMapper.getTaskInfo(taskId);
    }

    @Override
    public List<TaskInfoEntity> listByFlowId(UUID flowId) {
        return baseMapper.listByFlowId(flowId);
    }

    @Override
    public PageResponse<TaskInfoDto> pageTaskInfo(PageQuery<TaskInfoQueryDto> query) {
        LambdaQueryWrapper<TaskInfoEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<TaskInfoEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        PageResponse<TaskInfoDto> response = new PageResponse<>();
        response.setDataList(page.getRecords().stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<TaskInfoDto> listTaskInfo(TaskInfoQueryDto query) {
        LambdaQueryWrapper<TaskInfoEntity> wrapper = buildQueryWrapper(query);
        return baseMapper.selectList(wrapper).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public TaskInfoDto getTaskInfoById(UUID id) {
        TaskInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务不存在");
        }
        return toDto(entity);
    }

    @Override
    public UUID addTaskInfo(TaskInfoSaveDto dto) {
        checkCodeUnique(dto.getTaskCode(), null);

        TaskInfoEntity entity = new TaskInfoEntity();
        entity.setId(UUID.nameUUIDFromBytes(dto.getTaskCode().getBytes()));
        entity.setTaskName(dto.getTaskName());
        entity.setTaskCode(dto.getTaskCode());
        entity.setDescription(dto.getDescription());
        entity.setTaskTypeId(dto.getTaskTypeId());
        entity.setTaskType(dto.getTaskType());
        entity.setTaskParam(JacksonUtils.tryStr2JsonNode(dto.getTaskParam()));
        entity.setDefinition(JacksonUtils.tryStr2JsonNode(dto.getDefinition()));
        entity.setPluginId(dto.getPluginId());
        if (dto.getView() != null) {
            entity.setView(JacksonUtils.tryStr2JsonNode(dto.getView()));
        }
        entity.setDepEventIds(dto.getDepEventIds());
        entity.setEventId(dto.getEventId());
        entity.setIsBound(false);
        entity.setEnabled(false);
        entity.setSyncFlag(false);

        Date now = new Date();
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateTaskInfo(TaskInfoUpdateDto dto) {
        TaskInfoEntity entity = getById(dto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务不存在");
        }

        if (StringUtils.isNotBlank(dto.getTaskCode())) {
            checkCodeUnique(dto.getTaskCode(), dto.getId());
            entity.setTaskCode(dto.getTaskCode());
        }
        if (StringUtils.isNotBlank(dto.getTaskName())) {
            entity.setTaskName(dto.getTaskName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (StringUtils.isNotBlank(dto.getTaskTypeId())) {
            entity.setTaskTypeId(dto.getTaskTypeId());
        }
        if (StringUtils.isNotBlank(dto.getTaskType())) {
            entity.setTaskType(dto.getTaskType());
        }
        if (dto.getTaskParam() != null) {
            entity.setTaskParam(JacksonUtils.tryStr2JsonNode(dto.getTaskParam()));
        }
        if (dto.getDefinition() != null) {
            entity.setDefinition(JacksonUtils.tryStr2JsonNode(dto.getDefinition()));
        }
        if (dto.getPluginId() != null) {
            entity.setPluginId(dto.getPluginId());
        }
        if (dto.getView() != null) {
            entity.setView(JacksonUtils.tryStr2JsonNode(dto.getView()));
        }
        if (dto.getDepEventIds() != null) {
            entity.setDepEventIds(dto.getDepEventIds());
        }
        if (dto.getEventId() != null) {
            entity.setEventId(dto.getEventId());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }

        entity.setSyncFlag(false);
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    @Override
    public boolean deleteTaskInfo(UUID id) {
        TaskInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务不存在");
        }
        if (Boolean.TRUE.equals(entity.getIsBound())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务已绑定流程, 无法删除");
        }
        return removeById(id);
    }

    // region 私有方法

    /**
     * 构建查询条件.
     *
     * @param query 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<TaskInfoEntity> buildQueryWrapper(TaskInfoQueryDto query) {
        LambdaQueryWrapper<TaskInfoEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getTaskName())) {
                wrapper.apply("task_name ILIKE {0}", "%" + query.getTaskName() + "%");            }
            if (StringUtils.isNotBlank(query.getTaskCode())) {
                wrapper.apply("task_code ILIKE {0}", "%" + query.getTaskCode() + "%");            }
            if (StringUtils.isNotBlank(query.getTaskType())) {
                wrapper.eq(TaskInfoEntity::getTaskType, query.getTaskType());
            }
            if (query.getFlowId() != null) {
                wrapper.eq(TaskInfoEntity::getFlowId, query.getFlowId());
            }
            if (query.getEnabled() != null) {
                wrapper.eq(TaskInfoEntity::getEnabled, query.getEnabled());
            }
            if (query.getIsBound() != null) {
                wrapper.eq(TaskInfoEntity::getIsBound, query.getIsBound());
            }
        }
        wrapper.orderByDesc(TaskInfoEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 校验taskCode唯一性.
     *
     * @param taskCode  任务编码
     * @param excludeId 排除的ID(修改时排除自身)
     */
    private void checkCodeUnique(String taskCode, UUID excludeId) {
        if (StringUtils.isBlank(taskCode)) {
            return;
        }
        LambdaQueryWrapper<TaskInfoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskInfoEntity::getTaskCode, taskCode);
        if (excludeId != null) {
            wrapper.ne(TaskInfoEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务编码已存在");
        }
    }

    /**
     * Entity转Dto.
     *
     * @param entity 任务实体
     * @return 任务Dto
     */
    private TaskInfoDto toDto(TaskInfoEntity entity) {
        TaskInfoDto dto = new TaskInfoDto();
        dto.setId(entity.getId());
        dto.setTaskName(entity.getTaskName());
        dto.setTaskCode(entity.getTaskCode());
        dto.setDescription(entity.getDescription());
        dto.setTaskTypeId(entity.getTaskTypeId());
        dto.setTaskType(entity.getTaskType());
        dto.setTaskParam(JacksonUtils.isEmpty(entity.getTaskParam()) ? null : JacksonUtils.tryObj2Str(entity.getTaskParam()));
        dto.setDefinition(JacksonUtils.isEmpty(entity.getDefinition()) ? null : JacksonUtils.tryObj2Str(entity.getDefinition()));
        dto.setIsBound(entity.getIsBound());
        dto.setFlowId(entity.getFlowId());
        dto.setPluginId(entity.getPluginId());
        dto.setView(JacksonUtils.isEmpty(entity.getView()) ? null : JacksonUtils.tryObj2Str(entity.getView()));
        dto.setDepEventIds(entity.getDepEventIds());
        dto.setEventId(entity.getEventId());
        dto.setEnabled(entity.getEnabled());
        dto.setSyncFlag(entity.getSyncFlag());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
    // endregion
}
