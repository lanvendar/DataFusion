package com.datafusion.plugin.flink.table.config;

import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.ColumnConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.KafkaSourceConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonTableConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.TableConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.WriterConfig;
import com.datafusion.plugin.flink.table.core.FlinkTableException;
import com.datafusion.plugin.flink.table.core.SystemFieldNames;
import com.datafusion.plugin.flink.table.core.enums.LoadMode;
import com.datafusion.plugin.flink.table.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.flink.table.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.flink.table.core.enums.RecordErrorPolicy;
import com.datafusion.plugin.flink.table.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.flink.table.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.flink.table.core.enums.JsonType;
import com.datafusion.plugin.flink.table.util.TextUtils;

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
    public void validate(FlinkTableJobConfig config) {
        if (config == null) {
            throw new FlinkTableException("Job config is required");
        }
        if (config.job == null || TextUtils.isBlank(config.job.id)) {
            throw new FlinkTableException("job.id is required");
        }
        validateSource(config.source);
        validateFlinkConfig(config.flinkConfig);
        validateSink(config.sink);
    }

    private void validateSource(KafkaSourceConfig source) {
        if (source == null) {
            throw new FlinkTableException("source is required");
        }
        if (TextUtils.isBlank(source.bootstrapServers)) {
            throw new FlinkTableException("source.bootstrapServers is required");
        }
        boolean hasTopics = source.topics != null && !source.topics.isEmpty();
        boolean hasTopicPattern = !TextUtils.isBlank(source.topicPattern);
        if (hasTopics == hasTopicPattern) {
            throw new FlinkTableException("source.topics and source.topicPattern must choose exactly one");
        }
        if (TextUtils.isBlank(source.groupId)) {
            throw new FlinkTableException("source.groupId is required");
        }
        validateStartingOffsets(source.startingOffsets);
    }

    private void validateStartingOffsets(String startingOffsets) {
        String value = TextUtils.upper(startingOffsets, "GROUP-OFFSETS").replace('_', '-');
        Set<String> supported = Set.of("EARLIEST", "LATEST", "GROUP-OFFSETS", "TIMESTAMP");
        if (!supported.contains(value)) {
            throw new FlinkTableException("Unsupported source.startingOffsets: " + startingOffsets);
        }
    }

    private void validateFlinkConfig(Map<String, String> flinkConfig) {
        if (flinkConfig == null || flinkConfig.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : flinkConfig.entrySet()) {
            if (TextUtils.isBlank(entry.getKey())) {
                throw new FlinkTableException("flinkConfig key must not be blank");
            }
            if (entry.getValue() == null) {
                throw new FlinkTableException("flinkConfig." + entry.getKey() + " must not be null");
            }
        }
    }

    private void validateSink(PaimonSinkConfig sink) {
        if (sink == null) {
            throw new FlinkTableException("sink is required");
        }
        LoadMode.parse(sink.loadMode, LoadMode.APPEND);
        SchemaMismatchPolicy.parse(sink.schemaMismatchPolicy);
        RecordErrorPolicy.parse(sink.recordErrorPolicy);
        Map<String, String> options = sink.options;
        if (options == null || options.isEmpty()) {
            throw new FlinkTableException("sink.options is required");
        }
        if (TextUtils.isBlank(options.get("warehouse"))) {
            throw new FlinkTableException("sink.options.warehouse is required");
        }
        if (sink.tables == null || sink.tables.isEmpty()) {
            throw new FlinkTableException("sink.tables is required");
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
            if (!staticNames.add(table.table.database.asText() + "." + tableName.toLowerCase())) {
                throw new FlinkTableException("Duplicate static sink tableName: " + tableName);
            }
        }
        if (enabledCount == 0) {
            throw new FlinkTableException("sink.tables has no enabled table");
        }
    }

    private void validateTableConfig(TableConfig table) {
        if (table == null) {
            throw new FlinkTableException("sink.tables[].table is required");
        }
        validateRequiredStaticString(table.database, "sink.tables[].table.database");
        validateRequiredStaticString(table.name, "sink.tables[].table.name");
        validateExpression(table.comment, JsonType.STRING, false, "sink.tables[].table.comment");
        validateExpression(table.createIfNotExists, JsonType.BOOLEAN, false, "sink.tables[].table.createIfNotExists");
        validateExpression(table.partitionKeys, JsonType.ARRAY, false, "sink.tables[].table.partitionKeys");
    }

    private void validateColumns(PaimonTableConfig table) {
        if (table.columns == null) {
            return;
        }
        // 一旦 job.json 配置了 columns[]，就表示使用 job 里的完整表字段定义，
        // 而不是和 Kafka schema.columns 做局部拼接。
        Set<String> names = new HashSet<>();
        for (ColumnConfig column : table.columns) {
            if (column == null || TextUtils.isBlank(column.name)) {
                throw new FlinkTableException("sink.tables[].columns[].name is required");
            }
            if (!names.add(column.name.toLowerCase())) {
                throw new FlinkTableException("Duplicate sink column: " + column.name);
            }
            validateExpression(column.value, inferJsonType(column), false, "sink.tables[].columns[].value");
            if (column.length != null && column.length <= 0) {
                throw new FlinkTableException("sink.tables[].columns[].length must be greater than 0");
            }
            if (column.precision != null && column.precision <= 0) {
                throw new FlinkTableException("sink.tables[].columns[].precision must be greater than 0");
            }
            if (column.scale != null && column.scale < 0) {
                throw new FlinkTableException("sink.tables[].columns[].scale must not be less than 0");
            }
        }
        validateReservedColumns(table, names);
    }

    private void validateReservedColumns(PaimonTableConfig table, Set<String> columnNames) {
        if (isProxyPrimaryKey(table.table.primaryKeys) && columnNames.contains(SystemFieldNames.PROXY_PRIMARY_KEY_FIELD)) {
            throw new FlinkTableException("Reserved proxy primary key column conflicts with sink column: "
                    + SystemFieldNames.PROXY_PRIMARY_KEY_FIELD);
        }
        if (!Boolean.TRUE.equals(table.table.includeKafkaMetadataFields)) {
            return;
        }
        validateReservedColumn(columnNames, SystemFieldNames.KAFKA_TOPIC_FIELD);
        validateReservedColumn(columnNames, SystemFieldNames.KAFKA_PARTITION_FIELD);
        validateReservedColumn(columnNames, SystemFieldNames.KAFKA_OFFSET_FIELD);
    }

    private void validateReservedColumn(Set<String> columnNames, String fieldName) {
        if (columnNames.contains(fieldName)) {
            throw new FlinkTableException("Reserved Kafka metadata column conflicts with sink column: " + fieldName);
        }
    }

    private boolean isProxyPrimaryKey(PrimaryKeyConfig primaryKey) {
        return primaryKey != null && PrimaryKeyMode.parse(primaryKey.mode) == PrimaryKeyMode.PROXY;
    }

    private void validatePrimaryKey(TableConfig table, LoadMode loadMode) {
        if (table == null) {
            return;
        }
        if (PaimonTableConfigRules.hasJobTableMetadata(table)) {
            if (table.createIfNotExists == null) {
                throw new FlinkTableException(
                        "sink.tables[].table.createIfNotExists is required when job table metadata override is enabled");
            }
            if (table.partitionKeys == null) {
                throw new FlinkTableException(
                        "sink.tables[].table.partitionKeys is required when job table metadata override is enabled");
            }
        }
        if (table.primaryKeys == null) {
            if (PaimonTableConfigRules.hasJobTableMetadata(table) && loadMode == LoadMode.UPSERT) {
                throw new FlinkTableException(
                        "sink.tables[].table.primaryKeys is required for UPSERT when job table metadata override is enabled");
            }
            return;
        }
        PrimaryKeyMode mode = PrimaryKeyMode.parse(table.primaryKeys.mode);
        validatePrimaryKeyExpression(table.primaryKeys);
        if (mode == PrimaryKeyMode.PROXY) {
            ProxyPrimaryKeyType.parse(table.primaryKeys.algorithm);
        }
        if (mode == PrimaryKeyMode.FIELDS && loadMode == LoadMode.UPSERT && table.primaryKeys.defaultValue == null
                && TextUtils.isBlank(table.primaryKeys.path)) {
            throw new FlinkTableException("sink.tables[].table.primaryKeys.path or defaultValue is required for UPSERT");
        }
    }

    private void validatePrimaryKeyExpression(PrimaryKeyConfig primaryKey) {
        JsonType.parse(primaryKey.jsonType);
        if (!"ARRAY".equals(TextUtils.upper(primaryKey.jsonType, "ARRAY"))) {
            throw new FlinkTableException("sink.tables[].table.primaryKeys.jsonType must be ARRAY");
        }
    }

    private void validateWriter(WriterConfig writer) {
        if (writer == null) {
            return;
        }
        if (writer.batchSize != null && writer.batchSize <= 0) {
            throw new FlinkTableException("sink.writer.batchSize must be greater than 0");
        }
        if (writer.flushIntervalMs != null && writer.flushIntervalMs <= 0) {
            throw new FlinkTableException("sink.writer.flushIntervalMs must be greater than 0");
        }
        if (writer.maxOpenWriters != null && writer.maxOpenWriters <= 0) {
            throw new FlinkTableException("sink.writer.maxOpenWriters must be greater than 0");
        }
    }

    private void validateExpression(com.fasterxml.jackson.databind.JsonNode node, JsonType defaultType, boolean textAsPath, String name) {
        try {
            JsonType.parse(ExpressionSpecNormalizer.path(node, defaultType, null).jsonType);
        } catch (RuntimeException e) {
            throw new FlinkTableException("Invalid expression config: " + name + ", reason=" + e.getMessage(), e);
        }
        if (node == null && !textAsPath) {
            return;
        }
        if (node == null && textAsPath) {
            throw new FlinkTableException(name + " is required");
        }
    }

    private void validateRequiredExpression(com.fasterxml.jackson.databind.JsonNode node, String name) {
        if (node == null) {
            throw new FlinkTableException(name + " is required");
        }
    }

    private void validateRequiredStaticString(com.fasterxml.jackson.databind.JsonNode node, String name) {
        validateRequiredExpression(node, name);
        if (!node.isTextual() || TextUtils.isBlank(node.asText())) {
            throw new FlinkTableException(name + " must be static string");
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
