package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.HttpConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.PaginationConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.RedisCacheConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ColumnConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ValueExpressionConfig;
import com.datafusion.plugin.api.sink.SinkMode;
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
        validateTrigger(config);
        validateRuntime(config);
        validateHttpConfig(config.httpConfig, "httpConfig");
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
            validateRequest(step);
            validateHttpConfig(step.httpConfig, "steps." + step.id + ".httpConfig");
            validatePagination(step);
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
        for (FieldConfig field : step.response.fields) {
            if (TextUtils.isBlank(field.name)) {
                throw new ApiExtractException("response.fields.name is required for step: " + step.id);
            }
            if (field.value == null && TextUtils.isBlank(field.expression)) {
                throw new ApiExtractException("field expression or value is required: " + step.id + "." + field.name);
            }
        }
        if ("ARRAY".equals(recordMode) && step.response.fields.stream()
                .noneMatch(field -> isArrayProjection(field.expression))) {
            throw new ApiExtractException("ARRAY recordMode requires field expression with [] for step: " + step.id);
        }
    }

    private boolean isArrayProjection(String expression) {
        if (TextUtils.isBlank(expression)) {
            return false;
        }
        int bracketIdx = expression.indexOf("[]");
        if (bracketIdx < 0) {
            return false;
        }
        String fieldPath = expression.substring(bracketIdx + 2);
        if (fieldPath.startsWith(".")) {
            fieldPath = fieldPath.substring(1);
        }
        return !TextUtils.isBlank(fieldPath) && !fieldPath.contains("[]");
    }

    private void validateTrigger(ApiExtractJobConfig config) {
        TriggerMode mode = TriggerMode.parse(config.trigger == null ? "ONCE" : config.trigger.mode);
        if (mode == TriggerMode.CRON) {
            if (config.trigger == null || TextUtils.isBlank(config.trigger.cron)) {
                throw new ApiExtractException("trigger.cron is required when trigger.mode=CRON");
            }
            if (TextUtils.isBlank(config.trigger.timezone)) {
                throw new ApiExtractException("trigger.timezone is required when trigger.mode=CRON");
            }
            try {
                java.time.ZoneId.of(config.trigger.timezone);
            } catch (Exception e) {
                throw new ApiExtractException("Invalid trigger.timezone: " + config.trigger.timezone, e);
            }
        }
    }

    private void validateRuntime(ApiExtractJobConfig config) {
        if (config.runtime == null) {
            return;
        }
        if (config.runtime.loopCount < 1) {
            throw new ApiExtractException("runtime.loopCount must be greater than 0");
        }
        if (config.runtime.loopIntervalMs < 0) {
            throw new ApiExtractException("runtime.loopIntervalMs must be greater than or equal to 0");
        }
    }

    private void validateHttpConfig(HttpConfig config, String path) {
        if (config == null) {
            return;
        }
        if (config.connectMs <= 0 || config.readMs <= 0 || config.writeMs <= 0
                || config.probeConnectMs <= 0 || config.probeReadMs <= 0) {
            throw new ApiExtractException(path + " timeout values must be greater than 0");
        }
        if (config.maxAttempts < 1 || config.retryIntervalMs < 0 || config.backoffMultiplier < 1.0) {
            throw new ApiExtractException(path + " retry values are invalid");
        }
    }

    private void validateRequest(StepConfig step) {
        String method = TextUtils.upper(step.request.method, null);
        if (!Set.of("GET", "POST", "PUT", "DELETE").contains(method)) {
            throw new ApiExtractException("Unsupported request.method for step: " + step.id);
        }
        String bodyType = TextUtils.upper(step.request.bodyType, "NONE");
        if (!Set.of("NONE", "JSON", "FORM", "RAW").contains(bodyType)) {
            throw new ApiExtractException("Unsupported request.bodyType for step: " + step.id);
        }
    }

    private void validatePagination(StepConfig step) {
        PaginationConfig pagination = step.pagination == null ? new PaginationConfig() : step.pagination;
        String type = TextUtils.upper(pagination.type, "NONE");
        if ("NONE".equals(type)) {
            return;
        }
        if ("PAGE".equals(type)) {
            if (TextUtils.isBlank(pagination.pageParam) || TextUtils.isBlank(pagination.pageSizeParam)) {
                throw new ApiExtractException("PAGE pagination requires pageParam and pageSizeParam for step: " + step.id);
            }
            if (pagination.startPage < 1 || pagination.pageSize <= 0 || pagination.maxPages <= 0) {
                throw new ApiExtractException("PAGE pagination values are invalid for step: " + step.id);
            }
            return;
        }
        if ("OFFSET".equals(type)) {
            if (TextUtils.isBlank(pagination.offsetParam) || TextUtils.isBlank(pagination.limitParam)) {
                throw new ApiExtractException("OFFSET pagination requires offsetParam and limitParam for step: " + step.id);
            }
            if (pagination.startOffset < 0 || pagination.limit <= 0 || pagination.maxRequests <= 0) {
                throw new ApiExtractException("OFFSET pagination values are invalid for step: " + step.id);
            }
            return;
        }
        throw new ApiExtractException("Unsupported pagination.type for step: " + step.id);
    }

    /**
     * 校验落表配置,包括类型、字段和主键.
     *
     * @param config 任务配置
     */
    private void validateSink(ApiExtractJobConfig config) {
        if (config.sink == null || TextUtils.isBlank(config.sink.type)) {
            throw new ApiExtractException("sink.type is required");
        }
        String sinkType = TextUtils.upper(config.sink.type, null);
        if (!Set.of("STARROCKS", "PAIMON", "NOOP").contains(sinkType)) {
            throw new ApiExtractException("Unsupported sink.type: " + config.sink.type);
        }
        validateConnectType(config.sink.type, config.sink.connectType);
        validateSinkOptions(config.sink);
        if (!"NOOP".equals(sinkType) && (config.sink.table == null || TextUtils.isBlank(config.sink.table.name))) {
            throw new ApiExtractException("sink.table.name is required");
        }
        Set<String> columnNames = new HashSet<>();
        for (ColumnConfig field : config.sink.columns) {
            if (TextUtils.isBlank(field.name)) {
                throw new ApiExtractException("sink.columns.name is required");
            }
            if (!columnNames.add(field.name)) {
                throw new ApiExtractException("Duplicate sink column field: " + field.name);
            }
        }
        validateSinkColumnCoverage(config, columnNames);
        SinkMode sinkMode = SinkMode.parse(config.sink.loadMode);
        if (sinkMode == SinkMode.UPSERT && !"NOOP".equals(sinkType)
                && (config.sink.table.primaryKeys == null || config.sink.table.primaryKeys.isEmpty())) {
            throw new ApiExtractException(sinkType + " UPSERT requires sink.table.primaryKeys");
        }
        if (sinkMode == SinkMode.OVERWRITE_PARTITION && "STARROCKS".equals(sinkType)
                && (config.sink.table.partition == null || !config.sink.table.partition.enabled
                || TextUtils.isBlank(config.sink.table.partition.field))) {
            throw new ApiExtractException("StarRocks OVERWRITE_PARTITION requires sink.table.partition.field");
        }
        if (sinkMode == SinkMode.OVERWRITE_PARTITION && "PAIMON".equals(sinkType)
                && (config.sink.table.partitionKeys == null || config.sink.table.partitionKeys.isEmpty())) {
            throw new ApiExtractException("Paimon OVERWRITE_PARTITION requires sink.table.partitionKeys");
        }
        if (config.sink.write != null && config.sink.write.batchSize <= 0) {
            throw new ApiExtractException("sink.write.batchSize must be greater than 0");
        }
    }

    /**
     * 校验 Redis 缓存配置与步骤缓存的匹配性.
     *
     * @param config 任务配置
     */
    private void validateCache(ApiExtractJobConfig config) {
        boolean hasStepCache = config.steps.stream().anyMatch(this::hasEnabledCache);
        if (hasStepCache && (config.redis == null || !config.redis.enabled)) {
            throw new ApiExtractException("redis.enabled must be true when any steps[].redisCache is enabled");
        }
        if (config.redis != null && config.redis.enabled) {
            if (!"REDIS".equals(TextUtils.upper(config.redis.connectType, "REDIS"))
                    || TextUtils.isBlank(config.redis.optionString("host", null))
                    || config.redis.optionInt("port", 0) <= 0
                    || config.redis.optionInt("database", -1) < 0) {
                throw new ApiExtractException("redis connection config is invalid");
            }
            if (!Set.of("PUT", "UPSERT", "APPEND_LIST", "HASH")
                    .contains(TextUtils.upper(config.redis.loadMode, "UPSERT"))) {
                throw new ApiExtractException("Unsupported redis.loadMode: " + config.redis.loadMode);
            }
        }
        for (StepConfig step : config.steps) {
            validateStepCache(step);
        }
    }

    private boolean hasEnabledCache(StepConfig step) {
        return step.redisCache != null && step.redisCache.enabled;
    }

    private void validateStepCache(StepConfig step) {
        RedisCacheConfig cache = step.redisCache;
        if (cache == null || !cache.enabled) {
            return;
        }
        if (TextUtils.isBlank(cache.key)) {
            throw new ApiExtractException("steps[].redisCache.key is required for step: " + step.id);
        }
        String mode = TextUtils.upper(cache.loadMode, "UPSERT");
        if (!Set.of("PUT", "UPSERT", "APPEND_LIST", "HASH").contains(mode)) {
            throw new ApiExtractException("Unsupported steps[].redisCache.loadMode for step: " + step.id);
        }
        if (cache.valueExpressions == null || cache.valueExpressions.isEmpty()) {
            throw new ApiExtractException("steps[].redisCache.valueExpressions is required for step: " + step.id);
        }
        for (ValueExpressionConfig expression : cache.valueExpressions) {
            if (TextUtils.isBlank(expression.name) || TextUtils.isBlank(expression.expression)) {
                throw new ApiExtractException("steps[].redisCache.valueExpressions name and expression are required for step: " + step.id);
            }
        }
    }

    private void validateConnectType(String sinkType, String connectType) {
        String type = TextUtils.upper(sinkType, null);
        String connection = TextUtils.upper(connectType, null);
        if ("STARROCKS".equals(type) && !Set.of("JDBC", "LOAD_STREAM").contains(connection)) {
            throw new ApiExtractException("StarRocks connectType must be JDBC or LOAD_STREAM");
        }
        if ("PAIMON".equals(type) && !TextUtils.isBlank(connectType) && !"S3".equals(connection)) {
            throw new ApiExtractException("Paimon connectType must be S3");
        }
        if ("NOOP".equals(type) && !TextUtils.isBlank(connectType) && !"NOOP".equals(connection)) {
            throw new ApiExtractException("NOOP connectType must be NOOP");
        }
    }

    private void validateSinkOptions(ApiExtractJobConfig.SinkConfig sink) {
        String type = TextUtils.upper(sink.type, null);
        String connectType = TextUtils.upper(sink.connectType, null);
        if ("STARROCKS".equals(type) && "JDBC".equals(connectType)) {
            requireOption(sink, "jdbcUrl");
            requireOption(sink, "username");
            requireOption(sink, "database");
        }
        if ("STARROCKS".equals(type) && "LOAD_STREAM".equals(connectType)) {
            requireOption(sink, "loadUrl");
            requireOption(sink, "username");
            requireOption(sink, "database");
            if (SinkMode.parse(sink.loadMode) == SinkMode.OVERWRITE_PARTITION) {
                requireOption(sink, "jdbcUrl");
            }
        }
        if ("PAIMON".equals(type)) {
            requireOption(sink, "warehouse");
            requireOption(sink, "database");
            requireOption(sink, "endpoint");
        }
    }

    private void requireOption(ApiExtractJobConfig.SinkConfig sink, String key) {
        if (TextUtils.isBlank(sink.optionString(key, null))) {
            throw new ApiExtractException("sink.options." + key + " is required for " + sink.type);
        }
    }

    private void validateSinkColumnCoverage(ApiExtractJobConfig config, Set<String> columnNames) {
        if ("NOOP".equals(TextUtils.upper(config.sink.type, null))) {
            return;
        }
        for (StepConfig step : config.steps) {
            if (step.response == null || step.response.fields == null) {
                continue;
            }
            for (FieldConfig field : step.response.fields) {
                if (!columnNames.contains(field.name)) {
                    throw new ApiExtractException("sink.columns lacks response field: " + field.name);
                }
            }
        }
    }
}
