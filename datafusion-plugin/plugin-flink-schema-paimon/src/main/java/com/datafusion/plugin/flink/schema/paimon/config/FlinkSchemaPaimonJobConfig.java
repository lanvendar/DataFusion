package com.datafusion.plugin.flink.schema.paimon.config;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka schema 到 Paimon 的 Flink 作业配置.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class FlinkSchemaPaimonJobConfig implements Serializable {

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
     * Flink runtime 配置.
     */
    public RuntimeConfig runtime = new RuntimeConfig();

    /**
     * Paimon sink 配置.
     */
    public PaimonSinkGroupConfig sink = new PaimonSinkGroupConfig();

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
     * Flink runtime 配置.
     */
    public static class RuntimeConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 作业并行度.
         */
        public Integer parallelism = 1;

        /**
         * 部署模式.
         */
        public String deploymentMode = "LOCAL";

        /**
         * 执行模式.
         */
        public String executionMode = "STREAMING";

        /**
         * checkpoint 模式.
         */
        public String checkpointMode = "EXACTLY_ONCE";

        /**
         * checkpoint 间隔.
         */
        public Long checkpointIntervalMs = 60000L;

        /**
         * checkpoint 超时.
         */
        public Long checkpointTimeoutMs = 600000L;

        /**
         * 最大并发 checkpoint 数.
         */
        public Integer maxConcurrentCheckpoints = 1;

        /**
         * checkpoint 存储.
         */
        public String checkpointStorage;

        /**
         * state backend 类型.
         */
        public String stateBackend = "HASHMAP";

        /**
         * 重启策略.
         */
        public String restartStrategy = "FIXED_DELAY";

        /**
         * 重启次数.
         */
        public Integer restartAttempts = 3;

        /**
         * 重启延迟.
         */
        public Long restartDelayMs = 10000L;

    }

    /**
     * Paimon sink 组配置.
     */
    public static class PaimonSinkGroupConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 默认写入模式.
         */
        public String loadMode = "APPEND";

        /**
         * 连接类型.
         */
        public String connectType = "S3";

        /**
         * 是否补充 Kafka 元数据字段.
         */
        public Boolean includeKafkaMetadataFields = false;

        /**
         * 表结构不匹配处理策略.
         */
        public String schemaMismatchPolicy = "SKIP";

        /**
         * 单条记录错误处理策略.
         */
        public String recordErrorPolicy = "SKIP";

        /**
         * Paimon catalog 配置.
         */
        public Map<String, String> catalogOptions = new LinkedHashMap<>();

        /**
         * 全局 Paimon options.
         */
        public Map<String, String> options = new LinkedHashMap<>();

        /**
         * 目标表白名单.
         */
        public List<PaimonTableSinkConfig> tables = new ArrayList<>();

        /**
         * 写入控制配置.
         */
        public WriteConfig write = new WriteConfig();

        /**
         * 获取全局 Paimon options.
         *
         * @return 全局 Paimon options
         */
        public Map<String, String> globalOptions() {
            if (options != null && !options.isEmpty()) {
                return options;
            }
            return catalogOptions;
        }
    }

    /**
     * Paimon 目标表配置.
     */
    public static class PaimonTableSinkConfig implements Serializable {

        /**
         * 序列化版本号.
         */
        private static final long serialVersionUID = 1L;

        /**
         * 是否启用.
         */
        public Boolean enabled = true;

        /**
         * 目标 database.
         */
        public String database;

        /**
         * 目标表名.
         */
        public String tableName;

        /**
         * 表级写入模式.
         */
        public String loadMode;

        /**
         * 表级 Paimon options.
         */
        public Map<String, String> options = new LinkedHashMap<>();
    }

    /**
     * 写入控制配置.
     */
    public static class WriteConfig implements Serializable {

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
