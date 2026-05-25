package com.datafusion.common.spring.typehandler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;


/**
 * 自定义 TypeHandler，用于在 Java 的 Properties 类型和数据库的 JSON/JSONB 类型之间进行转换.
 *
 * @author xufeng
 * @version 1.0.0, 2025/8/19
 * @since 2025/8/19
 */
// 声明这个 Handler 要处理的 Java 类型
@MappedTypes(Properties.class)
// 声明这个 Handler 对应于数据库的什么 JDBC 类型
@MappedJdbcTypes(JdbcType.OTHER) // 使用 OTHER 或 UNKNOWN，因为 JSON 不是标准的 JDBC 类型
public class PropertiesTypeHandler extends BaseTypeHandler<Properties> {

    /**
     * 使用 Jackson 的 ObjectMapper 进行 JSON 序列化和反序列化.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        // null 值不输出(节省内存)
        OBJECT_MAPPER.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }


    /**
     * 将 Java 的 Properties 对象转换为数据库可以存储的格式 (JSON 字符串).
     *
     * @param ps PreparedStatement
     * @param i 参数索引
     * @param parameter Properties 对象
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Properties parameter, JdbcType jdbcType) throws SQLException {
        try {
            // 将 Properties 对象序列化为 JSON 字符串
            String jsonString = OBJECT_MAPPER.writeValueAsString(parameter);

            // 为了让 PostgreSQL JDBC 驱动正确识别 JSON 类型，最好使用 PGobject
            PGobject pGobject = new PGobject();
            pGobject.setType("json"); // 或 "jsonb"，根据你的字段类型
            pGobject.setValue(jsonString);

            ps.setObject(i, pGobject);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting Properties to JSON string", e);
        }
    }

    /**
     * 从数据库结果集 (ResultSet) 中获取 JSON 字符串并转换为 Properties 对象.
     * (根据列名)
     */
    @Override
    public Properties getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String jsonString = rs.getString(columnName);
        return parseJson(jsonString);
    }

    /**
     * 从数据库结果集 (ResultSet) 中获取 JSON 字符串并转换为 Properties 对象.
     * (根据列索引)
     */
    @Override
    public Properties getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String jsonString = rs.getString(columnIndex);
        return parseJson(jsonString);
    }

    /**
     * 从存储过程的出参中获取 JSON 字符串并转换为 Properties 对象.
     */
    @Override
    public Properties getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String jsonString = cs.getString(columnIndex);
        return parseJson(jsonString);
    }

    /**
     * 公共的解析方法，将 JSON 字符串反序列化为 Properties 对象.
     * @param jsonString 从数据库读取的 JSON 字符串
     * @return 解析后的 Properties 对象，如果输入为空或解析失败则返回 null 或空的 Properties
     */
    private Properties parseJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null; // 或者返回 new Properties()，根据你的业务需求
        }
        try {
            return OBJECT_MAPPER.readValue(jsonString, Properties.class);
        } catch (JsonProcessingException e) {
            // 最好在这里记录日志
            throw new RuntimeException("Error parsing JSON string to Properties", e);
        }
    }
}
