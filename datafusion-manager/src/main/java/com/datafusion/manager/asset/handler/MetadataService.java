package com.datafusion.manager.asset.handler;

import java.util.List;

public interface MetadataService {
    /**
     * 检查表是否存在.
     * @param tableName 表名,如果有schema名,则是全名
     * @return 如果表存在则返回true
     */
    boolean tableExists(String tableName);

    /**
     * 获取表的列信息.
     * @param tableName 表名
     * @return 列名列表，如果表不存在则返回null或空列表
     */
    List<String> getColumns(String tableName);

    /**
     * 获取指定列的数据类型.
     * @param tableName 表名
     * @param columnName 列名
     * @return 列的数据类型
     */
    String getColumnDataType(String tableName, String columnName);
    
}