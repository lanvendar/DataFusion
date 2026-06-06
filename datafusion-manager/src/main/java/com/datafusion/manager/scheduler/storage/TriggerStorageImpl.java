package com.datafusion.manager.scheduler.storage;

import com.datafusion.manager.scheduler.po.FlowInfoEntity;
import com.datafusion.manager.scheduler.po.FlowInstanceEntity;
import com.datafusion.manager.scheduler.po.TriggerInfoEntity;
import com.datafusion.manager.scheduler.service.FlowInfoService;
import com.datafusion.manager.scheduler.service.FlowInstanceService;
import com.datafusion.manager.scheduler.service.TriggerInfoService;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import com.datafusion.scheduler.master.trigger.storage.TriggerStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.datafusion.manager.utils.ImplUtil.strToUuid;
import static com.datafusion.manager.utils.ImplUtil.uuidToStr;

/**
 * 触发器存储实现, 适配scheduler TriggerStorage接口.
 *
 * <p>TriggerInfo是跨表的复合视图, 字段来源于flow_info和trigger_info.</p>
 *
 * <p>TriggerInstance 本质是FlowInstance, 无独立表.</p>
 *
 * @author datafusion
 * @version 1.0.0, 2025/3/25
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerStorageImpl implements TriggerStorage {

    /**
     * 分钟转毫秒系数.
     */
    private static final long MINUTES_TO_MS = 60_000L;

    /**
     * 触发器信息Service.
     */
    private final TriggerInfoService triggerInfoService;

    /**
     * 流程信息Service.
     */
    private final FlowInfoService flowInfoService;

    /**
     * 流程实例Service.
     */
    private final FlowInstanceService flowInstanceService;

    @Override
    public List<TriggerInfo> getAllScheduledTriggerInfo() {
        List<FlowInfoEntity> enabledFlows = flowInfoService.listAllEnabled();
        List<TriggerInfo> result = new ArrayList<>();
        for (FlowInfoEntity flow : enabledFlows) {
            TriggerInfoEntity trigger = triggerInfoService.getByTriggerId(flow.getId());
            if (trigger != null) {
                result.add(toTriggerInfo(flow, trigger));
            }
        }
        return result;
    }

    @Override
    public TriggerInfo getTriggerInfo(String payloadId) {
        UUID flowId = UUID.fromString(payloadId);
        FlowInfoEntity flow = flowInfoService.getFlowInfo(flowId);
        if (flow == null) {
            return null;
        }
        TriggerInfoEntity trigger = triggerInfoService.getByTriggerId(flowId);
        if (trigger == null) {
            return null;
        }
        return toTriggerInfo(flow, trigger);
    }

    @Override
    public void saveTriggerInfo(TriggerInfo triggerInfo) {
        UUID flowId = UUID.fromString(triggerInfo.getPayloadId());

        // 更新flow_info.enabled(scheduleFlag)
        FlowInfoEntity flow = flowInfoService.getFlowInfo(flowId);
        if (flow != null) {
            flow.setEnabled(triggerInfo.isScheduleFlag());
            flowInfoService.updateById(flow);
        }

        // 保存/更新trigger_info
        TriggerInfoEntity triggerEntity = toTriggerInfoEntity(triggerInfo);
        TriggerInfoEntity existing = triggerInfoService.getByTriggerId(triggerEntity.getId());
        if (existing != null) {
            triggerInfoService.updateById(triggerEntity);
        } else {
            triggerInfoService.save(triggerEntity);
        }
    }

    @Override
    public TriggerInstance getTriggerInstance(String scheduleInsId) {
        FlowInstanceEntity entity = flowInstanceService.getInstanceById(UUID.fromString(scheduleInsId));
        return toTriggerInstance(entity);
    }

    @Override
    public TriggerInstance getLastTriggerInstance(String payloadId, String version) {
        Long ver = version != null ? Long.parseLong(version) : null;
        FlowInstanceEntity entity = flowInstanceService.getLastInstance(UUID.fromString(payloadId), ver);
        return toTriggerInstance(entity);
    }

    @Override
    public void saveTriggerInstance(TriggerInstance triggerInstance) {
        FlowInstanceEntity entity = triggerInsToFlowEntity(triggerInstance);
        FlowInstanceEntity existing = flowInstanceService.getInstanceById(entity.getId());
        if (existing != null) {
            // 仅更新TriggerInstance映射的字段, 保留entity独有字段
            existing.setStatus(entity.getStatus());
            existing.setScheduleTime(entity.getScheduleTime());
            existing.setPublishVersion(entity.getPublishVersion());
            flowInstanceService.updateById(existing);
        } else {
            flowInstanceService.save(entity);
        }
    }

    // region 转换方法

    /**
     * 合并FlowInfoEntity和TriggerInfoEntity为TriggerInfo.
     *
     * @param flow    流程信息
     * @param trigger 触发器信息
     * @return 触发器调度信息
     */
    private TriggerInfo toTriggerInfo(FlowInfoEntity flow, TriggerInfoEntity trigger) {
        TriggerInfo info = new TriggerInfo();
        // 来自flow_info
        info.setPayloadId(uuidToStr(flow.getId()));
        info.setVersion(flow.getPublishVersion() != null ? String.valueOf(flow.getPublishVersion()) : null);
        info.setScheduleFlag(Boolean.TRUE.equals(flow.getEnabled()));
        info.setStartTime(flow.getStartTime() != null ? flow.getStartTime() : 0L);
        info.setEndTime(flow.getEndTime() != null ? flow.getEndTime() : Long.MAX_VALUE);

        // 来自trigger_info
        info.setTriggerId(uuidToStr(trigger.getId()));
        info.setTriggerType(parseTriggerType(trigger.getType()));
        info.setTriggerExpression(buildTriggerExpression(trigger));
        info.setTriggerPolicy(parseTriggerPolicy(trigger.getPolicy()));
        return info;
    }

    private TriggerInfoEntity toTriggerInfoEntity(TriggerInfo triggerInfo) {
        TriggerInfoEntity entity = new TriggerInfoEntity();
        // triggerId即为payloadId(共享主键)
        entity.setId(strToUuid(triggerInfo.getPayloadId()));
        entity.setType(triggerInfo.getTriggerType() != null
                ? String.valueOf(triggerInfo.getTriggerType().ordinal()) : null);
        entity.setPolicy(triggerInfo.getTriggerPolicy() != null
                ? String.valueOf(triggerInfo.getTriggerPolicy().ordinal()) : null);

        // 根据类型设置cron或interval
        if (triggerInfo.getTriggerType() == TriggerTypeEnum.CRON) {
            entity.setCron(triggerInfo.getTriggerExpression());
        } else if (triggerInfo.getTriggerType() == TriggerTypeEnum.INTERVAL) {
            long ms = Long.parseLong(triggerInfo.getTriggerExpression());
            entity.setInterval((int) (ms / MINUTES_TO_MS));
        }
        return entity;
    }

    private TriggerInstance toTriggerInstance(FlowInstanceEntity entity) {
        if (entity == null) {
            return null;
        }
        TriggerInstance ins = new TriggerInstance();
        ins.setPayloadId(uuidToStr(entity.getFlowId()));
        ins.setVersion(entity.getPublishVersion() != null ? String.valueOf(entity.getPublishVersion()) : null);
        ins.setInstanceId(uuidToStr(entity.getId()));
        ins.setScheduleTime(entity.getScheduleTime() != null ? entity.getScheduleTime() : 0L);
        ins.setScheduleSign(0);
        ins.setState(entity.getStatus() != null ? StatusEnum.fromString(entity.getStatus()) : null);
        return ins;
    }

    private FlowInstanceEntity triggerInsToFlowEntity(TriggerInstance triggerIns) {
        FlowInstanceEntity entity = new FlowInstanceEntity();
        entity.setId(strToUuid(triggerIns.getInstanceId()));
        entity.setFlowId(strToUuid(triggerIns.getPayloadId()));
        entity.setPublishVersion(triggerIns.getVersion() != null ? Long.parseLong(triggerIns.getVersion()) : null);
        entity.setScheduleTime(triggerIns.getScheduleTime());
        entity.setStatus(triggerIns.getState() != null ? triggerIns.getState().getStateType() : null);
        return entity;
    }

    private TriggerTypeEnum parseTriggerType(String type) {
        if (type == null) {
            return null;
        }
        try {
            return TriggerTypeEnum.valueOf(Integer.parseInt(type));
        } catch (NumberFormatException e) {
            log.warn("无法解析触发器类型: {}", type);
            return null;
        }
    }

    private TriggerPolicyEnum parseTriggerPolicy(String policy) {
        if (policy == null) {
            return TriggerPolicyEnum.EXECUTE_ONCE;
        }
        try {
            return TriggerPolicyEnum.valueOf(Integer.parseInt(policy));
        } catch (NumberFormatException e) {
            log.warn("无法解析触发器策略: {}", policy);
            return TriggerPolicyEnum.EXECUTE_ONCE;
        }
    }

    /**
     * 根据触发器类型构建表达式.
     * CRON类型直接取cron字段, INTERVAL类型将分钟转为毫秒字符串.
     *
     * @param trigger 触发器信息实体
     * @return 触发器表达式
     */
    private String buildTriggerExpression(TriggerInfoEntity trigger) {
        TriggerTypeEnum type = parseTriggerType(trigger.getType());
        if (type == TriggerTypeEnum.CRON) {
            return trigger.getCron();
        } else if (type == TriggerTypeEnum.INTERVAL && trigger.getInterval() != null) {
            return String.valueOf(trigger.getInterval() * MINUTES_TO_MS);
        }
        return null;
    }
    // endregion
}
