package com.datafusion.plugin.flink.schema.paimon.config;

import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.KafkaSourceConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonSinkGroupConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.PaimonTableSinkConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.RuntimeConfig;
import com.datafusion.plugin.flink.schema.paimon.config.FlinkSchemaPaimonJobConfig.WriteConfig;
import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.core.enums.CheckpointMode;
import com.datafusion.plugin.flink.schema.paimon.core.enums.DeploymentMode;
import com.datafusion.plugin.flink.schema.paimon.core.enums.ExecutionMode;
import com.datafusion.plugin.flink.schema.paimon.core.enums.LoadMode;
import com.datafusion.plugin.flink.schema.paimon.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.flink.schema.paimon.core.enums.RestartStrategyType;
import com.datafusion.plugin.flink.schema.paimon.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.flink.schema.paimon.core.enums.StateBackendType;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 作业配置校验器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConfigValidator {

    /**
     * 校验作业配置.
     *
     * @param config 作业配置
     */
    public void validate(FlinkSchemaPaimonJobConfig config) {
        if (config == null) {
            throw new FlinkSchemaPaimonException("Job config is required");
        }
        if (config.job == null || TextUtils.isBlank(config.job.id)) {
            throw new FlinkSchemaPaimonException("job.id is required");
        }
        validateSource(config.source);
        validateRuntime(config.runtime);
        validateSink(config.sink);
    }

    private void validateSource(KafkaSourceConfig source) {
        if (source == null) {
            throw new FlinkSchemaPaimonException("source is required");
        }
        if (TextUtils.isBlank(source.bootstrapServers)) {
            throw new FlinkSchemaPaimonException("source.bootstrapServers is required");
        }
        boolean hasTopics = source.topics != null && !source.topics.isEmpty();
        boolean hasTopicPattern = !TextUtils.isBlank(source.topicPattern);
        if (hasTopics == hasTopicPattern) {
            throw new FlinkSchemaPaimonException("source.topics and source.topicPattern must choose exactly one");
        }
        if (TextUtils.isBlank(source.groupId)) {
            throw new FlinkSchemaPaimonException("source.groupId is required");
        }
        validateStartingOffsets(source.startingOffsets);
    }

    private void validateStartingOffsets(String startingOffsets) {
        String value = TextUtils.upper(startingOffsets, "GROUP-OFFSETS").replace('_', '-');
        Set<String> supported = Set.of("EARLIEST", "LATEST", "GROUP-OFFSETS", "TIMESTAMP");
        if (!supported.contains(value)) {
            throw new FlinkSchemaPaimonException("Unsupported source.startingOffsets: " + startingOffsets);
        }
    }

    private void validateRuntime(RuntimeConfig runtime) {
        if (runtime == null) {
            return;
        }
        DeploymentMode.parse(runtime.deploymentMode);
        ExecutionMode.parse(runtime.executionMode);
        CheckpointMode.parse(runtime.checkpointMode);
        StateBackendType.parse(runtime.stateBackend);
        RestartStrategyType.parse(runtime.restartStrategy);
        if (runtime.parallelism != null && runtime.parallelism <= 0) {
            throw new FlinkSchemaPaimonException("runtime.parallelism must be greater than 0");
        }
        if (runtime.checkpointIntervalMs != null && runtime.checkpointIntervalMs <= 0) {
            throw new FlinkSchemaPaimonException("runtime.checkpointIntervalMs must be greater than 0");
        }
    }

    private void validateSink(PaimonSinkGroupConfig sink) {
        if (sink == null) {
            throw new FlinkSchemaPaimonException("sink is required");
        }
        LoadMode.parse(sink.loadMode, LoadMode.APPEND);
        SchemaMismatchPolicy.parse(sink.schemaMismatchPolicy);
        RecordErrorPolicy.parse(sink.recordErrorPolicy);
        Map<String, String> options = sink.options == null || sink.options.isEmpty() ? sink.catalogOptions : sink.options;
        if (options == null || options.isEmpty()) {
            throw new FlinkSchemaPaimonException("sink.options is required");
        }
        if (TextUtils.isBlank(options.get("warehouse"))) {
            throw new FlinkSchemaPaimonException("sink.options.warehouse is required");
        }
        if (sink.tables == null || sink.tables.isEmpty()) {
            throw new FlinkSchemaPaimonException("sink.tables is required");
        }
        validateTables(sink);
        validateWrite(sink.write);
    }

    private void validateTables(PaimonSinkGroupConfig sink) {
        Set<String> names = new HashSet<>();
        int enabledCount = 0;
        for (PaimonTableSinkConfig table : sink.tables) {
            if (table == null || Boolean.FALSE.equals(table.enabled)) {
                continue;
            }
            enabledCount++;
            if (TextUtils.isBlank(table.database)) {
                throw new FlinkSchemaPaimonException("sink.tables[].database is required");
            }
            if (TextUtils.isBlank(table.tableName)) {
                throw new FlinkSchemaPaimonException("sink.tables[].tableName is required");
            }
            if (!TextUtils.isBlank(table.loadMode)) {
                LoadMode.parse(table.loadMode, LoadMode.APPEND);
            }
            String tableKey = table.tableName.toLowerCase();
            if (!names.add(tableKey)) {
                throw new FlinkSchemaPaimonException("Duplicate sink tableName: " + table.tableName);
            }
        }
        if (enabledCount == 0) {
            throw new FlinkSchemaPaimonException("sink.tables has no enabled table");
        }
    }

    private void validateWrite(WriteConfig write) {
        if (write == null) {
            return;
        }
        if (write.batchSize != null && write.batchSize <= 0) {
            throw new FlinkSchemaPaimonException("sink.write.batchSize must be greater than 0");
        }
        if (write.flushIntervalMs != null && write.flushIntervalMs <= 0) {
            throw new FlinkSchemaPaimonException("sink.write.flushIntervalMs must be greater than 0");
        }
        if (write.maxOpenWriters != null && write.maxOpenWriters <= 0) {
            throw new FlinkSchemaPaimonException("sink.write.maxOpenWriters must be greater than 0");
        }
    }
}
