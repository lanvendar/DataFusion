package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * Sqlserver 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class SqlserverType extends AbstractType {
    
    /**
     * 字段类型定义.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考: 官方未找到
     * <a href="https://blog.csdn.net/weixin_39604653/article/details/144277964" />
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.TINYINT, Pair.of(new String[] {"TINYINT"}, true));
        map.put(DataType.SMALLINT, Pair.of(new String[] {"SHORT"}, true));
        map.put(DataType.INTEGER, Pair.of(new String[] {"INT"}, true));
        map.put(DataType.BIGINT, Pair.of(new String[] {"BIGINT"}, true));
        map.put(DataType.DECIMAL, Pair.of(new String[] {"DECIMAL", "NUMERIC"}, true));
        map.put(DataType.FLOAT, Pair.of(new String[] {"FLOAT"}, true));
        map.put(DataType.REAL, Pair.of(new String[] {"REAL"}, true));
        // 字符文本
        map.put(DataType.CHAR, Pair.of(new String[] {"CHAR"}, true));
        map.put(DataType.VARCHAR, Pair.of(new String[] {"VARCHAR"}, true));
        map.put(DataType.STRING, Pair.of(new String[] {"TEXT"}, true));
        // TODO nchar, nvarchar, ntext
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        map.put(DataType.TIME, Pair.of(new String[] {"TIME"}, true));
        map.put(DataType.TIMESTAMP, Pair.of(new String[] {"DATETIME2", "DATETIME"}, true));
        // 二进制
        map.put(DataType.BINARY, Pair.of(new String[] {"BINARY"}, true));
        map.put(DataType.VARBINARY, Pair.of(new String[] {"VARBINARY"}, true));
        // 对象集合
        map.put(DataType.UUID, Pair.of(new String[] {"UNIQUEIDENTIFIER"}, true));
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.SQLSERVER;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().dataType(DataType.STRING).javaType(String.class).fieldType("TEXT").length(null).precision(null).scale(null).build();
    }
}
