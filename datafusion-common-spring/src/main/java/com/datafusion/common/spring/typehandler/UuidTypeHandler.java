package com.datafusion.common.spring.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * UUID数据类型handler.
 *
 * @author lanvendar
 * @version 1.0.0, 2021/2/25
 * @since 2021/2/25
 */
@MappedTypes(UUID.class)
// 明确映射到PostgreSQL的UUID类型，或通用OTHER
// 如果你的PG数据库字段是uuid类型，那么JDBC_TYPE应该是OTHER
@MappedJdbcTypes(value = {JdbcType.OTHER, JdbcType.CHAR}, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        // 对于PostgreSQL的UUID类型，直接设置Object通常是正确的
        // JDBC驱动会负责将其映射到数据库的UUID类型
        // 如果parameter是UUID对象，并且数据库列是UUID类型，ps.setObject会自动处理
        ps.setObject(i, parameter);
    }
    
    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // PostgreSQL可能返回PGobject或直接UUID对象
        Object obj = rs.getObject(columnName);
        if (obj == null) {
            return null;
        }
        if (obj instanceof UUID) {
            return (UUID) obj;
        }
        // 如果返回的是PGobject，通常其value是UUID的字符串形式
        if (obj instanceof PGobject) {
            PGobject pgObject = (PGobject) obj;
            if ("uuid".equals(pgObject.getType()) && pgObject.getValue() != null) {
                return UUID.fromString(pgObject.getValue());
            }
        }
        // Fallback to String conversion if other methods fail or if it's stored as text
        String uuidString = rs.getString(columnName);
        return uuidString == null ? null : UUID.fromString(uuidString);
    }
    
    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        if (obj == null) {
            return null;
        }
        if (obj instanceof UUID) {
            return (UUID) obj;
        }
        if (obj instanceof PGobject) {
            PGobject pgObject = (PGobject) obj;
            if ("uuid".equals(pgObject.getType()) && pgObject.getValue() != null) {
                return UUID.fromString(pgObject.getValue());
            }
        }
        String uuidString = rs.getString(columnIndex);
        return uuidString == null ? null : UUID.fromString(uuidString);
    }
    
    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        if (obj == null) {
            return null;
        }
        if (obj instanceof UUID) {
            return (UUID) obj;
        }
        if (obj instanceof PGobject) {
            PGobject pgObject = (PGobject) obj;
            if ("uuid".equals(pgObject.getType()) && pgObject.getValue() != null) {
                return UUID.fromString(pgObject.getValue());
            }
        }
        String uuidString = cs.getString(columnIndex);
        return uuidString == null ? null : UUID.fromString(uuidString);
    }
}