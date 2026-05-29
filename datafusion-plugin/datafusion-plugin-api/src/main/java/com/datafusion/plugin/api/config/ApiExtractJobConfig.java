package com.datafusion.plugin.api.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 抽取任务配置类.
 *
 * <p>
 * 包含任务基础信息、触发配置、运行时参数、Redis 配置、步骤列表和落表配置.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class ApiExtractJobConfig {
    
    /**
     * 任务基础信息.
     */
    public JobConfig job = new JobConfig();
    
    /**
     * 触发配置.
     */
    public TriggerConfig trigger = new TriggerConfig();
    
    /**
     * 运行时控制参数.
     */
    public RuntimeConfig runtime = new RuntimeConfig();

    /**
     * 全局 HTTP 配置.
     */
    public HttpConfig httpConfig = new HttpConfig();
    
    /**
     * Redis 缓存配置.
     */
    public RedisConfig redis = new RedisConfig();
    
    /**
     * 输入变量映射.
     */
    public Map<String, Object> inputVars = new LinkedHashMap<>();

    /**
     * 步骤列表.
     */
    public List<StepConfig> steps = new ArrayList<>();
    
    /**
     * 落表配置.
     */
    public SinkConfig sink = new SinkConfig();

    /**
     * 任务基础信息配置.
     */
    public static class JobConfig {
        
        /**
         * 任务 ID.
         */
        public String id;
        
        /**
         * 任务名称.
         */
        public String name;
        
        /**
         * 任务描述.
         */
        public String description;
        
        /**
         * 任务版本.
         */
        public String version;
    }

    /**
     * 触发配置.
     */
    public static class TriggerConfig {
        
        /**
         * 触发模式(CRON/ONCE).
         */
        public String mode = "ONCE";
        
        /**
         * Cron 表达式.
         */
        public String cron;
        
        /**
         * 时区.
         */
        public String timezone = "Asia/Shanghai";
    }

    /**
     * 运行时控制参数.
     */
    public static class RuntimeConfig {
        
        /**
         * 循环执行次数.
         */
        public int loopCount = 1;
        
        /**
         * 循环间隔(毫秒).
         */
        public long loopIntervalMs = 0;
        
    }

    /**
     * HTTP 配置.
     */
    public static class HttpConfig {
        
        /**
         * 连接超时(毫秒).
         */
        public int connectMs = 5000;
        
        /**
         * 读取超时(毫秒).
         */
        public int readMs = 30000;
        
        /**
         * 写入超时(毫秒).
         */
        public int writeMs = 30000;

        /**
         * 探测连接超时(毫秒).
         */
        public int probeConnectMs = 5000;

        /**
         * 探测读取超时(毫秒).
         */
        public int probeReadMs = 15000;
        
        /**
         * 最大请求次数.
         */
        public int maxAttempts = 3;
        
        /**
         * 重试间隔(毫秒).
         */
        public long retryIntervalMs = 1000;
        
        /**
         * 退避倍数.
         */
        public double backoffMultiplier = 2.0;
        
        /**
         * 需要重试的 HTTP 状态码列表.
         */
        public List<Integer> retryOnStatus = new ArrayList<>(List.of(429, 500, 502, 503, 504));

        /**
         * HTTP 错误处理策略.
         */
        public String onHttpError = "FAIL";
        
        /**
         * 空数据处理策略.
         */
        public String onEmptyData = "SUCCESS";
        
        /**
         * 解析错误处理策略.
         */
        public String onParseError = "FAIL";
    }

    /**
     * Redis 缓存配置.
     */
    public static class RedisConfig {
        
        /**
         * 是否启用 Redis.
         */
        public boolean enabled;
        
        /**
         * Redis 写入模式.
         */
        public String loadMode = "UPSERT";
        
        /**
         * 连接类型.
         */
        public String connectType = "REDIS";
        
        /**
         * Redis 连接参数.
         */
        public Map<String, Object> options = new LinkedHashMap<>();

        /**
         * 获取字符串类型的 Redis options 配置值.
         *
         * @param key 配置键
         * @param defaultValue 默认值
         * @return 配置值
         */
        public String optionString(String key, String defaultValue) {
            Object value = options == null ? null : options.get(key);
            return value == null ? defaultValue : String.valueOf(value);
        }

        /**
         * 获取整数类型的 Redis options 配置值.
         *
         * @param key 配置键
         * @param defaultValue 默认值
         * @return 配置值
         */
        public int optionInt(String key, int defaultValue) {
            Object value = options == null ? null : options.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        }

        /**
         * 获取长整数类型的 Redis options 配置值.
         *
         * @param key 配置键
         * @param defaultValue 默认值
         * @return 配置值
         */
        public long optionLong(String key, long defaultValue) {
            Object value = options == null ? null : options.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return value == null ? defaultValue : Long.parseLong(String.valueOf(value));
        }
    }

    /**
     * 步骤配置.
     */
    public static class StepConfig {
        
        /**
         * 步骤 ID.
         */
        public String id;
        
        /**
         * 步骤类型(HTTP).
         */
        public String type = "HTTP";
        
        /**
         * 依赖的上游步骤 ID 列表.
         */
        public List<String> dependsOn = new ArrayList<>();
        
        /**
         * 是否启用该步骤.
         */
        public boolean enabled = true;
        
        /**
         * Step 级 HTTP 配置.
         */
        public HttpConfig httpConfig;

        /**
         * HTTP 请求配置.
         */
        public RequestConfig request = new RequestConfig();
        
        /**
         * 分页配置.
         */
        public PaginationConfig pagination = new PaginationConfig();
        
        /**
         * 响应解析配置.
         */
        public ResponseConfig response = new ResponseConfig();
        
        /**
         * Redis 缓存配置.
         */
        public RedisCacheConfig redisCache = new RedisCacheConfig();
        
        /**
         * 步骤输出映射.
         */
        public Map<String, String> outputVars = new LinkedHashMap<>();

    }

    /**
     * HTTP 请求配置.
     */
    public static class RequestConfig {
        
        /**
         * HTTP 方法(GET/POST/PUT/DELETE).
         */
        public String method = "GET";
        
        /**
         * 请求 URL.
         */
        public String url;
        
        /**
         * 请求头映射.
         */
        public Map<String, Object> headers = new LinkedHashMap<>();
        
        /**
         * 查询参数映射.
         */
        public Map<String, Object> queryParams = new LinkedHashMap<>();
        
        /**
         * 请求体类型(NONE/JSON/FORM/RAW).
         */
        public String bodyType = "NONE";
        
        /**
         * 请求体对象.
         */
        public Object body;
        
        /**
         * 原始请求体字符串.
         */
        public String rawBody;
        
    }

    /**
     * 分页配置.
     */
    public static class PaginationConfig {
        
        /**
         * 分页类型(NONE/PAGE/OFFSET).
         */
        public String type = "NONE";
        
        /**
         * 页码参数名.
         */
        public String pageParam;
        
        /**
         * 每页大小参数名.
         */
        public String pageSizeParam;
        
        /**
         * 起始页码.
         */
        public int startPage = 1;
        
        /**
         * 每页大小.
         */
        public int pageSize = 100;
        
        /**
         * 最大页数.
         */
        public int maxPages = 1;
        
        /**
         * Offset 参数名.
         */
        public String offsetParam;
        
        /**
         * Limit 参数名.
         */
        public String limitParam;
        
        /**
         * 起始 Offset.
         */
        public int startOffset = 0;
        
        /**
         * 每批 Limit.
         */
        public int limit = 100;
        
        /**
         * 最大请求次数.
         */
        public int maxRequests = 1;
        
        /**
         * 空数据时停止.
         */
        public boolean stopWhenEmpty = true;
    }

    /**
     * 响应解析配置.
     */
    public static class ResponseConfig {
        
        /**
         * 内容类型(JSON).
         */
        public String contentType = "JSON";
        
        /**
         * 成功的 HTTP 状态码列表.
         */
        public List<Integer> successStatus = new ArrayList<>(List.of(200));
        
        /**
         * 业务成功表达式(JMESPath).
         */
        public String successExpression;
        
        /**
         * 错误消息表达式(JMESPath).
         */
        public String messageExpression;
        
        /**
         * 记录模式(OBJECT/ARRAY).
         */
        public String recordMode;
        
        /**
         * 字段映射列表.
         */
        public List<FieldConfig> fields = new ArrayList<>();
    }

    /**
     * 字段映射配置.
     */
    public static class FieldConfig {
        
        /**
         * 字段名.
         */
        public String name;
        
        /**
         * JMESPath 表达式.
         */
        public String expression;
        
        /**
         * 固定值.
         */
        public Object value;
    }

    /**
     * Redis 缓存配置.
     */
    public static class RedisCacheConfig {

        /**
         * 是否启用缓存.
         */
        public boolean enabled;

        /**
         * 缓存 Key 模板.
         */
        public String key;

        /**
         * 缓存 TTL(秒).
         */
        public long ttlSeconds;

        /**
         * 缓存写入模式.
         */
        public String loadMode;

        /**
         * 缓存值表达式.
         */
        public List<ValueExpressionConfig> valueExpressions = new ArrayList<>();
    }

    /**
     * 值表达式配置.
     */
    public static class ValueExpressionConfig {
        /**
         * 输出字段名.
         */
        public String name;

        /**
         * JMESPath 表达式.
         */
        public String expression;
    }

    /**
     * 落表配置.
     */
    public static class SinkConfig {
        
        /**
         * 目标存储类型(STARROCKS/PAIMON/NOOP).
         */
        public String type;
        
        /**
         * 写入模式(APPEND/UPSERT/OVERWRITE_PARTITION).
         */
        public String loadMode = "APPEND";

        /**
         * 连接类型.
         */
        public String connectType;

        /**
         * 连接参数.
         */
        public Map<String, Object> options = new LinkedHashMap<>();

        /**
         * 获取字符串类型的 sink options 配置值.
         *
         * @param key 配置键
         * @param defaultValue 默认值
         * @return 配置值
         */
        public String optionString(String key, String defaultValue) {
            Object value = options == null ? null : options.get(key);
            return value == null ? defaultValue : String.valueOf(value);
        }

        /**
         * 获取整数类型的 sink options 配置值.
         *
         * @param key 配置键
         * @param defaultValue 默认值
         * @return 配置值
         */
        public int optionInt(String key, int defaultValue) {
            Object value = options == null ? null : options.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        }
        
        /**
         * 表配置.
         */
        public TableConfig table = new TableConfig();
        
        /**
         * 目标表字段列表.
         */
        public List<ColumnConfig> columns = new ArrayList<>();
        
        /**
         * 写入配置.
         */
        public WriteConfig write = new WriteConfig();
    }

    /**
     * 表配置.
     */
    public static class TableConfig {
        
        /**
         * 表名.
         */
        public String name;

        /**
         * 表注释.
         */
        public String comment;
        
        /**
         * 不存在时自动创建.
         */
        public boolean createIfNotExists = true;
        
        /**
         * 主键字段列表.
         */
        public List<String> primaryKeys = new ArrayList<>();
        
        /**
         * 分区键列表.
         */
        public List<String> partitionKeys = new ArrayList<>();
        
        /**
         * 分区配置.
         */
        public PartitionConfig partition = new PartitionConfig();
    }

    /**
     * 分区配置.
     */
    public static class PartitionConfig {
        
        /**
         * 是否启用分区.
         */
        public boolean enabled;
        
        /**
         * 分区字段.
         */
        public String field;
        
        /**
         * 分区类型(DAY/MONTH/YEAR).
         */
        public String type;
    }

    /**
     * 目标表字段配置.
     */
    public static class ColumnConfig {
        
        /**
         * 字段名.
         */
        public String name;
        
        /**
         * 字段类型.
         */
        public String type;
        
        /**
         * 字段长度(VARCHAR).
         */
        public Integer length;

        /**
         * 数值精度(DECIMAL).
         */
        public Integer precision;

        /**
         * 数值小数位(DECIMAL).
         */
        public Integer scale;

        /**
         * 日期/时间格式转换.
         */
        public String format;
        
        /**
         * 是否允许为空.
         */
        public boolean nullable = true;
        
        /**
         * 字段注释.
         */
        public String comment;
        
        /**
         * 默认值.
         */
        public Object defaultValue;
    }

    /**
     * 写入配置.
     */
    public static class WriteConfig {
        
        /**
         * 数据格式(JSON).
         */
        public String format = "JSON";
        
        /**
         * 批量大小.
         */
        public int batchSize = 1000;
        
        /**
         * 刷新间隔(毫秒).
         */
        public long flushIntervalMs = 5000;

        /**
         * 写入 Headers.
         */
        public Map<String, Object> headers = new LinkedHashMap<>();

        /**
         * Stream Load label 前缀.
         */
        public String labelPrefix = "datafusion_api";

        /**
         * StarRocks 部分更新.
         */
        public boolean partialUpdate;

        /**
         * 覆盖分区配置.
         */
        public Map<String, Object> overwritePartition = new LinkedHashMap<>();
    }
}
