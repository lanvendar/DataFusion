package com.datafusion.manager.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.system.dao.TaskTypeConfigMapper;
import com.datafusion.manager.system.dto.TaskTypeConfigDto;
import com.datafusion.manager.system.dto.TaskTypeConfigQueryDto;
import com.datafusion.manager.system.dto.TaskTypeConfigSaveDto;
import com.datafusion.manager.system.dto.TaskTypeConfigUpdateDto;
import com.datafusion.manager.system.po.TaskTypeConfigEntity;
import com.datafusion.manager.system.service.TaskTypeConfigService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 系统-任务类型配置Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/5
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TaskTypeConfigServiceImpl extends ServiceImpl<TaskTypeConfigMapper, TaskTypeConfigEntity>
        implements TaskTypeConfigService {

    /**
     * 临时默认租户ID.
     */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public PageResponse<TaskTypeConfigDto> pageTaskTypeConfig(PageQuery<TaskTypeConfigQueryDto> query) {
        Page<TaskTypeConfigDto> page = baseMapper.pageTaskTypeConfig(
                new Page<>(query.getCurrent(), query.getSize()), query.getOption(), DEFAULT_TENANT_ID);

        PageResponse<TaskTypeConfigDto> response = new PageResponse<>();
        response.setDataList(page.getRecords());
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<TaskTypeConfigDto> listTaskTypeConfig(TaskTypeConfigQueryDto query) {
        return baseMapper.listTaskTypeConfig(query, DEFAULT_TENANT_ID);
    }

    @Override
    public TaskTypeConfigDto getTaskTypeConfigById(UUID id) {
        TaskTypeConfigDto dto = baseMapper.getTaskTypeConfigById(id, DEFAULT_TENANT_ID);
        if (dto == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务类型配置不存在");
        }
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID addTaskTypeConfig(TaskTypeConfigSaveDto dto) {
        String taskType = normalizeTaskType(dto.getTaskType());
        checkTaskTypeUnique(taskType);
        UUID defaultPluginId = requireDefaultPluginId(dto.getDefaultPluginId());

        TaskTypeConfigEntity entity = new TaskTypeConfigEntity();
        entity.setId(generateTaskTypeConfigId(taskType));
        entity.setTaskType(taskType);
        entity.setDefaultPluginId(defaultPluginId);
        entity.setPluginType(normalizeOptionalCode(dto.getPluginType()));
        entity.setTenantId(DEFAULT_TENANT_ID);
        fillCreateAudit(entity);

        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTaskTypeConfig(TaskTypeConfigUpdateDto dto) {
        TaskTypeConfigEntity entity = getTenantTaskTypeConfig(dto.getId());
        entity.setDefaultPluginId(requireDefaultPluginId(dto.getDefaultPluginId()));
        if (dto.getPluginType() != null) {
            entity.setPluginType(normalizeOptionalCode(dto.getPluginType()));
        }
        fillUpdateAudit(entity);
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTaskTypeConfig(UUID id) {
        getTenantTaskTypeConfig(id);
        return removeById(id);
    }

    @Override
    public UUID getDefaultPluginIdByTaskType(String taskType) {
        String normalizedTaskType = normalizeTaskType(taskType);
        LambdaQueryWrapper<TaskTypeConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskTypeConfigEntity::getTenantId, DEFAULT_TENANT_ID);
        wrapper.eq(TaskTypeConfigEntity::getTaskType, normalizedTaskType);
        TaskTypeConfigEntity entity = baseMapper.selectOne(wrapper);
        if (entity == null || entity.getDefaultPluginId() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "无法根据任务类型解析默认执行插件: " + taskType);
        }
        return entity.getDefaultPluginId();
    }

    /**
     * 查询当前租户下的任务类型配置.
     *
     * @param id 任务类型配置ID
     * @return 任务类型配置实体
     */
    private TaskTypeConfigEntity getTenantTaskTypeConfig(UUID id) {
        LambdaQueryWrapper<TaskTypeConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskTypeConfigEntity::getId, id);
        wrapper.eq(TaskTypeConfigEntity::getTenantId, DEFAULT_TENANT_ID);
        TaskTypeConfigEntity entity = baseMapper.selectOne(wrapper);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务类型配置不存在");
        }
        return entity;
    }

    /**
     * 校验任务类型唯一性.
     *
     * @param taskType 任务类型
     */
    private void checkTaskTypeUnique(String taskType) {
        LambdaQueryWrapper<TaskTypeConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskTypeConfigEntity::getTenantId, DEFAULT_TENANT_ID);
        wrapper.eq(TaskTypeConfigEntity::getTaskType, taskType);
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务类型已存在");
        }
    }

    /**
     * 标准化任务类型.
     *
     * @param taskType 任务类型
     * @return 标准化任务类型
     */
    private String normalizeTaskType(String taskType) {
        String normalizedTaskType = StringUtils.trimToEmpty(taskType).toUpperCase(Locale.ROOT);
        if (StringUtils.isBlank(normalizedTaskType)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务类型不能为空");
        }
        return normalizedTaskType;
    }

    /**
     * 标准化可选编码.
     *
     * @param code 编码
     * @return 标准化编码
     */
    private String normalizeOptionalCode(String code) {
        return StringUtils.trimToNull(StringUtils.trimToEmpty(code).toUpperCase(Locale.ROOT));
    }

    /**
     * 校验默认插件ID.
     *
     * @param defaultPluginId 默认插件ID
     * @return 默认插件ID
     */
    private UUID requireDefaultPluginId(UUID defaultPluginId) {
        if (defaultPluginId == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "默认插件ID不能为空");
        }
        return defaultPluginId;
    }

    /**
     * 生成任务类型配置ID.
     *
     * @param taskType 任务类型
     * @return 任务类型配置ID
     */
    private UUID generateTaskTypeConfigId(String taskType) {
        return UUID.nameUUIDFromBytes(taskType.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 填充创建审计字段.
     *
     * @param entity 任务类型配置实体
     */
    private void fillCreateAudit(TaskTypeConfigEntity entity) {
        Date now = new Date();
        String currentUserName = HttpUtils.getCurrentUserName();
        entity.setCreator(currentUserName);
        entity.setUpdater(currentUserName);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
    }

    /**
     * 填充修改审计字段.
     *
     * @param entity 任务类型配置实体
     */
    private void fillUpdateAudit(TaskTypeConfigEntity entity) {
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
    }

}
