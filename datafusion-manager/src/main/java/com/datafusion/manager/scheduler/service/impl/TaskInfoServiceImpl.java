package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.TaskInfoMapper;
import com.datafusion.manager.scheduler.dto.TaskInfoCopyDto;
import com.datafusion.manager.scheduler.dto.TaskInfoDto;
import com.datafusion.manager.scheduler.dto.TaskInfoQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInfoSaveDto;
import com.datafusion.manager.scheduler.dto.TaskInfoUpdateDto;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.system.service.TaskTypeConfigService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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

    /**
     * 任务名称和编码最大长度.
     */
    private static final int TASK_NAME_CODE_MAX_LENGTH = 255;

    /**
     * 复制任务后缀预留长度.
     */
    private static final int TASK_COPY_SUFFIX_RESERVED_LENGTH = 20;

    /**
     * 复制任务时任务名称和编码的基础值最大长度.
     *
     * <p>表字段长度为255，复制后缀当前为16位（下划线 + yyMMddHHmmssSSS），
     * 这里按20位预留，给未来后缀扩展留下4位空间。</p>
     */
    private static final int TASK_COPY_BASE_MAX_LENGTH =
            TASK_NAME_CODE_MAX_LENGTH - TASK_COPY_SUFFIX_RESERVED_LENGTH;

    /**
     * 复制任务时间后缀格式.
     */
    private static final DateTimeFormatter COPY_SUFFIX_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");

    /**
     * 复制任务后缀匹配规则.
     */
    private static final Pattern COPY_SUFFIX_PATTERN = Pattern.compile("_\\d{15}$");

    /**
     * 任务类型配置Service.
     */
    private final TaskTypeConfigService taskTypeConfigService;

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
        entity.setId(UUID.nameUUIDFromBytes(dto.getTaskCode().getBytes(StandardCharsets.UTF_8)));
        entity.setTaskName(dto.getTaskName());
        entity.setTaskCode(dto.getTaskCode());
        entity.setDescription(dto.getDescription());
        entity.setTaskTypeId(dto.getTaskTypeId());
        entity.setTaskType(dto.getTaskType());
        if (StringUtils.isNotBlank(dto.getTaskParam())) {
            entity.setTaskParam(JacksonUtils.tryStr2JsonNode(dto.getTaskParam()));
        }
        entity.setDefinition(JacksonUtils.tryStr2JsonNode(dto.getDefinition()));
        entity.setPluginId(resolveDefaultPluginId(dto));
        if (dto.getView() != null) {
            entity.setView(JacksonUtils.tryStr2JsonNode(dto.getView()));
        }
        entity.setDepEventIds(dto.getDepEventIds());
        entity.setEventId(dto.getEventId());
        entity.setIsBound(false);
        entity.setEnabled(true);
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
    @Transactional(rollbackFor = Exception.class)
    public UUID copyTaskInfo(TaskInfoCopyDto dto) {
        TaskInfoEntity source = getById(dto.getSourceId());
        if (source == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务不存在");
        }

        String suffix = generateCopyTaskSuffix();
        String taskName = generateCopyValue(source.getTaskName(), suffix, "任务名称过长, 无法复制");
        String taskCode = generateCopyValue(source.getTaskCode(), suffix, "任务编码过长, 无法复制");
        checkCodeUnique(taskCode, null);

        TaskInfoEntity entity = new TaskInfoEntity();
        entity.setId(UUID.nameUUIDFromBytes(taskCode.getBytes(StandardCharsets.UTF_8)));
        entity.setTaskName(taskName);
        entity.setTaskCode(taskCode);
        entity.setDescription(source.getDescription());
        entity.setTaskTypeId(source.getTaskTypeId());
        entity.setTaskType(source.getTaskType());
        entity.setTaskParam(source.getTaskParam());
        entity.setDefinition(source.getDefinition());
        entity.setPluginId(taskTypeConfigService.getDefaultPluginIdByTaskType(entity.getTaskType()));
        entity.setIsBound(false);
        entity.setFlowId(null);
        entity.setView(null);
        entity.setDepEventIds(null);
        entity.setEventId(null);
        entity.setEnabled(true);
        entity.setSyncFlag(source.getSyncFlag());
        entity.setSourceRoute(buildCopySourceRoute(source));

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
        boolean updated = updateById(entity);
        if (Boolean.TRUE.equals(dto.getClearEventId())) {
            LambdaUpdateWrapper<TaskInfoEntity> clearEventWrapper = new LambdaUpdateWrapper<>();
            clearEventWrapper.eq(TaskInfoEntity::getId, dto.getId())
                    .set(TaskInfoEntity::getEventId, null);
            updated = update(clearEventWrapper);
        }
        return updated;
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
     * 根据任务类型解析默认执行插件ID.
     *
     * @param dto 任务新增参数
     * @return 默认执行插件ID
     */
    private UUID resolveDefaultPluginId(TaskInfoSaveDto dto) {
        if (dto.getPluginId() != null) {
            return dto.getPluginId();
        }
        return taskTypeConfigService.getDefaultPluginIdByTaskType(dto.getTaskType());
    }

    /**
     * 生成复制字段值.
     *
     * @param sourceValue  原值
     * @param suffix       新复制后缀
     * @param errorMessage 长度超限错误提示
     * @return 复制字段值
     */
    private String generateCopyValue(String sourceValue, String suffix, String errorMessage) {
        String baseValue = removeCopySuffix(sourceValue);
        if (StringUtils.length(baseValue) > TASK_COPY_BASE_MAX_LENGTH) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, errorMessage);
        }
        return baseValue + "_" + suffix;
    }

    /**
     * 移除已有复制后缀.
     *
     * @param value 原值
     * @return 基础值
     */
    private String removeCopySuffix(String value) {
        return COPY_SUFFIX_PATTERN.matcher(value).replaceFirst("");
    }

    /**
     * 生成复制任务后缀.
     *
     * @return 毫秒级时间后缀
     */
    private String generateCopyTaskSuffix() {
        return LocalDateTime.now().format(COPY_SUFFIX_FORMATTER);
    }

    /**
     * 构建复制来源路由.
     *
     * @param source 原任务
     * @return 来源路由JSON字符串
     */
    private String buildCopySourceRoute(TaskInfoEntity source) {
        Map<String, Object> sourceRoute = new LinkedHashMap<>();
        sourceRoute.put("sourceRoute", source.getSourceRoute());
        sourceRoute.put("copy_task_id", source.getId());
        sourceRoute.put("copy_task_name", source.getTaskName());
        return JacksonUtils.tryObj2Str(sourceRoute);
    }

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
                wrapper.apply("task_name ILIKE {0}", "%" + query.getTaskName() + "%");
            }
            if (StringUtils.isNotBlank(query.getTaskCode())) {
                wrapper.apply("task_code ILIKE {0}", "%" + query.getTaskCode() + "%");
            }
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
            if (query.getSyncFlag() != null) {
                wrapper.eq(TaskInfoEntity::getSyncFlag, query.getSyncFlag());
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
