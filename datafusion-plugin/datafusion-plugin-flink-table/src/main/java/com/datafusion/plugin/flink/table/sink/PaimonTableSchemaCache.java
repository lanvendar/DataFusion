package com.datafusion.plugin.flink.table.sink;

import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.ColumnConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonTableConfig;
import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.flink.table.core.PaimonSchemaMismatchException;
import com.datafusion.plugin.flink.table.core.SystemFieldNames;
import com.datafusion.plugin.flink.table.core.enums.JsonType;
import com.datafusion.plugin.flink.table.core.enums.PaimonTableSchemaStatus;
import com.datafusion.plugin.flink.table.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.flink.table.core.enums.ProxyPrimaryKeyType;
import com.datafusion.plugin.flink.table.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.flink.table.expression.ExpressionEvaluator;
import com.datafusion.plugin.flink.table.expression.ExpressionSpec;
import com.datafusion.plugin.flink.table.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.flink.table.resolve.ProxyPrimaryKeyGenerator;
import com.datafusion.plugin.flink.table.resolve.ResolvedTableWritePlan;
import com.datafusion.plugin.flink.table.util.TextUtils;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.types.DataField;
import org.apache.paimon.types.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paimon 真实表结构缓存.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonTableSchemaCache {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonTableSchemaCache.class);

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * 表结构不匹配处理策略.
     */
    private final SchemaMismatchPolicy schemaMismatchPolicy;

    /**
     * 表达式执行器.
     */
    private final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

    /**
     * Paimon 真实表结构缓存.
     */
    private final Map<String, PaimonTableSchemaSnapshot> snapshots = new LinkedHashMap<>();

    /**
     * Paimon 表结构状态缓存.
     */
    private final Map<String, PaimonTableSchemaStatus> statuses = new LinkedHashMap<>();

    /**
     * 构造表结构缓存.
     *
     * @param sink sink 配置
     * @param schemaMismatchPolicy 表结构不匹配处理策略
     */
    public PaimonTableSchemaCache(PaimonSinkConfig sink, SchemaMismatchPolicy schemaMismatchPolicy) {
        this.sink = sink;
        this.schemaMismatchPolicy = schemaMismatchPolicy;
        preload();
    }

    /**
     * 缓存表结构快照.
     *
     * @param identifier 表标识
     * @param snapshot 表结构快照
     */
    public void put(String identifier, PaimonTableSchemaSnapshot snapshot) {
        snapshots.put(identifier, snapshot);
        statuses.put(identifier, PaimonTableSchemaStatus.EXISTS);
    }

    /**
     * 获取表结构状态.
     *
     * @param identifier 表标识
     * @return 表结构状态
     */
    public PaimonTableSchemaStatus status(String identifier) {
        return statuses.get(identifier);
    }

    /**
     * 校验写入计划的 schema 是否兼容.
     *
     * @param plan 写入计划
     * @return 是否兼容
     */
    public boolean validate(ResolvedTableWritePlan plan) {
        PaimonTableSchemaSnapshot snapshot = snapshots.get(plan.tableConfig.identifier());
        if (snapshot == null) {
            return statuses.get(plan.tableConfig.identifier()) == PaimonTableSchemaStatus.MISSING_CONFIGURED;
        }
        if (applyExistingSchemaWhenColumnsOmitted(plan, snapshot)) {
            return true;
        }
        try {
            PaimonTableSchemaValidator.validate(plan.tableConfig, snapshot);
            return true;
        } catch (PaimonSchemaMismatchException e) {
            if (schemaMismatchPolicy == SchemaMismatchPolicy.FAIL) {
                throw e;
            }
            LOGGER.warn("Skip Paimon records because table schema is incompatible, "
                            + "identifier={}, topic={}, partition={}, offset={}, records={}, reason={}",
                    plan.tableConfig.identifier(), plan.topic, plan.partition, plan.offset, plan.records.size(), e.getMessage());
            return false;
        }
    }

    private boolean applyExistingSchemaWhenColumnsOmitted(ResolvedTableWritePlan plan, PaimonTableSchemaSnapshot snapshot) {
        if (hasUserColumns(plan)) {
            return false;
        }
        plan.tableConfig.columns = snapshot.fields().values().stream()
                .map(this::snapshotColumn)
                .toList();
        plan.tableConfig.primaryKeys = new ArrayList<>(snapshot.primaryKeys());
        plan.tableConfig.partitionKeys = new ArrayList<>(snapshot.partitionKeys());
        plan.records = remapRecords(plan, snapshot);
        LOGGER.info("Use existing Paimon table schema as write schema, identifier={}, fields={}",
                plan.tableConfig.identifier(), plan.tableConfig.columns.size());
        return true;
    }

    private boolean hasUserColumns(ResolvedTableWritePlan plan) {
        if (plan.tableConfig.columns == null || plan.tableConfig.columns.isEmpty()) {
            return false;
        }
        return plan.tableConfig.columns.stream().anyMatch(column -> !isGeneratedColumn(column.name));
    }

    private boolean isGeneratedColumn(String name) {
        return SystemFieldNames.PROXY_PRIMARY_KEY_FIELD.equals(name)
                || SystemFieldNames.KAFKA_TOPIC_FIELD.equals(name)
                || SystemFieldNames.KAFKA_PARTITION_FIELD.equals(name)
                || SystemFieldNames.KAFKA_OFFSET_FIELD.equals(name);
    }

    private ColumnConfig snapshotColumn(DataField field) {
        ColumnConfig column = new ColumnConfig();
        column.name = field.name();
        column.dataType = normalizedType(field.type());
        column.nullable = field.type().isNullable();
        column.comment = field.description();
        return column;
    }

    private String normalizedType(DataType type) {
        String sqlType = type.asSQLString().toUpperCase(Locale.ROOT);
        if (sqlType.startsWith("VARCHAR")) {
            return "VARCHAR";
        }
        if (sqlType.startsWith("CHAR")) {
            return "STRING";
        }
        if (sqlType.startsWith("TIMESTAMP")) {
            return "TIMESTAMP";
        }
        if (sqlType.startsWith("DECIMAL")) {
            return "DECIMAL";
        }
        return sqlType;
    }

    private List<Map<String, Object>> remapRecords(ResolvedTableWritePlan plan, PaimonTableSchemaSnapshot snapshot) {
        List<Map<String, Object>> sourceRecords = plan.sourceRecords == null || plan.sourceRecords.isEmpty()
                ? plan.records : plan.sourceRecords;
        List<Map<String, Object>> remapped = new ArrayList<>();
        for (Map<String, Object> source : sourceRecords) {
            Map<String, Object> target = new LinkedHashMap<>();
            for (DataField field : snapshot.fields().values()) {
                target.put(field.name(), snapshotValue(plan, source, field.name()));
            }
            appendProxyPrimaryKeyIfNeeded(plan, target, source, snapshot);
            remapped.add(target);
        }
        return remapped;
    }

    private Object snapshotValue(ResolvedTableWritePlan plan, Map<String, Object> source, String fieldName) {
        if (Boolean.TRUE.equals(plan.tableConfig.includeKafkaMetadataFields)) {
            if (SystemFieldNames.KAFKA_TOPIC_FIELD.equals(fieldName)) {
                return plan.topic;
            }
            if (SystemFieldNames.KAFKA_PARTITION_FIELD.equals(fieldName)) {
                return plan.partition;
            }
            if (SystemFieldNames.KAFKA_OFFSET_FIELD.equals(fieldName)) {
                return plan.offset;
            }
        }
        return source == null ? null : source.get(fieldName);
    }

    private void appendProxyPrimaryKeyIfNeeded(ResolvedTableWritePlan plan, Map<String, Object> target, Map<String, Object> source,
            PaimonTableSchemaSnapshot snapshot) {
        PrimaryKeyConfig primaryKey = plan.tableConfig.primaryKeysConfig;
        if (primaryKey == null || PrimaryKeyMode.parse(primaryKey.mode) != PrimaryKeyMode.PROXY) {
            return;
        }
        if (!snapshot.fields().containsKey(SystemFieldNames.PROXY_PRIMARY_KEY_FIELD.toLowerCase(Locale.ROOT))) {
            return;
        }
        List<String> fields = stringList(expressionEvaluator.evaluate(source, primaryKeySpec(primaryKey),
                "sink.tables[].table.primaryKeys"), "sink.tables[].table.primaryKeys");
        ProxyPrimaryKeyType type = ProxyPrimaryKeyType.parse(primaryKey.algorithm);
        target.put(SystemFieldNames.PROXY_PRIMARY_KEY_FIELD, ProxyPrimaryKeyGenerator.generate(target, fields, type));
    }

    private ExpressionSpec primaryKeySpec(PrimaryKeyConfig primaryKey) {
        ExpressionSpec spec = new ExpressionSpec();
        spec.path = primaryKey.path;
        spec.defaultValue = primaryKey.defaultValue;
        spec.jsonType = "ARRAY";
        return spec;
    }

    private List<String> stringList(Object value, String name) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new PaimonSchemaMismatchException(name + " must be string array");
        }
        List<String> values = new ArrayList<>();
        for (Object item : collection) {
            if (item == null || TextUtils.isBlank(String.valueOf(item))) {
                throw new PaimonSchemaMismatchException(name + " must not contain blank item");
            }
            values.add(String.valueOf(item));
        }
        return values;
    }

    private void preload() {
        try (Catalog catalog = CatalogFactory.createCatalog(CatalogContext.create(
                PaimonTableSchemaValidator.catalogOptions(sink.globalOptions())))) {
            for (PaimonTableConfig table : sink.tables) {
                if (isDisabled(table)) {
                    continue;
                }
                load(catalog, table);
            }
        } catch (Exception e) {
            throw new PaimonSchemaMismatchException("Failed to preload Paimon table schemas", e);
        }
    }

    private void load(Catalog catalog, PaimonTableConfig table) throws Exception {
        String database = staticString(table.table.database);
        String tableName = staticString(table.table.name);
        if (TextUtils.isBlank(database) || TextUtils.isBlank(tableName)) {
            LOGGER.info("Paimon table schema preload skipped because table name is dynamic, database={}, tableName={}",
                    database, tableName);
            return;
        }
        Identifier identifier = Identifier.create(database, tableName);
        try {
            put(database + "." + tableName, new PaimonTableSchemaSnapshot(catalog.getTable(identifier)));
            LOGGER.info("Paimon table schema preloaded, identifier={}", identifier);
        } catch (Catalog.TableNotExistException e) {
            statuses.put(database + "." + tableName, PaimonTableSchemaStatus.MISSING_CONFIGURED);
            LOGGER.info("Paimon table schema marked missing because table does not exist, identifier={}", identifier);
        }
    }

    private boolean isDisabled(PaimonTableConfig table) {
        return table == null || Boolean.FALSE.equals(table.enabled);
    }

    private String staticString(com.fasterxml.jackson.databind.JsonNode node) {
        Object value = ExpressionSpecNormalizer.constant(node, JsonType.STRING).defaultValue;
        return value instanceof String text ? text : null;
    }
}
