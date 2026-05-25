package com.datafusion.manager.asset.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DruidMetaDataServiceImpl implements MetadataService {

    /**
     * schema名称.
     */
    private final Map<String, Map<String, String>> registeredTables = new HashMap<>();

    /**
     * 注册表.
     *
     * @param tableName 表名,schema.tableName || tableName
     * @param columnsWithTypes 字段信息
     */
    public void registerTable(String tableName, Map<String, String> columnsWithTypes) {
        registeredTables.put(tableName, columnsWithTypes);
    }

    @Override
    public boolean tableExists(String tableName) {
        return registeredTables.get(tableName) == null ? false : true;
    }

    @Override
    public List<String> getColumns(String tableName) {
        Map<String, String> columns = registeredTables.get(tableName);
        return columns != null ? new ArrayList<>(columns.keySet()) : null;
    }

    @Override
    public String getColumnDataType(String tableName, String columnName) {
        Map<String, String> columns = registeredTables.get(tableName);
        return columns != null ? columns.get(columnName.toLowerCase()) : null;
    }
}
