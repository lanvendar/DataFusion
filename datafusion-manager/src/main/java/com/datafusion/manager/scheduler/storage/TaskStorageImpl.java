package com.datafusion.manager.scheduler.storage;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.scheduler.po.TaskInfoEntity;
import com.datafusion.manager.scheduler.po.TaskInstanceEntity;
import com.datafusion.manager.scheduler.po.TaskLinkEntity;
import com.datafusion.manager.scheduler.service.TaskInfoService;
import com.datafusion.manager.scheduler.service.TaskInstanceService;
import com.datafusion.manager.scheduler.service.TaskLinkService;
import com.datafusion.manager.utils.ImplUtil;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.task.model.TaskInfo;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.master.task.model.TaskLink;
import com.datafusion.scheduler.master.task.storage.TaskStorage;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.TaskResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * 任务存储实现, 适配scheduler TaskStorage接口.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskStorageImpl implements TaskStorage {

    /**
     * 任务信息Service.
     */
    private final TaskInfoService taskInfoService;

    /**
     * 任务实例Service.
     */
    private final TaskInstanceService taskInstanceService;

    /**
     * 任务编排关系Service.
     */
    private final TaskLinkService taskLinkService;

    // region TaskInfo 方法

    @Override
    public TaskInfo getTaskInfo(String taskId) {
        TaskInfoEntity entity = taskInfoService.getTaskInfo(UUID.fromString(taskId));
        return toTaskInfo(entity);
    }

    @Override
    public List<TaskLink> getTaskInfoLink(String flowId) {
        return taskLinkService.listByFlowId(UUID.fromString(flowId)).stream()
                .map(this::toTaskLink)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskInfo> getTaskInfoByFlowId(String flowId) {
        return taskInfoService.listByFlowId(UUID.fromString(flowId)).stream()
                .map(this::toTaskInfo)
                .collect(Collectors.toList());
    }
    // endregion

    // region TaskInstance 方法

    @Override
    public TaskInstance getInstanceById(String taskInsId) {
        TaskInstanceEntity entity = taskInstanceService.getInstanceById(UUID.fromString(taskInsId));
        return toTaskInstance(entity);
    }

    @Override
    public void saveInstance(TaskInstance taskInstance) {
        TaskInstanceEntity entity = toTaskInstanceEntity(taskInstance);
        TaskInstanceEntity existing = taskInstanceService.getInstanceById(entity.getId());
        if (existing != null) {
            taskInstanceService.updateById(entity);
        } else {
            taskInstanceService.save(entity);
        }
    }

    @Override
    public void removeInstanceById(String taskInsId) {
        taskInstanceService.removeByInstanceId(UUID.fromString(taskInsId));
    }

    @Override
    public List<TaskInstance> getTaskInsIdsByFlowInsId(String flowInsId) {
        return taskInstanceService.listByFlowInsId(UUID.fromString(flowInsId)).stream()
                .map(this::toTaskInstance)
                .collect(Collectors.toList());
    }

    @Override
    public void removeTaskInsByFlowInsId(String flowInsId) {
        taskInstanceService.removeByFlowInsId(UUID.fromString(flowInsId));
    }
    // endregion

    // region 转换方法

    private TaskInfo toTaskInfo(TaskInfoEntity entity) {
        if (entity == null) {
            return null;
        }
        TaskInfo info = new TaskInfo();
        info.setTaskId(ImplUtil.uuidToStr(entity.getId()));
        info.setFlowId(ImplUtil.uuidToStr(entity.getFlowId()));
        info.setTaskType(entity.getTaskType());
        info.setTaskName(entity.getTaskName());
        info.setTaskDesc(entity.getDescription());
        info.setTaskParam(toParamData(entity.getTaskParam()));
        info.setDefinition(entity.getDefinition());
        info.setDepEventIds(ImplUtil.parseCommaSet(entity.getDepEventIds()));
        info.setEventId(ImplUtil.uuidToStr(entity.getEventId()));
        info.setIsAble(entity.getEnabled());
        return info;
    }

    private TaskInstance toTaskInstance(TaskInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        TaskInstance ins = new TaskInstance();
        ins.setInstanceId(ImplUtil.uuidToStr(entity.getId()));
        ins.setFlowInstanceId(ImplUtil.uuidToStr(entity.getFlowInstanceId()));
        ins.setTaskId(ImplUtil.uuidToStr(entity.getTaskId()));
        ins.setTaskType(entity.getTaskType());
        ins.setTaskName(entity.getTaskName());
        ins.setTaskDesc(entity.getDescription());
        ins.setState(entity.getStatus() != null ? StatusEnum.fromString(entity.getStatus()) : null);
        ins.setStartTime(entity.getStartTime());
        ins.setEndTime(entity.getEndTime());
        ins.setCostTime(entity.getCostTime() != null ? entity.getCostTime().longValue() : null);
        ins.setLastInstanceIds(ImplUtil.parseCommaSet(entity.getLastInstanceId()));
        ins.setNextInstanceIds(ImplUtil.parseCommaSet(entity.getNextInstanceId()));
        ins.setDepEventIds(ImplUtil.parseCommaSet(entity.getDepEventIds()));
        ins.setEventId(ImplUtil.uuidToStr(entity.getEventId()));
        ins.setTaskParam(toParamData(entity.getTaskParam()));
        ins.setTaskData(entity.getTaskData());
        ins.setTaskResult(JacksonUtils.tryObj2Bean(entity.getWorkerResult(), TaskResult.class));
        ins.setPluginData(JacksonUtils.tryObj2Bean(entity.getPluginData(), PluginData.class));
        return ins;
    }

    private TaskInstanceEntity toTaskInstanceEntity(TaskInstance ins) {
        TaskInstanceEntity entity = new TaskInstanceEntity();
        entity.setId(ImplUtil.strToUuid(ins.getInstanceId()));
        entity.setFlowInstanceId(ImplUtil.strToUuid(ins.getFlowInstanceId()));
        entity.setTaskId(ImplUtil.strToUuid(ins.getTaskId()));
        entity.setTaskType(ins.getTaskType());
        entity.setTaskName(ins.getTaskName());
        entity.setDescription(ins.getTaskDesc());
        entity.setStatus(ins.getState() != null ? ins.getState().getStateType() : null);
        entity.setStartTime(ins.getStartTime());
        entity.setEndTime(ins.getEndTime());
        entity.setCostTime(ins.getCostTime() != null ? ins.getCostTime().intValue() : null);
        entity.setLastInstanceId(ImplUtil.joinCommaSet(ins.getLastInstanceIds()));
        entity.setNextInstanceId(ImplUtil.joinCommaSet(ins.getNextInstanceIds()));
        entity.setDepEventIds(ImplUtil.joinCommaSet(ins.getDepEventIds()));
        entity.setEventId(ImplUtil.strToUuid(ins.getEventId()));
        entity.setTaskParam(JacksonUtils.tryObj2JsonNode(ins.getTaskParam()));
        entity.setTaskData(ins.getTaskData());
        entity.setWorkerResult(JacksonUtils.tryObj2JsonNode(ins.getTaskResult()));
        entity.setPluginData(JacksonUtils.tryObj2JsonNode(ins.getPluginData()));
        return entity;
    }

    private TaskLink toTaskLink(TaskLinkEntity entity) {
        if (entity == null) {
            return null;
        }
        TaskLink link = new TaskLink();
        link.setId(ImplUtil.uuidToStr(entity.getId()));
        link.setStartId(ImplUtil.uuidToStr(entity.getStartId()));
        link.setEndId(ImplUtil.uuidToStr(entity.getEndId()));
        return link;
    }

    private ParamData toParamData(JsonNode jsonNode) {
        if (JacksonUtils.isEmpty(jsonNode)) {
            return new ParamData();
        }
        return JacksonUtils.tryObj2Bean(jsonNode, ParamData.class);
    }
    // endregion
}
