package com.datafusion.common.type;

/**
 * 数据库字段类型转换接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
public interface TypeInfoConverter {
    
    /**
     * 转换为目标库类型.
     *
     * @param typeInfo 字段类型对象
     * @return TypeInfo
     */
    TypeInfo convertTypeInfo(TypeInfo typeInfo);
}
