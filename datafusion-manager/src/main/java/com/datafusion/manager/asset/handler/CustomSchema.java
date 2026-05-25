package com.datafusion.manager.asset.handler;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义schema.
 *
 * @author zyw
 * @version 1.0.0 , 2025/10/10
 * @since 2025/10/10
 */
public class CustomSchema extends AbstractSchema {
    
    /**
     * schema名称.
     */
    private final String schemaName;
    
    /**
     * 表存储map.
     */
    private final Map<String, Table> tableMap;
    
    /**
     * 构造函数.
     *
     * @param schemaName schema名称
     */
    public CustomSchema(String schemaName) {
        this.schemaName = schemaName;
        this.tableMap = new HashMap<>();
    }
    
    /**
     * 添加表.
     *
     * @param tableName 表名
     * @param table     表对象
     */
    public void addTable(String tableName, Table table) {
        tableMap.put(tableName.toUpperCase(), table);
    }
    
    @Override
    protected Map<String, Table> getTableMap() {
        return tableMap;
    }
    
    @Override
    public String toString() {
        return schemaName;
    }
}
