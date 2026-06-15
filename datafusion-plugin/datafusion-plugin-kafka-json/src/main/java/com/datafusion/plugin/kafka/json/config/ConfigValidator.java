package com.datafusion.plugin.kafka.json.config;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.KafkaSourceConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.RuntimeConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.TableConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.WriterConfig;
import com.datafusion.plugin.kafka.json.core.KafkaJsonPaimonException;
import com.datafusion.plugin.kafka.json.core.enums.CheckpointMode;
import com.datafusion.plugin.kafka.json.core.enums.DeploymentMode;
import com.datafusion.plugin.kafka.json.core.enums.ExecutionMode;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.kafka.json.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.kafka.json.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.kafka.json.core.enums.RestartStrategyType;
import com.datafusion.plugin.kafka.json.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.kafka.json.core.enums.StateBackendType;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.expression.JsonType;
import com.datafusion.plugin.kafka.json.util.TextUtils;

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
    public void validate(KafkaJsonPaimonJobConfig config) {
        if (config == null) {
            throw new KafkaJsonPaimonException("Job config is required");
        }
        if (config.job == null || TextUtils.isBlank(config.job.id)) {
            throw new KafkaJsonPaimonException("job.id is required");
        }
        validateSource(config.source);
        validateRuntime(config.runtime);
        validateSink(config.sink);
    }

    private void validateSource(KafkaSourceConfig source) {
        if (source == null) {
            throw new KafkaJsonPaimonException("source is required");
        }
        if (TextUtils.isBlank(source.bootstrapServers)) {
            throw new KafkaJsonPaimonException("source.bootstrapServers is required");
        }
        boolean hasTopics = source.topics != null && !source.topics.isEmpty();
        boolean hasTopicPattern = !TextUtils.isBlank(source.topicPattern);
        if (hasTopics == hasTopicPattern) {
            throw new KafkaJsonPaimonException("source.topics and source.topicPattern must choose exactly one");
        }
        if (TextUtils.isBlank(source.groupId)) {
            throw new KafkaJsonPaimonException("source.groupId is required");
        }
        validateStartingOffsets(source.startingOffsets);
    }

    private void validateStartingOffsets(String startingOffsets) {
        String value = TextUtils.upper(startingOffsets, "GROUP-OFFSETS").replace('_', '-');
        Set<String> supported = Set.of("EARLIEST", "LATEST", "GROUP-OFFSETS", "TIMESTAMP");
        if (!supported.contains(value)) {
            throw new KafkaJsonPaimonException("Unsupported source.startingOffsets: " + startingOffsets);
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
            throw new KafkaJsonPaimonException("runtime.parallelism must be greater than 0");
        }
        if (runtime.checkpointIntervalMs != null && runtime.checkpointIntervalMs <= 0) {
            throw new KafkaJsonPaimonException("runtime.checkpointIntervalMs must be greater than 0");
        }
    }

    private void validateSink(PaimonSinkConfig sink) {
        if (sink == null) {
            throw new KafkaJsonPaimonException("sink is required");
        }
        LoadMode.parse(sink.loadMode, LoadMode.APPEND);
        SchemaMismatchPolicy.parse(sink.schemaMismatchPolicy);
        RecordErrorPolicy.parse(sink.recordErrorPolicy);
        Map<String, String> options = sink.options;
        if (options == null || options.isEmpty()) {
            throw new KafkaJsonPaimonException("sink.options is required");
        }
        if (TextUtils.isBlank(options.get("warehouse"))) {
            throw new KafkaJsonPaimonException("sink.options.warehouse is required");
        }
        if (sink.tables == null || sink.tables.isEmpty()) {
            throw new KafkaJsonPaimonException("sink.tables is required");
        }
        validateTables(sink);
        validateWriter(sink.writer);
    }

    private void validateTables(PaimonSinkConfig sink) {
        Set<String> staticNames = new HashSet<>();
        int enabledCount = 0;
        for (PaimonTableConfig table : sink.tables) {
            if (table == null || Boolean.FALSE.equals(table.enabled)) {
                continue;
            }
            enabledCount++;
            validateTableConfig(table.table);
            validateExpression(table.columnsMapping, JsonType.ANY, true, "sink.tables[].columnsMapping");
            if (!TextUtils.isBlank(table.loadMode)) {
                LoadMode.parse(table.loadMode, LoadMode.APPEND);
            }
            validateColumns(table);
            validatePrimaryKey(table.table, LoadMode.parse(table.loadMode, LoadMode.parse(sink.loadMode, LoadMode.APPEND)));
            String tableName = staticDefault(table.table.name);
            if (!TextUtils.isBlank(tableName) && !staticNames.add(tableName.toLowerCase())) {
                throw new KafkaJsonPaimonException("Duplicate static sink tableName: " + tableName);
            }
        }
        if (enabledCount == 0) {
            throw new KafkaJsonPaimonException("sink.tables has no enabled table");
        }
    }

    private void validateTableConfig(TableConfig table) {
        if (table == null) {
            throw new KafkaJsonPaimonException("sink.tables[].table is required");
        }
        validateExpression(table.database, JsonType.STRING, false, "sink.tables[].table.database");
        validateExpression(table.name, JsonType.STRING, false, "sink.tables[].table.name");
        validateExpression(table.comment, JsonType.STRING, false, "sink.tables[].table.comment");
        validateExpression(table.createIfNotExists, JsonType.BOOLEAN, false, "sink.tables[].table.createIfNotExists");
        validateExpression(table.partitionKeys, JsonType.ARRAY, false, "sink.tables[].table.partitionKeys");
    }

    private void validateColumns(PaimonTableConfig table) {
        if (table.columns == null) {
            return;
        }
        Set<String> names = new HashSet<>();
        for (ColumnConfig column : table.columns) {
            if (column == null || TextUtils.isBlank(column.name)) {
                throw new KafkaJsonPaimonException("sink.tables[].columns[].name is required");
            }
            if (!names.add(column.name.toLowerCase())) {
                throw new KafkaJsonPaimonException("Duplicate sink column: " + column.name);
            }
            validateExpression(column.value, inferJsonType(column), false, "sink.tables[].columns[].value");
            if (column.length != null && column.length <= 0) {
                throw new KafkaJsonPaimonException("sink.tables[].columns[].length must be greater than 0");
            }
            if (column.precision != null && column.precision <= 0) {
                throw new KafkaJsonPaimonException("sink.tables[].columns[].precision must be greater than 0");
            }
            if (column.scale != null && column.scale < 0) {
                throw new KafkaJsonPaimonException("sink.tables[].columns[].scale must not be less than 0");
            }
        }
    }

    private void validatePrimaryKey(TableConfig table, LoadMode loadMode) {
        if (table == null || table.primaryKeys == null) {
            return;
        }
        PrimaryKeyMode mode = PrimaryKeyMode.parse(table.primaryKeys.mode);
        validatePrimaryKeyExpression(table.primaryKeys);
        if (mode == PrimaryKeyMode.PROXY) {
            ProxyPrimaryKeyType.parse(table.primaryKeys.algorithm);
        }
        if (mode == PrimaryKeyMode.FIELDS && loadMode == LoadMode.UPSERT && table.primaryKeys.defaultValue == null
                && TextUtils.isBlank(table.primaryKeys.path)) {
            throw new KafkaJsonPaimonException("sink.tables[].table.primaryKeys.path or defaultValue is required for UPSERT");
        }
    }

    private void validatePrimaryKeyExpression(PrimaryKeyConfig primaryKey) {
        JsonType.parse(primaryKey.jsonType);
        if (!"ARRAY".equals(TextUtils.upper(primaryKey.jsonType, "ARRAY"))) {
            throw new KafkaJsonPaimonException("sink.tables[].table.primaryKeys.jsonType must be ARRAY");
        }
    }

    private void validateWriter(WriterConfig writer) {
        if (writer == null) {
            return;
        }
        if (writer.batchSize != null && writer.batchSize <= 0) {
            throw new KafkaJsonPaimonException("sink.writer.batchSize must be greater than 0");
        }
        if (writer.flushIntervalMs != null && writer.flushIntervalMs <= 0) {
            throw new KafkaJsonPaimonException("sink.writer.flushIntervalMs must be greater than 0");
        }
        if (writer.maxOpenWriters != null && writer.maxOpenWriters <= 0) {
            throw new KafkaJsonPaimonException("sink.writer.maxOpenWriters must be greater than 0");
        }
    }

    private void validateExpression(com.fasterxml.jackson.databind.JsonNode node, JsonType defaultType, boolean textAsPath, String name) {
        try {
            JsonType.parse(ExpressionSpecNormalizer.path(node, defaultType, null).jsonType);
        } catch (RuntimeException e) {
            throw new KafkaJsonPaimonException("Invalid expression config: " + name + ", reason=" + e.getMessage(), e);
        }
        if (node == null && !textAsPath) {
            return;
        }
        if (node == null && textAsPath) {
            throw new KafkaJsonPaimonException(name + " is required");
        }
    }

    private void validateRequiredExpression(com.fasterxml.jackson.databind.JsonNode node, String name) {
        if (node == null) {
            throw new KafkaJsonPaimonException(name + " is required");
        }
    }

    private JsonType inferJsonType(ColumnConfig column) {
        String dataType = TextUtils.upper(!TextUtils.isBlank(column.dataType) ? column.dataType : column.type, "STRING");
        if ("INT".equals(dataType) || "INTEGER".equals(dataType) || "BIGINT".equals(dataType) || "LONG".equals(dataType)
                || "DOUBLE".equals(dataType) || "FLOAT".equals(dataType) || "DECIMAL".equals(dataType)) {
            return JsonType.NUMBER;
        }
        if ("BOOLEAN".equals(dataType)) {
            return JsonType.BOOLEAN;
        }
        return JsonType.ANY;
    }

    private String staticDefault(com.fasterxml.jackson.databind.JsonNode node) {
        Object value = ExpressionSpecNormalizer.constant(node, JsonType.STRING).defaultValue;
        return value instanceof String text ? text : null;
    }

}
