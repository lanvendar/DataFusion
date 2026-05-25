package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * Oracle 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class OracleType extends AbstractType {
    /**
     * 字段类型定义.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考: 官方未找到
     * <a href="https://blog.csdn.net/reggie97/article/details/145280199" />
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.INTEGER, Pair.of(new String[] {"INT", "INTEGER"}, true));
        map.put(DataType.BIGINT, Pair.of(new String[] {"LONG"}, true));
        map.put(DataType.DECIMAL, Pair.of(new String[] {"NUMBER", "DECIMAL"}, true));
        map.put(DataType.FLOAT, Pair.of(new String[] {"FLOAT"}, true));
        map.put(DataType.REAL, Pair.of(new String[] {"REAL"}, true));
        // 字符文本
        map.put(DataType.CHAR, Pair.of(new String[] {"CHAR"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"VARCHAR2"}, true));
        // TODO NCHAR,NVARCHAR2,Unicode字符集
        map.put(DataType.STRING, Pair.of(new String[] {"CLOB"}, true));
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"TIMESTAMP"}, true));
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.ORACLE;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder()
                .dataType(DataType.VARCHAR)//
                .javaType(String.class)//
                .fieldType("VARCHAR2")//
                .length(1000)//
                .precision(null)//
                .scale(null)//
                .build();
    }
    
}
