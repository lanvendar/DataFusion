package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.core.PaimonSchemaMismatchException;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableConfig;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.paimon.options.Options;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.DataType;
import org.apache.paimon.types.DataTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Paimon 表结构校验器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PaimonTableSchemaValidator {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonTableSchemaValidator.class);

    private PaimonTableSchemaValidator() {
    }

    /**
     * 校验 Kafka JSON schema 和 Paimon 真实表结构是否兼容.
     *
     * @param tableConfig Kafka JSON schema 解析后的目标表配置
     * @param snapshot Paimon 真实表结构快照
     */
    public static void validate(ResolvedTableConfig tableConfig, PaimonTableSchemaSnapshot snapshot) {
        List<String> actualFieldNames = new ArrayList<>(snapshot.fields().keySet());
        List<String> configuredFieldNames = tableConfig.columns.stream()
                .map(column -> column.name.toLowerCase(Locale.ROOT))
                .toList();
        if (!Objects.equals(actualFieldNames, configuredFieldNames)) {
            throw new PaimonSchemaMismatchException("Paimon field names not match json schema: " + tableConfig.identifier()
                    + ", actualPaimon=" + actualFieldNames + ", jsonSchema=" + configuredFieldNames);
        }
        for (ColumnConfig configured : tableConfig.columns) {
            DataField actual = snapshot.fields().get(configured.name.toLowerCase(Locale.ROOT));
            if (actual == null) {
                throw new PaimonSchemaMismatchException("Paimon table lacks configured field: " + configured.name);
            }
            DataType expected = paimonType(configured);
            if (!actual.type().equalsIgnoreNullable(expected)) {
                throw new PaimonSchemaMismatchException("Paimon field type mismatch: " + configured.name
                        + ", expected=" + expected.asSQLString() + ", actual=" + actual.type().asSQLString());
            }
            warnCommentMismatch(configured.name, configured.comment, actual.description());
        }
        validatePrimaryKeys(tableConfig, snapshot);
        validatePartitionKeys(tableConfig, snapshot);
        validateTableOptions(tableConfig, snapshot);
        warnCommentMismatch(tableConfig.identifier(), tableConfig.tableComment, snapshot.comment());
    }

    /**
     * 转换 catalog options.
     *
     * @param catalogOptions catalog 配置
     * @return Paimon options
     */
    public static Options catalogOptions(Map<String, String> catalogOptions) {
        Map<String, String> options = new LinkedHashMap<>(catalogOptions);
        if (TextUtils.isBlank(options.get("type"))) {
            options.put("type", options.getOrDefault("catalogType", "filesystem"));
        }
        return Options.fromMap(options);
    }

    /**
     * 获取 Paimon 表 options.
     *
     * @param tableConfig 目标表配置
     * @return Paimon 表 options
     */
    public static Map<String, String> tableOptions(ResolvedTableConfig tableConfig) {
        Map<String, String> options = new LinkedHashMap<>(tableConfig.options);
        options.keySet().removeIf(PaimonTableSchemaValidator::isConnectionOption);
        return options;
    }

    /**
     * 获取 Paimon 字段类型.
     *
     * @param field 字段配置
     * @return Paimon 字段类型
     */
    public static DataType paimonType(ColumnConfig field) {
        String type = TextUtils.upper(field.dataType, "STRING");
        DataType dataType;
        if ("STRING".equals(type) || "JSON".equals(type)) {
            dataType = DataTypes.STRING();
        } else if ("VARCHAR".equals(type)) {
            dataType = DataTypes.VARCHAR(field.length == null ? 255 : field.length);
        } else if ("INT".equals(type) || "INTEGER".equals(type)) {
            dataType = DataTypes.INT();
        } else if ("BIGINT".equals(type) || "LONG".equals(type)) {
            dataType = DataTypes.BIGINT();
        } else if ("DOUBLE".equals(type)) {
            dataType = DataTypes.DOUBLE();
        } else if ("FLOAT".equals(type)) {
            dataType = DataTypes.FLOAT();
        } else if ("DECIMAL".equals(type)) {
            dataType = DataTypes.DECIMAL(field.precision == null ? 18 : field.precision, field.scale == null ? 4 : field.scale);
        } else if ("BOOLEAN".equals(type)) {
            dataType = DataTypes.BOOLEAN();
        } else if ("DATE".equals(type)) {
            dataType = DataTypes.DATE();
        } else if ("TIMESTAMP".equals(type) || "DATETIME".equals(type)) {
            dataType = DataTypes.TIMESTAMP();
        } else {
            dataType = DataTypes.STRING();
        }
        return dataType.copy(!Boolean.FALSE.equals(field.nullable));
    }

    private static void validatePrimaryKeys(ResolvedTableConfig tableConfig, PaimonTableSchemaSnapshot snapshot) {
        if (tableConfig.primaryKeyMode == PrimaryKeyMode.PROXY) {
            validateProxyPrimaryKey(tableConfig, snapshot);
            return;
        }
        if (tableConfig.primaryKeyMode != PrimaryKeyMode.FIELDS) {
            return;
        }
        if (tableConfig.loadMode == LoadMode.APPEND) {
            return;
        }
        List<String> jsonPrimaryKeys = safeList(tableConfig.primaryKeys);
        if (jsonPrimaryKeys.isEmpty()) {
            throw new PaimonSchemaMismatchException("FIELDS primary keys must not be empty: " + tableConfig.identifier());
        }
        List<String> actualPrimaryKeys = snapshot.primaryKeys();
        if (!Objects.equals(actualPrimaryKeys, jsonPrimaryKeys)) {
            LOGGER.warn("Paimon primary keys not match json schema, identifier={}, actualPaimon={}, jsonSchema={}",
                    tableConfig.identifier(), actualPrimaryKeys, jsonPrimaryKeys);
            throw new PaimonSchemaMismatchException("Paimon primary keys not match json schema: " + tableConfig.identifier()
                    + ", actualPaimon=" + actualPrimaryKeys + ", jsonSchema=" + jsonPrimaryKeys);
        }
    }

    private static void validatePartitionKeys(ResolvedTableConfig tableConfig, PaimonTableSchemaSnapshot snapshot) {
        List<String> jsonPartitionKeys = safeList(tableConfig.partitionKeys);
        if (jsonPartitionKeys.isEmpty()) {
            throw new PaimonSchemaMismatchException("Paimon partition keys must not be empty: " + tableConfig.identifier());
        }
        List<String> actualPartitionKeys = snapshot.partitionKeys();
        if (!Objects.equals(actualPartitionKeys, jsonPartitionKeys)) {
            LOGGER.warn("Paimon partition keys not match json schema, identifier={}, actualPaimon={}, jsonSchema={}",
                    tableConfig.identifier(), actualPartitionKeys, jsonPartitionKeys);
            throw new PaimonSchemaMismatchException("Paimon partition keys not match json schema: " + tableConfig.identifier()
                    + ", actualPaimon=" + actualPartitionKeys + ", jsonSchema=" + jsonPartitionKeys);
        }
    }

    private static void validateProxyPrimaryKey(ResolvedTableConfig tableConfig, PaimonTableSchemaSnapshot snapshot) {
        if (tableConfig.loadMode != LoadMode.UPSERT) {
            return;
        }
        String proxyField = "_id_";
        if (!snapshot.fields().containsKey(proxyField.toLowerCase(Locale.ROOT))) {
            throw new PaimonSchemaMismatchException("Paimon table lacks proxy primary key field: " + proxyField);
        }
    }

    private static void validateTableOptions(ResolvedTableConfig tableConfig, PaimonTableSchemaSnapshot snapshot) {
        Map<String, String> actual = snapshot.options();
        for (Map.Entry<String, String> entry : tableOptions(tableConfig).entrySet()) {
            String actualValue = actual.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), actualValue)) {
                throw new PaimonSchemaMismatchException("Paimon table option mismatch: " + entry.getKey()
                        + ", expected=" + entry.getValue() + ", actual=" + actualValue);
            }
        }
    }

    private static boolean isConnectionOption(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return "warehouse".equals(normalized)
                || "metastore".equals(normalized)
                || "catalogtype".equals(normalized)
                || "type".equals(normalized)
                || "database".equals(normalized)
                || "endpoint".equals(normalized)
                || normalized.startsWith("s3.")
                || normalized.startsWith("fs.s3a.")
                || normalized.startsWith("hadoop.fs.s3a.");
    }

    private static void warnCommentMismatch(String objectName, String expected, String actual) {
        if (!Objects.equals(emptyToNull(expected), emptyToNull(actual))) {
            LOGGER.warn("Paimon comment mismatch ignored, object={}, expected={}, actual={}", objectName, expected, actual);
        }
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : values;
    }

    private static String emptyToNull(String value) {
        return TextUtils.isBlank(value) ? null : value;
    }
}
