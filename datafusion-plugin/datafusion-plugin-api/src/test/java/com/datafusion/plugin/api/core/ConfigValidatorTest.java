package com.datafusion.plugin.api.core;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.FieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.SchemaFieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.StepConfig;
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
    public void validateShouldAcceptOnceNoopConfig() {
        Assertions.assertDoesNotThrow(() -> validator.validate(validConfig()));
    }

    @Test
    public void validateShouldRequireCronExpressionForCronMode() {
        ApiExtractJobConfig config = validConfig();
        config.trigger.mode = "CRON";
        config.trigger.cron = null;

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("trigger.cron"));
    }

    @Test
    public void validateShouldRejectInvalidCacheMode() {
        ApiExtractJobConfig config = validConfig();
        config.redis.enabled = true;
        config.steps.get(0).cache.enabled = true;
        config.steps.get(0).cache.key = "user:${job.id}";
        config.steps.get(0).cache.mode = "MERGE";

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("cache.mode"));
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
        config.sink.mode = "UPSERT";
        config.sink.table.name = "api_user";
        config.sink.schema.add(schemaField("id"));
        config.sink.schema.add(schemaField("name"));

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("UPSERT"));
    }

    @Test
    public void validateShouldRequireSinkSchemaCoverage() {
        ApiExtractJobConfig config = validConfig();
        config.sink.type = "STARROCKS";
        config.sink.table.name = "api_user";

        ApiExtractException error = Assertions.assertThrows(ApiExtractException.class, () -> validator.validate(config));

        Assertions.assertTrue(error.getMessage().contains("sink.schema lacks response field"));
    }

    private ApiExtractJobConfig validConfig() {
        ApiExtractJobConfig config = new ApiExtractJobConfig();
        config.job.id = "api-user";
        config.trigger.mode = "ONCE";
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
        step.response.fields.add(field("id", true));
        step.response.fields.add(field("name", false));
        return step;
    }

    private FieldConfig field(String name, boolean key) {
        FieldConfig field = new FieldConfig();
        field.name = name;
        field.expression = "data[]." + name;
        field.isKey = key;
        return field;
    }

    private SchemaFieldConfig schemaField(String name) {
        SchemaFieldConfig field = new SchemaFieldConfig();
        field.name = name;
        field.type = "STRING";
        return field;
    }
}
