package com.datafusion.plugin.kafka.json.sink;

import org.apache.paimon.data.BinaryString;
import org.apache.paimon.data.Decimal;
import org.apache.paimon.data.Timestamp;
import org.apache.paimon.types.DataType;
import org.apache.paimon.types.DataTypeRoot;
import org.apache.paimon.types.DecimalType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Paimon 字段值转换器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class PaimonValueConverter {

    /**
     * 日期时间格式.
     */
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 类型转换器映射.
     */
    private static final Map<DataTypeRoot, ValueConverter> CONVERTERS = converters();

    private PaimonValueConverter() {
    }

    /**
     * 转换字段值.
     *
     * @param value 原始值
     * @param type Paimon 类型
     * @return Paimon 内部值
     */
    public static Object convert(Object value, DataType type) {
        if (value == null) {
            return null;
        }
        ValueConverter converter = CONVERTERS.get(type.getTypeRoot());
        if (converter == null) {
            return toBinaryString(value);
        }
        return converter.convert(value, type);
    }

    private static Map<DataTypeRoot, ValueConverter> converters() {
        Map<DataTypeRoot, ValueConverter> converters = new EnumMap<>(DataTypeRoot.class);
        converters.put(DataTypeRoot.VARCHAR, (value, type) -> toBinaryString(value));
        converters.put(DataTypeRoot.CHAR, (value, type) -> toBinaryString(value));
        converters.put(DataTypeRoot.INTEGER, (value, type) -> value instanceof Number number
                ? number.intValue() : Integer.parseInt(String.valueOf(value)));
        converters.put(DataTypeRoot.BIGINT, (value, type) -> value instanceof Number number
                ? number.longValue() : Long.parseLong(String.valueOf(value)));
        converters.put(DataTypeRoot.DOUBLE, (value, type) -> value instanceof Number number
                ? number.doubleValue() : Double.parseDouble(String.valueOf(value)));
        converters.put(DataTypeRoot.FLOAT, (value, type) -> value instanceof Number number
                ? number.floatValue() : Float.parseFloat(String.valueOf(value)));
        converters.put(DataTypeRoot.BOOLEAN, (value, type) -> toBoolean(value));
        converters.put(DataTypeRoot.DECIMAL, PaimonValueConverter::toDecimal);
        converters.put(DataTypeRoot.DATE, (value, type) -> (int) LocalDate.parse(String.valueOf(value)).toEpochDay());
        converters.put(DataTypeRoot.TIMESTAMP_WITHOUT_TIME_ZONE, (value, type) ->
                Timestamp.fromLocalDateTime(parseDateTime(String.valueOf(value))));
        return converters;
    }

    private static Decimal toDecimal(Object value, DataType type) {
        DecimalType decimalType = (DecimalType) type;
        return Decimal.fromBigDecimal(new BigDecimal(String.valueOf(value)), decimalType.getPrecision(), decimalType.getScale());
    }

    private static BinaryString toBinaryString(Object value) {
        return BinaryString.fromString(String.valueOf(value));
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).toLowerCase(Locale.ROOT);
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw new IllegalArgumentException("Unsupported boolean value: " + value);
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value.length() == 19 && value.charAt(10) == ' ') {
            return LocalDateTime.parse(value, DATETIME_FORMATTER);
        }
        return LocalDateTime.parse(value);
    }

    /**
     * 字段值转换函数.
     */
    private interface ValueConverter {

        /**
         * 转换字段值.
         *
         * @param value 原始值
         * @param type Paimon 类型
         * @return Paimon 内部值
         */
        Object convert(Object value, DataType type);
    }
}
