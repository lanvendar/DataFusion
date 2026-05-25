package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import lombok.extern.slf4j.Slf4j;

/**
 * 两两互转的数据库类型标准化和转换实现类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
@Slf4j
public class TypeInfoConvertSwitch implements TypeInfoConverter {
    /**
     * 数据库类型.
     */
    private final Type sourceType;
    
    /**
     * 目标数据库类型.
     */
    private final Type targetType;
    
    /**
     * 构造函数.
     *
     * @param sourceDbType 数据库类型
     * @param targetDbType 目标数据库类型
     */
    public TypeInfoConvertSwitch(Type sourceDbType, Type targetDbType) {
        this.sourceType = sourceDbType;
        this.targetType = targetDbType;
    }
    
    @Override
    public TypeInfo convertTypeInfo(TypeInfo typeInfo) {
        DataType dataType = sourceType.getFieldType().get(typeInfo.getFieldType().toUpperCase());
        if (null == dataType || null == targetType.getDataType().get(dataType)) {
            log.warn("字段类型[{}]不支持映射", typeInfo.getFieldType());
            return sourceType.getDefaultType();
        }
        
        // 构建TypeInfo对象
        typeInfo.setDataType(dataType);
        typeInfo.setJavaType(targetType.getJavaType(dataType));
        typeInfo.setFieldType(targetType.getDataType().get(dataType).getKey()[0]);
        typeInfo.setLength(targetType.getLength(dataType, typeInfo.getLength()));
        Pair<Integer, Integer> precisionAndScale = targetType.getPrecisionAndScale(dataType, typeInfo.getPrecision(), typeInfo.getScale());
        typeInfo.setPrecision(precisionAndScale.getKey());
        typeInfo.setScale(precisionAndScale.getValue());
        return typeInfo;
    }
}
