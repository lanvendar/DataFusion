package com.datafusion.common.type;

/**
 * 数据库字段类型格式化接口.
 *
 * @author lanvendar
 * @version 1.0.0, 2025/3/24
 * @since 2025/3/24
 */
public interface TypeInfoParser {
    /**
     * 解析完整的字段类型字符串.
     *
     * @param fieldType 来自数据库的原始字段类型，例如 "VARCHAR(255)"中的 VARCHAR, "DECIMAL(10, 2)中的 DECIMAL".
     * @param length    字段长度，例如 VARCHAR(255) 中的 255.
     * @param precision 字段精度，例如 DECIMAL(10, 2) 中的 10.
     * @param scale     字段精度，例如 DECIMAL(10, 2) 中的 2.
     * @return 一个标准化的 TypeInfo 对象.
     */
    TypeInfo parse(String fieldType, Integer length, Integer precision, Integer scale);
    
    /**
     * 解析完整的字段类型字符串.
     *
     * @param fieldType 来自数据库的原始字段类型，例如 "VARCHAR(255)"中的 VARCHAR, "DECIMAL(10, 2)中的 DECIMAL".
     * @param precision 字段精度，例如 DECIMAL(10, 2) 中的 10.
     * @param scale     字段精度，例如 DECIMAL(10, 2) 中的 2.
     * @return 一个标准化的 TypeInfo 对象.
     */
    TypeInfo parse(String fieldType, Integer precision, Integer scale);
    
    /**
     * 解析完整的字段类型字符串.
     *
     * @param fieldType 来自数据库的原始字段类型，例如 "VARCHAR(255)"中的 VARCHAR, "DECIMAL(10, 2)中的 DECIMAL".
     * @param length    字段长度，例如 VARCHAR(255) 中的 255.
     * @return 一个标准化的 TypeInfo 对象。
     */
    TypeInfo parse(String fieldType, Integer length);
    
    /**
     * 解析完整的字段类型字符串.
     *
     * @param fullFieldType 来自数据库的原始字段类型，例如 "VARCHAR(255)", "DECIMAL(10, 2)".
     * @return 一个标准化的 TypeInfo 对象。
     */
    TypeInfo parse(String fullFieldType);
}
