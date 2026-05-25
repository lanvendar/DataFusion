package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.datafusion.common.enums.DatabaseTypeEnum;

import java.util.Map;
import java.util.Set;

/**
 * 数据库类型定义.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
public interface Type {
    /**
     * 返回此类型定义所支持的数据库类型枚举.
     * 这是实现SPI动态发现的关键.
     *
     * @return 数据库类型枚举
     */
    DatabaseTypeEnum getSupportedDatabase();
    
    /**
     * 获取数据库字段类型定义.
     *
     * @return 字段类型
     */
    Map<String, DataType> getFieldType();
    
    /**
     * 获取数据库字段类型定义.
     *
     * @return 字段类型
     */
    Set<String> getFieldTypeList();
    
    /**
     * 获取标准字段类型定义.
     *
     * @return 数据库类型定义
     */
    Map<DataType, Pair<String[], Boolean>> getDataType();
    
    /**
     * 获取字段对应的java类型.
     * TODO 入参 dataType 是否为 fieldType 时更合理 ?
     *
     * @param dataType 标准类型枚举
     * @return java类型
     */
    java.lang.reflect.Type getJavaType(DataType dataType);
    
    /**
     * 获取字段默认长度.
     * TODO 入参 dataType 是否为 fieldType 时更合理 ?
     *
     * @param dataType 标准类型枚举
     * @param length   当前字段长度
     * @return 默认长度
     */
    Integer getLength(DataType dataType, Integer length);
    
    /**
     * 获取字段默认有效长度和小数长度.
     * TODO 入参 dataType 是否为 fieldType 时更合理 ?
     *
     * @param dataType  标准类型枚举
     * @param precision 当前有效长度
     * @param scale     当前小数长度
     * @return {@code Pair<Integer, Integer>,<有效长度,小数长度>}
     */
    Pair<Integer, Integer> getPrecisionAndScale(DataType dataType, Integer precision, Integer scale);
    
    /**
     * 获取默认兜底类型.
     *
     * @return 默认兜底类型
     */
    TypeInfo getDefaultType();
}
