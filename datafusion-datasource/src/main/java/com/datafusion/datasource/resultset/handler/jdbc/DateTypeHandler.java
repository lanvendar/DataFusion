package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Date (java.sql.Date) 类型处理器，能够灵活处理数据库中的 TIMESTAMP, DATE, 或 TIME 类型.
 *
 * <p>
 * 核心目标是从各种输入源中提取日期部分（年、月、日）.
 * </p>
 *
 * @author lanvendar
 * @version 1.0.0, 2025/08/29
 * @since 2025/08/29
 */
public class DateTypeHandler implements JdbcTypeHandler<Date> {
    
    @Override
    public Date getResult(ResultSet rs, int columnIndex) throws SQLException {
        // 同样使用 getObject 获取与列类型最匹配的 Java 对象
        Object object = rs.getObject(columnIndex);
        return convertToSqlDate(object);
    }
    
    @Override
    public Date getResult(ResultSet rs, String columnName) throws SQLException {
        // 按列名获取
        Object object = rs.getObject(columnName);
        return convertToSqlDate(object);
    }
    
    /**
     * 将从 JDBC 获取的 Object 转换为 java.sql.Date.
     *
     * @param dbObject 从 ResultSet 中获取的对象
     * @return 转换后的 java.sql.Date，如果输入为 null 则返回 null
     */
    private Date convertToSqlDate(Object dbObject) {
        if (dbObject == null) {
            return null; // 如果数据库值为 NULL，直接返回 null
        }
        
        // 1. 如果本身就是 java.sql.Date，直接返回（最理想情况）
        if (dbObject instanceof Date) {
            return (Date) dbObject;
        }
        
        // 2. 如果是 Timestamp，它包含日期和时间。我们需要提取日期部分。
        if (dbObject instanceof Timestamp) {
            // 将 Timestamp 转换为 LocalDate，再转换为 java.sql.Date
            LocalDate localDate = ((Timestamp) dbObject).toLocalDateTime().toLocalDate();
            return Date.valueOf(localDate);
        }
        
        // 3. 如果是 Time，它只包含时间，没有日期信息。
        //    在这种情况下，返回一个基准日期（如 1970-01-01）是合理的默认行为。
        if (dbObject instanceof Time) {
            // 返回纪元日 (Epoch Day)
            return Date.valueOf(LocalDate.EPOCH);
        }
        
        // 4. 如果驱动返回了现代的 java.time 类型
        if (dbObject instanceof LocalDateTime) {
            LocalDate localDate = ((LocalDateTime) dbObject).toLocalDate();
            return Date.valueOf(localDate);
        }
        
        if (dbObject instanceof LocalDate) {
            return Date.valueOf((LocalDate) dbObject);
        }
        
        // 5. 如果是字符串，尝试解析 (作为最后的兼容手段)
        if (dbObject instanceof String) {
            try {
                // 尝试按 LocalDate 的标准格式解析，然后转换为 sql.Date
                LocalDate parsedDate = LocalDate.parse((String) dbObject);
                return Date.valueOf(parsedDate);
            } catch (Exception e) {
                // 如果解析失败，可以记录警告
                // log.warn("无法将字符串 '{}' 解析为 java.sql.Date", dbObject);
            }
        }
        
        // 如果所有转换都失败，抛出异常
        throw new UnsupportedOperationException("无法将类型 " + dbObject.getClass().getName() + " 的值转换为 java.sql.Date。");
    }
}
