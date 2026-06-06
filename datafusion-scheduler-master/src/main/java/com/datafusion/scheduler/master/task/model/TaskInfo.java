package com.datafusion.scheduler.master.task.model;

import com.datafusion.scheduler.model.ParamData;
import com.datafusion.scheduler.model.PluginData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Set;

/**
 * 任务信息对象.
 *
 * @author lanvendar
 * @version 3.0.0, 2023/8/30
 * @since 2023/8/30
 */
@Data
public class TaskInfo {

    /**
     * 任务id.
     */
    private String taskId;

    /**
     * 流程id.
     */
    private String flowId;

    /**
     * 任务类型.
     */
    private String taskType;

    /**
     * 任务名称.
     */
    private String taskName;

    /**
     * 任务描述.
     */
    private String taskDesc;

    /**
     * 业务参数，数组.
     */
    private ParamData taskParam;

    /**
     * 任务定义.
     */
    private JsonNode definition;

    /**
     * 事件任务依赖清单，集合.
     */
    private Set<String> depEventIds;

    /**
     * 事件id.
     */
    private String eventId;

    /**
     * 执行组件id.
     */
    private PluginData pluginData;

    /**
     * 禁用/启用.
     */
    private Boolean isAble;
}
