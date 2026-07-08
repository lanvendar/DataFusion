package com.datafusion.plugin.kafka.json.resolve;

import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.ColumnConfig;
import com.datafusion.plugin.kafka.json.config.KafkaJsonPaimonJobConfig.PrimaryKeyConfig;
import com.datafusion.plugin.kafka.json.core.enums.LoadMode;
import com.datafusion.plugin.kafka.json.core.enums.PrimaryKeyMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析后的目标表配置.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ResolvedTableConfig implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * database 名称.
     */
    public String database;

    /**
     * 写入模式.
     */
    public LoadMode loadMode;

    /**
     * 表名.
     */
    public String tableName;

    /**
     * 表注释.
     */
    public String tableComment;

    /**
     * 是否自动建表.
     */
    public Boolean createIfNotExists = true;

    /**
     * 分区字段.
     */
    public List<String> partitionKeys = new ArrayList<>();

    /**
     * 主键字段.
     */
    public List<String> primaryKeys = new ArrayList<>();

    /**
     * 主键模式.
     */
    public PrimaryKeyMode primaryKeyMode;

    /**
     * 主键配置.
     */
    public PrimaryKeyConfig primaryKeysConfig;

    /**
     * 是否补充 Kafka 元数据字段.
     */
    public Boolean includeKafkaMetadataFields = false;

    /**
     * 字段定义.
     */
    public List<ColumnConfig> columns = new ArrayList<>();

    /**
     * Paimon 表级 options.
     */
    public Map<String, String> options = new LinkedHashMap<>();

    /**
     * 表标识符.
     *
     * @return database.table
     */
    public String identifier() {
        return database + "." + tableName;
    }
}
