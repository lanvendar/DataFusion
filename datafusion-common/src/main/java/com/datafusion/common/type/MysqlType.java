package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * Mysql 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class MysqlType extends AbstractType {
    
    /**
     * 定义字段类型映射关系.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考:
     * <a href="https://dev.mysql.com/doc/refman/8.4/en/data-types.html" />
     *
     * @param map 字段类型映射关系
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.TINYINT, Pair.of(new String[] {"TINYINT", "TINYINT UNSIGNED"}, true));
        map.put(DataType.SMALLINT, Pair.of(new String[] {"SMALLINT", "SMALLINT UNSIGNED"}, true));
        map.put(DataType.INTEGER, Pair.of(new String[] {"INT", "INT UNSIGNED", "MEDIUMINT", "MEDIUMINT UNSIGNED"}, true));
        map.put(DataType.BIGINT, Pair.of(new String[] {"BIGINT", "BIGINT UNSIGNED"}, true));
        map.put(DataType.DECIMAL, Pair.of(new String[] {"DECIMAL", "NUMERIC"}, true));
        map.put(DataType.FLOAT, Pair.of(new String[] {"FLOAT"}, true));
        map.put(DataType.REAL, Pair.of(new String[] {"REAL"}, true));
        map.put(DataType.DOUBLE, Pair.of(new String[] {"DOUBLE", "DOUBLE PRECISION"}, true));
        // 字符文本
        map.put(DataType.CHAR, Pair.of(new String[] {"CHAR"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"VARCHAR"}, true));
        map.put(DataType.STRING, Pair.of(new String[] {"LONGTEXT", "TEXT", "MEDIUMTEXT", "TINYTEXT"}, true));
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"TIMESTAMP", "DATETIME"}, true));
        // 二进制
        map.put(DataType.BINARY, Pair.of(new String[] {"VARBINARY", "BINARY"}, true));
        map.put(DataType.VARBINARY, Pair.of(new String[] {"LONGBLOB", "TINYBLOB", "BLOB", "MEDIUMBLOB"}, true));
        // 对象集合
        map.put(DataType.BOOLEAN, Pair.of(new String[] {"BOOL"}, true));
        map.put(DataType.UUID, Pair.of(new String[] {"VARCHAR"}, true));
        map.put(DataType.JSON, Pair.of(new String[] {"JSON"}, true));
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.MYSQL;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().dataType(DataType.STRING).javaType(String.class).fieldType("TEXT").length(null).precision(null).scale(null).build();
    }
}
