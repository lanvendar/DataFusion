package com.datafusion.plugin.flink.schema.paimon.resolve;

import com.datafusion.plugin.flink.schema.paimon.core.enums.LoadMode;
import com.datafusion.plugin.flink.schema.paimon.message.ColumnConfig;
import com.datafusion.plugin.flink.schema.paimon.message.TableConfig;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析后的目标表配置.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ResolvedTableConfig implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * database 名称.
     */
    public String database;

    /**
     * 写入模式.
     */
    public LoadMode loadMode;

    /**
     * 表定义.
     */
    public TableConfig table = new TableConfig();

    /**
     * 字段定义.
     */
    public List<ColumnConfig> columns = new ArrayList<>();

    /**
     * Paimon 表级 options.
     */
    public Map<String, String> options = new LinkedHashMap<>();

    /**
     * 表标识符.
     *
     * @return database.table
     */
    public String identifier() {
        return database + "." + table.name;
    }
}
