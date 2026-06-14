package com.datafusion.plugin.kafka.json.sink;

import org.apache.paimon.table.Table;
import org.apache.paimon.types.DataField;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Paimon 表结构快照.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonTableSchemaSnapshot {

    /**
     * 字段映射.
     */
    private final Map<String, DataField> fields;

    /**
     * 主键字段.
     */
    private final List<String> primaryKeys;

    /**
     * 分区字段.
     */
    private final List<String> partitionKeys;

    /**
     * 表 options.
     */
    private final Map<String, String> options;

    /**
     * 表注释.
     */
    private final String comment;

    /**
     * 构造表结构快照.
     *
     * @param table Paimon 表
     */
    public PaimonTableSchemaSnapshot(Table table) {
        this.fields = table.rowType().getFields().stream()
                .collect(Collectors.toMap(field -> field.name().toLowerCase(Locale.ROOT), field -> field));
        this.primaryKeys = new ArrayList<>(table.primaryKeys());
        this.partitionKeys = new ArrayList<>(table.partitionKeys());
        this.options = new LinkedHashMap<>(table.options());
        this.comment = table.comment().orElse(null);
    }

    /**
     * 获取字段映射.
     *
     * @return 字段映射
     */
    public Map<String, DataField> fields() {
        return fields;
    }

    /**
     * 获取主键字段.
     *
     * @return 主键字段
     */
    public List<String> primaryKeys() {
        return primaryKeys;
    }

    /**
     * 获取分区字段.
     *
     * @return 分区字段
     */
    public List<String> partitionKeys() {
        return partitionKeys;
    }

    /**
     * 获取表 options.
     *
     * @return 表 options
     */
    public Map<String, String> options() {
        return options;
    }

    /**
     * 获取表注释.
     *
     * @return 表注释
     */
    public String comment() {
        return comment;
    }
}
