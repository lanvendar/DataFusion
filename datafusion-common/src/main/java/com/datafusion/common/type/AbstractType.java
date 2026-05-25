package com.datafusion.common.type;

import cn.hutool.core.lang.Pair;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 类型定义抽象类.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/22
 * @since 2025/3/22
 */
public abstract class AbstractType implements Type {
    /**
     * 按字段类型 String 索引的 Map 集合.
     */
    private final Map<String, DataType> fieldTypeMap;
    
    /**
     * 数据库字段定义.
     * {@code String[], Boolean)} String[]:字段类型枚举,Boolean:true本库支持的标准类型(可转类型);false非本库支持的标准类型(被可转类型);
     */
    private final Map<DataType, Pair<String[], Boolean>> dataTypeMap;
    
    /**
     * 构造函数.
     */
    protected AbstractType() {
        this.dataTypeMap = new EnumMap<>(DataType.class);
        // 让子类填充这个Map
        defineMappings(this.dataTypeMap);
        
        // 初始化逻辑统一放在父类
        this.fieldTypeMap = new HashMap<>();
        
        //字段类型定义索引,重复fieldType时,标准类型只有一个为true.
        for (Map.Entry<DataType, Pair<String[], Boolean>> entry : dataTypeMap.entrySet()) {
            Pair<String[], Boolean> value = entry.getValue();
            if (Boolean.FALSE.equals(value.getValue())) {
                continue;
            }
            for (String type : value.getKey()) {
                fieldTypeMap.put(type, entry.getKey());
            }
        }
    }
    
    /**
     * 子类必须实现此方法来提供自己的类型映射定义.
     */
    protected abstract void defineMappings(Map<DataType, Pair<String[], Boolean>> map);
    
    @Override
    public Map<String, DataType> getFieldType() {
        return Collections.unmodifiableMap(this.fieldTypeMap);
    }
    
    @Override
    public Set<String> getFieldTypeList() {
        return dataTypeMap.values().stream() // 获取所有 Pair<String[], Boolean>
                .filter(Pair::getValue) // 只有值为 true 的 Pair 才是本数据库支持的类型
                .flatMap(pair -> Arrays.stream(pair.getKey())) // 将每个 String[] 扁平化为一个 String 流
                .collect(Collectors.toSet()); // 收集到 Set 中,自动去重
    }
    
    @Override
    public Map<DataType, Pair<String[], Boolean>> getDataType() {
        return Collections.unmodifiableMap(this.dataTypeMap);
    }
    
    /**
     * 获取字段对应的java类型.
     *
     * @param dataType 标准类型枚举
     * @return java类型
     */
    @Override
    public java.lang.reflect.Type getJavaType(DataType dataType) {
        switch (dataType) {
            case TINYINT:
            case SMALLINT:
                return Short.class;
            case INTEGER:
                return Integer.class;
            case BIGINT:
                return Long.class;
            case DECIMAL:
                return BigDecimal.class;
            case FLOAT:
            case REAL:
                return Float.class;
            case DOUBLE:
                return Double.class;
            case CHAR:
            case VARCHAR:
            case STRING:
                return String.class;
            case DATE:
                return Date.class;
            case TIME:
                return LocalDate.class;
            case TIMESTAMP:
                return LocalDateTime.class;
            case BOOLEAN:
                return Boolean.class;
            case BINARY:
            case VARBINARY:
                return Byte.class;
            case UUID:
                return UUID.class;
            case OBJECT:
                return Object.class;
            case ARRAY:
                return List.class;
            case MAP:
                return Map.class;
            case JSON:
                return JsonNode.class;
            default:
                return String.class;
        }
    }
    
    /**
     * 获取字段默认长度.
     *
     * @param dataType 标准类型枚举
     * @param length   当前字段长度
     * @return 该字段支持的长度
     */
    @Override
    public Integer getLength(DataType dataType, Integer length) {
        return length;
    }
    
    /**
     * 获取字段默认有效长度和小数长度.
     *
     * @param dataType  标准类型枚举
     * @param precision 当前有效长度
     * @param scale     当前小数长度
     * @return {@code Pair<Integer, Integer>,<有效长度,小数长度>}
     */
    @Override
    public Pair<Integer, Integer> getPrecisionAndScale(DataType dataType, Integer precision, Integer scale) {
        return Pair.of(precision, scale);
    }
}
