package com.datafusion.plugin.kafka.json.sink;

import org.apache.paimon.table.sink.CommitMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Paimon 单表待提交消息.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonCommittable implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * database 名称.
     */
    public String database;

    /**
     * 表名.
     */
    public String tableName;

    /**
     * 提交编号.
     */
    public long commitIdentifier;

    /**
     * Paimon commit messages.
     */
    public List<CommitMessage> commitMessages = new ArrayList<>();

    /**
     * 表标识符.
     *
     * @return database.table
     */
    public String identifier() {
        return database + "." + tableName;
    }
}
