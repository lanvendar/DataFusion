package com.datafusion.plugin.flink.schema.paimon.message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 消息 schema 节点.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class MessageSchema implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 表定义.
     */
    public TableConfig table = new TableConfig();

    /**
     * 字段定义.
     */
    public List<ColumnConfig> columns = new ArrayList<>();
}
