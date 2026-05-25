package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.util.List;

/**
 * 达梦数据库建表实体.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/18
 * @since 2025/9/18
 */
@Data
public class DmTableCreateParam {
    
    /**
     *  tableInfo.
     */
    private TableInfo tableInfo;
    
    /**
     * columnInfos.
     */
    private List<TableColumnInfo> columnInfos;
    
}
