package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;
import com.google.auto.service.AutoService;

import java.util.Map;

/**
 * excel 类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
@AutoService(Type.class)
public class ExcelType extends AbstractType {
    /**
     * 定义字段类型映射关系.
     * 此处是标准字段类型(dataType)枚举与数据库字段类型(fieldType)的映射定义,dataType与fieldType为一对多关系.
     * 数据库互转默认取String[0]的字段类型,此时存在类型丢失.
     * 参考:
     *
     * @param map 字段类型映射关系
     */
    @Override
    protected void defineMappings(Map<DataType, Pair<String[], Boolean>> map) {
        // 数字
        map.put(DataType.BIGINT, Pair.of(new String[] {"BIGINT"}, true));
        map.put(DataType.DECIMAL, Pair.of(new String[] {"DECIMAL"}, true));
        // 字符文本
        map.put(DataType.STRING, Pair.of(new String[] {"TEXT", "STRING"}, true));
        // 日期时间
        map.put(DataType.DATE, Pair.of(new String[] {"DATE"}, true));
        // 对象集合
        map.put(DataType.BOOLEAN, Pair.of(new String[] {"BOOLEAN"}, true));
        
    }
    
    @Override
    public DatabaseTypeEnum getSupportedDatabase() {
        return DatabaseTypeEnum.EXCEL;
    }
    
    @Override
    public TypeInfo getDefaultType() {
        return TypeInfo.builder().dataType(DataType.STRING).javaType(String.class).fieldType("TEXT").length(null).precision(null).scale(null).build();
    }
}
