package com.datafusion.plugin.flink.schema.paimon.message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka schema 表定义.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class TableConfig implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 表名.
     */
    public String name;

    /**
     * 表注释.
     */
    public String comment;

    /**
     * 是否允许自动建表.
     */
    public Boolean createIfNotExists = true;

    /**
     * 主键字段.
     */
    public List<String> primaryKeys = new ArrayList<>();

    /**
     * 分区字段.
     */
    public List<String> partitionKeys = new ArrayList<>();

    /**
     * 复制表定义.
     *
     * @return 表定义副本
     */
    public TableConfig copy() {
        TableConfig copy = new TableConfig();
        copy.name = name;
        copy.comment = comment;
        copy.createIfNotExists = createIfNotExists;
        copy.primaryKeys = primaryKeys == null ? new ArrayList<>() : new ArrayList<>(primaryKeys);
        copy.partitionKeys = partitionKeys == null ? new ArrayList<>() : new ArrayList<>(partitionKeys);
        return copy;
    }
}
