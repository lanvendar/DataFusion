package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * localDate类型处理器.
 *
 * @author xufeng
 * @version 1.0.0, 2025/9/2
 * @since 2025/9/2
 */
public class LocalDateTypeHandler implements JdbcTypeHandler<LocalDate> {

    @Override
    public LocalDate getResult(ResultSet rs, int columnIndex) throws SQLException {
        // 使用 getObject 是最健壮的方式，它能返回与列类型最匹配的 Java 对象
        Object object = rs.getObject(columnIndex);
        return convertToLocalDate(object);
    }

    @Override
    public LocalDate getResult(ResultSet rs, String columnName) throws SQLException {
        // 使用 getObject 是最健方式，它能返回与列类型最匹配的 Java 对象
        Object object = rs.getObject(columnName);
        return convertToLocalDate(object);
    }

    /**
     * 将从 JDBC 获取的 Object 转换为 LocalDate.
     * 这是一个核心的辅助方法，用于处理不同类型的转换逻辑。
     *
     * @param dbObject 从 ResultSet 中获取的对象
     * @return 转换后的 LocalDate，如果输入为 null 则返回 null
     */
    private LocalDate convertToLocalDate(Object dbObject) {
        if (dbObject == null) {
            return null; // 如果数据库值为 NULL，直接返回 null
        }

        // 1. 如果驱动直接返回了 LocalDate (现代 JDBC 4.2+ 驱动，例如 MaxCompute)
        //    这是我们主要需要处理的情况。
        if (dbObject instanceof LocalDate) {
            return (LocalDate) dbObject;
        }

        // 2. 如果是 java.sql.Date，直接转换（最常见的 JDBC 类型）
        if (dbObject instanceof Date) {
            return ((Date) dbObject).toLocalDate();
        }

        // 3. 如果是 Timestamp，表示包含了日期和时间
        //    我们只取其日期部分，忽略时间部分。
        if (dbObject instanceof Timestamp) {
            return ((Timestamp) dbObject).toLocalDateTime().toLocalDate();
        }

        // 4. 如果是 LocalDateTime，同样只取其日期部分
        if (dbObject instanceof LocalDateTime) {
            return ((LocalDateTime) dbObject).toLocalDate();
        }

        // 5. 如果是字符串，尝试解析 (作为最后的兼容手段)
        if (dbObject instanceof String) {
            try {
                // 尝试按标准 ISO 格式 "YYYY-MM-DD" 解析
                return LocalDate.parse((String) dbObject);
            } catch (Exception e) {
                // 如果解析失败，可以记录一个警告日志
                // log.warn("无法将字符串 '{}' 解析为 LocalDate", dbObject);
            }
        }

        // 如果所有转换都失败，抛出异常，清晰地告诉开发者问题所在
        throw new UnsupportedOperationException("无法将类型 " + dbObject.getClass().getName() + " 的值转换为 LocalDate。");
    }
}