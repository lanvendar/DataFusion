package com.datafusion.datasource.resultset.handler.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * UUID 类型处理器.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/8/29
 * @since 2025/8/29
 */
public class UuidTypeHandler implements JdbcTypeHandler<UUID> {
    @Override
    public UUID getResult(ResultSet rs, int columnIndex) throws SQLException {
        // 依然推荐使用 getObject() 作为统一的、最灵活的入口
        Object object = rs.getObject(columnIndex);
        return convertToUuid(object);
    }
    
    @Override
    public UUID getResult(ResultSet rs, String columnName) throws SQLException {
        // 按列名获取
        Object object = rs.getObject(columnName);
        return convertToUuid(object);
    }
    
    /**
     * 将从 JDBC 获取的 Object 转换为 UUID.
     *
     * @param dbObject 从 ResultSet 中获取的对象
     * @return 转换后的 UUID，如果输入为 null 则返回 null
     */
    private UUID convertToUuid(Object dbObject) {
        if (dbObject == null) {
            return null; // 如果数据库值为 NULL，直接返回 null
        }
        
        // 1. 如果驱动直接返回了 UUID 对象 (例如 PostgreSQL 的原生 UUID 类型)
        if (dbObject instanceof UUID) {
            return (UUID) dbObject;
        }
        
        // 2. 如果是字符串，尝试使用 UUID.fromString() 进行解析
        if (dbObject instanceof String) {
            String uuidString = (String) dbObject;
            if (uuidString.trim().isEmpty()) {
                return null; // 将空字符串视作 null
            }
            try {
                return UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                // 如果字符串格式不正确，抛出更明确的异常信息
                throw new IllegalStateException("无法将字符串 '" + uuidString + "' 解析为 UUID。", e);
            }
        }
        
        // 3. 如果是字节数组 (某些数据库可能以 BINARY(16) 存储 UUID)
        if (dbObject instanceof byte[]) {
            byte[] bytes = (byte[]) dbObject;
            if (bytes.length == 16) {
                return bytesToUuid(bytes);
            } else {
                throw new IllegalStateException("字节数组长度必须为 16 才能转换为 UUID，实际长度为: " + bytes.length);
            }
        }
        
        // 如果所有转换都失败，抛出异常
        throw new UnsupportedOperationException("无法将类型 " + dbObject.getClass().getName() + " 的值转换为 UUID。");
    }
    
    /**
     * 将 16 字节的数组转换为 UUID.
     * UUID 由一个 long (most significant bits) 和另一个 long (least significant bits) 组成.
     *
     * @param bytes 包含 16 个字节的数组
     * @return 转换后的 UUID
     */
    private UUID bytesToUuid(byte[] bytes) {
        long msb = 0;
        long lsb = 0;
        // 前 8 个字节是 most significant bits
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xff);
        }
        // 后 8 个字节是 least significant bits
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xff);
        }
        return new UUID(msb, lsb);
    }
}
