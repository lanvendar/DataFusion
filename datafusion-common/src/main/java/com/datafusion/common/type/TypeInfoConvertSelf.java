package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import lombok.extern.slf4j.Slf4j;

/**
 * 同一个互转的数据库类型标准化和转换实现类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
@Slf4j
public class TypeInfoConvertSelf implements TypeInfoConverter {
    /**
     * 数据库类型定义.
     */
    private final Type selfType;
    
    /**
     * 构造函数.
     *
     * @param selfType 数据库类型
     */
    public TypeInfoConvertSelf(Type selfType) {
        this.selfType = selfType;
    }
    
    @Override
    public TypeInfo convertTypeInfo(TypeInfo typeInfo) {
        DataType dataType = selfType.getFieldType().get(typeInfo.getFieldType().toUpperCase());
        if (dataType == null) {
            log.warn("字段类型[{}]不支持自转", typeInfo.getFieldType());
            return selfType.getDefaultType();
        }
        // 自转,标准化字段类型
        typeInfo.setDataType(dataType);
        typeInfo.setJavaType(selfType.getJavaType(dataType));
        typeInfo.setFieldType(selfType.getDataType().get(dataType).getKey()[0]);
        typeInfo.setLength(selfType.getLength(dataType, typeInfo.getLength()));
        Pair<Integer, Integer> precisionAndScale = selfType.getPrecisionAndScale(dataType, typeInfo.getPrecision(), typeInfo.getScale());
        typeInfo.setPrecision(precisionAndScale.getKey());
        typeInfo.setScale(precisionAndScale.getValue());
        return typeInfo;
    }
}
