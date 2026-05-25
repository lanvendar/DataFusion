package com.datafusion.scheduler.master.trigger.core;

import com.datafusion.common.constant.SystemConstant;
import com.datafusion.scheduler.enums.StatusEnum;
import com.datafusion.scheduler.master.trigger.SchedulerTrigger;
import com.datafusion.scheduler.master.trigger.TriggerInstanceHandler;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;
import com.datafusion.scheduler.master.trigger.model.TriggerInstance;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 分发调度实例触发线程.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/10/29
 * @since 2024/10/29
 */

@Slf4j
public class DispatchTriggerThread extends TriggerThread {

    /**
     * 异步任务的监听线程池.
     */
    private final SchedulerTrigger trigger;

    /**
     * flow调度实例缓存.
     */
    private final TriggerInstanceHandler handler;

    /**
     * 构造方法.
     *
     * @param executor 执行器线程池
     * @param handler  调度实例生成器
     */
    public DispatchTriggerThread(ThreadPoolExecutor executor, TriggerInstanceHandler handler) {
        super("DispatchTriggerThread", executor);
        this.handler = handler;
        this.trigger = handler.getSchedulerTrigger();
    }

    @Override
    List<TriggerInstance> fetchInstances() {
        return handler.dequeue();
    }

    @Override
    Boolean triggerAction(TriggerInstance instance) {
        MDC.put(SystemConstant.MDC_FLOW_INSTANCE_ID, instance.getInstanceId());
        if (handler.checkScheduleAvailable(instance)) {
            StatusEnum state = handler.getScheduleInstanceState(instance.getInstanceId());
            if (StatusEnum.INIT_SUCCESS == state) {
                log.debug("启动实例,flow instance id={}", instance.getInstanceId());
                //提交中
                this.trigger.dispatchSubmit(instance);
                return true;
            } else {
                log.warn("调度实例[{}]生成失败，状态为state={}", instance.getInstanceId(), state);
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    void actionSuccess(Boolean result, TriggerInstance instance) {
        if (Boolean.TRUE.equals(result)) {
            //设置上一次实例缓存
            handler.saveLastScheduleInstance(instance);

            if (handler.checkScheduleAvailable(instance)) {
                //生成新一轮的调度实例缓存
                log.debug("启动实例成功,生成下次调度实例缓存");
                //TODO 此处System.currentTimeMillis() 是否应该为业务时间或者为SchedulerInfo中的开始时间
                //instanceCache.generate(schedulerInfo, System.currentTimeMillis());
                TriggerInfo triggerInfo = handler.getTriggerInfo(instance.getPayloadId());
                handler.generateInstance(triggerInfo, instance.getScheduleTime(), false);
            }
        } else {
            log.warn("未启动实例:flowInsId={}", instance.getInstanceId());
        }
    }

    @Override
    void actionFailure(TriggerInstance instance) {
        log.error("启动实例失败,重新加入调度实例缓存");
        handler.addCache(instance);
    }
}
