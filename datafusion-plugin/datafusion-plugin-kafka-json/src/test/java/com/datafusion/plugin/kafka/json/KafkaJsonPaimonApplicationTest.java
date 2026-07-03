package com.datafusion.plugin.kafka.json;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * KafkaJsonPaimonApplication 单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class KafkaJsonPaimonApplicationTest {

    /**
     * --job 参数按 base64 JSON 内容加载配置.
     */
    @Test
    void shouldLoadConfigFromBase64Job() {
        String jobJson = "{\"job\":{\"id\":\"job-from-base64\"},\"flinkConfig\":{\"parallelism.default\":\"2\"}}";
        String encodedJob = Base64.getEncoder().encodeToString(jobJson.getBytes(StandardCharsets.UTF_8));

        KafkaJsonPaimonJobConfig config = KafkaJsonPaimonApplication.loadConfig(new String[] {"--job", encodedJob});

        Assertions.assertEquals("job-from-base64", config.job.id);
        Assertions.assertEquals("2", config.flinkConfig.get("parallelism.default"));
    }

    /**
     * --jobFile 参数按 JSON 文件加载配置.
     *
     * @param tempDir 临时目录
     * @throws Exception 文件写入异常
     */
    @Test
    void shouldLoadConfigFromJobFile(@TempDir Path tempDir) throws Exception {
        Path jobFile = tempDir.resolve("flink-job.json");
        Files.writeString(jobFile, "{\"job\":{\"id\":\"job-from-file\"}}", StandardCharsets.UTF_8);

        KafkaJsonPaimonJobConfig config = KafkaJsonPaimonApplication.loadConfig(new String[] {
                "--jobFile", jobFile.toString()
        });

        Assertions.assertEquals("job-from-file", config.job.id);
    }

    /**
     * 参数名缺少值时失败.
     */
    @Test
    void shouldFailWhenArgumentValueMissing() {
        Assertions.assertThrows(KafkaJsonPaimonException.class,
                () -> KafkaJsonPaimonApplication.loadConfig(new String[] {"--job"}));
    }

    /**
     * --config 不再作为兼容参数.
     */
    @Test
    void shouldRejectLegacyConfigArgument() {
        Assertions.assertThrows(KafkaJsonPaimonException.class,
                () -> KafkaJsonPaimonApplication.loadConfig(new String[] {"--config", "job.json"}));
    }
}
