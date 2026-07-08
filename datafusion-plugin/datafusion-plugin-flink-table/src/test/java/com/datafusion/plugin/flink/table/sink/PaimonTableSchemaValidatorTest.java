package com.datafusion.plugin.flink.table.sink;

import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.ColumnConfig;
import com.datafusion.plugin.flink.table.config.PaimonTableConfig;
import com.datafusion.plugin.flink.table.core.PaimonSchemaMismatchException;
import com.datafusion.plugin.flink.table.core.enums.LoadMode;
import com.datafusion.plugin.flink.table.core.enums.PrimaryKeyMode;
import com.datafusion.plugin.flink.table.resolve.ProxyPrimaryKeyGenerator;
import org.apache.paimon.types.DataField;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paimon 表结构校验器单元测试.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
class PaimonTableSchemaValidatorTest {

    /**
     * UPSERT + PROXY 只要求真实表存在代理主键字段，并校验分区键和字段结构.
     */
    @Test
    void shouldValidateUpsertProxyByGeneratedIdFieldAndPartitionKeys() {
        PaimonTableConfig config = tableConfig(LoadMode.UPSERT, PrimaryKeyMode.PROXY,
                List.of(ProxyPrimaryKeyGenerator.FIELD_NAME, "day_pt"), List.of("day_pt"), proxyColumns());
        PaimonTableSchemaSnapshot snapshot = snapshot(config.columns, List.of("another_key"), List.of("day_pt"));

        Assertions.assertDoesNotThrow(() -> PaimonTableSchemaValidator.validate(config, snapshot));
    }

    /**
     * UPSERT + FIELDS 要求主键字段非空.
     */
    @Test
    void shouldRejectUpsertFieldsWhenPrimaryKeysEmpty() {
        PaimonTableConfig config = tableConfig(LoadMode.UPSERT, PrimaryKeyMode.FIELDS, List.of(), List.of("day_pt"),
                normalColumns());
        PaimonTableSchemaSnapshot snapshot = snapshot(config.columns, List.of(), List.of("day_pt"));

        Assertions.assertThrows(PaimonSchemaMismatchException.class,
                () -> PaimonTableSchemaValidator.validate(config, snapshot));
    }

    /**
     * APPEND + FIELDS 不比对主键，但仍校验分区键和字段结构.
     */
    @Test
    void shouldIgnorePrimaryKeysForAppendFields() {
        PaimonTableConfig config = tableConfig(LoadMode.APPEND, PrimaryKeyMode.FIELDS, List.of(), List.of("day_pt"),
                normalColumns());
        PaimonTableSchemaSnapshot snapshot = snapshot(config.columns, List.of("unexpected_key"), List.of("day_pt"));

        Assertions.assertDoesNotThrow(() -> PaimonTableSchemaValidator.validate(config, snapshot));
    }

    /**
     * 四种模式都要求配置分区键非空.
     */
    @Test
    void shouldRejectAppendProxyWhenPartitionKeysEmpty() {
        PaimonTableConfig config = tableConfig(LoadMode.APPEND, PrimaryKeyMode.PROXY,
                List.of(ProxyPrimaryKeyGenerator.FIELD_NAME), List.of(), proxyColumns());
        PaimonTableSchemaSnapshot snapshot = snapshot(config.columns, List.of(), List.of());

        Assertions.assertThrows(PaimonSchemaMismatchException.class,
                () -> PaimonTableSchemaValidator.validate(config, snapshot));
    }

    /**
     * 字段名集合和顺序必须与真实 Paimon 表一致.
     */
    @Test
    void shouldRejectWhenFieldStructureNotMatch() {
        PaimonTableConfig config = tableConfig(LoadMode.APPEND, PrimaryKeyMode.FIELDS, List.of(), List.of("day_pt"),
                normalColumns());
        PaimonTableSchemaSnapshot snapshot = snapshot(List.of(column("today", "VARCHAR", 32, false)), List.of(),
                List.of("day_pt"));

        Assertions.assertThrows(PaimonSchemaMismatchException.class,
                () -> PaimonTableSchemaValidator.validate(config, snapshot));
    }

    private PaimonTableConfig tableConfig(LoadMode loadMode, PrimaryKeyMode primaryKeyMode, List<String> primaryKeys,
                                          List<String> partitionKeys, List<ColumnConfig> columns) {
        PaimonTableConfig config = new PaimonTableConfig();
        config.database = "dw_dev";
        config.tableName = "ods_test";
        config.loadMode = loadMode;
        config.primaryKeyMode = primaryKeyMode;
        config.primaryKeys = primaryKeys;
        config.partitionKeys = partitionKeys;
        config.columns = columns;
        return config;
    }

    private List<ColumnConfig> proxyColumns() {
        return List.of(column(ProxyPrimaryKeyGenerator.FIELD_NAME, "VARCHAR", 64, false),
                column("today", "VARCHAR", 32, false), column("day_pt", "DATE", null, false));
    }

    private List<ColumnConfig> normalColumns() {
        return List.of(column("today", "VARCHAR", 32, false), column("day_pt", "DATE", null, false));
    }

    private ColumnConfig column(String name, String dataType, Integer length, boolean nullable) {
        ColumnConfig column = new ColumnConfig();
        column.name = name;
        column.dataType = dataType;
        column.length = length;
        column.nullable = nullable;
        return column;
    }

    private PaimonTableSchemaSnapshot snapshot(List<ColumnConfig> columns, List<String> primaryKeys, List<String> partitionKeys) {
        Map<String, DataField> fields = new LinkedHashMap<>();
        int id = 0;
        for (ColumnConfig column : columns) {
            fields.put(column.name.toLowerCase(Locale.ROOT),
                    new DataField(id++, column.name, PaimonTableSchemaValidator.paimonType(column), column.comment));
        }
        return new PaimonTableSchemaSnapshot(fields, primaryKeys, partitionKeys, Map.of(), null);
    }
}
