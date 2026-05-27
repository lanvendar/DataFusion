package com.datafusion.plugin.api.core;

import com.datafusion.common.cron.CronUtil;
import com.datafusion.plugin.api.cache.InMemoryIntermediateCache;
import com.datafusion.plugin.api.cache.IntermediateCache;
import com.datafusion.plugin.api.cache.RedisIntermediateCache;
import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
import com.datafusion.plugin.api.expression.JmesPathEvaluator;
import com.datafusion.plugin.api.http.JavaApiHttpClient;
import com.datafusion.plugin.api.mapping.RecordMapper;
import com.datafusion.plugin.api.sink.SinkWriter;
import com.datafusion.plugin.api.sink.SinkWriterFactory;
import com.datafusion.plugin.api.step.HttpStepExecutor;
import com.datafusion.plugin.api.template.TemplateResolver;
import com.datafusion.plugin.api.util.TextUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 默认 API 抽取执行器.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class DefaultApiExtractRunner implements ApiExtractRunner {

    /**
     * 配置校验器.
     */
    private final ConfigValidator validator = new ConfigValidator();

    /**
     * 步骤执行计划生成器.
     */
    private final StepPlanner stepPlanner = new StepPlanner();

    /**
     * JMESPath 表达式求值器.
     */
    private final JmesPathEvaluator evaluator = new JmesPathEvaluator();

    /**
     * Sink writer 工厂.
     */
    private final SinkWriterFactory sinkWriterFactory = new SinkWriterFactory();

    @Override
    public ApiExtractResult run(ApiExtractJobConfig config) {
        validator.validate(config);
        String mode = config.trigger == null ? "ONCE" : TextUtils.upper(config.trigger.mode, "ONCE");
        if ("CRON".equals(mode)) {
            return runCron(config);
        }
        return runOnce(config);
    }

    private ApiExtractResult runCron(ApiExtractJobConfig config) {
        if (config.trigger == null || TextUtils.isBlank(config.trigger.cron)) {
            throw new ApiExtractException("trigger.cron is required when trigger.mode=CRON");
        }
        Date next = CronUtil.next(config.trigger.cron, new Date());
        long waitMs = Math.max(0, next.getTime() - System.currentTimeMillis());
        sleep(waitMs);
        return runOnce(config);
    }

    private ApiExtractResult runOnce(ApiExtractJobConfig config) {
        long start = System.currentTimeMillis();
        String runId = UUID.randomUUID().toString();
        List<StepResult> stepResults = new ArrayList<>();
        long records = 0;
        try (IntermediateCache cache = createCache(config);
                SinkWriter sinkWriter = sinkWriterFactory.create(config.sink)) {
            sinkWriter.open(config.sink);
            int loopCount = Math.max(1, config.runtime == null ? 1 : config.runtime.loopCount);
            for (int i = 0; i < loopCount; i++) {
                ApiExtractContext context = new ApiExtractContext(config, runId);
                TemplateResolver resolver = new TemplateResolver(cache);
                HttpStepExecutor executor = new HttpStepExecutor(
                        new JavaApiHttpClient(), evaluator, resolver, new RecordMapper(evaluator, resolver), cache);
                List<StepConfig> steps = stepPlanner.plan(config.steps);
                for (StepConfig step : steps) {
                    long stepStart = System.currentTimeMillis();
                    long stepRecords = executor.execute(context, step, sinkWriter);
                    stepResults.add(new StepResult(step.id, true, stepRecords, System.currentTimeMillis() - stepStart, null));
                    records += stepRecords;
                }
                if (i + 1 < loopCount && config.runtime.loopIntervalMs > 0) {
                    sleep(config.runtime.loopIntervalMs);
                }
            }
            sinkWriter.flush();
            return ApiExtractResult.success(config.job.id, runId, records, System.currentTimeMillis() - start, stepResults);
        } catch (Exception e) {
            return ApiExtractResult.failure(config.job.id, runId, records, System.currentTimeMillis() - start,
                    e.getMessage(), stepResults);
        }
    }

    private IntermediateCache createCache(ApiExtractJobConfig config) {
        if (config.redis != null && config.redis.enabled) {
            return new RedisIntermediateCache(config.redis);
        }
        return new InMemoryIntermediateCache();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiExtractException("Interrupted", e);
        }
    }
}
