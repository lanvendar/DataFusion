package com.datafusion.manager.metadata.support.model;

import cn.hutool.core.util.StrUtil;
import com.datafusion.manager.metadata.enums.StarrocksTableModel;
import lombok.Data;

import java.util.List;

/**
 * Hive建表参数.
 *
 * @author david
 * @version 3.6.4, 2024/9/14
 * @since 3.6.4, 2024/9/14
 */
@Data
public class StarrocksTableCreateParam {

    /**
     * 表名称.
     */
    private String tableName;

    /**
     * 表描述.
     */
    private String tableDesc;

    /**
     * 表模型.
     */
    private String tableModel;

    /**
     * set tableMode的时候,初始化建表语句的key.
     *
     * @param tableModel 建表模型
     */
    public void setTableModel(String tableModel) {
        this.tableModel = tableModel;
        if (StrUtil.isNotEmpty(tableModel)) {
            this.createKey = StarrocksTableModel.getByModelType(tableModel).getCreateKey();
        }
    }

    /**
     * 表模型.
     */
    private String createKey;

    /**
     * 分区键.
     */
    private String partitionKeys;

    /**
     * 字段定义.
     */
    private List<TableColumnInfo> columnInfos;

    /**
     * 分区字段.
     */
    private String primaryKeys;
    
    /**
     * 分布字段.
     */
    private String bucketKeys;
    
    /**
     * 分桶数量.
     */
    private Integer bucketNum;
}
