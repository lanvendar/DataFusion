package com.datafusion.plugin.kafka.json;

import com.datafusion.plugin.kafka.json.config.ConfigLoader;
import com.datafusion.plugin.kafka.json.config.ConfigValidator;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig;
import com.datafusion.plugin.kafka.json.runtime.FlinkRuntimeConfigurer;
import com.datafusion.plugin.kafka.json.sink.PaimonMultiTableSinkFunction;
import com.datafusion.plugin.kafka.json.source.KafkaRecord;
import com.datafusion.plugin.kafka.json.source.KafkaSourceFactory;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * kafka-json-paimon 插件启动入口.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaJsonPaimonApplication {

    /**
     * 启动作业.
     *
     * @param args 启动参数
     * @throws Exception Flink 作业异常
     */
    public static void main(String[] args) throws Exception {
        String configPath = parseConfigPath(args);
        ConfigLoader loader = new ConfigLoader();
        KafkaJsonPaimonJobConfig config = loader.load(configPath);
        new ConfigValidator().validate(config);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        new FlinkRuntimeConfigurer().configure(env, config.runtime);

        KafkaSource<KafkaRecord> source = new KafkaSourceFactory().create(config.source);
        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-schema-source")
                .name("kafka-schema-source")
                .addSink(new PaimonMultiTableSinkFunction(config.sink))
                .name("paimon-multi-table-sink")
                .setParallelism(config.runtime == null || config.runtime.parallelism == null ? 1 : config.runtime.parallelism);
        env.execute(config.job.id);
    }

    private static String parseConfigPath(String[] args) {
        if (args == null || args.length == 0) {
            return "job.json";
        }
        for (int i = 0; i < args.length; i++) {
            if (("--config".equals(args[i]) || "-c".equals(args[i])) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return args[0];
    }
}
