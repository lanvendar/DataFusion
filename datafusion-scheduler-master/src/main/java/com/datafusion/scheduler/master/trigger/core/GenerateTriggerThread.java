package com.datafusion.scheduler.master.trigger.core;

import com.datafusion.common.constant.SystemConstant;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.trigger.SchedulerTrigger;
import com.datafusion.scheduler.master.trigger.TriggerInstanceHandler;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 产生调度实例触发线程.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/10/29
 * @since 2024/10/29
 */
@Slf4j
public class GenerateTriggerThread extends TriggerThread {
    /**
     * 带策略的调度生成器.
     */
    private final TriggerInstanceHandler handler;

    /**
     * 调度动作触发接口.
     */
    private final SchedulerTrigger trigger;

    /**
     * 构造函数.
     *
     * @param executor 执行器线程池
     * @param handler  调度实例生成器
     */
    public GenerateTriggerThread(ThreadPoolExecutor executor, TriggerInstanceHandler handler) {
        super("GenerateTriggerThread", executor);
        this.handler = handler;
        this.trigger = handler.getSchedulerTrigger();
    }

    @Override
    List<TriggerInstance> fetchInstances() {
        return handler.fetchCache();
    }

    @Override
    Boolean triggerAction(TriggerInstance instance) {
        MDC.put(SystemConstant.MDC_FLOW_INSTANCE_ID, instance.getInstanceId());
        log.debug("初始化flow实例,flow instance id={}", instance.getInstanceId());
        // 初始化创建实例
        if (handler.checkScheduleAvailable(instance)) {
            this.trigger.fetchInit(instance);
            return true;
        } else {
            return false;
        }
    }

    @Override
    void actionSuccess(Boolean result, TriggerInstance instance) {
        if (Boolean.TRUE.equals(result)) {
            log.debug("初始化调度实例成功,加入到延时队列,flowInsId={},scheduleTime={}",
                    instance.getInstanceId(), instance.getScheduleTime());
            // 加入 dispatch 分发延迟队列
            handler.enqueue(instance);
        } else {
            log.warn("未初始化实例:flowInsId={}", instance.getInstanceId());
        }
    }

    @Override
    void actionFailure(TriggerInstance instance) {
        //查看调度实例是否生成
        StatusEnum scheduleInsState = handler.getScheduleInstanceState(instance.getInstanceId());
        if (null != scheduleInsState) {
            instance.setState(StatusEnum.INIT_FAILURE);
            handler.saveTriggerInstance(instance);
        } else {
            handler.addCache(instance);
        }
    }
}
