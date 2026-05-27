package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.SchemaFieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
import com.datafusion.plugin.api.util.TextUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置校验器,用于验证 API 抽取任务配置的完整性和正确性.
 *
 * <p>
 * 校验规则包括:任务 ID、步骤依赖、响应配置、落表配置等.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConfigValidator {

    /**
     * 校验完整的任务配置.
     *
     * @param config 待校验的配置对象
     * @throws ApiExtractException 配置不合法时抛出
     */
    public void validate(ApiExtractJobConfig config) {
        if (config == null) {
            throw new ApiExtractException("Config is required");
        }
        if (config.job == null || TextUtils.isBlank(config.job.id)) {
            throw new ApiExtractException("job.id is required");
        }
        if (config.steps == null || config.steps.isEmpty()) {
            throw new ApiExtractException("steps is required");
        }
        validateSteps(config.steps);
        validateCache(config);
        validateSink(config);
    }

    /**
     * 校验步骤列表的唯一性和合法性.
     *
     * @param steps 步骤列表
     */
    private void validateSteps(List<StepConfig> steps) {
        Set<String> ids = new HashSet<>();
        for (StepConfig step : steps) {
            if (TextUtils.isBlank(step.id)) {
                throw new ApiExtractException("step.id is required");
            }
            if (!ids.add(step.id)) {
                throw new ApiExtractException("Duplicate step id: " + step.id);
            }
            if (!"HTTP".equals(TextUtils.upper(step.type, "HTTP"))) {
                throw new ApiExtractException("Only HTTP step is supported: " + step.id);
            }
            if (step.request == null || TextUtils.isBlank(step.request.method) || TextUtils.isBlank(step.request.url)) {
                throw new ApiExtractException("request.method and request.url are required for step: " + step.id);
            }
            validateResponse(step);
        }
        validateSingleChainDag(steps, ids);
    }

    /**
     * 校验单链 DAG 依赖关系,V1.0 仅支持线性依赖.
     *
     * @param steps 步骤列表
     * @param ids 步骤 ID 集合
     */
    private void validateSingleChainDag(List<StepConfig> steps, Set<String> ids) {
        Map<String, Integer> childrenCount = new HashMap<>();
        Set<String> hasParent = new HashSet<>();
        for (StepConfig step : steps) {
            if (step.dependsOn == null) {
                continue;
            }
            if (step.dependsOn.size() > 1) {
                throw new ApiExtractException("V1.0 only supports single-chain DAG, step has multiple parents: " + step.id);
            }
            for (String parent : step.dependsOn) {
                if (!ids.contains(parent)) {
                    throw new ApiExtractException("Unknown dependsOn step: " + parent);
                }
                hasParent.add(step.id);
                childrenCount.merge(parent, 1, Integer::sum);
                if (childrenCount.get(parent) > 1) {
                    throw new ApiExtractException("V1.0 only supports single-chain DAG, step has multiple children: " + parent);
                }
            }
        }
        if (steps.stream().anyMatch(step -> step.dependsOn != null && !step.dependsOn.isEmpty())) {
            long roots = steps.stream().filter(step -> !hasParent.contains(step.id)).count();
            if (roots != 1) {
                throw new ApiExtractException("Single-chain DAG must have exactly one root step");
            }
            long dependentSteps = steps.stream().filter(step -> step.dependsOn != null && !step.dependsOn.isEmpty()).count();
            if (dependentSteps != steps.size() - 1) {
                throw new ApiExtractException("Single-chain DAG requires every non-root step to configure dependsOn");
            }
        }
    }

    /**
     * 校验响应配置,包括记录模式和字段定义.
     *
     * @param step 步骤配置
     */
    private void validateResponse(StepConfig step) {
        if (step.response == null || step.response.fields == null || step.response.fields.isEmpty()) {
            return;
        }
        String recordMode = TextUtils.upper(step.response.recordMode, null);
        if (!"OBJECT".equals(recordMode) && !"ARRAY".equals(recordMode)) {
            throw new ApiExtractException("response.recordMode must be OBJECT or ARRAY for step: " + step.id);
        }
        int keyCount = 0;
        for (FieldConfig field : step.response.fields) {
            if (TextUtils.isBlank(field.name)) {
                throw new ApiExtractException("response.fields.name is required for step: " + step.id);
            }
            if (field.value == null && TextUtils.isBlank(field.expression)) {
                throw new ApiExtractException("field expression or value is required: " + step.id + "." + field.name);
            }
            if (field.isKey) {
                keyCount++;
            }
        }
        if ("ARRAY".equals(recordMode) && keyCount != 1) {
            throw new ApiExtractException("ARRAY recordMode requires exactly one isKey field for step: " + step.id);
        }
    }

    /**
     * 校验落表配置,包括类型、Schema 和主键.
     *
     * @param config 任务配置
     */
    private void validateSink(ApiExtractJobConfig config) {
        if (config.sink == null || TextUtils.isBlank(config.sink.type)) {
            throw new ApiExtractException("sink.type is required");
        }
        Set<String> schemaNames = new HashSet<>();
        for (SchemaFieldConfig field : config.sink.schema) {
            if (TextUtils.isBlank(field.name)) {
                throw new ApiExtractException("sink.schema.name is required");
            }
            if (!schemaNames.add(field.name)) {
                throw new ApiExtractException("Duplicate sink schema field: " + field.name);
            }
        }
        if ("UPSERT".equals(TextUtils.upper(config.sink.mode, "APPEND"))
                && "STARROCKS".equals(TextUtils.upper(config.sink.type, null))
                && (config.sink.table == null || config.sink.table.primaryKeys == null || config.sink.table.primaryKeys.isEmpty())) {
            throw new ApiExtractException("StarRocks UPSERT requires sink.table.primaryKeys");
        }
    }

    /**
     * 校验 Redis 缓存配置与步骤缓存的匹配性.
     *
     * @param config 任务配置
     */
    private void validateCache(ApiExtractJobConfig config) {
        boolean hasStepCache = config.steps.stream().anyMatch(step -> step.cache != null && step.cache.enabled);
        if (hasStepCache && (config.redis == null || !config.redis.enabled)) {
            throw new ApiExtractException("redis.enabled must be true when any step.cache is enabled");
        }
    }
}
