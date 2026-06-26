package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.scheduler.dao.FlowInfoMapper;
import com.datafusion.manager.scheduler.dao.TriggerInfoMapper;
import com.datafusion.manager.scheduler.dto.DagSaveDto;
import com.datafusion.manager.scheduler.dto.EdgeDto;
import com.datafusion.manager.scheduler.dto.EdgeViewDto;
import com.datafusion.manager.scheduler.dto.FlowDagDto;
import com.datafusion.manager.scheduler.dto.FlowInfoDto;
import com.datafusion.manager.scheduler.dto.FlowInfoQueryDto;
import com.datafusion.manager.scheduler.dto.FlowInfoSaveDto;
import com.datafusion.manager.scheduler.dto.FlowInfoUpdateDto;
import com.datafusion.manager.scheduler.dto.FlowPublishDto;
import com.datafusion.manager.scheduler.dto.NodeDataDto;
import com.datafusion.manager.scheduler.dto.NodeDto;
import com.datafusion.manager.scheduler.dto.NodeViewDto;
import com.datafusion.manager.scheduler.dto.PositionDto;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.po.TaskLinkEntity;
import com.datafusion.manager.scheduler.po.TriggerInfoEntity;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.scheduler.service.TaskLinkService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.scheduler.master.MasterService;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-流程信息Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInfoServiceImpl extends ServiceImpl<FlowInfoMapper, FlowInfoEntity>
        implements FlowInfoService {

    /**
     * 任务信息Service.
     */
    private final TaskInfoService taskInfoService;

    /**
     * 任务编排关系Service.
     */
    private final TaskLinkService taskLinkService;

    /**
     * 触发器信息Mapper.
     */
    private final TriggerInfoMapper triggerInfoMapper;

    /**
     * master 服务.
     */
    private final ObjectProvider<MasterService> masterServiceProvider;

    @Override
    public FlowInfoEntity getFlowInfo(UUID flowId) {
        return baseMapper.getFlowInfo(flowId);
    }

    @Override
    public List<FlowInfoEntity> listAllEnabled() {
        return baseMapper.listAllEnabled();
    }

    @Override
    public PageResponse<FlowInfoDto> pageFlowInfo(PageQuery<FlowInfoQueryDto> query) {
        LambdaQueryWrapper<FlowInfoEntity> wrapper = buildQueryWrapper(query.getOption());
        IPage<FlowInfoEntity> page = baseMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);
        List<FlowInfoEntity> records = page.getRecords();
        Map<UUID, String> triggerNameMap = buildTriggerNameMap(records);

        PageResponse<FlowInfoDto> response = new PageResponse<>();
        response.setDataList(records.stream().map(entity -> toDto(entity, triggerNameMap)).collect(Collectors.toList()));
        response.setCurrent((int) page.getCurrent());
        response.setSize((int) page.getSize());
        response.setTotal((int) page.getTotal());
        return response;
    }

    @Override
    public List<FlowInfoDto> listFlowInfo(FlowInfoQueryDto query) {
        LambdaQueryWrapper<FlowInfoEntity> wrapper = buildQueryWrapper(query);
        List<FlowInfoEntity> records = baseMapper.selectList(wrapper);
        Map<UUID, String> triggerNameMap = buildTriggerNameMap(records);
        return records.stream().map(entity -> toDto(entity, triggerNameMap)).collect(Collectors.toList());
    }

    @Override
    public FlowInfoDto getFlowInfoById(UUID id) {
        FlowInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }
        return toDto(entity, buildTriggerNameMap(List.of(entity)));
    }

    @Override
    public UUID addFlowInfo(FlowInfoSaveDto dto) {
        checkCodeUnique(dto.getFlowCode(), null);

        FlowInfoEntity entity = new FlowInfoEntity();
        entity.setId(UUID.nameUUIDFromBytes(dto.getFlowCode().getBytes()));
        entity.setFlowName(dto.getFlowName());
        entity.setFlowCode(dto.getFlowCode());
        entity.setGroupId(dto.getGroupId());
        entity.setDescription(dto.getDescription());
        entity.setFlowType(dto.getFlowType());
        if (StringUtils.isNotBlank(dto.getFlowParam())) {
            entity.setFlowParam(JacksonUtils.tryStr2JsonNode(dto.getFlowParam()));
        }
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setDepEventIds(joinDepEventIds(dto.getDepEventIds()));
        entity.setTriggerId(dto.getTriggerId());
        entity.setEnabled(false);
        entity.setPublishState(false);
        entity.setPublishVersion(0L);

        Date now = new Date();
        entity.setCreator(HttpUtils.getCurrentUserName());
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);

        save(entity);
        return entity.getId();
    }

    @Override
    public boolean updateFlowInfo(FlowInfoUpdateDto dto) {
        FlowInfoEntity entity = getById(dto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }

        if (StringUtils.isNotBlank(dto.getFlowCode())) {
            checkCodeUnique(dto.getFlowCode(), dto.getId());
            entity.setFlowCode(dto.getFlowCode());
        }
        if (StringUtils.isNotBlank(dto.getFlowName())) {
            entity.setFlowName(dto.getFlowName());
        }
        if (dto.getGroupId() != null) {
            entity.setGroupId(dto.getGroupId());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (StringUtils.isNotBlank(dto.getFlowType())) {
            entity.setFlowType(dto.getFlowType());
        }
        if (StringUtils.isNotBlank(dto.getFlowParam())) {
            entity.setFlowParam(JacksonUtils.tryStr2JsonNode(dto.getFlowParam()));
        }
        if (dto.getStartTime() != null) {
            entity.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            entity.setEndTime(dto.getEndTime());
        }
        if (dto.getDepEventIds() != null) {
            entity.setDepEventIds(joinDepEventIds(dto.getDepEventIds()));
        }
        if (dto.getTriggerId() != null) {
            entity.setTriggerId(dto.getTriggerId());
        }

        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        return updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFlowInfo(UUID id) {
        FlowInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程调度中, 无法删除");
        }
        if (Boolean.TRUE.equals(entity.getPublishState())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程已发布, 无法删除");
        }

        // 解绑所有任务
        unbindTasks(id);
        // 清理连线
        deleteLinks(id);
        // 删除流程
        return removeById(id);
    }

    @Override
    public FlowDagDto getDag(UUID flowId) {
        FlowInfoEntity flowEntity = getById(flowId);
        if (flowEntity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }

        FlowDagDto dagDto = new FlowDagDto();
        dagDto.setFlowId(flowId);

        // 组装节点: 从 task_info 查绑定任务
        List<TaskInfoEntity> tasks = taskInfoService.listByFlowId(flowId);
        List<NodeDto> nodes = new ArrayList<>();
        for (TaskInfoEntity task : tasks) {
            NodeDto node = new NodeDto();
            node.setId(task.getId().toString());

            // 从 task_info.view 解析节点视图
            JsonNode viewNode = task.getView();
            if (viewNode != null && !viewNode.isEmpty()) {
                NodeViewDto nodeView = new NodeViewDto();
                // 解析 position
                JsonNode positionNode = viewNode.get("position");
                if (positionNode != null) {
                    PositionDto position = new PositionDto();
                    position.setX(positionNode.has("x") ? positionNode.get("x").asDouble() : null);
                    position.setY(positionNode.has("y") ? positionNode.get("y").asDouble() : null);
                    nodeView.setPosition(position);
                }
                // 解析 style
                if (viewNode.has("style") && viewNode.get("style").isObject()) {
                    nodeView.setStyle(JacksonUtils.convertJsonNodeToPojoSafely(viewNode.get("style"), java.util.Map.class));
                }
                // 解析 extra
                if (viewNode.has("extra") && viewNode.get("extra").isObject()) {
                    nodeView.setExtra(JacksonUtils.convertJsonNodeToPojoSafely(viewNode.get("extra"), java.util.Map.class));
                }
                node.setNodeView(nodeView);
            }

            // 填充业务数据
            NodeDataDto data = new NodeDataDto();
            data.setTaskId(task.getId().toString());
            data.setTaskName(task.getTaskName());
            data.setTaskCode(task.getTaskCode());
            data.setTaskType(task.getTaskType());
            data.setDescription(task.getDescription());
            data.setSyncFlag(task.getSyncFlag());
            data.setPluginId(task.getPluginId() == null ? null : task.getPluginId().toString());
            data.setDepEventIds(task.getDepEventIds());
            data.setEventId(task.getEventId() == null ? null : task.getEventId().toString());
            data.setEnabled(task.getEnabled());
            data.setTaskParam(JacksonUtils.isEmpty(task.getTaskParam()) ? null : JacksonUtils.tryObj2Str(task.getTaskParam()));
            data.setDefinition(JacksonUtils.isEmpty(task.getDefinition()) ? null : JacksonUtils.tryObj2Str(task.getDefinition()));
            node.setData(data);

            nodes.add(node);
        }
        dagDto.setNodes(nodes);

        // 组装连线: 从 task_link 查
        List<TaskLinkEntity> links = taskLinkService.listByFlowId(flowId);
        List<EdgeDto> edges = new ArrayList<>();
        for (TaskLinkEntity link : links) {
            EdgeDto edge = new EdgeDto();
            edge.setId(link.getId().toString());
            edge.setSource(link.getStartId().toString());
            edge.setTarget(link.getEndId().toString());

            // 从 task_link.view 解析连线视图
            JsonNode viewNode = link.getView();
            if (viewNode != null && !viewNode.isEmpty()) {
                EdgeViewDto edgeView = new EdgeViewDto();
                // 解析 label
                if (viewNode.has("label")) {
                    edgeView.setLabel(viewNode.get("label").asText());
                }
                // 解析 style
                if (viewNode.has("style") && viewNode.get("style").isObject()) {
                    edgeView.setStyle(JacksonUtils.convertJsonNodeToPojoSafely(viewNode.get("style"), java.util.Map.class));
                }
                // 解析 extra
                if (viewNode.has("extra") && viewNode.get("extra").isObject()) {
                    edgeView.setExtra(JacksonUtils.convertJsonNodeToPojoSafely(viewNode.get("extra"), java.util.Map.class));
                }
                edge.setEdgeView(edgeView);
            }

            edges.add(edge);
        }
        dagDto.setEdges(edges);

        return dagDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDag(DagSaveDto dto) {
        FlowInfoEntity flowEntity = getById(dto.getFlowId());
        if (flowEntity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }

        UUID flowId = dto.getFlowId();

        // 1. 解绑旧任务
        unbindTasks(flowId);

        // 2. 绑定新任务 + 更新节点视图
        if (!CollectionUtils.isEmpty(dto.getNodes())) {
            for (NodeDto node : dto.getNodes()) {
                UUID taskId = UUID.fromString(node.getId());
                TaskInfoEntity task = taskInfoService.getById(taskId);
                if (task == null) {
                    throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300,
                            "任务不存在: " + node.getId());
                }
                // 校验单流程绑定
                if (Boolean.TRUE.equals(task.getIsBound()) && !flowId.equals(task.getFlowId())) {
                    throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300,
                            "任务[" + task.getTaskCode() + "]已被其他流程引用");
                }

                bindTaskToFlow(taskId, flowId, node);
            }
        }

        // 3. 删除旧连线
        deleteLinks(flowId);

        // 4. 批量插入新连线
        if (!CollectionUtils.isEmpty(dto.getEdges())) {
            List<TaskLinkEntity> linkEntities = new ArrayList<>();
            for (EdgeDto edge : dto.getEdges()) {
                TaskLinkEntity link = new TaskLinkEntity();
                // 前端生成的 edge ID 为复合字符串（如 e{source}-{target}），非标准 UUID 格式
                link.setId(UUID.randomUUID());
                link.setFlowId(flowId);
                link.setStartId(UUID.fromString(edge.getSource()));
                link.setEndId(UUID.fromString(edge.getTarget()));

                // 将 edgeView 写入 view JSON
                if (edge.getEdgeView() != null) {
                    link.setView(JacksonUtils.convertPojoToJsonNodeSafely(edge.getEdgeView()));
                }

                linkEntities.add(link);
            }
            taskLinkService.saveBatch(linkEntities);
        }

        return true;
    }

    @Override
    public boolean publish(FlowPublishDto dto) {
        FlowInfoEntity entity = getById(dto.getId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }
        checkFlowHasTask(entity.getId(), "空流程无法发布");

        entity.setPublishState(true);
        entity.setPublishVersion(System.currentTimeMillis());
        if (Boolean.TRUE.equals(dto.getEnableSchedule())) {
            entity.setEnabled(true);
        }
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        boolean updated = updateById(entity);
        if (updated && Boolean.TRUE.equals(dto.getEnableSchedule())) {
            addSchedule(entity.getId());
        }
        return updated;
    }

    @Override
    public boolean unpublish(UUID id) {
        FlowInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }

        if (Boolean.TRUE.equals(entity.getEnabled())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程调度中, 请先取消调度后再取消发布");
        }
        entity.setPublishState(false);
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        boolean updated = updateById(entity);
        if (updated) {
            invalidateSchedulerInfo(id);
        }
        return updated;
    }

    @Override
    public boolean enable(UUID id) {
        FlowInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }
        if (!Boolean.TRUE.equals(entity.getPublishState())) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程未发布, 无法开始调度");
        }
        checkFlowHasTask(entity.getId(), "空流程无法开始调度");

        entity.setEnabled(true);
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        boolean updated = updateById(entity);
        if (updated) {
            addSchedule(id);
        }
        return updated;
    }

    @Override
    public boolean disable(UUID id) {
        FlowInfoEntity entity = getById(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程不存在");
        }

        entity.setEnabled(false);
        entity.setUpdater(HttpUtils.getCurrentUserName());
        entity.setUpdateTime(new Date());
        boolean updated = updateById(entity);
        if (updated) {
            stopSchedule(id);
        }
        return updated;
    }

    // region 私有方法

    /**
     * 构建查询条件.
     *
     * @param query 查询参数
     * @return 查询条件
     */
    private LambdaQueryWrapper<FlowInfoEntity> buildQueryWrapper(FlowInfoQueryDto query) {
        LambdaQueryWrapper<FlowInfoEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.isNotBlank(query.getFlowName())) {
                wrapper.like(FlowInfoEntity::getFlowName, query.getFlowName());
            }
            if (StringUtils.isNotBlank(query.getFlowType())) {
                wrapper.eq(FlowInfoEntity::getFlowType, query.getFlowType());
            }
            if (query.getEnabled() != null) {
                wrapper.eq(FlowInfoEntity::getEnabled, query.getEnabled());
            }
            if (query.getPublishState() != null) {
                wrapper.eq(FlowInfoEntity::getPublishState, query.getPublishState());
            }
        }
        wrapper.orderByDesc(FlowInfoEntity::getCreateTime);
        return wrapper;
    }

    /**
     * 校验flowCode唯一性.
     *
     * @param flowCode  流程编码
     * @param excludeId 排除的ID(修改时排除自身)
     */
    private void checkCodeUnique(String flowCode, UUID excludeId) {
        if (StringUtils.isBlank(flowCode)) {
            return;
        }
        LambdaQueryWrapper<FlowInfoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowInfoEntity::getFlowCode, flowCode);
        if (excludeId != null) {
            wrapper.ne(FlowInfoEntity::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程编码已存在");
        }
    }

    /**
     * 校验流程已绑定任务.
     *
     * @param flowId       流程ID
     * @param errorMessage 校验失败提示
     */
    private void checkFlowHasTask(UUID flowId, String errorMessage) {
        if (CollectionUtils.isEmpty(taskInfoService.listByFlowId(flowId))) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, errorMessage);
        }
    }

    /**
     * 解绑流程下所有任务.
     *
     * @param flowId 流程ID
     */
    private void unbindTasks(UUID flowId) {
        List<TaskInfoEntity> tasks = taskInfoService.listByFlowId(flowId);
        for (TaskInfoEntity task : tasks) {
            LambdaUpdateWrapper<TaskInfoEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(TaskInfoEntity::getId, task.getId())
                    .set(TaskInfoEntity::getIsBound, false)
                    .set(TaskInfoEntity::getFlowId, null)
                    .set(TaskInfoEntity::getView, null)
                    .set(TaskInfoEntity::getSyncFlag, false)
                    .set(TaskInfoEntity::getUpdater, HttpUtils.getCurrentUserName())
                    .set(TaskInfoEntity::getUpdateTime, new Date());
            taskInfoService.update(wrapper);
        }
    }

    /**
     * 绑定任务到流程并只更新编排字段.
     *
     * @param taskId 任务ID
     * @param flowId 流程ID
     * @param node   DAG节点
     */
    private void bindTaskToFlow(UUID taskId, UUID flowId, NodeDto node) {
        LambdaUpdateWrapper<TaskInfoEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TaskInfoEntity::getId, taskId)
                .set(TaskInfoEntity::getIsBound, true)
                .set(TaskInfoEntity::getFlowId, flowId)
                .set(TaskInfoEntity::getSyncFlag, false)
                .set(TaskInfoEntity::getUpdater, HttpUtils.getCurrentUserName())
                .set(TaskInfoEntity::getUpdateTime, new Date());

        if (node.getNodeView() != null) {
            wrapper.set(TaskInfoEntity::getView, JacksonUtils.convertPojoToJsonNodeSafely(node.getNodeView()));
        }
        taskInfoService.update(wrapper);
    }

    /**
     * 删除流程下所有连线.
     *
     * @param flowId 流程ID
     */
    private void deleteLinks(UUID flowId) {
        LambdaQueryWrapper<TaskLinkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskLinkEntity::getFlowId, flowId);
        taskLinkService.remove(wrapper);
    }

    /**
     * List转逗号分割字符串.
     *
     * @param list 字符串列表
     * @return 逗号分割字符串
     */
    private String joinDepEventIds(List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        return String.join(",", list);
    }

    /**
     * 逗号分割字符串转List.
     *
     * @param str 逗号分割字符串
     * @return 字符串列表
     */
    private List<String> splitDepEventIds(String str) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        return Arrays.asList(str.split(","));
    }

    /**
     * 批量查询流程关联的触发器名称.
     *
     * @param entities 流程实体列表
     * @return 触发器ID到触发器名称的映射
     */
    private Map<UUID, String> buildTriggerNameMap(List<FlowInfoEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyMap();
        }
        List<UUID> triggerIds = entities.stream()
                .map(FlowInfoEntity::getTriggerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(triggerIds)) {
            return Collections.emptyMap();
        }
        return triggerInfoMapper.selectBatchIds(triggerIds).stream()
                .collect(Collectors.toMap(TriggerInfoEntity::getId, TriggerInfoEntity::getName, (left, right) -> left));
    }

    /**
     * 将已启用流程加入运行中的 master 调度缓存.
     *
     * @param flowId 流程ID
     */
    private void addSchedule(UUID flowId) {
        MasterService masterService = masterServiceProvider.getIfAvailable();
        if (masterService == null) {
            return;
        }
        masterService.getMasterStorage().invalidateSchedulerInfo(flowId.toString());
        TriggerInfo triggerInfo = masterService.getMasterStorage().getTriggerStorage().getTriggerInfo(flowId.toString());
        if (triggerInfo == null) {
            log.warn("流程启用后未找到触发器信息, flowId={}", flowId);
            return;
        }
        long baseTime = triggerInfo.getStartTime() > 0 ? triggerInfo.getStartTime() : System.currentTimeMillis();
        masterService.addSchedule(triggerInfo, baseTime, true);
    }

    /**
     * 停止运行中的 master 调度.
     *
     * @param flowId 流程ID
     */
    private void stopSchedule(UUID flowId) {
        MasterService masterService = masterServiceProvider.getIfAvailable();
        if (masterService != null) {
            masterService.stopSchedule(flowId.toString());
        }
    }

    /**
     * 失效运行中 master 的调度定义缓存.
     *
     * @param flowId 流程ID
     */
    private void invalidateSchedulerInfo(UUID flowId) {
        MasterService masterService = masterServiceProvider.getIfAvailable();
        if (masterService != null) {
            masterService.getMasterStorage().invalidateSchedulerInfo(flowId.toString());
        }
    }

    /**
     * Entity转Dto.
     *
     * @param entity         流程实体
     * @param triggerNameMap 触发器名称映射
     * @return 流程Dto
     */
    private FlowInfoDto toDto(FlowInfoEntity entity, Map<UUID, String> triggerNameMap) {
        FlowInfoDto dto = new FlowInfoDto();
        dto.setId(entity.getId());
        dto.setFlowName(entity.getFlowName());
        dto.setFlowCode(entity.getFlowCode());
        dto.setGroupId(entity.getGroupId());
        dto.setDescription(entity.getDescription());
        dto.setFlowType(entity.getFlowType());
        dto.setFlowParam(JacksonUtils.isEmpty(entity.getFlowParam()) ? null : JacksonUtils.tryObj2Str(entity.getFlowParam()));
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setEnabled(entity.getEnabled());
        dto.setDepEventIds(splitDepEventIds(entity.getDepEventIds()));
        dto.setEventId(entity.getEventId());
        dto.setTriggerId(entity.getTriggerId());
        dto.setTriggerName(triggerNameMap.get(entity.getTriggerId()));
        dto.setPublishState(entity.getPublishState());
        dto.setPublishVersion(entity.getPublishVersion());
        dto.setCreator(entity.getCreator());
        dto.setUpdater(entity.getUpdater());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
    // endregion
}
