package com.datafusion.plugin.flink.schema.paimon.sink;

import com.datafusion.plugin.flink.schema.paimon.core.FlinkSchemaPaimonException;
import com.datafusion.plugin.flink.schema.paimon.message.ColumnConfig;
import com.datafusion.plugin.flink.schema.paimon.resolve.ResolvedTableConfig;
import com.datafusion.plugin.flink.schema.paimon.util.TextUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paimon 写入前记录归一化.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class RecordNormalizer {

    private RecordNormalizer() {
    }

    /**
     * 按字段顺序、默认值、format 和非空约束归一化记录.
     *
     * @param records 原始记录
     * @param tableConfig 目标表配置
     * @return 归一化记录
     */
    public static List<Map<String, Object>> normalize(List<Map<String, Object>> records, ResolvedTableConfig tableConfig) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> record : records) {
            normalized.add(normalizeRecord(record, tableConfig));
        }
        return normalized;
    }

    private static Map<String, Object> normalizeRecord(Map<String, Object> record, ResolvedTableConfig tableConfig) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (ColumnConfig column : tableConfig.columns) {
            Object value = record == null ? null : record.get(column.name);
            if (isEmpty(value) && column.defaultValue != null) {
                value = column.defaultValue;
            }
            if (!isEmpty(value) && !TextUtils.isBlank(column.format)) {
                value = formatValue(column, String.valueOf(value));
            }
            if (isEmpty(value) && Boolean.FALSE.equals(column.nullable)) {
                throw new FlinkSchemaPaimonException(tableConfig.identifier() + " required column is empty: " + column.name);
            }
            normalized.put(column.name, value);
        }
        return normalized;
    }

    private static Object formatValue(ColumnConfig column, String value) {
        String[] formats = column.format.split("->", 2);
        DateTimeFormatter source = DateTimeFormatter.ofPattern(formats[0]);
        DateTimeFormatter target = DateTimeFormatter.ofPattern(formats.length == 2 ? formats[1] : formats[0]);
        String type = TextUtils.upper(column.type, "STRING");
        if ("DATE".equals(type)) {
            try {
                return LocalDateTime.parse(value, source).toLocalDate().format(target);
            } catch (java.time.format.DateTimeParseException e) {
                return LocalDate.parse(value, source).format(target);
            }
        }
        return LocalDateTime.parse(value, source).format(target);
    }

    private static boolean isEmpty(Object value) {
        return value == null || value instanceof String text && TextUtils.isBlank(text);
    }
}
