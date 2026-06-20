package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.manager.scheduler.dao.TaskInfoMapper;
import com.datafusion.manager.scheduler.dto.TaskDefinitionMarkUnsyncedDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionMarkUnsyncedResultDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterDto;
import com.datafusion.manager.scheduler.dto.TaskDefinitionRegisterResultDto;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.TaskDefinitionRegisterService;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.system.service.TaskTypeConfigService;
import com.datafusion.manager.utils.HttpUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
     * bizRef前缀.
     */
    private static final String BIZ_REF_PREFIX = "bizref:v1";

    /**
     * 任务信息Mapper.
     */
    private final TaskInfoMapper taskInfoMapper;

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
        BizRefIdentity bizRefIdentity = parseBizRef(requireBizRef(dto.getDefinition()));
        TaskInfoEntity entity = taskInfoMapper.getTaskInfoByBizRef(
                bizRefIdentity.getSystem(), bizRefIdentity.getBizType(), bizRefIdentity.getBizKey());
        boolean created = entity == null;
        if (created) {
            entity = buildNewEntity(dto, bizRefIdentity.getRaw());
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
    public TaskDefinitionMarkUnsyncedResultDto markUnsynced(TaskDefinitionMarkUnsyncedDto dto) {
        BizRefIdentity bizRefIdentity = parseBizRef(dto.getBizRef());
        TaskInfoEntity entity = taskInfoMapper.getTaskInfoByBizRef(
                bizRefIdentity.getSystem(), bizRefIdentity.getBizType(), bizRefIdentity.getBizKey());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务定义不存在");
        }
        entity.setSyncFlag(false);
        if (dto.getSourceRoute() != null) {
            entity.setSourceRoute(dto.getSourceRoute());
        }
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
     * @param dto    登记参数
     * @param bizRef 业务定位串
     * @return 任务实体
     */
    private TaskInfoEntity buildNewEntity(TaskDefinitionRegisterDto dto, String bizRef) {
        String taskCode = resolveTaskCode(dto.getTaskCode(), bizRef, null);
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
        entity.setEnabled(false);
        entity.setSyncFlag(true);
        entity.setSourceRoute(dto.getSourceRoute());
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
        String taskCode = resolveTaskCode(dto.getTaskCode(), requireBizRef(dto.getDefinition()), entity.getTaskCode());
        if (StringUtils.isNotBlank(taskCode) && !StringUtils.equals(taskCode, entity.getTaskCode())) {
            checkTaskCodeUnique(taskCode, entity.getId());
            entity.setTaskCode(taskCode);
        }
        entity.setTaskName(dto.getTaskName());
        entity.setDescription(dto.getDescription());
        entity.setTaskTypeId(dto.getTaskTypeId());
        entity.setTaskType(dto.getTaskType());
        entity.setTaskParam(dto.getTaskParam());
        entity.setDefinition(dto.getDefinition());
        if (dto.getSourceRoute() != null) {
            entity.setSourceRoute(dto.getSourceRoute());
        }
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
     * @param taskCode 任务编码
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
     * @param inputCode 输入编码
     * @param bizRef 业务定位串
     * @param currentCode 当前编码
     * @return 任务编码
     */
    private String resolveTaskCode(String inputCode, String bizRef, String currentCode) {
        if (StringUtils.isNotBlank(inputCode)) {
            return inputCode;
        }
        if (StringUtils.isNotBlank(currentCode)) {
            return currentCode;
        }
        return "TASK_" + UUID.nameUUIDFromBytes(bizRef.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 必须获取bizRef.
     *
     * @param definition 定义JSON
     * @return bizRef
     */
    private String requireBizRef(JsonNode definition) {
        if (definition == null || definition.isNull() || !definition.isObject()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务定义格式非法");
        }
        JsonNode bizRefNode = definition.get("bizRef");
        if (bizRefNode == null || bizRefNode.isNull() || StringUtils.isBlank(bizRefNode.asText())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务定义bizRef不能为空");
        }
        JsonNode dataNode = definition.get("data");
        if (dataNode == null || dataNode.isNull() || !dataNode.isObject()) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务定义data不能为空");
        }
        return bizRefNode.asText();
    }

    /**
     * 解析bizRef.
     *
     * @param bizRef 业务定位串
     * @return 业务定位信息
     */
    private BizRefIdentity parseBizRef(String bizRef) {
        if (StringUtils.isBlank(bizRef)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "bizRef不能为空");
        }
        String[] segments = bizRef.split(":");
        if (segments.length < 5 || !StringUtils.equals(segments[0] + ":" + segments[1], BIZ_REF_PREFIX)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "bizRef格式非法");
        }
        Map<String, String> values = new LinkedHashMap<>(8);
        for (int i = 2; i < segments.length; i++) {
            String segment = segments[i];
            int index = segment.indexOf('=');
            if (index <= 0 || index == segment.length() - 1) {
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "bizRef格式非法");
            }
            String key = segment.substring(0, index);
            String value = URLDecoder.decode(segment.substring(index + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        String system = values.get("system");
        String bizType = values.get("bizType");
        String bizKey = values.get("bizKey");
        if (StringUtils.isAnyBlank(system, bizType, bizKey)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "bizRef核心字段缺失");
        }
        return new BizRefIdentity(bizRef, system, bizType, bizKey);
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
     * @param entity 任务实体
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

    /**
     * bizRef核心标识.
     */
    @Getter
    @RequiredArgsConstructor
    private static class BizRefIdentity {

        /**
         * 原始bizRef.
         */
        private final String raw;

        /**
         * 来源系统.
         */
        private final String system;

        /**
         * 业务类型.
         */
        private final String bizType;

        /**
         * 业务唯一键.
         */
        private final String bizKey;
    }
}
