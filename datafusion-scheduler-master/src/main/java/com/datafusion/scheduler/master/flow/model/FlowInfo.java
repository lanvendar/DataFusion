package com.datafusion.scheduler.master.flow.model;

import com.datafusion.scheduler.master.flow.enums.FlowTypeEnum;
import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import lombok.Data;

import java.util.Set;

/**
 * 流程对象.
 *
 * @author lanvendar
 * @version 1.0.0, 2024/10/18
 * @since 2024/10/18
 */
@Data
public class FlowInfo {

    /**
     * flow id.
     */
    private String flowId;

    /**
     * flow 名称.
     */
    private String flowName;

    /**
     * 流程类型:1流任务;2批任务.
     */
    private FlowTypeEnum flowType;

    /**
     * flow 调度版本.
     */
    private String version;

    /**
     * 流程参数.
     */
    private ParamData flowParam;

    /**
     * 全局依赖事件.
     */
    private Set<String> depEventIds;

    /**
     * flow定义的业务事件id.
     */
    private String eventId;

    /**
     * flow全局组件.
     */
    private PluginData pluginData;
}

