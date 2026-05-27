package com.datafusion.plugin.api.sink.starrocks;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.SchemaFieldConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.sink.SinkWriter;
import com.datafusion.plugin.api.util.JsonUtils;
import com.datafusion.plugin.api.util.TextUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * StarRocks 数据写入器.
 *
 * <p>
 * 使用 Stream Load API 批量写入数据到 StarRocks,支持自动建表和 Schema 校验.
 * </p>
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class StarRocksSinkWriter implements SinkWriter {
    
    /**
     * 落表配置.
     */
    private SinkConfig sink;
    
    /**
     * JDBC 连接.
     */
    private Connection connection;
    
    /**
     * HTTP 客户端,用于 Stream Load.
     */
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * 打开写入器并初始化连接.
     *
     * @param sink 落表配置
     */
    @Override
    public void open(SinkConfig sink) {
        this.sink = sink;
        try {
            connection = DriverManager.getConnection(required("jdbcUrl"), required("username"), password());
            ensureTable();
        } catch (SQLException e) {
            throw new ApiExtractException("Failed to open StarRocks sink", e);
        }
    }

    /**
     * 批量写入记录到 StarRocks.
     *
     * @param records 记录列表
     */
    @Override
    public void write(List<Record> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        String payload = toJsonLines(records);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(loadUrl()))
                .timeout(Duration.ofMillis(Math.max(1000, sink.write.flushIntervalMs)))
                .header("Authorization", basicAuth())
                .header("label", "datafusion_api_" + UUID.randomUUID().toString().replace("-", ""))
                .header("format", "json")
                .header("strip_outer_array", "false")
                .header("read_json_by_line", "true")
                .PUT(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !response.body().contains("\"Status\":\"Success\"")) {
                throw new ApiExtractException("StarRocks Stream Load failed: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiExtractException("StarRocks Stream Load failed", e);
        }
    }

    /**
     * 刷新缓冲区(空操作,Stream Load 即时提交).
     */
    @Override
    public void flush() {
    }

    /**
     * 关闭写入器并释放 JDBC 连接.
     */
    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // ignore close failure
            }
        }
    }

    /**
     * 确保目标表存在,不存在则创建,存在则校验 Schema.
     *
     * @throws SQLException SQL 异常
     */
    private void ensureTable() throws SQLException {
        if (sink.table == null || TextUtils.isBlank(sink.table.name)) {
            throw new ApiExtractException("sink.table.name is required for StarRocks");
        }
        if (!tableExists()) {
            if (!sink.table.createIfNotExists) {
                throw new ApiExtractException("StarRocks table does not exist: " + sink.table.name);
            }
            createTable();
            return;
        }
        validateSchema();
    }

    /**
     * 检查表是否存在.
     *
     * @return true 表示表存在
     * @throws SQLException SQL 异常
     */
    private boolean tableExists() throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, database(), sink.table.name, new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    /**
     * 校验表 Schema 与配置的兼容性.
     *
     * @throws SQLException SQL 异常
     */
    private void validateSchema() throws SQLException {
        Map<String, String> existing = new LinkedHashMap<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, database(), sink.table.name, null)) {
            while (resultSet.next()) {
                existing.put(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                        resultSet.getString("TYPE_NAME").toUpperCase(Locale.ROOT));
            }
        }
        for (SchemaFieldConfig field : sink.schema) {
            String actualType = existing.get(field.name.toLowerCase(Locale.ROOT));
            if (actualType == null) {
                throw new ApiExtractException("StarRocks table lacks configured field: " + field.name);
            }
            String expected = normalizeType(field.type);
            if (!actualType.contains(expected) && !expected.contains(actualType)) {
                throw new ApiExtractException("StarRocks field type mismatch: " + field.name
                        + ", expected=" + field.type + ", actual=" + actualType);
            }
        }
    }

    /**
     * 创建 StarRocks 表.
     *
     * @throws SQLException SQL 异常
     */
    private void createTable() throws SQLException {
        if (sink.schema == null || sink.schema.isEmpty()) {
            throw new ApiExtractException("sink.schema is required to create StarRocks table");
        }
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(quote(database())).append(".").append(quote(sink.table.name)).append(" (");
        StringJoiner columns = new StringJoiner(", ");
        for (SchemaFieldConfig field : sink.schema) {
            columns.add(columnDefinition(field));
        }
        sql.append(columns).append(") ");
        if (sink.table.primaryKeys != null && !sink.table.primaryKeys.isEmpty()) {
            sql.append("PRIMARY KEY(").append(String.join(", ", sink.table.primaryKeys)).append(") ");
        } else {
            sql.append("DUPLICATE KEY(").append(sink.schema.get(0).name).append(") ");
        }
        sql.append("DISTRIBUTED BY HASH(").append(distributionKey()).append(") BUCKETS 10 ")
                .append("PROPERTIES (\"replication_num\" = \"1\")");
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql.toString());
        }
    }

    /**
     * 生成列定义 SQL.
     *
     * @param field 字段配置
     * @return 列定义 SQL 片段
     */
    private String columnDefinition(SchemaFieldConfig field) {
        StringBuilder sql = new StringBuilder(quote(field.name)).append(" ").append(starRocksType(field));
        if (!field.nullable) {
            sql.append(" NOT NULL");
        }
        if (field.comment != null) {
            sql.append(" COMMENT '").append(field.comment.replace("'", "''")).append("'");
        }
        return sql.toString();
    }

    /**
     * 转换为 StarRocks 数据类型.
     *
     * @param field 字段配置
     * @return StarRocks 类型字符串
     */
    private String starRocksType(SchemaFieldConfig field) {
        String type = TextUtils.upper(field.type, "VARCHAR");
        if ("VARCHAR".equals(type) && field.length != null) {
            return "VARCHAR(" + field.length + ")";
        }
        if ("STRING".equals(type)) {
            return "STRING";
        }
        if ("JSON".equals(type)) {
            return "JSON";
        }
        return type;
    }

    /**
     * 标准化类型名称.
     *
     * @param type 原始类型
     * @return 标准化后的类型
     */
    private String normalizeType(String type) {
        String normalized = TextUtils.upper(type, "VARCHAR");
        if (normalized.startsWith("VARCHAR")) {
            return "VARCHAR";
        }
        if ("STRING".equals(normalized)) {
            return "VARCHAR";
        }
        return normalized;
    }

    /**
     * 获取分布键.
     *
     * @return 分布键字段名
     */
    private String distributionKey() {
        if (sink.table.primaryKeys != null && !sink.table.primaryKeys.isEmpty()) {
            return sink.table.primaryKeys.get(0);
        }
        return sink.schema.get(0).name;
    }

    /**
     * 将记录列表转换为 JSON Lines 格式.
     *
     * @param records 记录列表
     * @return JSON Lines 字符串
     */
    private String toJsonLines(List<Record> records) {
        StringJoiner joiner = new StringJoiner("\n");
        for (Record record : records) {
            joiner.add(JsonUtils.write(record));
        }
        return joiner.toString();
    }

    /**
     * 构建 Stream Load URL.
     *
     * @return Stream Load URL
     */
    private String loadUrl() {
        String loadUrl = required("loadUrl");
        if (loadUrl.endsWith("/")) {
            loadUrl = loadUrl.substring(0, loadUrl.length() - 1);
        }
        return loadUrl + "/api/" + database() + "/" + sink.table.name + "/_stream_load";
    }

    /**
     * 生成 Basic Auth 头.
     *
     * @return Authorization 头值
     */
    private String basicAuth() {
        String raw = required("username") + ":" + password();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 获取数据库名.
     *
     * @return 数据库名
     */
    private String database() {
        String database = stringValue(sink.connection.get("database"));
        if (!TextUtils.isBlank(database)) {
            return database;
        }
        String jdbcUrl = required("jdbcUrl");
        int slash = jdbcUrl.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < jdbcUrl.length()) {
            String tail = jdbcUrl.substring(slash + 1);
            int query = tail.indexOf('?');
            return query >= 0 ? tail.substring(0, query) : tail;
        }
        throw new ApiExtractException("StarRocks connection.database is required");
    }

    /**
     * 获取密码.
     *
     * @return 密码
     */
    private String password() {
        String password = stringValue(sink.connection.get("password"));
        if (!TextUtils.isBlank(password)) {
            return password;
        }
        String passwordRef = stringValue(sink.connection.get("passwordRef"));
        return TextUtils.isBlank(passwordRef) ? "" : System.getenv(passwordRef);
    }

    /**
     * 获取必需的连接配置项.
     *
     * @param key 配置键
     * @return 配置值
     */
    private String required(String key) {
        String value = stringValue(sink.connection.get(key));
        if (TextUtils.isBlank(value)) {
            throw new ApiExtractException("StarRocks connection." + key + " is required");
        }
        return value;
    }

    /**
     * 将对象转换为字符串.
     *
     * @param value 待转换的对象
     * @return 字符串表示
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 引用标识符(反引号).
     *
     * @param value 标识符
     * @return 引用后的标识符
     */
    private String quote(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}
