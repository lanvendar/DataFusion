package com.datafusion.manager.metadata.support.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 数据表创建结果.
 *
 * @author david
 * @version 3.6.4, 2024/9/9
 * @since 3.6.4, 2024/9/9
 */
@Data
@Accessors(chain = true)
public class TableCreated {

    /**
     * 表名称.
     */
    private String tableName;

    /**
     * 建表结果.
     */
    private boolean success;

    /**
     * 信息.
     */
    private String message;
}
