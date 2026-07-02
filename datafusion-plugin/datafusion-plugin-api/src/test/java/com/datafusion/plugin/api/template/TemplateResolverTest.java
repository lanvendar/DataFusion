package com.datafusion.plugin.api.template;

import com.datafusion.plugin.api.cache.InMemoryIntermediateCache;
import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ApiExtractContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Template resolver unit tests.
 *
 * @author DataFusion
 * @version 1.0.0
 */
public class TemplateResolverTest {

    @Test
    public void resolveShouldSupportJobRunVarsStepsAndRedisTokens() {
        ApiExtractJobConfig config = new ApiExtractJobConfig();
        config.job.id = "api-user";
        config.redis.options.put("keyPrefix", "datafusion:plugin:api");
        ApiExtractContext context = new ApiExtractContext(config, "run-001");
        context.putStepOutput("fetch_token", Map.of("accessToken", "token-123"));
        InMemoryIntermediateCache cache = new InMemoryIntermediateCache();
        cache.put("datafusion:plugin:api:token", Map.of("value", "cached-token"), 0);
        TemplateResolver resolver = new TemplateResolver(cache);

        String result = resolver.resolve("${job.id}/${run.id}/${steps.fetch_token.outputVars.accessToken}/${redis.token}", context);

        Assertions.assertEquals("api-user/run-001/token-123/{\"value\":\"cached-token\"}", result);
    }

    @Test
    public void resolveObjectShouldResolveNestedStructures() {
        ApiExtractJobConfig config = new ApiExtractJobConfig();
        config.job.id = "api-user";
        ApiExtractContext context = new ApiExtractContext(config, "run-001");
        TemplateResolver resolver = new TemplateResolver();

        Object value = resolver.resolveObject(Map.of("run", "${run.id}", "nested", Map.of("job", "${job.id}")), context);

        Assertions.assertEquals(Map.of("run", "run-001", "nested", Map.of("job", "api-user")), value);
    }
}
