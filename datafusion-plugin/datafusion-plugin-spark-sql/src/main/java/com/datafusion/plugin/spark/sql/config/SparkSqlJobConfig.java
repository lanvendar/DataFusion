package com.datafusion.plugin.spark.sql.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spark SQL 作业配置.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class SparkSqlJobConfig {

    /**
     * SELECT 日志最大行数.
     */
    public static final int SELECT_LOG_ROW_LIMIT = 100;

    /**
     * 作业信息.
     */
    public JobConfig job = new JobConfig();

    /**
     * SQL 执行对象.
     */
    public String sqlTargetType;

    /**
     * Catalog 名称.
     */
    public String catalogName;

    /**
     * Database 名称.
     */
    public String databaseName;

    /**
     * 是否切换默认 Database.
     */
    public boolean useDatabase = true;

    /**
     * 是否启用 Hive 支持.
     */
    public boolean enableHiveSupport;

    /**
     * 是否输出 SQL 文本.
     */
    public boolean enableSqlLogging = true;

    /**
     * 是否允许 SQL 失败.
     */
    public boolean allowSqlFailure;

    /**
     * SQL 列表.
     */
    public List<SqlStatement> statements = new ArrayList<>();

    /**
     * Paimon 配置.
     */
    public Map<String, String> paimonConf = new LinkedHashMap<>();

    /**
     * Spark 配置.
     */
    public Map<String, String> sparkConf = new LinkedHashMap<>();

    /**
     * Hadoop 配置.
     */
    public Map<String, String> hadoopConf = new LinkedHashMap<>();

    /**
     * 校验作业配置.
     */
    public void validate() {
        if (job == null || job.id == null || job.id.isBlank()) {
            throw new IllegalArgumentException("job.id不能为空");
        }
        if (!"PAIMON".equalsIgnoreCase(sqlTargetType)) {
            throw new IllegalArgumentException("sqlTargetType仅支持PAIMON");
        }
        if (catalogName == null || catalogName.isBlank()) {
            throw new IllegalArgumentException("catalogName不能为空");
        }
        if (useDatabase && (databaseName == null || databaseName.isBlank())) {
            throw new IllegalArgumentException("useDatabase=true时databaseName不能为空");
        }
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("statements不能为空");
        }
        for (int i = 0; i < statements.size(); i++) {
            SqlStatement statement = statements.get(i);
            if (statement == null || statement.sql == null || statement.sql.isBlank()) {
                throw new IllegalArgumentException("statements[" + i + "].sql不能为空");
            }
        }
        if (paimonConf == null) {
            throw new IllegalArgumentException("paimonConf不能为空");
        }
        String catalogKey = "spark.sql.catalog." + catalogName;
        if (isBlank(paimonConf.get("spark.sql.extensions"))) {
            throw new IllegalArgumentException("paimonConf缺少spark.sql.extensions");
        }
        if (isBlank(paimonConf.get(catalogKey))) {
            throw new IllegalArgumentException("paimonConf缺少" + catalogKey);
        }
        if (isBlank(paimonConf.get(catalogKey + ".warehouse"))) {
            throw new IllegalArgumentException("paimonConf缺少" + catalogKey + ".warehouse");
        }
        if (sparkConf == null) {
            sparkConf = new LinkedHashMap<>();
        }
        if (hadoopConf == null) {
            hadoopConf = new LinkedHashMap<>();
        }
        validateMap("paimonConf", paimonConf);
        validateMap("sparkConf", sparkConf);
        validateMap("hadoopConf", hadoopConf);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void validateMap(String name, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (isBlank(entry.getKey()) || entry.getValue() == null) {
                throw new IllegalArgumentException(name + "包含空配置");
            }
        }
    }

    /**
     * 作业信息.
     */
    public static class JobConfig {

        /**
         * 作业 ID.
         */
        public String id;

        /**
         * 作业名称.
         */
        public String name;

        /**
         * 作业说明.
         */
        public String description;

        /**
         * 作业版本.
         */
        public String version;
    }

    /**
     * SQL 语句.
     */
    public static class SqlStatement {

        /**
         * SQL 说明.
         */
        public String comment;

        /**
         * SQL 文本.
         */
        public String sql;
    }
}
