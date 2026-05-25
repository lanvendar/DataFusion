package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * postgres 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
@AutoService(Type.class)
public class PostgresType extends AbstractType {
    /**
     * 字段类型定义.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考:
     * <a href="https://www.postgresql.org/docs/12/datatype.html" />
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.TINYINT, Pair.of(new String[] {"SMALLINT"}, false));
        map.put(DataType.SMALLINT, Pair.of(new String[] {"SMALLINT", "INT2", "SMALLSERIAL", "SERIAL2"}, true));
        map.put(DataType.INTEGER, Pair.of(new String[] {"INTEGER", "INT4", "SERIAL", "SERIAL4"}, true));
        map.put(DataType.BIGINT, Pair.of(new String[] {"BIGINT", "INT8", "BIGSERIAL", "SERIAL8"}, true));
        map.put(DataType.DECIMAL, Pair.of(new String[] {"DECIMAL", "NUMERIC"}, true));
        map.put(DataType.REAL, Pair.of(new String[] {"REAL"}, true)); //6 decimal digits precision
        map.put(DataType.DOUBLE, Pair.of(new String[] {"FLOAT8", "DOUBLE PRECISION", "MONEY"}, true));
        map.put(DataType.FLOAT, Pair.of(new String[] {"FLOAT4"}, true));
        // 字符文本
        map.put(DataType.CHAR, Pair.of(new String[] {"CHAR", "CHARACTER", "BPCHAR"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"VARCHAR", "CHARACTER VARYING"}, true));
        // TODO name,64 bytes,internal type for object names
        map.put(DataType.STRING, Pair.of(new String[] {"TEXT"}, true));
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        map.put(DataType.TIME, Pair.of(new String[] {"TIME"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"TIMESTAMP"}, true));
        // TODO interval,16 bytes,time interval
        // 二进制
        map.put(DataType.BINARY, Pair.of(new String[] {"BIT", "BIT VARYING"}, true));
        map.put(DataType.VARBINARY, Pair.of(new String[] {"BYTEA"}, true));
        // 对象集合
        map.put(DataType.BOOLEAN, Pair.of(new String[] {"BOOLEAN", "BOOL"}, true));
        map.put(DataType.UUID, Pair.of(new String[] {"UUID"}, true));
        map.put(DataType.JSON, Pair.of(new String[] {"JSON", "JSONB"}, true));
        // TODO Arrays 暂不支持: select format_type(atttypid, atttypmod) from pg_attribute 但还是有维度缺失
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.POSTGRES;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().dataType(DataType.STRING).javaType(String.class).fieldType("TEXT").length(null).precision(null).scale(null).build();
    }
}
