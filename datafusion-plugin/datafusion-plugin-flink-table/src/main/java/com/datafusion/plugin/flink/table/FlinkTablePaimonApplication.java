package com.datafusion.plugin.flink.table;

import com.datafusion.plugin.flink.table.config.ConfigLoader;
import com.datafusion.plugin.flink.table.config.ConfigValidator;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonTableConfig;
import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.expression.ExpressionSpec;
import com.datafusion.plugin.flink.table.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.flink.table.core.enums.JsonType;
import com.datafusion.plugin.flink.table.resolve.FlinkTableToPaimonWritePlanFunction;
import com.datafusion.plugin.flink.table.runtime.FlinkRuntimeConfigurer;
import com.datafusion.plugin.flink.table.sink.PaimonMultiTableSink;
import com.datafusion.plugin.flink.table.source.KafkaRecord;
import com.datafusion.plugin.flink.table.source.KafkaSourceFactory;
import com.datafusion.plugin.flink.table.util.TextUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * kafka-json-paimon 插件启动入口.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class FlinkTablePaimonApplication {

    /**
     * Inline job argument.
     */
    private static final String JOB_ARG = "--job";

    /**
     * Job file argument.
     */
    private static final String JOB_FILE_ARG = "--jobFile";

    /**
     * Default job file.
     */
    private static final String DEFAULT_JOB_FILE = "job.json";

    /**
     * 启动作业.
     *
     * @param args 启动参数
     * @throws Exception Flink 作业异常
     */
    public static void main(String[] args) throws Exception {
        FlinkTableJobConfig config = loadConfig(args);
        new ConfigValidator().validate(config);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        new FlinkRuntimeConfigurer().configure(env, config.flinkConfig);
        String jobName = resolveJobName(config);
        String sinkName = resolveSinkName(config);

        KafkaSource<KafkaRecord> source = new KafkaSourceFactory().create(config.source);
        env.fromSource(source, WatermarkStrategy.noWatermarks(), jobName)
                .name(jobName)
                .flatMap(new FlinkTableToPaimonWritePlanFunction(config.sink))
                .name(jobName + "-resolve")
                .keyBy(plan -> plan.tableConfig.identifier())
                .sinkTo(new PaimonMultiTableSink(config.sink, commitUser(config)))
                .name(sinkName);
        env.execute(jobName);
    }

    /**
     * Load job config from launch arguments.
     *
     * @param args launch arguments
     * @return job config
     */
    static FlinkTableJobConfig loadConfig(String[] args) {
        ConfigLoader loader = new ConfigLoader();
        String inlineJob = argumentValue(args, JOB_ARG);
        if (inlineJob != null) {
            if (TextUtils.isBlank(inlineJob)) {
                throw new FlinkTableException("Job content is required");
            }
            return loader.loadContent(decodeBase64Job(inlineJob));
        }
        return loader.load(parseJobFilePath(args));
    }

    private static String parseJobFilePath(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_JOB_FILE;
        }
        String jobFile = argumentValue(args, JOB_FILE_ARG);
        if (jobFile != null) {
            return jobFile;
        }
        throw new FlinkTableException("Unsupported arguments, only --job or --jobFile are supported");
    }

    private static String argumentValue(String[] args, String argumentName) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (int i = 0; i < args.length; i++) {
            if (!argumentName.equals(args[i])) {
                continue;
            }
            if (i + 1 >= args.length) {
                throw new FlinkTableException("Missing value for argument: " + argumentName);
            }
            return args[i + 1];
        }
        return null;
    }

    private static String decodeBase64Job(String encodedJob) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedJob);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new FlinkTableException("Failed to decode base64 job content", e);
        }
    }

    private static String resolveJobName(FlinkTableJobConfig config) {
        if (config == null || config.job == null) {
            return "kafka-json-paimon-job";
        }
        return TextUtils.isBlank(config.job.name) ? config.job.id : config.job.name;
    }

    private static String resolveSinkName(FlinkTableJobConfig config) {
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
        ExpressionSpec spec = ExpressionSpecNormalizer.constant(table.table.name, JsonType.STRING);
        Object defaultValue = spec.defaultValue;
        return defaultValue instanceof String ? (String) defaultValue : null;
    }

    private static String commitUser(FlinkTableJobConfig config) {
        return "datafusion-kafka-json-" + resolveJobName(config);
    }
}
