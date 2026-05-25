package com.datafusion.common.spring.typehandler;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * mybatis 数据类型 JsonNodeTypeHandler支持类.
 *
 * @author lanvendar
 * @version 3.0.0, 2024/3/13
 * @since 2024/3/13
 */
@Slf4j
@MappedTypes(JsonNode.class)
@MappedJdbcTypes(value = {JdbcType.OTHER}, includeNullJdbcType = true)
public class JsonNodeTypeHandler extends BaseTypeHandler<JsonNode> {

    /**
     * JsonNode 对象.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 未知字段忽略
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 不使用科学计数
        MAPPER.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        // null 值不输出(节省内存)
        MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        //注册了一个 JavaTimeModule 模块,对LocalDate、LocalTime、Instant 等进行序列化和反序列化的支持
        MAPPER.registerModule(new JavaTimeModule());
    }

    /**
     * 数据库类型分类.
     */
    private Map<String, JsonObjectHandler> dbTypeMap;

    /**
     * 构造类.
     */
    public JsonNodeTypeHandler() {
        dbTypeMap = new HashMap<>();
        dbTypeMap.put("PostgreSQL", new PostgresJsonObjectHandler());
        //todo 暂时先注释掉
        //dbTypeMap.put("Kingbase8", new KingBaseJsonObjectHandler());
    }

    /**
     * 设置非null属性.
     *
     * @param ps        PreparedStatement
     * @param i         字段顺序
     * @param parameter 参数
     * @param jdbcType  jdbc类型
     * @throws SQLException SQL异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        Connection connection = ps.getConnection();
        String dbType = getDatabaseType(connection);
        JsonObjectHandler handler = dbTypeMap.get(dbType);
        if (handler == null) {
            throw new UnsupportedOperationException("Unsupported database type");
        }
        ps.setObject(i, handler.createJsonObject(connection, parameter, MAPPER));
    }

    /**
     * 获取可空值.
     *
     * @param rs         返回数据集
     * @param columnName 数据库字段名字
     * @return 列表数据
     * @throws SQLException SQL异常
     */
    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /**
     * 获取可空值.
     *
     * @param rs          返回数据集
     * @param columnIndex 数据库字段顺序
     * @return 列表数据
     * @throws SQLException SQL异常
     */
    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /**
     * 获取可空值.
     *
     * @param cs          返回数据集
     * @param columnIndex 数据库字段顺序
     * @return 列表数据
     * @throws SQLException SQL异常
     */
    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /**
     * jackson 解析.
     *
     * @param jsonStr 字符串
     * @return jackson 的 JsonNode 对象
     */
    private JsonNode parse(String jsonStr) {
        if (StrUtil.isEmpty(jsonStr)) {
            return null;
        }
        try {
            return MAPPER.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            log.error("parse failed, jsonStr={}", jsonStr, e);
            return null;
        }
    }

    /**
     * 数据库类型判断.
     *
     * @param connection 连接信息.
     * @return 数据库类型
     * @throws SQLException 数据库异常
     */
    private String getDatabaseType(Connection connection) throws SQLException {
        // 根据连接获取数据库类型，这里假设从连接属性中获取
        return connection.getMetaData().getDatabaseProductName();
    }
}