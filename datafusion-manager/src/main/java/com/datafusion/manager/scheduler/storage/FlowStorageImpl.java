package com.datafusion.manager.scheduler.storage;

import com.datafusion.common.utils.JacksonUtils;
import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.FlowInstanceService;
import com.datafusion.manager.utils.ImplUtil;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.flow.enums.FlowTypeEnum;
import com.datafusion.scheduler.master.flow.model.FlowInfo;
import com.datafusion.scheduler.master.flow.model.FlowInstance;
import com.datafusion.scheduler.master.flow.storage.FlowStorage;
import com.datafusion.scheduler.model.ParamData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 流程存储实现, 适配scheduler FlowStorage接口.
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowStorageImpl implements FlowStorage {

    /**
     * 流程信息Service.
     */
    private final FlowInfoService flowInfoService;

    /**
     * 流程实例Service.
     */
    private final FlowInstanceService flowInstanceService;

    // region FlowInfo 方法

    @Override
    public FlowInfo getFlowInfo(String flowId) {
        FlowInfoEntity entity = flowInfoService.getFlowInfo(UUID.fromString(flowId));
        return toFlowInfo(entity);
    }

    @Override
    public List<FlowInfo> getAllFlowInfo() {
        return flowInfoService.listAllEnabled().stream()
                .map(this::toFlowInfo)
                .collect(Collectors.toList());
    }
    // endregion

    // region FlowInstance 方法

    @Override
    public FlowInstance getInstanceById(String flowInsId) {
        FlowInstanceEntity entity = flowInstanceService.getInstanceById(UUID.fromString(flowInsId));
        return toFlowInstance(entity);
    }

    @Override
    public void saveInstance(FlowInstance flowIns) {
        FlowInstanceEntity entity = toFlowInstanceEntity(flowIns);
        FlowInstanceEntity existing = flowInstanceService.getInstanceById(entity.getId());
        if (existing != null) {
            flowInstanceService.updateById(entity);
        } else {
            flowInstanceService.save(entity);
        }
    }

    @Override
    public void removeInstanceById(String flowInsId) {
        flowInstanceService.removeByInstanceId(UUID.fromString(flowInsId));
    }

    @Override
    public List<FlowInstance> getAvailableInstance(String flowId) {
        UUID id = flowId != null ? UUID.fromString(flowId) : null;
        return flowInstanceService.listAvailable(id).stream()
                .map(this::toFlowInstance)
                .collect(Collectors.toList());
    }

    @Override
    public FlowInstance getLastInstance(String flowId, String version) {
        Long ver = version != null ? Long.parseLong(version) : null;
        FlowInstanceEntity entity = flowInstanceService.getLastInstance(UUID.fromString(flowId), ver);
        return toFlowInstance(entity);
    }
    // endregion

    // region 转换方法

    private FlowInfo toFlowInfo(FlowInfoEntity entity) {
        if (entity == null) {
            return null;
        }
        FlowInfo info = new FlowInfo();
        info.setFlowId(ImplUtil.uuidToStr(entity.getId()));
        info.setFlowName(entity.getFlowName());
        info.setFlowType(entity.getFlowType() != null ? FlowTypeEnum.fromString(entity.getFlowType()) : null);
        info.setVersion(entity.getPublishVersion() != null ? String.valueOf(entity.getPublishVersion()) : null);
        info.setFlowParam(JacksonUtils.tryObj2Bean(entity.getFlowParam(), ParamData.class));
        info.setDepEventIds(ImplUtil.parseCommaSet(entity.getDepEventIds()));
        info.setEventId(ImplUtil.uuidToStr(entity.getEventId()));
        return info;
    }

    private FlowInstance toFlowInstance(FlowInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        FlowInstance ins = new FlowInstance();
        ins.setInstanceId(ImplUtil.uuidToStr(entity.getId()));
        ins.setFlowId(ImplUtil.uuidToStr(entity.getFlowId()));
        ins.setFlowName(entity.getFlowName());
        ins.setFlowType(entity.getFlowType() != null ? FlowTypeEnum.fromString(entity.getFlowType()) : null);
        ins.setVersion(entity.getPublishVersion() != null ? String.valueOf(entity.getPublishVersion()) : null);
        ins.setState(entity.getStatus() != null ? StatusEnum.fromString(entity.getStatus()) : null);
        ins.setScheduleTime(entity.getScheduleTime());
        ins.setStartTime(entity.getStartTime());
        ins.setEndTime(entity.getEndTime());
        ins.setFlowParam(JacksonUtils.tryObj2Bean(entity.getFlowParam(), ParamData.class));
        ins.setDepEventIds(ImplUtil.parseCommaSet(entity.getDepEventIds()));
        ins.setEventId(ImplUtil.uuidToStr(entity.getEventId()));
        return ins;
    }

    private FlowInstanceEntity toFlowInstanceEntity(FlowInstance ins) {
        FlowInstanceEntity entity = new FlowInstanceEntity();
        entity.setId(ImplUtil.strToUuid(ins.getInstanceId()));
        entity.setFlowId(ImplUtil.strToUuid(ins.getFlowId()));
        entity.setFlowName(ins.getFlowName());
        entity.setFlowType(ins.getFlowType() != null ? ins.getFlowType().getType() : null);
        entity.setPublishVersion(ins.getVersion() != null ? Long.parseLong(ins.getVersion()) : null);
        entity.setStatus(ins.getState() != null ? ins.getState().getStateType() : null);
        entity.setScheduleTime(ins.getScheduleTime());
        entity.setStartTime(ins.getStartTime());
        entity.setEndTime(ins.getEndTime());
        entity.setFlowParam(JacksonUtils.tryObj2JsonNode(ins.getFlowParam()));
        entity.setDepEventIds(ImplUtil.joinCommaSet(ins.getDepEventIds()));
        entity.setEventId(ImplUtil.strToUuid(ins.getEventId()));
        return entity;
    }
    // endregion
}
