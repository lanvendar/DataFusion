package com.datafusion.manager.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.datafusion.common.exception.CommonException;
import com.datafusion.common.exception.ErrorCodeEnum;
import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.common.spring.dto.request.page.PageQuery;
import com.datafusion.common.spring.dto.response.PageResponse;
import com.datafusion.manager.scheduler.dao.TaskInstanceMapper;
import com.datafusion.manager.scheduler.dao.TaskInstanceHisMapper;
import com.datafusion.manager.scheduler.dto.FlowInstanceTaskQueryDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceActionDto;
import com.datafusion.manager.scheduler.dto.SchedulerInstanceQueryDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogDto;
import com.datafusion.manager.scheduler.dto.TaskInstanceLogQueryDto;
import com.datafusion.manager.scheduler.model.SchedulerInstanceActionPolicy;
import com.datafusion.manager.scheduler.po.TaskInstanceEntity;
import com.datafusion.manager.scheduler.po.TaskInstanceHisEntity;
import com.datafusion.manager.scheduler.service.TaskInstanceService;
import com.datafusion.manager.utils.HttpUtils;
import com.datafusion.manager.utils.ImplUtil;
import com.datafusion.scheduler.enums.ActionType;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.MasterService;
import com.datafusion.scheduler.master.task.model.TaskInstance;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.datafusion.scheduler.model.TaskResult;
import com.datafusion.scheduler.model.WorkerResult;
import com.datafusion.scheduler.model.TaskRuntimeFiles;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 调度-任务实例Service实现.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskInstanceServiceImpl extends ServiceImpl<TaskInstanceMapper, TaskInstanceEntity>
        implements TaskInstanceService {

    /**
     * 默认日志读取大小.
     */
    private static final int DEFAULT_LOG_LIMIT = 64 * 1024;

    /**
     * 最大日志读取大小.
     */
    private static final int MAX_LOG_LIMIT = 1024 * 1024;

    /**
     * 历史视图类型.
     */
    private static final String VIEW_TYPE_HISTORY = "HISTORY";

    /**
     * 普通日志类型.
     */
    private static final String LOG_TYPE_LOG = "LOG";

    /**
     * 错误日志类型.
     */
    private static final String LOG_TYPE_ERROR = "ERROR";

    /**
     * 状态日志类型.
     */
    private static final String LOG_TYPE_STATUS = "STATUS";

    /**
     * 插件日志类型.
     */
    private static final String LOG_TYPE_PLUGIN = "PLUGIN";

    /**
     * 历史任务实例Mapper.
     */
    private final TaskInstanceHisMapper taskInstanceHisMapper;

    /**
     * master 服务.
     */
    private final ObjectProvider<MasterService> masterServiceProvider;

    @Override
    public PageResponse<TaskInstanceDto> pageTaskInstance(PageQuery<SchedulerInstanceQueryDto> query) {
        PageQuery<SchedulerInstanceQueryDto> pageQuery = normalizePageQuery(query);
        SchedulerInstanceQueryDto option = normalizeOption(pageQuery.getOption());

        if (isHistory(option.getViewType())) {
            Page<TaskInstanceHisEntity> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
            Page<TaskInstanceHisEntity> result = taskInstanceHisMapper.pageTaskInstance(page, option);
            return toPageResponse(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
        }

        Page<TaskInstanceEntity> page = new Page<>(pageQuery.getCurrent(), pageQuery.getSize());
        Page<TaskInstanceEntity> result = baseMapper.pageTaskInstance(page, option);
        return toPageResponse(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public TaskInstanceDto getTaskInstanceById(UUID id) {
        TaskInstanceEntity entity = findTaskInstance(id);
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务实例不存在");
        }
        return toDto(entity);
    }

    @Override
    public List<TaskInstanceDto> listByFlowInstance(FlowInstanceTaskQueryDto query) {
        if (query == null || query.getFlowInstanceId() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "流程实例ID不能为空");
        }

        if (isHistory(query.getViewType())) {
            return taskInstanceHisMapper.listByFlowInsId(query.getFlowInstanceId()).stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }
        return baseMapper.listByFlowInsId(query.getFlowInstanceId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public TaskInstanceLogDto readTaskInstanceLog(TaskInstanceLogQueryDto query) {
        if (query == null || query.getTaskInstanceId() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务实例ID不能为空");
        }

        TaskInstanceEntity entity = findTaskInstance(query.getTaskInstanceId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务实例不存在");
        }

        String logType = StringUtils.defaultIfBlank(query.getLogType(), LOG_TYPE_LOG).toUpperCase();
        Path path = resolveLogPath(entity, logType);
        return readLogFile(path, logType, query.getOffset(), query.getLimit());
    }

    @Override
    public Boolean actionTaskInstance(SchedulerInstanceActionDto action) {
        if (action == null || action.getTaskInstanceId() == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务实例ID不能为空");
        }
        ActionType actionType = parseActionType(action.getActionType());
        TaskInstanceEntity entity = baseMapper.getInstanceById(action.getTaskInstanceId());
        if (entity == null) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "仅支持操作实时任务实例");
        }
        if (!SchedulerInstanceActionPolicy.canTaskAction(entity.getStatus(), actionType)) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "当前任务实例状态不支持该操作");
        }

        TaskInstance instance = toTaskInstance(entity);
        MasterService masterService = masterServiceProvider.getObject();
        switch (actionType) {
            case SUBMIT:
                masterService.getTaskAction().taskSubmit(instance);
                break;
            case STOP:
                masterService.getTaskAction().taskStop(instance);
                break;
            case KILL:
                masterService.getTaskAction().taskKill(instance);
                break;
            case RESTART:
                masterService.getTaskAction().taskRestart(instance);
                break;
            case ENFORCE_SUCCESS:
                masterService.getTaskAction().taskEnforceSuccess(instance);
                break;
            default:
                throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "不支持的任务实例操作");
        }
        log.info("操作任务实例,user={},taskInstanceId={},flowInstanceId={},actionType={},result={}",
                HttpUtils.getCurrentUserName(), action.getTaskInstanceId(), entity.getFlowInstanceId(), actionType, true);
        return true;
    }

    @Override
    public TaskInstanceEntity getInstanceById(UUID instanceId) {
        return baseMapper.getInstanceById(instanceId);
    }

    @Override
    public List<UUID> listInsIdsByFlowInsId(UUID flowInstanceId) {
        return baseMapper.listInsIdsByFlowInsId(flowInstanceId);
    }

    @Override
    public List<TaskInstanceEntity> listByFlowInsId(UUID flowInstanceId) {
        return baseMapper.listByFlowInsId(flowInstanceId);
    }

    @Override
    public int removeByInstanceId(UUID instanceId) {
        return baseMapper.removeByInstanceId(instanceId);
    }

    @Override
    public int removeByFlowInsId(UUID flowInstanceId) {
        return baseMapper.removeByFlowInsId(flowInstanceId);
    }

    private PageQuery<SchedulerInstanceQueryDto> normalizePageQuery(PageQuery<SchedulerInstanceQueryDto> query) {
        if (query != null) {
            return query;
        }
        return new PageQuery<>(new SchedulerInstanceQueryDto());
    }

    private SchedulerInstanceQueryDto normalizeOption(SchedulerInstanceQueryDto option) {
        return option != null ? option : new SchedulerInstanceQueryDto();
    }

    private boolean isHistory(String viewType) {
        return VIEW_TYPE_HISTORY.equalsIgnoreCase(viewType);
    }

    private TaskInstanceEntity findTaskInstance(UUID id) {
        TaskInstanceEntity entity = baseMapper.getInstanceById(id);
        if (entity != null) {
            return entity;
        }
        return taskInstanceHisMapper.getInstanceById(id);
    }

    private PageResponse<TaskInstanceDto> toPageResponse(List<? extends TaskInstanceEntity> records,
                                                         long current,
                                                         long size,
                                                         long total) {
        PageResponse<TaskInstanceDto> response = new PageResponse<>();
        response.setDataList(records.stream().map(this::toDto).collect(Collectors.toList()));
        response.setCurrent((int) current);
        response.setSize((int) size);
        response.setTotal((int) total);
        return response;
    }

    private TaskInstanceDto toDto(TaskInstanceEntity entity) {
        TaskInstanceDto dto = new TaskInstanceDto();
        dto.setId(entity.getId());
        dto.setFlowInstanceId(entity.getFlowInstanceId());
        dto.setTaskId(entity.getTaskId());
        dto.setTaskType(entity.getTaskType());
        dto.setTaskName(entity.getTaskName());
        dto.setTaskCode(entity.getTaskCode());
        dto.setStatus(entity.getStatus());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setCostTime(entity.getCostTime());
        dto.setLastInstanceId(entity.getLastInstanceId());
        dto.setNextInstanceId(entity.getNextInstanceId());
        dto.setWorkerId(entity.getWorkerId());
        dto.setWorkerResult(entity.getWorkerResult());
        dto.setWorkerResultText(extractWorkerResultText(entity.getWorkerResult()));
        dto.setWorkDirPath(extractWorkDirPath(entity.getWorkerResult()));
        dto.setAvailableActions(SchedulerInstanceActionPolicy.taskActions(entity.getStatus()));
        return dto;
    }

    private String extractWorkerResultText(JsonNode workerResult) {
        if (workerResult == null || workerResult.isNull()) {
            return null;
        }
        if (workerResult.hasNonNull("message")) {
            return workerResult.get("message").asText();
        }
        if (workerResult.hasNonNull("appId")) {
            return "appId: " + workerResult.get("appId").asText();
        }
        if (workerResult.hasNonNull("workerId")) {
            return "workerId: " + workerResult.get("workerId").asText();
        }
        return workerResult.toString();
    }

    private String extractWorkDirPath(JsonNode workerResult) {
        return extractText(workerResult, "workDirPath");
    }

    private String extractText(JsonNode workerResult, String fieldName) {
        if (workerResult == null || workerResult.isNull() || !workerResult.hasNonNull(fieldName)) {
            return null;
        }
        return workerResult.get(fieldName).asText();
    }

    private Path resolveLogPath(TaskInstanceEntity entity, String logType) {
        if (LOG_TYPE_PLUGIN.equals(logType)) {
            return resolvePluginLogPath(entity);
        }
        String workDirPath = extractWorkDirPath(entity.getWorkerResult());
        if (StringUtils.isBlank(workDirPath)) {
            return null;
        }
        Path workDir = Path.of(workDirPath);
        switch (logType) {
            case LOG_TYPE_ERROR:
                return TaskRuntimeFiles.stderrLog(workDir);
            case LOG_TYPE_STATUS:
                return TaskRuntimeFiles.stateLog(workDir);
            case LOG_TYPE_LOG:
            default:
                return TaskRuntimeFiles.stdoutLog(workDir);
        }
    }

    private Path resolvePluginLogPath(TaskInstanceEntity entity) {
        String workDirPath = extractWorkDirPath(entity.getWorkerResult());
        String pluginLogUri = extractText(entity.getWorkerResult(), "pluginLogUri");
        if (StringUtils.isAnyBlank(workDirPath, pluginLogUri) || pluginLogUri.contains("://")) {
            return null;
        }
        Path workDir = Path.of(workDirPath).normalize();
        Path pluginLogPath = Path.of(pluginLogUri);
        Path normalizedPluginLogPath = pluginLogPath.isAbsolute()
                ? pluginLogPath.normalize()
                : workDir.resolve(pluginLogPath).normalize();
        if (!normalizedPluginLogPath.startsWith(workDir)) {
            return null;
        }
        return normalizedPluginLogPath;
    }

    private TaskInstanceLogDto readLogFile(Path path, String logType, Long offsetValue, Integer limitValue) {
        TaskInstanceLogDto dto = new TaskInstanceLogDto();
        dto.setLogType(logType);
        dto.setPath(path != null ? path.toString() : null);
        dto.setContent("");
        dto.setNextOffset(offsetValue != null ? Math.max(0L, offsetValue) : 0L);
        dto.setHasMore(false);

        if (path == null || !Files.isRegularFile(path)) {
            return dto;
        }

        long offset = offsetValue != null ? Math.max(0L, offsetValue) : 0L;
        int limit = normalizeLimit(limitValue);
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            if (offset > length) {
                offset = length;
            }
            int readSize = (int) Math.min(limit, length - offset);
            byte[] buffer = new byte[readSize];
            file.seek(offset);
            int read = file.read(buffer);
            if (read > 0) {
                dto.setContent(new String(buffer, 0, read, StandardCharsets.UTF_8));
                dto.setNextOffset(offset + read);
                dto.setHasMore(dto.getNextOffset() < length);
            } else {
                dto.setNextOffset(offset);
            }
            return dto;
        } catch (IOException e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "读取任务日志失败");
        }
    }

    private int normalizeLimit(Integer limitValue) {
        if (limitValue == null || limitValue <= 0) {
            return DEFAULT_LOG_LIMIT;
        }
        return Math.min(limitValue, MAX_LOG_LIMIT);
    }

    private ActionType parseActionType(String actionType) {
        try {
            return ActionType.valueOf(actionType);
        } catch (Exception e) {
            throw new CommonException(ErrorCodeEnum.SERVICE_ERROR_C0300, "任务实例操作类型不合法");
        }
    }

    private TaskInstance toTaskInstance(TaskInstanceEntity entity) {
        TaskInstance instance = new TaskInstance();
        instance.setInstanceId(ImplUtil.uuidToStr(entity.getId()));
        instance.setFlowInstanceId(ImplUtil.uuidToStr(entity.getFlowInstanceId()));
        instance.setTaskId(ImplUtil.uuidToStr(entity.getTaskId()));
        instance.setTaskType(entity.getTaskType());
        instance.setTaskName(entity.getTaskName());
        instance.setTaskDesc(entity.getDescription());
        instance.setState(entity.getStatus() != null ? StatusEnum.fromString(entity.getStatus()) : null);
        instance.setStartTime(entity.getStartTime());
        instance.setEndTime(entity.getEndTime());
        instance.setCostTime(entity.getCostTime() != null ? entity.getCostTime().longValue() : null);
        instance.setLastInstanceIds(ImplUtil.parseCommaSet(entity.getLastInstanceId()));
        instance.setNextInstanceIds(ImplUtil.parseCommaSet(entity.getNextInstanceId()));
        instance.setDepEventIds(ImplUtil.parseCommaSet(entity.getDepEventIds()));
        instance.setEventId(ImplUtil.uuidToStr(entity.getEventId()));
        instance.setTaskParam(toParamData(entity.getTaskParam()));
        instance.setTaskData(entity.getTaskData());
        instance.setTaskResult(toTaskResult(entity));
        instance.setPluginData(JacksonUtils.tryObj2Bean(entity.getPluginData(), PluginData.class));
        return instance;
    }

    private TaskResult toTaskResult(TaskInstanceEntity entity) {
        WorkerResult workerResult = JacksonUtils.tryObj2Bean(entity.getWorkerResult(), WorkerResult.class);
        if (workerResult == null && JacksonUtils.isEmpty(entity.getWorkerResult())) {
            return null;
        }
        return TaskResult.builder()
                .taskInstanceId(ImplUtil.uuidToStr(entity.getId()))
                .flowInstanceId(ImplUtil.uuidToStr(entity.getFlowInstanceId()))
                .taskName(entity.getTaskName())
                .taskState(entity.getStatus() == null ? null : StatusEnum.fromString(entity.getStatus()))
                .workerResult(workerResult)
                .build();
    }

    private ParamData toParamData(JsonNode jsonNode) {
        if (JacksonUtils.isEmpty(jsonNode)) {
            return new ParamData();
        }
        return JacksonUtils.tryObj2Bean(jsonNode, ParamData.class);
    }
}
