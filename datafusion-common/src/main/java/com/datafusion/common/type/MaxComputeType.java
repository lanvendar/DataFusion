package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * MaxCompute 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class MaxComputeType extends AbstractType {
    
    /**
     * 字段类型定义.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考:
     * <a href="https://help.aliyun.com/zh/maxcompute/user-guide/data-types/?spm=a2c4g.11186623.help-menu-27797.d_2_15_0.2b625184reCPYY" />
     * <a href="https://help.aliyun.com/zh/maxcompute/user-guide/maxcompute-v2-0-data-type-edition?spm=a2c4g.11186623.help-menu-27797.d_2_15_0_2.22e95184j1OVGV&scm=20140722.H_159541._.OR_help-T_cn~zh-V_1" />
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
        map.put(DataType.CHAR, Pair.of(new String[] {"STRING"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"STRING"}, true));
        map.put(DataType.UUID, Pair.of(new String[] {"STRING"}, true));
        map.put(DataType.STRING, Pair.of(new String[] {"STRING"}, true));
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        map.put(DataType.TIME, Pair.of(new String[] {"DATETIME"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"TIMESTAMP", "TIMESTAMP_NTZ"}, true));
        // TODO INTERVAL,maxcompute has this type
        // 二进制
        map.put(DataType.BOOLEAN, Pair.of(new String[] {"BOOLEAN"}, true));
        map.put(DataType.VARBINARY, Pair.of(new String[] {"BINARY"}, true));
        // 对象集合
        map.put(DataType.JSON, Pair.of(new String[] {"JSON"}, true));
        map.put(DataType.OBJECT, Pair.of(new String[] {"STRUCTS"}, true));
        map.put(DataType.MAP, Pair.of(new String[] {"MAP"}, true));
        map.put(DataType.ARRAY, Pair.of(new String[] {"ARRAY"}, true));
    }
    
    @Override
    public Pair<Integer, Integer> getPrecisionAndScale(DataType dataType, Integer precision, Integer scale) {
        switch (dataType) {
            case DECIMAL:
                return Pair.of(precision, scale);
            default:
                return Pair.of(null, null);
        }
    }
    
    @Override
    public Integer getLength(DataType dataType, Integer length) {
        //maxcompute中,字符串类型,全都是string
        return null;
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.MAXCOMPUTE;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().dataType(DataType.STRING).javaType(String.class).fieldType("STRING").length(null).precision(null).scale(null)
                .build();
    }
    
}
