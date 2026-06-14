package com.datafusion.plugin.kafka.json.sink;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PaimonTableConfig;
import com.datafusion.plugin.kafka.json.core.PaimonSchemaMismatchException;
import com.datafusion.plugin.kafka.json.core.enums.SchemaMismatchPolicy;
import com.datafusion.plugin.kafka.json.expression.ExpressionSpecNormalizer;
import com.datafusion.plugin.kafka.json.expression.JsonType;
import com.datafusion.plugin.kafka.json.resolve.ResolvedTableWritePlan;
import com.datafusion.plugin.kafka.json.util.TextUtils;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
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
     * Paimon 真实表结构缓存.
     */
    private final Map<String, PaimonTableSchemaSnapshot> snapshots = new LinkedHashMap<>();

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
            throw new PaimonSchemaMismatchException("Failed to preload Paimon table schemas: " + e.getMessage());
        }
    }

    private void load(Catalog catalog, PaimonTableConfig table) throws Exception {
        String database = staticString(table.database);
        String tableName = staticString(table.tableName);
        if (TextUtils.isBlank(database) || TextUtils.isBlank(tableName)) {
            LOGGER.info("Paimon table schema preload skipped because table name is dynamic, database={}, tableName={}",
                    database, tableName);
            return;
        }
        Identifier identifier = Identifier.create(database, tableName);
        try {
            snapshots.put(database + "." + tableName, new PaimonTableSchemaSnapshot(catalog.getTable(identifier)));
            LOGGER.info("Paimon table schema preloaded, identifier={}", identifier);
        } catch (Catalog.TableNotExistException e) {
            LOGGER.info("Paimon table schema preload skipped because table does not exist, identifier={}", identifier);
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
