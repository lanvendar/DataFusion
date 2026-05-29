package com.datafusion.plugin.api.sink;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.ColumnConfig;
import com.datafusion.plugin.api.config.ApiExtractJobConfig.SinkConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.core.Record;
import com.datafusion.plugin.api.util.TextUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sink 写入前记录归一化.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SinkRecordNormalizer {

    private SinkRecordNormalizer() {
    }

    /**
     * 根据 sink.columns 应用格式转换、默认值和非空约束.
     *
     * @param records 原始记录列表
     * @param sink sink 配置
     * @param sinkName sink 名称
     * @return 归一化后的记录列表
     */
    public static List<Record> normalize(List<Record> records, SinkConfig sink, String sinkName) {
        if (sink.columns == null || sink.columns.isEmpty()) {
            return records;
        }
        return records.stream().map(record -> normalizeRecord(record, sink, sinkName)).toList();
    }

    private static Record normalizeRecord(Record record, SinkConfig sink, String sinkName) {
        Record normalized = new Record();
        for (ColumnConfig column : sink.columns) {
            Object value = record.get(column.name);
            if (isEmpty(value) && column.defaultValue != null) {
                value = column.defaultValue;
            }
            if (!isEmpty(value) && !TextUtils.isBlank(column.format)) {
                value = formatValue(column, String.valueOf(value));
            }
            if (isEmpty(value) && !column.nullable) {
                throw new ApiExtractException(sinkName + " required column is empty: " + column.name);
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
