package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.scheduler.dto.TaskDefinitionMarkUnsyncedResultDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterResultDto;
import com.datafusion.manager.scheduler.model.BusinessSourceRoute;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.TaskDefinitionRegisterService;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.system.service.TaskTypeConfigService;
import com.datafusion.manager.utils.HttpUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * 任务定义统一登记Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2026/6/16
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class TaskDefinitionRegisterServiceImpl implements TaskDefinitionRegisterService {

    /**
     * 任务信息Service.
     */
    private final TaskInfoService taskInfoService;

    /**
     * 流程信息Service.
     */
    private final FlowInfoService flowInfoService;

    /**
     * 任务类型配置Service.
     */
    private final TaskTypeConfigService taskTypeConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskDefinitionRegisterResultDto register(TaskDefinitionRegisterDto dto) {
        BusinessSourceRoute sourceRoute = dto.getSourceRoute();
        TaskInfoEntity entity = taskInfoService.getBySourceIdentity(
                sourceRoute.encodedBizSystem(), sourceRoute.encodedBizKey());
        boolean created = entity == null;
        if (created) {
            entity = buildNewEntity(dto);
            taskInfoService.save(entity);
        } else {
            checkRegisterAllowed(entity);
            mergeEntity(entity, dto);
            taskInfoService.updateById(entity);
        }
        return toRegisterResult(entity, created);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskDefinitionMarkUnsyncedResultDto markUnsynced(BusinessSourceRoute sourceRoute) {
        TaskInfoEntity entity = taskInfoService.getBySourceIdentity(
                sourceRoute.encodedBizSystem(), sourceRoute.encodedBizKey());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务定义不存在");
        }
        entity.setSourceRoute(sourceRoute.toSourceRoute());
        entity.setSyncFlag(false);
        fillUpdateAudit(entity);
        taskInfoService.updateById(entity);

        TaskDefinitionMarkUnsyncedResultDto result = new TaskDefinitionMarkUnsyncedResultDto();
        result.setTaskId(entity.getId());
        result.setSyncFlag(false);
        return result;
    }

    /**
     * 构建新任务实体.
     *
     * @param dto 登记参数
     * @return 任务实体
     */
    private TaskInfoEntity buildNewEntity(TaskDefinitionRegisterDto dto) {
        String taskCode = resolveTaskCode(dto.getTaskCode(), dto.getSourceRoute().identity(), null);
        checkTaskCodeUnique(taskCode, null);

        TaskInfoEntity entity = new TaskInfoEntity();
        entity.setId(UUID.nameUUIDFromBytes(taskCode.getBytes(StandardCharsets.UTF_8)));
        entity.setTaskName(dto.getTaskName());
        entity.setTaskCode(taskCode);
        entity.setDescription(dto.getDescription());
        entity.setTaskTypeId(dto.getTaskTypeId());
        entity.setTaskType(dto.getTaskType());
        entity.setTaskParam(dto.getTaskParam());
        entity.setDefinition(dto.getDefinition());
        entity.setPluginId(taskTypeConfigService.getDefaultPluginIdByTaskType(dto.getTaskType()));
        entity.setIsBound(false);
        entity.setEnabled(true);
        entity.setSyncFlag(true);
        entity.setSourceRoute(dto.getSourceRoute().toSourceRoute());
        fillCreateAudit(entity);
        return entity;
    }

    /**
     * 合并任务实体.
     *
     * @param entity 任务实体
     * @param dto    登记参数
     */
    private void mergeEntity(TaskInfoEntity entity, TaskDefinitionRegisterDto dto) {
        String taskCode = resolveTaskCode(dto.getTaskCode(), dto.getSourceRoute().identity(), entity.getTaskCode());
        if (!StringUtils.equals(taskCode, entity.getTaskCode())) {
            checkTaskCodeUnique(taskCode, entity.getId());
            entity.setTaskCode(taskCode);
        }
        entity.setTaskName(dto.getTaskName());
        entity.setDescription(dto.getDescription());
        entity.setTaskTypeId(dto.getTaskTypeId());
        entity.setTaskType(dto.getTaskType());
        entity.setTaskParam(dto.getTaskParam());
        entity.setDefinition(dto.getDefinition());
        entity.setSourceRoute(dto.getSourceRoute().toSourceRoute());
        entity.setSyncFlag(true);
        fillUpdateAudit(entity);
    }

    /**
     * 校验登记是否允许.
     *
     * @param entity 任务实体
     */
    private void checkRegisterAllowed(TaskInfoEntity entity) {
        if (entity.getFlowId() == null) {
            return;
        }
        FlowInfoEntity flowInfo = flowInfoService.getById(entity.getFlowId());
        if (flowInfo != null && Boolean.TRUE.equals(flowInfo.getPublishState())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程已发布, 无法更新任务定义");
        }
    }

    /**
     * 校验任务编码唯一性.
     *
     * @param taskCode  任务编码
     * @param excludeId 排除ID
     */
    private void checkTaskCodeUnique(String taskCode, UUID excludeId) {
        LambdaQueryWrapper<TaskInfoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskInfoEntity::getTaskCode, taskCode);
        if (excludeId != null) {
            wrapper.ne(TaskInfoEntity::getId, excludeId);
        }
        if (taskInfoService.count(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务编码已存在");
        }
    }

    /**
     * 解析任务编码.
     *
     * @param inputCode   输入编码
     * @param identity    业务身份
     * @param currentCode 当前编码
     * @return 任务编码
     */
    private String resolveTaskCode(String inputCode, String identity, String currentCode) {
        if (StringUtils.isNotBlank(inputCode)) {
            return inputCode;
        }
        if (StringUtils.isNotBlank(currentCode)) {
            return currentCode;
        }
        return "TASK_" + UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 填充创建审计字段.
     *
     * @param entity 任务实体
     */
    private void fillCreateAudit(TaskInfoEntity entity) {
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
     * @param entity 任务实体
     */
    private void fillUpdateAudit(TaskInfoEntity entity) {
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
    }

    /**
     * 转换登记结果.
     *
     * @param entity  任务实体
     * @param created 是否新建
     * @return 登记结果
     */
    private TaskDefinitionRegisterResultDto toRegisterResult(TaskInfoEntity entity, boolean created) {
        TaskDefinitionRegisterResultDto result = new TaskDefinitionRegisterResultDto();
        result.setTaskId(entity.getId());
        result.setTaskCode(entity.getTaskCode());
        result.setCreated(created);
        result.setSyncFlag(entity.getSyncFlag());
        result.setIsBound(entity.getIsBound());
        result.setFlowId(entity.getFlowId());
        return result;
    }
}
