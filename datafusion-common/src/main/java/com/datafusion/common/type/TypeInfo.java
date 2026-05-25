package com.datafusion.common.type;

import com.datafusion.common.constant.SystemConstant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Type;

/**
 * 字段类型对象.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
@Builder
@Getter
@Setter(AccessLevel.PROTECTED)
public class TypeInfo {
    /**
     * 统一转换类型.
     */
    private DataType dataType;
    
    /**
     * java类型.
     */
    private Type javaType;
    
    /**
     * 数据库字段类型.
     */
    private String fieldType;
    
    /**
     * 字段长度(length).
     * 没有长度时,为 null
     */
    private Integer length;
    
    /**
     * 字段有效长度/精度(precision).
     * 没有精度时,为 null
     */
    private Integer precision;
    
    /**
     * 字段小数位(scale).
     * 没有小数时,为 null
     */
    private Integer scale;
    
    /**
     * 存储字段原始类型.
     */
    private String originalFieldType;
    
    /**
     * 获取java类型简称.
     *
     * @return java类型简称
     */
    public String getSimpleJavaType() {
        String typeName = javaType.getTypeName();
        return typeName.substring(typeName.lastIndexOf(SystemConstant.POINT) + 1);
    }
    
    /**
     * 获取DDL创建语句的字段类型(含字段长度).
     *
     * @return 全字段类型(含字段长度)
     */
    public String getFullFieldType() {
        if (precision != null && precision > 0) {
            if (scale != null && scale > 0) {
                return fieldType + String.format("(%s,%s)", precision, scale);
            }
            return fieldType + String.format("(%s)", precision);
        }
        
        if (length != null) {
            return fieldType + String.format("(%s)", length);
        }
        
        return fieldType;
    }
    
    /**
     * toString.
     *
     * @return string
     */
    @Override
    public String toString() {
        return "TypeInfo{" + "dataType=" + dataType + ", javaType=" + javaType + ", simpleJavaType='" + getSimpleJavaType() + '\''
                + ", fieldType='" + fieldType + '\'' + ", length='" + length + '\'' + ", precision='" + precision + '\''
                + ", scale='" + scale + '\'' + '\'' + ", originalFieldType='" + originalFieldType + '\''
                + ", fullFieldType='" + getFullFieldType() + '}';
    }
}
