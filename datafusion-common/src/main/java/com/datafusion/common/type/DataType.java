package com.datafusion.common.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Types;

/**
 * 标准字段类型(dataType)枚举,与数据库字段类型(fieldType)为一对多关系.
 *
 * @author lanvendar
 * @version 3.0.0, 2025/03/22
 * @since 2025/03/22
 */
@Getter
@AllArgsConstructor
public enum DataType {
    //-----------------------------------数值类型------------------------------//
    /**
     * 8位整数.
     */
    TINYINT(PrecScale.NO_NO, Types.TINYINT, DataTypeFamily.NUMERIC),
    /**
     * 16位整数.
     */
    SMALLINT(PrecScale.NO_NO, Types.SMALLINT, DataTypeFamily.NUMERIC),
    /**
     * 32位整数.
     */
    INTEGER(PrecScale.NO_NO, Types.INTEGER, DataTypeFamily.NUMERIC),
    /**
     * 64位整数.
     */
    BIGINT(PrecScale.NO_NO, Types.BIGINT, DataTypeFamily.NUMERIC),
    /**
     * 可变精度的精确值.
     */
    DECIMAL(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES, Types.DECIMAL, DataTypeFamily.NUMERIC),
    /**
     * 单精度浮点数,4或8字节.
     */
    FLOAT(PrecScale.NO_NO, Types.FLOAT, DataTypeFamily.NUMERIC),
    /**
     * 单精度浮点数,4字节.
     */
    REAL(PrecScale.NO_NO | PrecScale.YES_NO, Types.REAL, DataTypeFamily.NUMERIC),
    /**
     * 双精度浮点数.
     */
    DOUBLE(PrecScale.NO_NO, Types.DOUBLE, DataTypeFamily.NUMERIC),
    
    //-----------------------------------字符串类型------------------------------//
    /**
     * 固定长度字符串.
     */
    CHAR(PrecScale.NO_NO | PrecScale.YES_NO, Types.CHAR, DataTypeFamily.TEXT),
    /**
     * 可变长度字符串.
     */
    VARCHAR(PrecScale.NO_NO | PrecScale.YES_NO, Types.VARCHAR, DataTypeFamily.TEXT),
    /**
     * LONGVARCHAR 可变长度字符串.
     */
    STRING(PrecScale.NO_NO | PrecScale.YES_NO, Types.LONGNVARCHAR, DataTypeFamily.TEXT),
    //-----------------------------------日期和时间类型------------------------------//
    /**
     * 日期（年、月、日）.
     */
    DATE(PrecScale.NO_NO, Types.DATE, DataTypeFamily.DATE),
    /**
     * 时间（时、分、秒）.
     */
    TIME(PrecScale.NO_NO | PrecScale.YES_NO, Types.TIME, DataTypeFamily.DATE),
    /**
     * 日期和时间（年、月、日、时、分、秒）.
     */
    TIMESTAMP(PrecScale.NO_NO | PrecScale.YES_NO, Types.TIMESTAMP, DataTypeFamily.DATE),
    //-----------------------------------其他特殊类型------------------------------//
    /**
     * 布尔类型.
     */
    BOOLEAN(PrecScale.NO_NO, Types.BOOLEAN, DataTypeFamily.BOOLEAN),
    /**
     * 固定长度二进制数据类型.
     */
    BINARY(PrecScale.NO_NO | PrecScale.YES_NO, Types.BINARY, DataTypeFamily.BINARY),
    /**
     * 可变长度二进制数据类型.
     */
    VARBINARY(PrecScale.NO_NO | PrecScale.YES_NO, Types.VARBINARY, DataTypeFamily.BINARY),
    /**
     * UUID通用唯一标识符.
     */
    UUID(PrecScale.NO_NO, Types.OTHER, DataTypeFamily.UUID),
    /**
     * 任意对象类型.
     */
    OBJECT(PrecScale.NO_NO | PrecScale.YES_NO | PrecScale.YES_YES, Types.JAVA_OBJECT, DataTypeFamily.OBJECT),
    /**
     * 数组类型.
     */
    ARRAY(PrecScale.NO_NO, Types.ARRAY, DataTypeFamily.ARRAY),
    /**
     * MAP类型.
     */
    MAP(PrecScale.NO_NO, Types.OTHER, DataTypeFamily.MAP),
    /**
     * JSON格式的数据.
     */
    JSON(PrecScale.NO_NO, Types.OTHER, DataTypeFamily.JSON);
    
    /**
     * Bitwise-or of flags indicating allowable precision/scale combinations.
     */
    private final int signatures;
    
    /**
     * 数据库类型.
     */
    private final int sqlType;
    
    /**
     * 数据类型家族.
     */
    private final DataTypeFamily dataTypeFamily;
    
    /**
     * Flags indicating precision/scale combinations.
     *
     * <p>Note: for intervals:
     *
     * <ul>
     * <li>precision = start (leading field) precision</li>
     * <li>scale = fractional second precision</li>
     * </ul>
     */
    private interface PrecScale {
        /**
         * Precision/scale is not applicable.
         */
        int NO_NO = 1;

        /**
         * Precision is applicable, scale is not.
         */
        int YES_NO = 2;

        /**
         * Precision/scale is applicable.
         */
        int YES_YES = 4;
    }
}
