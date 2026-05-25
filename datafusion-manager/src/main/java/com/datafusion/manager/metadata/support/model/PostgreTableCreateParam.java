package com.datafusion.manager.metadata.support.model;

import lombok.Data;

import java.util.List;
import java.util.Properties;

/**
 * .
 *
 * @author zyw
 * @version 1.0.0 , 2025/9/18
 * @since 2025/9/18
 */
@Data
public class PostgreTableCreateParam {
    
    /**
     * 表名称.
     */
    private String tableName;
    
    /**
     * 表描述.
     */
    private String tableDesc;
    
    /**
     * 字段定义.
     */
    private List<TableColumnInfo> columnInfos;
    
    /**
     * 主键字段.
     */
    private String primaryKeys;
    
    /**
     * 表属性.
     */
    private Properties tableProperties;
}
