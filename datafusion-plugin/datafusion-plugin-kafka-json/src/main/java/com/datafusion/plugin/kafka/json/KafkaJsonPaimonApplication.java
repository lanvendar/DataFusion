package com.datafusion.plugin.kafka.json;

import com.datafusion.plugin.kafka.json.config.ConfigLoader;
import com.datafusion.plugin.kafka.json.config.ConfigValidator;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpec;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.expression.JsonType;
import com.datafusion.plugin.kafka.json.resolve.KafkaJsonToPaimonWritePlanFunction;
import com.datafusion.plugin.kafka.json.runtime.FlinkRuntimeConfigurer;
import com.datafusion.plugin.kafka.json.sink.PaimonMultiTableSink;
import com.datafusion.plugin.kafka.json.source.KafkaRecord;
import com.datafusion.plugin.kafka.json.source.KafkaSourceFactory;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.ArrayList;
import java.util.List;

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
        String jobName = resolveJobName(config);
        String sinkName = resolveSinkName(config);

        KafkaSource<KafkaRecord> source = new KafkaSourceFactory().create(config.source);
        env.fromSource(source, WatermarkStrategy.noWatermarks(), jobName)
                .name(jobName)
                .flatMap(new KafkaJsonToPaimonWritePlanFunction(config.sink))
                .name(jobName + "-resolve")
                .keyBy(plan -> plan.tableConfig.identifier())
                .sinkTo(new PaimonMultiTableSink(config.sink, commitUser(config)))
                .name(sinkName)
                .setParallelism(config.runtime == null || config.runtime.parallelism == null ? 1 : config.runtime.parallelism);
        env.execute(jobName);
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

    private static String resolveJobName(KafkaJsonPaimonJobConfig config) {
        if (config == null || config.job == null) {
            return "kafka-json-paimon-job";
        }
        return TextUtils.isBlank(config.job.name) ? config.job.id : config.job.name;
    }

    private static String resolveSinkName(KafkaJsonPaimonJobConfig config) {
        if (config == null || config.sink == null || config.sink.tables == null || config.sink.tables.isEmpty()) {
            return "paimon-sink";
        }
        List<String> staticTableNames = new ArrayList<>();
        for (PaimonTableConfig table : config.sink.tables) {
            if (table == null || Boolean.FALSE.equals(table.enabled)) {
                continue;
            }
            String tableName = staticTableName(table);
            if (TextUtils.isBlank(tableName)) {
                return "paimon-multi-table-sink";
            }
            staticTableNames.add(tableName);
        }
        if (staticTableNames.isEmpty()) {
            return "paimon-sink";
        }
        if (staticTableNames.size() == 1) {
            return staticTableNames.get(0);
        }
        return "paimon-multi-table-sink[" + String.join(",", staticTableNames) + "]";
    }

    private static String staticTableName(PaimonTableConfig table) {
        ExpressionSpec spec = ExpressionSpecNormalizer.constant(table.tableName, JsonType.STRING);
        Object defaultValue = spec.defaultValue;
        return defaultValue instanceof String ? (String) defaultValue : null;
    }

    private static String commitUser(KafkaJsonPaimonJobConfig config) {
        return "datafusion-kafka-json-" + resolveJobName(config);
    }
}
