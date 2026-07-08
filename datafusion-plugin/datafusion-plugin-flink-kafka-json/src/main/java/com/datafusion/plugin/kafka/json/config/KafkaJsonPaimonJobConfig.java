package com.datafusion.plugin.kafka.json.config;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka JSON 到 Paimon 的 Flink 作业配置.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class KafkaJsonPaimonJobConfig implements Serializable {

    /**
     * 序列化版本号.
     */
    private static final long serialVersionUID = 1L;

    /**
     * 作业信息.
     */
    public JobConfig job = new JobConfig();

    /**
     * Kafka source 配置.
     */
    public KafkaSourceConfig source = new KafkaSourceConfig();

    /**
     * Flink 配置.
     */
    public Map<String, String> flinkConfig = new LinkedHashMap<>();

    /**
     * Paimon sink 配置.
     */
    public PaimonSinkConfig sink = new PaimonSinkConfig();

    /**
     * 作业元信息.
     */
    public static class JobConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 作业 ID.
         */
        public String id;

        /**
         * 作业名称.
         */
        public String name;

        /**
         * 作业描述.
         */
        public String description;

        /**
         * 配置版本.
         */
        public String version;
    }

    /**
     * Kafka source 配置.
     */
    public static class KafkaSourceConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * source 类型.
         */
        public String type = "KAFKA";

        /**
         * Kafka broker 地址.
         */
        public String bootstrapServers;

        /**
         * topic 列表.
         */
        public List<String> topics = new ArrayList<>();

        /**
         * topic 正则.
         */
        public String topicPattern;

        /**
         * 消费组 ID.
         */
        public String groupId;

        /**
         * 起始位点.
         */
        public String startingOffsets = "group-offsets";

        /**
         * Kafka 透传配置.
         */
        public Map<String, String> properties = new LinkedHashMap<>();
    }

    /**
     * Paimon sink 配置.
     */
    public static class PaimonSinkConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * sink 类型.
         */
        public String type = "PAIMON";

        /**
         * 连接类型.
         */
        public String connectType = "S3";

        /**
         * 默认写入模式.
         */
        public String loadMode = "APPEND";

        /**
         * 表结构不匹配处理策略.
         */
        public String schemaMismatchPolicy = "SKIP";

        /**
         * 单条记录错误处理策略.
         */
        public String recordErrorPolicy = "SKIP";

        /**
         * 全局 Paimon options.
         */
        public Map<String, String> options = new LinkedHashMap<>();

        /**
         * 目标表配置.
         */
        public List<PaimonTableConfig> tables = new ArrayList<>();

        /**
         * writer 控制配置.
         */
        public WriterConfig writer = new WriterConfig();

        /**
         * 获取全局 Paimon options.
         *
         * @return 全局 Paimon options
         */
        public Map<String, String> globalOptions() {
            return options == null ? new LinkedHashMap<>() : options;
        }
    }

    /**
     * Paimon 目标表配置.
     */
    public static class PaimonTableConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 是否启用.
         */
        public Boolean enabled = true;

        /**
         * 目标表结构配置.
         */
        public TableConfig table = new TableConfig();

        /**
         * 列映射表达式或简写.
         */
        public JsonNode columnsMapping;

        /**
         * 表级写入模式.
         */
        public String loadMode;

        /**
         * 字段定义.
         */
        public List<ColumnConfig> columns = new ArrayList<>();

        /**
         * 表级 Paimon options.
         */
        public Map<String, String> options = new LinkedHashMap<>();
    }

    /**
     * Paimon 表结构配置.
     */
    public static class TableConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 目标 database 表达式或简写.
         */
        public JsonNode database;

        /**
         * 目标表名表达式或简写.
         */
        public JsonNode name;

        /**
         * 表注释表达式或简写.
         */
        public JsonNode comment;

        /**
         * 是否自动建表表达式或简写.
         */
        public JsonNode createIfNotExists;

        /**
         * 分区字段表达式或简写.
         */
        public JsonNode partitionKeys;

        /**
         * 主键配置.
         */
        public PrimaryKeyConfig primaryKeys;

        /**
         * 是否补充 Kafka 元数据字段.
         */
        public Boolean includeKafkaMetadataFields = false;
    }

    /**
     * 字段定义.
     */
    public static class ColumnConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 字段名.
         */
        public String name;

        /**
         * 字段类型.
         */
        public String dataType;

        /**
         * 标准 schema 字段类型别名.
         */
        public String type;

        /**
         * 字符串长度.
         */
        public Integer length;

        /**
         * 数值精度.
         */
        public Integer precision;

        /**
         * 数值小数位.
         */
        public Integer scale;

        /**
         * 是否允许为空.
         */
        public Boolean nullable;

        /**
         * 字段注释.
         */
        public String comment;

        /**
         * 日期时间格式.
         */
        public String format;

        /**
         * 字段取值表达式.
         */
        public JsonNode value;

        /**
         * 显式配置字段.
         */
        public transient List<String> configuredFields = new ArrayList<>();

        /**
         * 复制字段定义.
         *
         * @return 字段定义副本
         */
        public ColumnConfig copy() {
            ColumnConfig copy = new ColumnConfig();
            copy.name = name;
            copy.dataType = effectiveDataType();
            copy.type = type;
            copy.length = length;
            copy.precision = precision;
            copy.scale = scale;
            copy.nullable = nullable;
            copy.comment = comment;
            copy.format = format;
            copy.value = value;
            copy.configuredFields = configuredFields == null ? new ArrayList<>() : new ArrayList<>(configuredFields);
            return copy;
        }

        private String effectiveDataType() {
            if (dataType != null && !dataType.isBlank()) {
                return dataType;
            }
            if (type != null && !type.isBlank()) {
                return type;
            }
            return "STRING";
        }
    }

    /**
     * 主键配置.
     */
    public static class PrimaryKeyConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 主键模式.
         */
        public String mode;

        /**
         * 代理主键算法.
         */
        public String algorithm = "UUID";

        /**
         * JMESPath 表达式.
         */
        public String path;

        /**
         * 默认字段列表.
         */
        public Object defaultValue;

        /**
         * JSON 类型.
         */
        public String jsonType = "ARRAY";
    }

    /**
     * writer 控制配置.
     */
    public static class WriterConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 单表批量提交大小.
         */
        public Integer batchSize = 1000;

        /**
         * flush 间隔.
         */
        public Long flushIntervalMs = 5000L;

        /**
         * 最大打开 writer 数.
         */
        public Integer maxOpenWriters = 256;
    }
}
