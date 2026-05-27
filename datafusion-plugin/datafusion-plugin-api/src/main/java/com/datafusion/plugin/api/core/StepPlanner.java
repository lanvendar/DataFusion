package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 步骤执行计划生成器,根据依赖关系对步骤进行拓扑排序.
 *
 * <p>支持有向无环图(DAG)的拓扑排序,检测循环依赖.</p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class StepPlanner {

    /**
     * 根据依赖关系生成步骤执行顺序.
     *
     * @param steps 原始步骤列表
     * @return 按依赖顺序排列的步骤列表
     * @throws ApiExtractException 存在循环依赖时抛出
     */
    public List<StepConfig> plan(List<StepConfig> steps) {
        boolean hasDependsOn = steps.stream().anyMatch(step -> step.dependsOn != null && !step.dependsOn.isEmpty());
        if (!hasDependsOn) {
            return steps.stream().filter(step -> step.enabled).toList();
        }
        Map<String, StepConfig> byId = new LinkedHashMap<>();
        for (StepConfig step : steps) {
            byId.put(step.id, step);
        }
        List<StepConfig> result = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (StepConfig step : steps) {
            visit(step, byId, visiting, visited, result);
        }
        return result.stream().filter(step -> step.enabled).toList();
    }

    /**
     * 深度优先遍历步骤依赖图.
     *
     * @param step 当前步骤
     * @param byId 步骤 ID 映射
     * @param visiting 正在访问的节点集合,用于检测循环
     * @param visited 已访问的节点集合
     * @param result 排序后的结果列表
     */
    private void visit(StepConfig step, Map<String, StepConfig> byId, Set<String> visiting, Set<String> visited,
            List<StepConfig> result) {
        if (visited.contains(step.id)) {
            return;
        }
        if (!visiting.add(step.id)) {
            throw new ApiExtractException("Cycle found in step dependsOn: " + step.id);
        }
        if (step.dependsOn != null) {
            for (String parentId : step.dependsOn) {
                StepConfig parent = byId.get(parentId);
                if (parent == null) {
                    throw new ApiExtractException("Unknown dependsOn step: " + parentId);
                }
                visit(parent, byId, visiting, visited, result);
            }
        }
        visiting.remove(step.id);
        visited.add(step.id);
        result.add(step);
    }
}
