package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.RedisCacheConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ColumnConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.ValueExpressionConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Config validator unit tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class ConfigValidatorTest {

    private final ConfigValidator validator = new ConfigValidator();

    @Test
    public void validateShouldAcceptNoopConfig() {
        Assertions.assertDoesNotThrow(() -> validator.validate(validConfig()));
    }

    @Test
    public void validateShouldRejectInvalidCacheMode() {
        ApiExtractJobConfig config = validConfig();
        config.redis.enabled = true;
        config.redis.options.put("host", "localhost");
        config.redis.options.put("port", 6379);
        config.redis.options.put("database", 0);
        RedisCacheConfig redisCache = config.steps.get(0).redisCache;
        redisCache.enabled = true;
        redisCache.key = "user:${job.id}";
        redisCache.loadMode = "MERGE";
        ValueExpressionConfig valueExpression = new ValueExpressionConfig();
        valueExpression.name = "id";
        valueExpression.expression = "data.id";
        redisCache.valueExpressions.add(valueExpression);

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("redisCache.loadMode"));
    }

    @Test
    public void validateShouldRequirePagePaginationParams() {
        ApiExtractJobConfig config = validConfig();
        config.steps.get(0).pagination.type = "PAGE";
        config.steps.get(0).pagination.pageParam = "page";

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("pageParam and pageSizeParam"));
    }

    @Test
    public void validateShouldRequireStarRocksUpsertPrimaryKeys() {
        ApiExtractJobConfig config = validConfig();
        config.sink.type = "STARROCKS";
        config.sink.loadMode = "UPSERT";
        config.sink.connectType = "LOAD_STREAM";
        config.sink.options.put("loadUrl", "http://starrocks-fe:8030");
        config.sink.options.put("username", "root");
        config.sink.options.put("database", "dwd");
        config.sink.table.name = "api_user";
        config.sink.columns.add(column("id"));
        config.sink.columns.add(column("name"));

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("UPSERT"));
    }

    @Test
    public void validateShouldRequireSinkColumnCoverage() {
        ApiExtractJobConfig config = validConfig();
        config.sink.type = "STARROCKS";
        config.sink.loadMode = "APPEND";
        config.sink.connectType = "LOAD_STREAM";
        config.sink.options.put("loadUrl", "http://starrocks-fe:8030");
        config.sink.options.put("username", "root");
        config.sink.options.put("database", "dwd");
        config.sink.table.name = "api_user";

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("sink.columns lacks response field"));
    }

    private ApiExtractJobConfig validConfig() {
        ApiExtractJobConfig config = new ApiExtractJobConfig();
        config.job.id = "api-user";
        config.sink.type = "NOOP";
        config.steps.add(step());
        return config;
    }

    private StepConfig step() {
        StepConfig step = new StepConfig();
        step.id = "fetch_user";
        step.request.method = "GET";
        step.request.url = "http://example.test/users";
        step.response.recordMode = "ARRAY";
        step.response.fields.add(field("id"));
        step.response.fields.add(field("name"));
        return step;
    }

    private FieldConfig field(String name) {
        FieldConfig field = new FieldConfig();
        field.name = name;
        field.expression = "data[]." + name;
        return field;
    }

    private ColumnConfig column(String name) {
        ColumnConfig field = new ColumnConfig();
        field.name = name;
        field.type = "STRING";
        return field;
    }
}
