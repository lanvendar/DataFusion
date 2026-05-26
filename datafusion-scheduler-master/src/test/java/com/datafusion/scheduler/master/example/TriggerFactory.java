package com.datafusion.scheduler.master.example;

import com.datafusion.scheduler.master.trigger.enums.TriggerPolicyEnum;
import com.datafusion.scheduler.master.trigger.enums.TriggerTypeEnum;
import com.datafusion.scheduler.master.trigger.model.TriggerInfo;

/**
 * 触发器创建工厂接口，由测试类实现以复用已有的工厂方法.
 */
@FunctionalInterface
public interface TriggerFactory {

    /**
     * 创建触发器信息.
     */
    TriggerInfo create(String payloadId, String triggerId, String version,
                       TriggerTypeEnum type, String expression, TriggerPolicyEnum policy);
}
