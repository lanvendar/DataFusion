package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * localDateTime类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public class LocalDateTimeTypeHandler implements JdbcTypeHandler<LocalDateTime> {
    
    @Override
    public LocalDateTime getResult(ResultSet rs, int columnIndex) throws SQLException {
        // 使用 getObject 是最健壮的方式，它能返回与列类型最匹配的 Java 对象
        Object object = rs.getObject(columnIndex);
        return convertToLocalDateTime(object);
    }
    
    @Override
    public LocalDateTime getResult(ResultSet rs, String columnName) throws SQLException {
        // 使用 getObject 是最健壮的方式，它能返回与列类型最匹配的 Java 对象
        Object object = rs.getObject(columnName);
        return convertToLocalDateTime(object);
    }
    
    /**
     * 将从 JDBC 获取的 Object 转换为 LocalDateTime.
     * 这是一个核心的辅助方法，用于处理不同类型的转换逻辑。
     *
     * @param dbObject 从 ResultSet 中获取的对象
     * @return 转换后的 LocalDateTime，如果输入为 null 则返回 null
     */
    private LocalDateTime convertToLocalDateTime(Object dbObject) {
        if (dbObject == null) {
            return null; // 如果数据库值为 NULL，直接返回 null
        }
        
        // 1. 如果是 Timestamp，直接转换（最常见、最精确的情况）
        if (dbObject instanceof Timestamp) {
            return ((Timestamp) dbObject).toLocalDateTime();
        }
        
        // 2. 如果是 Date (java.sql.Date)，表示只有日期
        //    转换为 LocalDateTime 时，时间部分设为午夜 00:00:00
        if (dbObject instanceof Date) {
            LocalDate datePart = ((Date) dbObject).toLocalDate();
            return datePart.atStartOfDay(); // 或者 datePart.atTime(0, 0)
        }
        
        // 3. 如果是 Time (java.sql.Time)，表示只有时间
        //    转换为 LocalDateTime 时，日期部分使用一个基准日期，例如 1970-01-01
        //    注意：这可能不是所有业务场景都期望的行为，但提供了一个合理的默认值。
        if (dbObject instanceof Time) {
            LocalTime timePart = ((Time) dbObject).toLocalTime();
            // 使用 LocalDate.EPOCH (1970-01-01) 作为默认日期
            return timePart.atDate(LocalDate.EPOCH);
        }
        
        // 4. 如果驱动直接返回了 LocalDateTime (现代 JDBC 4.2+ 驱动)
        if (dbObject instanceof LocalDateTime) {
            return (LocalDateTime) dbObject;
        }
        
        // 5. 如果是字符串，尝试解析 (作为最后的兼容手段)
        if (dbObject instanceof String) {
            try {
                // 尝试按标准 ISO 格式解析
                return LocalDateTime.parse((String) dbObject);
            } catch (Exception e) {
                // 如果解析失败，可以记录一个警告日志
                // log.warn("无法将字符串 '{}' 解析为 LocalDateTime", dbObject);
            }
        }
        
        // 如果所有转换都失败，抛出异常，清晰地告诉开发者问题所在
        throw new UnsupportedOperationException("无法将类型 " + dbObject.getClass().getName() + " 的值转换为 LocalDateTime。");
    }
}
