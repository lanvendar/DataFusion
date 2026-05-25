package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * StarRocks 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class StarrocksType extends AbstractType {
    
    /**
     * 字段类型定义.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考:
     * <a href="https://docs.starrocks.io/zh/docs/sql-reference/data-types" />
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.TINYINT, Pair.of(new String[] {"TINYINT"}, true));
        map.put(DataType.SMALLINT, Pair.of(new String[] {"SMALLINT"}, true));
        map.put(DataType.INTEGER, Pair.of(new String[] {"INT"}, true));
        map.put(DataType.BIGINT, Pair.of(new String[] {"BIGINT"}, true));
        //TODO LARGEINT 16 字节有符号整数
        map.put(DataType.DECIMAL, Pair.of(new String[] {"DECIMAL"}, true));
        map.put(DataType.FLOAT, Pair.of(new String[] {"FLOAT"}, true));
        map.put(DataType.REAL, Pair.of(new String[] {"DECIMAL"}, false));
        map.put(DataType.DOUBLE, Pair.of(new String[] {"DOUBLE"}, true));
        map.put(DataType.BOOLEAN, Pair.of(new String[] {"BOOLEAN"}, true));
        // 字符文本
        map.put(DataType.CHAR, Pair.of(new String[] {"CHAR"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"VARCHAR"}, true));
        map.put(DataType.STRING, Pair.of(new String[] {"STRING"}, true));
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"DATETIME"}, true));
        // 二进制
        map.put(DataType.BINARY, Pair.of(new String[] {"BINARY"}, true));
        map.put(DataType.VARBINARY, Pair.of(new String[] {"VARBINARY"}, true));
        // 对象集合
        map.put(DataType.UUID, Pair.of(new String[] {"VARCHAR"}, true));
        map.put(DataType.ARRAY, Pair.of(new String[] {"ARRAY"}, true));
        map.put(DataType.JSON, Pair.of(new String[] {"JSON"}, true));
        map.put(DataType.MAP, Pair.of(new String[] {"MAP"}, true));
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.STARROCKS;
    }
    
    @Override
    public Integer getLength(DataType dataType, Integer length) {
        //代表将DataType中,UUID,本身没有长度的,但是转化后以后有长度的
        switch (dataType) {
            case UUID:
                return 36;
            //starrocks中,datetime类型,不能带精度啥的
            case TIMESTAMP:
                return null;
            default:
                return length;
        }
    }
    
    @Override
    public Pair<Integer, Integer> getPrecisionAndScale(DataType dataType, Integer precision, Integer scale) {
        switch (dataType) {
            //目前,precision,scale,除了decimal中,其他precision并无作用,
            case DECIMAL:
                return Pair.of(precision, scale);
            default:
                return Pair.of(null, null);
        }
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().dataType(DataType.STRING).javaType(String.class).fieldType("STRING").length(65535).precision(null).scale(null)
                .build();
    }
}
