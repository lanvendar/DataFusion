package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * Paimon 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class PaimonType extends AbstractType {
    
    /**
     * 数据库字段类型定义.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考:
     * <a href="https://paimon.apache.org/docs/1.0/concepts/data-types/" />
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.TINYINT, Pair.of(new String[] {"TINYINT"}, true));
        map.put(DataType.SMALLINT, Pair.of(new String[] {"SMALLINT"}, true));
        map.put(DataType.INTEGER, Pair.of(new String[] {"INT"}, true));
        map.put(DataType.BIGINT, Pair.of(new String[] {"BIGINT"}, true));
        map.put(DataType.FLOAT, Pair.of(new String[] {"FLOAT"}, true));
        map.put(DataType.DOUBLE, Pair.of(new String[] {"DOUBLE"}, true));
        map.put(DataType.DECIMAL, Pair.of(new String[] {"DECIMAL"}, true));
        // 字符文本
        map.put(DataType.CHAR, Pair.of(new String[] {"CHAR"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"VARCHAR"}, true));
        map.put(DataType.STRING, Pair.of(new String[] {"STRING"}, true));
        // 日期时间
        map.put(DataType.TIME, Pair.of(new String[] {"TIME"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"TIMESTAMP"}, true));
        // 二进制
        map.put(DataType.BINARY, Pair.of(new String[] {"BINARY"}, true));
        map.put(DataType.VARBINARY, Pair.of(new String[] {"VARBINARY", "BYTES"}, true));
        // 布尔
        map.put(DataType.BOOLEAN, Pair.of(new String[] {"BOOLEAN"}, true));
        // 对象集合
        map.put(DataType.ARRAY, Pair.of(new String[] {"ARRAY"}, true));
        map.put(DataType.MAP, Pair.of(new String[] {"MAP"}, true));
        // TODO MULTISET<t>
        map.put(DataType.OBJECT, Pair.of(new String[] {"ROW"}, true));
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.PAIMON;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().javaType(String.class).fieldType("STRING").length(null).precision(null).scale(null).build();
    }
}
