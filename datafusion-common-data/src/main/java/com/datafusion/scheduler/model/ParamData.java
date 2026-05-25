package com.datafusion.scheduler.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

/**
 * 流程/任务参数.
 *
 * @author lanvendar
 * @version 3.0.0, 2022/7/26
 * @since 2022/7/26
 */
@Data
public class ParamData {

    /**
     * 流程参数.
     */
    private JsonNode params;

    /**
     * 获取输入变量列表,用来替换flowParam和flowDag表达式中的值.
     */
    private Map<String, Variable> vars;
}
