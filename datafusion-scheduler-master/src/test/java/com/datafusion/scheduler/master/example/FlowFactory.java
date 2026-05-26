package com.datafusion.scheduler.master.example;

import com.datafusion.scheduler.master.flow.model.FlowInfo;

/**
 * 流程创建工厂接口，由测试类实现以复用已有的工厂方法.
 */
@FunctionalInterface
public interface FlowFactory {

    /**
     * 创建流程信息.
     */
    FlowInfo create(String flowId, String flowName, String version);
}
