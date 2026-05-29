package com.datafusion.plugin.api.integration;

import com.datafusion.plugin.api.config.ApiExtractJobConfig;
import com.datafusion.plugin.api.core.ConfigValidator;
import com.datafusion.plugin.api.util.JsonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 化纤产品信息 Paimon 同步任务配置测试.
 *
 * @author DataFusion
 * @version 1.0.0
 */
@Tag("integration")
public class OdsHqplProductInfoPaimonJobConfigTest {

    /**
     * 任务配置文件.
     */
    private static final String PRODUCT_INFO_CONFIG_FILE = "ods_hqpl_product_info-paimon-job.json";

    /**
     * 基准价任务配置文件.
     */
    private static final String BENCHMARK_PRICE_CONFIG_FILE = "ods_hqpl_product_benchmark_price-paimon-job.json";

    /**
     * 校验 ODS 产品信息任务配置满足每日凌晨 1 点触发.
     *
     * @throws Exception 配置读取失败时抛出
     */
    @Test
    public void validateOdsProductInfoPaimonCronConfig() throws Exception {
        ApiExtractJobConfig config = loadConfig(PRODUCT_INFO_CONFIG_FILE);

        Assertions.assertEquals("ods_hqpl_product_info", config.job.id);
        Assertions.assertEquals("CRON", config.trigger.mode);
        Assertions.assertEquals("0 0 1 * * ?", config.trigger.cron);
        Assertions.assertEquals("Asia/Shanghai", config.trigger.timezone);
        Assertions.assertEquals("PAIMON", config.sink.type);
        Assertions.assertEquals("S3", config.sink.connectType);
        Assertions.assertEquals("dim_product_info", config.sink.table.name);
        Assertions.assertEquals(1, config.sink.table.primaryKeys.size());
        Assertions.assertEquals("name", config.sink.table.primaryKeys.get(0));
        Assertions.assertTrue(config.sink.table.partitionKeys.isEmpty());
        Assertions.assertFalse(config.sink.columns.isEmpty());
        Assertions.assertTrue(config.sink.columns.stream().anyMatch(column -> "update_time".equals(column.name)));

        Assertions.assertDoesNotThrow(() -> new ConfigValidator().validate(config));
    }

    /**
     * 校验 ODS 产品基准价任务配置满足 Paimon 主键和分区要求.
     *
     * @throws Exception 配置读取失败时抛出
     */
    @Test
    public void validateOdsBenchmarkPricePaimonOnceConfig() throws Exception {
        ApiExtractJobConfig config = loadConfig(BENCHMARK_PRICE_CONFIG_FILE);

        Assertions.assertEquals("ods_hqpl_product_benchmark_price", config.job.id);
        Assertions.assertEquals("ONCE", config.trigger.mode);
        Assertions.assertEquals("0 0 1 * * ?", config.trigger.cron);
        Assertions.assertEquals("PAIMON", config.sink.type);
        Assertions.assertEquals("ods_hqpl_product_benchmark_price", config.sink.table.name);
        Assertions.assertEquals(3, config.sink.table.primaryKeys.size());
        Assertions.assertTrue(config.sink.table.primaryKeys.contains("product_id_name"));
        Assertions.assertTrue(config.sink.table.primaryKeys.contains("product_detail_id_name"));
        Assertions.assertTrue(config.sink.table.primaryKeys.contains("product_class"));
        Assertions.assertEquals(1, config.sink.table.partitionKeys.size());
        Assertions.assertEquals("day_pt", config.sink.table.partitionKeys.get(0));
        Assertions.assertTrue(config.sink.columns.stream().anyMatch(column -> "day_pt".equals(column.name)));

        Assertions.assertDoesNotThrow(() -> new ConfigValidator().validate(config));
    }

    private ApiExtractJobConfig loadConfig(String configFile) throws Exception {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(configFile)) {
            Assertions.assertNotNull(inputStream, "配置文件不存在: " + configFile);
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return JsonUtils.read(json, ApiExtractJobConfig.class);
        }
    }
}
